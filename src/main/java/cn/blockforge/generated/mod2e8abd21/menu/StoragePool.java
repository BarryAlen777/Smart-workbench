package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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

    /** 把本地计数恢复到仓库当前数量（每个配方单独试一次时用）。 */
    public void reset() {
        for (int i = 0; i < this.size; i++) {
            this.available[i] = this.stacks[i].getCount();
        }
    }

    /**
     * 便宜判断：仓库里到底有没有这种材料。简单材料（不查 NBT）直接按物品查表，
     * 复杂的老老实实扫一遍。
     */
    public boolean mayContain(Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return true;
        }
        if (ingredient.isSimple()) {
            for (ItemStack preview : ingredient.getItems()) {
                List<Integer> list = this.byItem.get(preview.getItem());
                if (list != null) {
                    for (int candidate : list) {
                        if (this.available[candidate] > 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        return countMatching(ingredient) > 0;
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

    public int countMatching(Ingredient ingredient) {
        int count = 0;
        if (ingredient.isSimple()) {
            for (ItemStack preview : ingredient.getItems()) {
                List<Integer> candidates = this.byItem.get(preview.getItem());
                if (candidates == null) {
                    continue;
                }
                for (int candidate : candidates) {
                    if (this.available[candidate] > 0 && ingredient.test(this.stacks[candidate])) {
                        count++;
                    }
                }
            }
            return count;
        }
        for (int i = 0; i < this.size; i++) {
            if (this.available[i] > 0 && ingredient.test(this.stacks[i])) {
                count++;
            }
        }
        return count;
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
     */
    public int[] plan(NonNullList<Ingredient> ingredients) {
        int[] chosen = new int[ingredients.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            chosen[i] = -1;
            if (!ingredients.get(i).isEmpty()) {
                order.add(i);
            }
        }
        if (order.isEmpty()) {
            return null;
        }
        order.sort(Comparator.comparingInt(i -> countMatching(ingredients.get(i))));
        for (int i : order) {
            Ingredient ingredient = ingredients.get(i);
            int best = -1;
            for (int c = 0; c < this.size; c++) {
                if (this.available[c] > 0 && ingredient.test(this.stacks[c])) {
                    best = c;
                    break;
                }
            }
            if (best < 0) {
                return null;
            }
            this.available[best]--;
            chosen[i] = best;
        }
        return chosen;
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
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.stacks[i];
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack accumulated = null;
            for (ItemStack candidate : merged) {
                if (ItemStack.isSameItemSameTags(candidate, stack)) {
                    accumulated = candidate;
                    break;
                }
            }
            if (accumulated == null) {
                if (merged.size() >= limit) {
                    continue;
                }
                merged.add(stack.copy());
            } else {
                long total = (long) accumulated.getCount() + stack.getCount();
                accumulated.setCount((int) Math.min(Integer.MAX_VALUE, total));
            }
        }
        return merged;
    }
}
