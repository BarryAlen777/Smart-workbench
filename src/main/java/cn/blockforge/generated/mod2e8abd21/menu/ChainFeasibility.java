package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 链式可合成项的「数量级复核」。
 * <p>
 * 右侧列表的链式闭包只按<b>物品种类</b>判断「这种材料有没有」，不看数量够不够。
 * 于是会出现「铁砧＝3 铁块＋4 铁锭，库存铁够 27 个但不够 31 个」这类项：类型上全都有，
 * 列在表里，点下去却弹「缺少基础材料：铁锭x2」。这不是玩家操作问题，是列表虚报。
 * <p>
 * 这里用和点击时同一套算法按真实数量复算一遍：从库存出发，缺的中间材料沿配方树
 * 递归补做，能补出来才判定「做得到」。判定为做不到的链式项直接从列表里去掉，
 * 列表里剩下的每一项都能真的点出来。
 * <p>
 * 为了不给服务器添负担，这里做了四件事：
 * <ul>
 *   <li>只按「物品种类 → 总数」记账（不区分 NBT、不分槽位），查找是 O(1)，比点击路径的
 *       逐槽规划轻得多；代价是偶尔会多留一项，但绝不会把真能做的东西误删；</li>
 *   <li>账本直接从 {@link StoragePool#accumulateCounts} 聚合，不再整仓深拷贝一份
 *       ItemStack（优化方案三：旧版每轮复核前先 {@code snapshotStacks()} 逐个 copy()，
 *       大仓库一次几千份拷贝全打在扫描的那一秒上）；</li>
 *   <li>深度、展开步数沿用点击路径的配置上限 —— 点击会被这些上限拦住的项，这里也照样拦住，
 *       两边不会打架；</li>
 *   <li>整轮复核有一份总预算，单个候选还有一份小预算。预算用尽时返回
 *       {@link Verdict#UNKNOWN}，调用方按「保留」处理（退回旧行为），只慢不丢。</li>
 * </ul>
 */
final class ChainFeasibility {

    /** 复核结论。 */
    enum Verdict {
        /** 按当前库存真能做出来。 */
        OBTAINABLE,
        /** 数量上凑不出来，列在表里就是虚报，应当剔除。 */
        IMPOSSIBLE,
        /** 预算用尽，没算完；保守起见保留。 */
        UNKNOWN
    }

    private final Map<Item, List<RecipeFacts>> byResult;
    private final int maxDepth;
    private final int maxSteps;
    private final int perCandidateBudget;
    private final int totalBudget;

    /** 当前「可用数量」账本：初始是库存总数，规划过程中加加减减，每轮结束全部回滚。 */
    private final Map<Item, Long> counts = new HashMap<>();
    /** 撤销日志：每次增减都记一笔，回滚时倒着撤销，省掉整张表的拷贝。 */
    private final List<Item> undoItems = new ArrayList<>();
    private final List<Long> undoDeltas = new ArrayList<>();
    /** 正在展开的配方 ID，用来断环（A 需要 B、B 又需要 A）。 */
    private final Set<ResourceLocation> active = new HashSet<>();

    /** 本次复核的目标物品种类：补料配方只要用到它就直接跳过（防「拆自己」）。 */
    private Item blockedTarget;

    private int nodes;
    private int totalNodes;
    private int steps;
    private boolean madeIntermediate;
    private boolean exhausted;

    ChainFeasibility(StoragePool pool, Map<Item, List<RecipeFacts>> byResult,
                     int maxDepth, int maxSteps, int perCandidateBudget, int totalBudget) {
        this.byResult = byResult;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxSteps = Math.max(1, maxSteps);
        this.perCandidateBudget = Math.max(1, perCandidateBudget);
        this.totalBudget = Math.max(1, totalBudget);
        pool.accumulateCounts(this.counts);
    }

    /** 复核一份配方。同一实例可以连续调用，每次都会把账本还原到库存原值。 */
    Verdict check(RecipeFacts recipe) {
        // 预算用尽后也要先做一次「一眼假」判定。玩家报的「石头配方明明只能熔炉烧圆石，
        // 却被列成可链式」就是这一类：熔炉配方压根不在工作台配方索引里，无论给多少预算
        // 都补不出来。这条判定不递归、不花预算，所以预算见底时照样能如实剔除。
        if (quicklyImpossible(recipe)) {
            return Verdict.IMPOSSIBLE;
        }
        if (this.exhausted) {
            return Verdict.UNKNOWN;
        }
        this.nodes = 0;
        this.steps = 0;
        this.madeIntermediate = false;
        // 和点击规划同一套「防拆自己」规则：复核哪件物品，就不许任何补料配方吃掉它，
        // 否则列表会虚报「链式可合成」——点下去却只做出来一堆原地打转。
        this.blockedTarget = recipe.resultItem;
        this.active.clear();
        boolean ok = planRecipe(recipe, 0);
        // 这两个结论要在回滚之前取出来：rollbackAll 会把中间状态一并还原。
        boolean intermediate = this.madeIntermediate;
        rollbackAll();
        this.active.clear();
        if (this.exhausted) {
            return Verdict.UNKNOWN;
        }
        // 链式项的定义就是「直接材料不齐、要靠补做中间材料」：一次补做都没有，
        // 说明点击路径也会因为 autoCraftable 为空而失败，同样不该留在列表里。
        return ok && intermediate ? Verdict.OBTAINABLE : Verdict.IMPOSSIBLE;
    }

    /**
     * 不递归的「一眼假」判定：只要有一种材料在账本里一个都没有，并且它的任何候选物品
     * 都<b>没有工作台配方</b>能产出，这份配方就必然做不出来。只查一层、不看数量，
     * 因此是乐观的：算不出问题就放行，不会误删真能做的东西。
     */
    private boolean quicklyImpossible(RecipeFacts recipe) {
        for (int i = 0; i < recipe.ingredients.size(); i++) {
            if (recipe.ingredients.get(i).isEmpty()) {
                continue;
            }
            if (hasAvailable(recipe.candidateItems[i]) || hasAnyCraftingSource(recipe.previews[i])) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** 账本里这些种类还剩至少一个吗（按物品匹配，不看 NBT）。 */
    private boolean hasAvailable(Item[] items) {
        for (Item item : items) {
            if (this.counts.getOrDefault(item, 0L) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这份配方的任一直接材料，候选物品里是否包含本次目标物品。
     * 只看直接材料就够：材料链再深，最深处那条「吃目标」的配方被拦住后，
     * 上面各层要么换别的来源、要么整链失败，效果等价于整链跳过。
     */
    private boolean usesBlockedTarget(RecipeFacts recipe) {
        if (this.blockedTarget == null) {
            return false;
        }
        for (Item[] candidates : recipe.candidateItems) {
            for (Item item : candidates) {
                if (item == this.blockedTarget) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 这份材料的某个候选物品，有没有工作台配方能产出它（只查一层索引，不递归展开）。 */
    private boolean hasAnyCraftingSource(ItemStack[] previews) {
        for (ItemStack preview : previews) {
            for (RecipeFacts producer : this.byResult.getOrDefault(preview.getItem(), List.of())) {
                if (ItemStack.isSameItemSameTags(producer.result, preview)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 规划一份配方：材料先花库存，不够就找能产出它的普通配方补做。
     * 逻辑与 {@code SmartWorkbenchMenu#planRecipe} 对应，只是账本换成「物品 → 数量」。
     */
    private boolean planRecipe(RecipeFacts recipe, int depth) {
        if (this.exhausted) {
            return false;
        }
        if (++this.nodes > this.perCandidateBudget || ++this.totalNodes > this.totalBudget) {
            this.exhausted = true;
            return false;
        }
        if (depth > this.maxDepth || this.steps >= this.maxSteps || !recipe.plain) {
            return false;
        }
        // 「防拆自己」闸门（同点击规划）：补料配方的直接材料里出现目标物品，直接跳过。
        // 顶层那条（depth == 0）是玩家点的那一步本身，不受影响。
        if (depth > 0 && this.blockedTarget != null && usesBlockedTarget(recipe)) {
            return false;
        }
        ResourceLocation id = recipe.id;
        if (id != null && !this.active.add(id)) {
            return false;
        }
        Mark mark = mark();
        boolean complete = true;
        for (int i = 0; i < recipe.ingredients.size(); i++) {
            if (recipe.ingredients.get(i).isEmpty()) {
                continue;
            }
            if (!planIngredient(recipe, i, depth + 1)) {
                complete = false;
                break;
            }
        }
        if (complete) {
            if (recipe.resultItem == null || recipe.result.isEmpty()) {
                complete = false;
            } else {
                add(recipe.resultItem, recipe.result.getCount());
                this.steps++;
            }
        }
        if (id != null) {
            this.active.remove(id);
        }
        if (!complete) {
            restore(mark);
        }
        return complete;
    }

    /** 先花现成的，花不到就找配方补做一份。 */
    private boolean planIngredient(RecipeFacts recipe, int index, int depth) {
        ItemStack[] previews = recipe.previews[index];
        if (consume(previews)) {
            return true;
        }
        Set<Item> tried = new HashSet<>();
        for (ItemStack preview : previews) {
            if (preview.isEmpty() || !tried.add(preview.getItem())) {
                continue;
            }
            for (RecipeFacts dependency : this.byResult.getOrDefault(preview.getItem(), List.of())) {
                if (this.exhausted) {
                    return false;
                }
                ResourceLocation id = dependency.id;
                if (id == null || this.active.contains(id)) {
                    continue;
                }
                if (!ItemStack.isSameItemSameTags(dependency.result, preview)) {
                    continue;
                }
                Mark mark = mark();
                if (planRecipe(dependency, depth) && consume(previews)) {
                    this.madeIntermediate = true;
                    return true;
                }
                restore(mark);
            }
        }
        return false;
    }

    /** 从账本里扣掉一件匹配这份材料的物品。 */
    private boolean consume(ItemStack[] previews) {
        for (ItemStack preview : previews) {
            Item item = preview.getItem();
            if (this.counts.getOrDefault(item, 0L) > 0) {
                add(item, -1);
                return true;
            }
        }
        return false;
    }

    private void add(Item item, long delta) {
        if (delta == 0) {
            return;
        }
        this.counts.merge(item, delta, Long::sum);
        this.undoItems.add(item);
        this.undoDeltas.add(delta);
    }

    private Mark mark() {
        return new Mark(this.undoItems.size(), this.steps, this.madeIntermediate);
    }

    private void restore(Mark mark) {
        rollbackTo(mark.undoSize());
        this.steps = mark.steps();
        this.madeIntermediate = mark.intermediate();
    }

    private void rollbackAll() {
        rollbackTo(0);
        this.steps = 0;
        this.madeIntermediate = false;
    }

    private void rollbackTo(int size) {
        for (int i = this.undoItems.size() - 1; i >= size; i--) {
            Item item = this.undoItems.get(i);
            long remaining = this.counts.getOrDefault(item, 0L) - this.undoDeltas.get(i);
            if (remaining > 0) {
                this.counts.put(item, remaining);
            } else {
                this.counts.remove(item);
            }
        }
        if (size < this.undoItems.size()) {
            this.undoItems.subList(size, this.undoItems.size()).clear();
            this.undoDeltas.subList(size, this.undoDeltas.size()).clear();
        }
    }

    private record Mark(int undoSize, int steps, boolean intermediate) {
    }
}
