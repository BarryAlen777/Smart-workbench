package cn.blockforge.generated.mod2e8abd21.menu;

import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 一份合成配方的「派生数据」：材料表、产物、每个材料的候选物品、3x3 摆位、
 * 是否普通配方，全部在 {@link RecipeIndexCache} 建全局索引时算一次（优化方案一的一部分）。
 * <p>
 * 以前每秒的列表扫描、链式闭包、点击规划三条路都反复调
 * {@code getIngredients()} / {@code getResultItem()} / {@code ingredient.getItems()}——
 * 产物每问一次就拷贝一份 ItemStack，上万配方时全是白烧的 CPU 和 GC。
 * 现在热路径都改读这里预先算好的字段。
 * <p>
 * 不可变约定：{@link #result} 与 {@link #layout} 是索引独占的，绝不外流；
 * {@link #previews} 直接引用 Ingredient 内部的缓存数组，只读不改。
 */
final class RecipeFacts {

    private static final ItemStack[] NO_STACKS = new ItemStack[0];
    private static final Item[] NO_ITEMS = new Item[0];

    final CraftingRecipe recipe;
    /** 配方 ID（索引里只收 ID 非空的配方；临时构造的兜底实例可能为 null，用时判空）。 */
    final ResourceLocation id;
    final NonNullList<Ingredient> ingredients;
    /** 每个材料的候选预览堆（复用 Ingredient 内部缓存，当只读用）。空材料是零长数组。 */
    final ItemStack[][] previews;
    /** 每个材料的候选物品种类（按首现顺序去重）。 */
    final Item[][] candidateItems;
    /** 材料是否「纯物品」（无 NBT、无标签摊开）：true 时按种类命中基本等价于材料命中。 */
    final boolean[] simple;
    /** 产物（索引独占拷贝，交给客户端前必须先 copy）。可能是空堆（临时实例）。 */
    final ItemStack result;
    /** {@code result.getItem()}；产物为空时为 null。 */
    final Item resultItem;
    /** 每个材料该摆在 3x3 合成格的哪个格子上（原 gridLayout 的结果，算一次）。 */
    final int[] layout;
    /** 是否「普通工作台配方」（尺寸放得下、材料表完整）。只有临时实例可能为 false。 */
    final boolean plain;

    private RecipeFacts(CraftingRecipe recipe, ItemStack result, boolean plain) {
        this.recipe = recipe;
        this.id = recipe.getId();
        this.result = result;
        this.resultItem = result.isEmpty() ? null : result.getItem();
        this.plain = plain;
        NonNullList<Ingredient> list = recipe.getIngredients();
        this.ingredients = list;
        int n = list.size();
        this.previews = new ItemStack[n][];
        this.candidateItems = new Item[n][];
        this.simple = new boolean[n];
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            Ingredient ingredient = list.get(i);
            if (ingredient.isEmpty()) {
                this.previews[i] = NO_STACKS;
                this.candidateItems[i] = NO_ITEMS;
                continue;
            }
            ItemStack[] stacks = ingredient.getItems();
            this.previews[i] = stacks;
            this.simple[i] = ingredient.isSimple();
            seen.clear();
            for (ItemStack stack : stacks) {
                seen.add(stack.getItem());
            }
            if (seen.isEmpty()) {
                this.candidateItems[i] = NO_ITEMS;
            } else {
                this.candidateItems[i] = seen.toArray(new Item[0]);
            }
        }
        this.layout = plain ? gridLayout(recipe, list) : new int[n];
    }

    /** 临时兜底实例（重载后拿到旧配方对象时用），当场验一次「普通配方」。 */
    static RecipeFacts of(CraftingRecipe recipe, RegistryAccess registries) {
        return new RecipeFacts(recipe, recipe.getResultItem(registries), isPlainCraft(recipe));
    }

    /** 索引构建专用：产物和「普通配方」结论在外部已经算好，直接收。 */
    static RecipeFacts ofIndexed(CraftingRecipe recipe, ItemStack result) {
        return new RecipeFacts(recipe, result, true);
    }

    /** 链式闭包用：这份配方的每种材料，是否都至少有一个候选种类落在可达集合里（只看种类）。 */
    boolean reachableWith(Set<Item> reachable) {
        for (Item[] items : candidateItems) {
            if (items.length == 0) {
                continue;
            }
            boolean hit = false;
            for (Item item : items) {
                if (reachable.contains(item)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /* ==================== 以下从 SmartWorkbenchMenu 原样搬来（方案一/四） ==================== */

    /**
     * 判断一份配方能不能一键取料 / 批量合成：只要是能放进 3x3、材料表完整的合成配方就行。
     * <p>
     * <b>这里刻意不按 {@link CraftingRecipe#isSpecial()} 一刀切。</b>精妙存储的
     * {@code sophisticatedstorage:storage_tier_upgrade}（把木箱升级成铁箱/金箱/钻石箱，
     * 同时保留箱子里的物品）把自己标成 special，但它本质仍是一份 shaped 配方：材料表完整、
     * 结果由 {@code assemble} 在合成格里现算，完全可以一键摆料和批量合成。
     * <p>
     * 真正没法自动化的特殊配方（修装备、复制旗帜、染色、烟花、地图扩展等）都是
     * {@code CustomRecipe} 的子类，它们的材料表本来就是空的，会被 {@code ingredients.isEmpty()}
     * 拦住；即便有别的模组给特殊配方写了材料表，取料后也会在 {@code recipe.matches} 复核，
     * 不合格就退款并如实提示，不会凭空产出。
     */
    static boolean isPlainCraft(CraftingRecipe recipe) {
        if (!recipe.canCraftInDimensions(GuiLayout.GRID_COLS, GuiLayout.GRID_ROWS)) {
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return false;
        }
        // 材料表比 3x3 的格子还多：畸形配方（个别模组的 IShapedRecipe 尺寸和材料表对不上）
        // 会让 gridLayout 回退成「依次摆」，多出来的材料被夹到最后一格——前面取出的那份
        // 还没进格子就被覆盖，等于白吞玩家的材料。这种配方直接不接一键取料。
        if (ingredients.size() > GuiLayout.GRID_COLS * GuiLayout.GRID_ROWS) {
            return false;
        }
        int width = recipeWidth(recipe);
        int height = recipeHeight(recipe, width, ingredients.size());
        return width <= GuiLayout.GRID_COLS && height <= GuiLayout.GRID_ROWS;
    }

    /**
     * 特殊配方自匹配复核：把「每种材料的第一候选」按一键取料的摆法放进 3x3，
     * 问配方本身 {@code matches()} 认不认。
     * <p>
     * 背景：精妙存储的 {@code double_chest_tier_upgrade}（双联箱子升档）在 JSON 里写的
     * 是「金箱子 + 钻石 + 钻石块」，产物图标和普通的钻石箱子配方一模一样；但它的
     * {@code matches()} 实际要求格子里那只箱子必须是<b>双联</b>金箱子——而双联箱子物品
     * 做不出配方。于是这份配方在列表里是「看着能做、点了永远缺料」的假项。
     * <p>
     * 普通（非 special）配方材料表就是全部条件，预览摆法必然匹配，不必复核；
     * 复核抛异常按不通过处理——那种配方连自己的材料表都不认，摆进真格子只会更糟。
     */
    static boolean matchesWithOwnPreviews(CraftingRecipe recipe, Level level, ServerPlayer feedbackPlayer) {
        if (!recipe.isSpecial()) {
            return true;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        NonNullList<ItemStack> items =
                NonNullList.withSize(SmartWorkbenchBlockEntity.GRID_SIZE, ItemStack.EMPTY);
        int[] layout = gridLayout(recipe, ingredients);
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] previews = ingredient.getItems();
            if (previews.length == 0) {
                return false;
            }
            int slot = layout[i];
            if (slot < 0 || slot >= SmartWorkbenchBlockEntity.GRID_SIZE || !items.get(slot).isEmpty()) {
                return false;
            }
            items.set(slot, previews[0].copyWithCount(1));
        }
        PreviewGrid grid = new PreviewGrid(items, GuiLayout.GRID_COLS, GuiLayout.GRID_ROWS);
        // 复核完恢复调用前的合成玩家上下文（审查报告问题⑤）：直接置 null 会把
        // 外层已设置的身份抹掉，嵌套调用时后面的判断就在「无玩家」状态下跑了。
        Player previousCraftingPlayer = ForgeHooks.getCraftingPlayer();
        try {
            ForgeHooks.setCraftingPlayer(feedbackPlayer);
            return recipe.matches(grid, level);
        } catch (Throwable ignored) {
            return false;
        } finally {
            ForgeHooks.setCraftingPlayer(previousCraftingPlayer);
        }
    }

    static int recipeWidth(CraftingRecipe recipe) {
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shaped.getRecipeWidth();
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth();
        }
        return GuiLayout.GRID_COLS;
    }

    static int recipeHeight(CraftingRecipe recipe, int width, int ingredientCount) {
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shaped.getRecipeHeight();
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getHeight();
        }
        return width <= 0 ? ingredientCount : (ingredientCount + width - 1) / width;
    }

    /** 这个配方该摆在 3x3 的哪些格子上，返回每个材料对应的格子序号。 */
    static int[] gridLayout(CraftingRecipe recipe, NonNullList<Ingredient> ingredients) {
        int columns = GuiLayout.GRID_COLS;
        int rows = GuiLayout.GRID_ROWS;
        int[] positions = new int[ingredients.size()];
        int width = recipeWidth(recipe);
        int height = recipeHeight(recipe, width, ingredients.size());
        boolean shaped = recipe instanceof ShapedRecipe || recipe instanceof IShapedRecipe<?>;
        // 第三方 IShapedRecipe 的 getIngredients().size() 可能和 width*height 对不上；
        // 这种畸形配方按形状摆会算到 3x3 外面（IndexOutOfBounds），直接退回“依次摆”。
        if (!shaped || width <= 0 || height <= 0 || width > columns || height > rows
                || width * height != ingredients.size()) {
            for (int i = 0; i < positions.length; i++) {
                positions[i] = Math.min(i, SmartWorkbenchBlockEntity.GRID_SIZE - 1);
            }
            return positions;
        }
        int offsetX = (columns - width) / 2;
        int offsetY = (rows - height) / 2;
        for (int i = 0; i < positions.length; i++) {
            int slot = (offsetY + i / width) * columns + offsetX + i % width;
            positions[i] = Math.max(0, Math.min(SmartWorkbenchBlockEntity.GRID_SIZE - 1, slot));
        }
        return positions;
    }

    /**
     * 复核专用的临时合成格：TransientCraftingContainer 需要一个菜单实例，
     * 而全局索引建在菜单之外，这里自备一个不依赖菜单的最小实现。
     */
    private static final class PreviewGrid implements CraftingContainer {
        private final NonNullList<ItemStack> items;
        private final int width;
        private final int height;

        private PreviewGrid(NonNullList<ItemStack> items, int width, int height) {
            this.items = items;
            this.width = width;
            this.height = height;
        }

        @Override
        public int getContainerSize() {
            return this.items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : this.items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(count, stack.getCount());
            ItemStack out = stack.copyWithCount(taken);
            stack.shrink(taken);
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
            }
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = this.items.get(slot);
            this.items.set(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.items.set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            this.items.replaceAll(ignored -> ItemStack.EMPTY);
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public List<ItemStack> getItems() {
            return this.items;
        }

        // CraftingContainer 在 1.20.1 还要求实现这个：把格子内容报给「可堆叠内容」统计。
        // 临时复核格只在 matchesWithOwnPreviews 里用来跑一次配方匹配，这里照原版
        // TransientCraftingContainer 的做法逐格登记即可。
        @Override
        public void fillStackedContents(StackedContents contents) {
            for (ItemStack stack : this.items) {
                contents.accountSimpleStack(stack);
            }
        }
    }
}
