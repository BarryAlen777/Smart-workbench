package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
 * 这里用和点击时同一套算法{@link SmartWorkbenchMenu#planRecipe}按真实数量复算一遍：
 * 从库存出发，缺的中间材料沿配方树递归补做，能补出来才判定「做得到」。
 * 判定为做不到的链式项直接从列表里去掉，列表里剩下的每一项都能真的点出来。
 * <p>
 * 为了不给服务器添负担，这里做了三件事：
 * <ul>
 *   <li>只按「物品种类 → 总数」记账（不区分 NBT、不分槽位），查找是 O(1)，比点击路径的
 *       逐槽规划轻得多；代价是偶尔会多留一项，但绝不会把真能做的东西误删；</li>
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

    private final Map<Item, List<CraftingRecipe>> byResult;
    private final RegistryAccess registries;
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

    private int nodes;
    private int totalNodes;
    private int steps;
    private boolean madeIntermediate;
    private boolean exhausted;

    ChainFeasibility(List<ItemStack> initial, Map<Item, List<CraftingRecipe>> byResult,
                     RegistryAccess registries, int maxDepth, int maxSteps,
                     int perCandidateBudget, int totalBudget) {
        this.byResult = byResult;
        this.registries = registries;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxSteps = Math.max(1, maxSteps);
        this.perCandidateBudget = Math.max(1, perCandidateBudget);
        this.totalBudget = Math.max(1, totalBudget);
        for (ItemStack stack : initial) {
            if (!stack.isEmpty()) {
                this.counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            }
        }
    }

    /** 整轮复核的预算是否已经用尽；用尽后调用方应当停止复核、保留剩余项。 */
    boolean isExhausted() {
        return this.exhausted;
    }

    /** 复核一份配方。同一实例可以连续调用，每次都会把账本还原到库存原值。 */
    Verdict check(CraftingRecipe recipe) {
        if (this.exhausted) {
            return Verdict.UNKNOWN;
        }
        this.nodes = 0;
        this.steps = 0;
        this.madeIntermediate = false;
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
     * 规划一份配方：材料先花库存，不够就找能产出它的普通配方补做。
     * 逻辑与 {@link SmartWorkbenchMenu#planRecipe} 对应，只是账本换成「物品 → 数量」。
     */
    private boolean planRecipe(CraftingRecipe recipe, int depth) {
        if (this.exhausted) {
            return false;
        }
        if (++this.nodes > this.perCandidateBudget || ++this.totalNodes > this.totalBudget) {
            this.exhausted = true;
            return false;
        }
        if (depth > this.maxDepth || this.steps >= this.maxSteps || recipe == null) {
            return false;
        }
        ResourceLocation id = recipe.getId();
        if (id != null && !this.active.add(id)) {
            return false;
        }
        Mark mark = mark();
        boolean complete = true;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            if (!planIngredient(ingredient, depth + 1)) {
                complete = false;
                break;
            }
        }
        if (complete) {
            ItemStack result = recipe.getResultItem(this.registries);
            if (!result.isEmpty()) {
                add(result.getItem(), result.getCount());
                this.steps++;
            } else {
                complete = false;
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
    private boolean planIngredient(Ingredient ingredient, int depth) {
        if (ingredient.isEmpty()) {
            return true;
        }
        if (consume(ingredient)) {
            return true;
        }
        Set<Item> tried = new HashSet<>();
        for (ItemStack preview : ingredient.getItems()) {
            if (preview.isEmpty() || !tried.add(preview.getItem())) {
                continue;
            }
            Item wanted = preview.getItem();
            for (CraftingRecipe dependency : this.byResult.getOrDefault(wanted, List.of())) {
                if (this.exhausted) {
                    return false;
                }
                ResourceLocation id = dependency.getId();
                if (id == null || this.active.contains(id)) {
                    continue;
                }
                ItemStack result = dependency.getResultItem(this.registries);
                if (result.isEmpty() || !ItemStack.isSameItemSameTags(result, preview)) {
                    continue;
                }
                Mark mark = mark();
                if (planRecipe(dependency, depth) && consume(ingredient)) {
                    this.madeIntermediate = true;
                    return true;
                }
                restore(mark);
            }
        }
        return false;
    }

    /** 从账本里扣掉一件匹配这份材料的物品。 */
    private boolean consume(Ingredient ingredient) {
        for (ItemStack preview : ingredient.getItems()) {
            if (preview.isEmpty()) {
                continue;
            }
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
