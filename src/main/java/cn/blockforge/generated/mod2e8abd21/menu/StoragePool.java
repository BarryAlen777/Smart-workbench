package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把所有接入的存储拍平成一排“候选格子”，用来模拟「这点材料够不够合成一次」，
 * 以及真正取料时的定位。分配时先满足“可选项最少”的材料，减少通用材料被提前吃光。
 */
public final class StoragePool {

    /** 一件从接入存储取出的材料的原始位置，用于切换配方时原路退回。 */
    public record Source(IItemHandler handler, int slot) {
    }

    private final List<IItemHandler> handlers;
    private final int[] handlerIndex;
    private final int[] slotIndex;
    private final ItemStack[] stacks;
    private final int[] available;
    private final int size;
    private final Map<Item, List<Integer>> byItem;

    private StoragePool(List<IItemHandler> handlers, int[] handlerIndex, int[] slotIndex,
                        ItemStack[] stacks, int[] available, Map<Item, List<Integer>> byItem) {
        this.handlers = handlers;
        this.handlerIndex = handlerIndex;
        this.slotIndex = slotIndex;
        this.stacks = stacks;
        this.available = available;
        this.byItem = byItem;
        this.size = stacks.length;
    }

    public static StoragePool collect(List<IItemHandler> handlers) {
        List<Integer> handlerList = new ArrayList<>();
        List<Integer> slotList = new ArrayList<>();
        List<ItemStack> stackList = new ArrayList<>();
        Map<Item, List<Integer>> itemMap = new HashMap<>();
        // 同一个物理格子可能被两个处理器读到：原版大箱子的两半各自查询能力时，
        // 拿到的都是「整个合并库存」的包装器，同一格会被读两遍。这里按物品栈对象本身去重
        // （同一个格子两次读到的是同一个 ItemStack 实例），否则材料数量会凭空翻倍。
        Set<ItemStack> seenStacks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int h = 0; h < handlers.size(); h++) {
            IItemHandler handler = handlers.get(h);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !seenStacks.add(stack)) {
                    continue;
                }
                handlerList.add(h);
                slotList.add(slot);
                stackList.add(stack);
                itemMap.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(stackList.size() - 1);
            }
        }
        int size = stackList.size();
        int[] handlerIndex = new int[size];
        int[] slotIndex = new int[size];
        int[] available = new int[size];
        for (int i = 0; i < size; i++) {
            handlerIndex[i] = handlerList.get(i);
            slotIndex[i] = slotList.get(i);
            available[i] = stackList.get(i).getCount();
        }
        return new StoragePool(handlers, handlerIndex, slotIndex, stackList.toArray(new ItemStack[0]),
                available, itemMap);
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    /**
     * 库存内容的廉价指纹：物品、数量、NBT 和槽位顺序全揉进一个 64 位数。
     * <p>
     * 用来判断「这次扫描和上次相比变没变」——一样就不必把上千份配方重新匹配一遍，
     * 大整合包里每秒一次的列表刷新就不会白烧服务器 CPU。
     */
    public long fingerprint() {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.stacks[i];
            if (stack.isEmpty()) {
                continue;
            }
            long h = stack.getCount();
            h = h * 31L + stack.getItem().getDescriptionId().hashCode();
            if (stack.getTag() != null) {
                h = h * 31L + stack.getTag().hashCode();
            }
            hash ^= h;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * 比 {@link #fingerprint()} 更“钝”的指纹：只看有哪些种类（按物品+NBT 聚合）、
     * 每种封顶 9 个，并且<b>与东西摆在哪个箱子哪个格子完全无关</b>。
     * <p>
     * 可合成列表只回答「这份配方能不能做一次」，而一次合成最多用到 9 个同种材料，
     * 所以按种类聚合后夹到 9，指纹没变就代表列表结果不会变——两种抖动都不再重发整表：
     * 数量大数来回波动（漏斗抽走几块、农场往里塞几个），以及把同一批东西在箱子之间搬来搬去
     * （审查报告问题 4／优化第 4 条：旧版按槽位顺序异或，A 搬 B 总量没变指纹照变，
     * 大仓库里整张可合成表被白白重发）。
     * <p>
     * 顺序无关的做法：先聚合成「种类 → 封顶总数」，每种的混合值走 splitmix64 终混，
     * 再<b>相加</b>（加法可交换，与遍历顺序无关）。同一物品拆成两堆还是一堆、
     * 摆在哪个格子里，指纹都一样。
     */
    public long listFingerprint() {
        // 聚合只在指纹计算这一小段用；每秒最多一次、每次一遍槽位，开销可以忽略。
        Map<Item, Map<CompoundTag, Integer>> totals = new HashMap<>();
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.stacks[i];
            if (stack.isEmpty()) {
                continue;
            }
            Map<CompoundTag, Integer> byTag =
                    totals.computeIfAbsent(stack.getItem(), k -> new HashMap<>());
            CompoundTag tag = stack.getTag();
            int capped = Math.min(9, byTag.getOrDefault(tag, 0) + Math.min(stack.getCount(), 9));
            byTag.put(tag, capped);
        }
        long hash = 0x9E3779B97F4A7C15L;
        for (Map.Entry<Item, Map<CompoundTag, Integer>> byItem : totals.entrySet()) {
            long itemId = byItem.getKey().getDescriptionId().hashCode();
            for (Map.Entry<CompoundTag, Integer> byTag : byItem.getValue().entrySet()) {
                long h = byTag.getValue() + 1L;
                h = h * 31L + itemId;
                if (byTag.getKey() != null) {
                    h = h * 31L + byTag.getKey().hashCode();
                }
                // splitmix64 终混：把「某一种数量差 1」这类小差异彻底打散再相加，
                // 免得相邻种类的小差值在加法里互相抵消。
                h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
                h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
                h ^= (h >>> 31);
                hash += h;
            }
        }
        return hash;
    }

    /** 规划递归配方时使用的只读库存副本，不会触碰真实存储。 */
    public List<ItemStack> snapshotStacks() {
        List<ItemStack> result = new ArrayList<>(this.size);
        for (ItemStack stack : this.stacks) {
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    /** 库存里出现过哪些物品种类（只看种类、不看数量），给链式可达性闭包用。 */
    public Set<Item> distinctItems() {
        return new HashSet<>(this.byItem.keySet());
    }

    /** 按「物品 → 总数量」把整仓聚合进目标账本（链式复核用，省掉逐份 stack 的深拷贝）。 */
    public void accumulateCounts(Map<Item, Long> target) {
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.stacks[i];
            if (!stack.isEmpty()) {
                target.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            }
        }
    }

    /** 找一个还剩料、且能匹配这个材料的候选格下标，没有则返回 -1。 */
    public int find(Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return -1;
        }
        // 普通物品配方直接走物品索引；带 NBT 或标签的 Ingredient 再回退到全表，
        // 避免每个材料都从头扫描所有容器槽位。
        if (ingredient.isSimple()) {
            for (ItemStack preview : ingredient.getItems()) {
                List<Integer> candidates = this.byItem.get(preview.getItem());
                if (candidates == null) {
                    continue;
                }
                for (int candidate : candidates) {
                    if (this.available[candidate] > 0 && ingredient.test(this.stacks[candidate])) {
                        return candidate;
                    }
                }
            }
            return -1;
        }
        for (int c = 0; c < this.size; c++) {
            if (this.available[c] > 0 && ingredient.test(this.stacks[c])) {
                return c;
            }
        }
        return -1;
    }

    /** 仓库里这种材料一共还有多少个，用来做界面提示。 */
    public int countAvailable(Ingredient ingredient) {
        int total = 0;
        if (ingredient.isSimple()) {
            for (ItemStack preview : ingredient.getItems()) {
                List<Integer> candidates = this.byItem.get(preview.getItem());
                if (candidates == null) {
                    continue;
                }
                for (int candidate : candidates) {
                    if (this.available[candidate] > 0 && ingredient.test(this.stacks[candidate])) {
                        total += this.available[candidate];
                    }
                }
            }
            return Math.min(998, total);
        }
        for (int i = 0; i < this.size; i++) {
            if (this.available[i] > 0 && ingredient.test(this.stacks[i])) {
                total += this.available[i];
            }
        }
        return Math.min(998, total);
    }

    /**
     * 模拟消耗一次合成：返回每个材料格选中的候选格子下标（空材料为 -1），缺料时返回 null。
     * 只改动本地计数，不会真的动存储。
     * <p>
     * 方案二改造：①找格子走物品索引（原来每个材料都把全部候选格从头扫一遍，
     * 大仓库 × 上万配方是每秒扫描的大头）；②失败时自动把已扣的计数原路退回——
     * 旧版失败留下半截扣减、调用方每份配方前都要整表 reset() 补救，
     * 现在可用计数对外恒定，热路径一次整表拷贝都不用。
     */
    public int[] plan(NonNullList<Ingredient> ingredients) {
        int n = ingredients.size();
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!ingredients.get(i).isEmpty()) {
                m++;
            }
        }
        if (m == 0) {
            return null;
        }
        ensureScratch(m);
        for (int i = 0, w = 0; i < n; i++) {
            if (ingredients.get(i).isEmpty()) {
                continue;
            }
            this.scratchOrder[w] = i;
            // 「可选项最少」的先分配，减少通用材料被提前吃光；
            // 计数只用来排序，超过材料种数就和上限没区别，够数就提前收工。
            this.scratchKeys[w] = countMatchingCapped(ingredients.get(i), m);
            w++;
        }
        sortScratch(m);
        int[] chosen = new int[n];
        Arrays.fill(chosen, -1);
        for (int oi = 0; oi < m; oi++) {
            int i = this.scratchOrder[oi];
            int best = find(ingredients.get(i));
            if (best < 0) {
                release(chosen);
                return null;
            }
            this.available[best]--;
            chosen[i] = best;
        }
        return chosen;
    }

    /** {@link #plan(NonNullList)} 的索引快路径版：直接吃配方索引预计算好的候选种类/简易标记。 */
    public int[] plan(RecipeFacts facts) {
        NonNullList<Ingredient> ingredients = facts.ingredients;
        int n = ingredients.size();
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (!ingredients.get(i).isEmpty()) {
                m++;
            }
        }
        if (m == 0) {
            return null;
        }
        ensureScratch(m);
        for (int i = 0, w = 0; i < n; i++) {
            if (ingredients.get(i).isEmpty()) {
                continue;
            }
            this.scratchOrder[w] = i;
            this.scratchKeys[w] = optionCount(facts, i, m);
            w++;
        }
        sortScratch(m);
        int[] chosen = new int[n];
        Arrays.fill(chosen, -1);
        for (int oi = 0; oi < m; oi++) {
            int i = this.scratchOrder[oi];
            int best = locate(facts, i);
            if (best < 0) {
                release(chosen);
                return null;
            }
            this.available[best]--;
            chosen[i] = best;
        }
        return chosen;
    }

    /**
     * 纯可行性试算（列表扫描用）：够做一次返回 true，并且<b>不留任何痕迹</b>——
     * 试完立刻把扣掉的本地计数原路放回，下一份配方直接接着试，
     * 替代了旧版「每份配方先 reset() 整表 + plan() 泄漏扣减」的组合。
     */
    public boolean canCraft(NonNullList<Ingredient> ingredients) {
        int[] chosen = plan(ingredients);
        if (chosen == null) {
            return false;
        }
        release(chosen);
        return true;
    }

    public boolean canCraft(RecipeFacts facts) {
        int[] chosen = plan(facts);
        if (chosen == null) {
            return false;
        }
        release(chosen);
        return true;
    }

    /** 把一次 plan 已扣的本地计数原路放回（只放回真正扣过的槽位，开销跟材料数走，不跟库存走）。 */
    public void release(int[] chosen) {
        for (int candidate : chosen) {
            if (candidate >= 0) {
                this.available[candidate]++;
            }
        }
    }

    /** 索引快路径版找格子：简单材料按候选种类查 byItem；复杂材料才老实全扫。 */
    private int locate(RecipeFacts facts, int i) {
        Ingredient ingredient = facts.ingredients.get(i);
        if (facts.simple[i]) {
            for (Item item : facts.candidateItems[i]) {
                List<Integer> candidates = this.byItem.get(item);
                if (candidates == null) {
                    continue;
                }
                for (int candidate : candidates) {
                    if (this.available[candidate] <= 0) {
                        continue;
                    }
                    ItemStack stack = this.stacks[candidate];
                    // 按物品命中：槽位堆没 NBT 时和 Ingredient.test 完全等价（简易材料的
                    // 候选预览都不带 NBT），省一次 test 的类型比较循环。
                    if (!stack.hasTag() || ingredient.test(stack)) {
                        return candidate;
                    }
                }
            }
            return -1;
        }
        for (int c = 0; c < this.size; c++) {
            if (this.available[c] > 0 && ingredient.test(this.stacks[c])) {
                return c;
            }
        }
        return -1;
    }

    /** 这个材料有多少个可选槽位（只用于「谁最紧俏」排序）；够上限就提前返回，不数满。 */
    private int optionCount(RecipeFacts facts, int i, int cap) {
        if (facts.simple[i]) {
            int count = 0;
            for (Item item : facts.candidateItems[i]) {
                List<Integer> slots = this.byItem.get(item);
                if (slots == null) {
                    continue;
                }
                for (int candidate : slots) {
                    if (this.available[candidate] > 0) {
                        if (++count >= cap) {
                            return cap;
                        }
                    }
                }
            }
            return count;
        }
        return countMatchingCapped(facts.ingredients.get(i), cap);
    }

    private int countMatchingCapped(Ingredient ingredient, int cap) {
        int count = 0;
        if (ingredient.isSimple()) {
            for (ItemStack preview : ingredient.getItems()) {
                List<Integer> candidates = this.byItem.get(preview.getItem());
                if (candidates == null) {
                    continue;
                }
                for (int candidate : candidates) {
                    if (this.available[candidate] > 0 && ingredient.test(this.stacks[candidate])
                            && ++count >= cap) {
                        return cap;
                    }
                }
            }
            return count;
        }
        for (int i = 0; i < this.size; i++) {
            if (this.available[i] > 0 && ingredient.test(this.stacks[i]) && ++count >= cap) {
                return cap;
            }
        }
        return count;
    }

    /** 排序用的两块临时下标/键值缓冲：材料最多 9 个，实例上留一份就够，热循环零分配。 */
    private int[] scratchOrder = new int[0];
    private int[] scratchKeys = new int[0];

    private void ensureScratch(int n) {
        if (this.scratchOrder.length < n) {
            this.scratchOrder = new int[n];
            this.scratchKeys = new int[n];
        }
    }

    /** 材料数最多 9，插入 sort 比 Comparator + TimSort 装箱便宜得多。 */
    private void sortScratch(int m) {
        for (int a = 1; a < m; a++) {
            int key = this.scratchKeys[a];
            int idx = this.scratchOrder[a];
            int b = a - 1;
            while (b >= 0 && this.scratchKeys[b] > key) {
                this.scratchKeys[b + 1] = this.scratchKeys[b];
                this.scratchOrder[b + 1] = this.scratchOrder[b];
                b--;
            }
            this.scratchKeys[b + 1] = key;
            this.scratchOrder[b + 1] = idx;
        }
    }

    public Source source(int candidate) {
        if (candidate < 0 || candidate >= this.size) {
            return null;
        }
        return new Source(this.handlers.get(this.handlerIndex[candidate]), this.slotIndex[candidate]);
    }

    /** 真正从对应的存储里取出物品。 */
    public ItemStack take(int candidate, int count) {
        if (candidate < 0 || candidate >= this.size || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = this.handlers.get(this.handlerIndex[candidate])
                .extractItem(this.slotIndex[candidate], count, false);
        if (!extracted.isEmpty()) {
            this.available[candidate] = Math.max(0, this.available[candidate] - extracted.getCount());
        }
        return extracted;
    }

    /** 规划阶段已经扣过本地库存计数，实际取料时不能再扣一次。 */
    public ItemStack takePlanned(int candidate, int count) {
        if (candidate < 0 || candidate >= this.size || count <= 0) {
            return ItemStack.EMPTY;
        }
        return this.handlers.get(this.handlerIndex[candidate])
                .extractItem(this.slotIndex[candidate], count, false);
    }

    /** 取料中途失败时把东西塞回原来的存储，返回塞不下的部分。 */
    public ItemStack giveBack(int candidate, ItemStack stack) {
        if (candidate < 0 || candidate >= this.size || stack.isEmpty()) {
            return stack;
        }
        return this.handlers.get(this.handlerIndex[candidate])
                .insertItem(this.slotIndex[candidate], stack, false);
    }

    /**
     * 给客户端用的快照：同类物品合并成一条（数量为总数），最多保留 limit 种。
     * 只读不消耗，数量取 collect 时的原值。REI 靠它判断「箱子里的材料够不够」。
     */
    public List<ItemStack> mergeForClient(int limit) {
        return mergeForClientSnapshot(limit).stacks();
    }

    /** 快照，以及「是否因为超过 limit 被截断」——截断时客户端不能把「缺料」当真。 */
    public record ClientSnapshot(List<ItemStack> stacks, boolean truncated) {
    }

    /**
     * 带截断标记的快照。超过 limit 种物品时列表是不完整的，客户端拿到后
     * 必须降级为「不阻断、交给服务端裁决」，否则大仓库会误报缺料。
     */
    public ClientSnapshot mergeForClientSnapshot(int limit) {
        return mergeForClientSnapshotPrefix(this.handlers.size(), limit);
    }

    /**
     * 只把<b>前 {@code handlerLimit} 路处理器</b>的货并进快照（其余照旧能参与别的计算）。
     * {@code scanHandlers()} 的排布是「接入存储在前、玩家背包和合成格垫后」，
     * 传 {@code storages.size()} 就正好截出"给 REI 的那份纯存储快照"——
     * 让调用方拿同一份池子办两件事，不必再为快照把全部箱子<b>扫第二遍</b>（每秒省一遍全仓库遍历）。
     */
    public ClientSnapshot mergeForClientSnapshotPrefix(int handlerLimit, int limit) {
        List<ItemStack> merged = new ArrayList<>();
        boolean truncated = false;
        // 按「物品 + NBT」分桶，别拿每种新物品去和已合并的列表逐个比：
        // 大仓库一次快照有几千个槽位，原来的 O(n²) 在每次刷新时都要跑一遍。
        Map<Item, Map<CompoundTag, ItemStack>> buckets = new HashMap<>();
        for (int i = 0; i < this.size; i++) {
            if (this.handlerIndex[i] >= handlerLimit) {
                continue;
            }
            ItemStack stack = this.stacks[i];
            if (stack.isEmpty()) {
                continue;
            }
            Map<CompoundTag, ItemStack> byTag =
                    buckets.computeIfAbsent(stack.getItem(), k -> new HashMap<>());
            ItemStack accumulated = byTag.get(stack.getTag());
            if (accumulated == null) {
                if (merged.size() >= limit) {
                    truncated = true;
                    continue;
                }
                ItemStack copy = stack.copy();
                merged.add(copy);
                byTag.put(copy.getTag(), copy);
            } else {
                long total = (long) accumulated.getCount() + stack.getCount();
                accumulated.setCount((int) Math.min(Integer.MAX_VALUE, total));
            }
        }
        return new ClientSnapshot(merged, truncated);
    }

    /**
     * 批量合成每轮都要知道「箱子里现在还剩多少」。整份重新 collect 会反复分配列表和索引，
     * 上千轮下来 GC 抖动明显；这里直接回读每个已知格子的当前数量（同一个同步包内
     * 世界不会 tick，只有我们自己抽走的材料会变）。
     * <p>
     * 如果某个格子的物品被换掉了（个别模组的包装器会替换 ItemStack），返回 false，
     * 调用方退回完整的 {@link #collect(List)}，宁可多扫一次也不拿错库存。
     */
    public boolean refreshCounts() {
        for (int i = 0; i < this.size; i++) {
            ItemStack live = this.handlers.get(this.handlerIndex[i]).getStackInSlot(this.slotIndex[i]);
            if (live.isEmpty() || !ItemStack.isSameItemSameTags(live, this.stacks[i])) {
                return false;
            }
            this.stacks[i] = live;
            this.available[i] = live.getCount();
        }
        return true;
    }
}
