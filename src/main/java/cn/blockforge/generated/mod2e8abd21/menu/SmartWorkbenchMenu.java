package cn.blockforge.generated.mod2e8abd21.menu;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.compat.MaterialAvailability;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import cn.blockforge.generated.mod2e8abd21.network.S2CCraftableListPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CRecursiveCraftPromptPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 智能工作台的容器菜单：3x3 合成格存在方块实体里（关界面不丢东西），
 * 右侧列表支持「从附近存储取料进合成格」和「直接从存储合成一次」。
 * <p>
 * 这里特意继承原版 {@link CraftingMenu}：REI（物品管理器）的默认配方转移处理器
 * 只认 {@code CraftingMenu} 和 {@code InventoryMenu} 这两种菜单，继承之后
 * REI 上点配方就能把材料直接摆进我们的合成格；否则它只会弹「此配方和容器不支持移动物品」。
 */
public class SmartWorkbenchMenu extends CraftingMenu {

    public static final int SLOT_RESULT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_END = GRID_START + SmartWorkbenchBlockEntity.GRID_SIZE;
    public static final int INV_START = GRID_END;
    public static final int INV_END = INV_START + 36;

    /** 界面上的「刷新」按钮（原版按钮通道，编号必须留在 -128~127 之内）。 */
    public static final int BUTTON_REFRESH = 0;
    /** 左侧「自动补齐」开关。 */
    public static final int BUTTON_AUTO_REFILL = 1;

    /** 合成格行列数也取自 GuiLayout，改 json 就能整体放大/缩小合成区 */
    private static final int COLUMNS = GuiLayout.GRID_COLS;
    private static final int ROWS = GuiLayout.GRID_ROWS;

    /** 发给客户端的存储快照最多保留多少种物品，防止精妙存储那种大仓库把包撑爆 */
    private static final int STORAGE_SNAPSHOT_LIMIT = 512;
    /** Ctrl 批量合成一次最多做多少个，防止点一下就把整仓库抽干或卡住 */
    private static final int MAX_BATCH_CRAFTS = 256;
    /** 递归补齐最多展开这么多次，避免错误配方或循环配方拖住服务器。 */
    private static final int MAX_RECURSIVE_STEPS = 256;
    private static final int MAX_RECURSIVE_DEPTH = 16;

    /** 客户端正在显示的“自动补齐”确认框；服务端不会依赖这些字段做权限判断。 */
    private ResourceLocation recursivePromptRecipeId;
    private List<S2CRecursiveCraftPromptPacket.MissingEntry> recursivePromptAuto = List.of();
    private List<S2CRecursiveCraftPromptPacket.MissingEntry> recursivePromptUnavailable = List.of();

    /** craftOnce 的结果：成功 / 缺料 / 配方不支持 / 背包满了（产物已掉在地上） */
    private static final int CRAFT_OK = 0;
    private static final int CRAFT_MISSING = 1;
    private static final int CRAFT_UNSUPPORTED = 2;
    private static final int CRAFT_INVENTORY_FULL = 3;
    /** 绑定了输出容器但容器满了，产物已退回背包，本次自动合成到此为止。 */
    private static final int CRAFT_OUTPUT_FULL = 4;

    private final Level level;
    private final Player player;
    private final BlockPos benchPos;
    private final SmartWorkbenchBlockEntity bench;
    private final CraftingContainer matrix;
    private final ResultContainer resultSlots = new ResultContainer();
    /** 合成格中由存储取来的材料来源；切换配方时优先退回原槽位。 */
    private final List<SourcePart>[] matrixSources = createSourceLists();
    private boolean updatingMatrix;

    @SuppressWarnings("unchecked")
    private static List<SourcePart>[] createSourceLists() {
        List<SourcePart>[] sources = (List<SourcePart>[]) new List<?>[SmartWorkbenchBlockEntity.GRID_SIZE];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = new ArrayList<>();
        }
        return sources;
    }

    private record SourcePart(ItemStack stack, StoragePool.Source source) {
    }

    private String matrixSignature = "";
    private List<CraftableEntry> craftables = List.of();
    /** 普通点击缺料时是否询问并自动递归补齐中间材料。 */
    private boolean autoRefillEnabled = true;
    /** 客户端专用的接入存储快照，给 REI 判断「原料够不够」用 */
    private List<ItemStack> clientStorageStacks = List.of();
    /** 客户端显示用；服务端的真实数量仍从方块实体读取。 */
    private int clientConnectedStorageCount;
    /** 客户端显示用：绑定的输出容器数量。 */
    private int clientOutputCount;
    private String listSignature = "";
    private String storageInputSignature = "";
    private long nextRefreshTick;
    /** 同一个菜单内复用普通合成配方索引，避免递归规划时反复遍历全量配方。 */
    private List<CraftingRecipe> plainRecipes;
    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> recipesByResult;
    private long recipeIndexVersion = Long.MIN_VALUE;
    /** 所有客户端操作共用一个服务端冷却，防止连点堆积主线程任务。 */
    private long nextActionTick;
    private CraftFeedback activeFeedback;

    /** 客户端工厂：从网络缓冲区读工作台坐标。 */
    public SmartWorkbenchMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public SmartWorkbenchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), pos));
        this.level = inventory.player.level();
        this.player = inventory.player;
        this.benchPos = pos;
        this.bench = this.level.getBlockEntity(pos) instanceof SmartWorkbenchBlockEntity entity ? entity : null;

        // 父类 CraftingMenu 的构造函数已经往 slots 里塞了一套它自己的槽位（用的是它内部的合成格），
        // 这里清掉，换成"以方块实体为合成格"的我们这一套。
        this.slots.clear();

        NonNullList<ItemStack> grid = this.bench != null
                ? this.bench.getItems()
                // 客户端区块没加载等情况用临时列表顶上，物品照样能靠服务端同步显示
                : NonNullList.withSize(SmartWorkbenchBlockEntity.GRID_SIZE, ItemStack.EMPTY);
        this.matrix = new TransientCraftingContainer(this, COLUMNS, ROWS, grid);

        // 槽位坐标全部引用 GuiLayout：它和 GUI 贴图出自同一份 gui_layout.json（见 tools/gen_gui_texture.py），
        // 所以贴图上的格子框一定落在这些坐标上，不会再出现"贴图和槽位错位"。
        this.addSlot(new WorkbenchResultSlot(this, this.player, this.resultSlots,
                GuiLayout.OUTPUT_ITEM_X, GuiLayout.OUTPUT_ITEM_Y));
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                this.addSlot(new MatrixSlot(this.matrix, col + row * COLUMNS,
                        GuiLayout.GRID_ITEM_X + col * GuiLayout.GRID_PITCH,
                        GuiLayout.GRID_ITEM_Y + row * GuiLayout.GRID_PITCH));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                // 背包槽位下标 9~35 是主背包，0~8 是快捷栏，别写成 col + row * 9，那样会漏掉 27~35 并把快捷栏显示两遍
                this.addSlot(new Slot(inventory, col + (row + 1) * 9,
                        GuiLayout.INV_ITEM_X + col * GuiLayout.INV_PITCH,
                        GuiLayout.INV_ITEM_Y + row * GuiLayout.INV_PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col,
                    GuiLayout.HOTBAR_ITEM_X + col * GuiLayout.HOTBAR_PITCH, GuiLayout.HOTBAR_ITEM_Y));
        }
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return SmartWorkbenchMenu.this.autoRefillEnabled ? 1 : 0;
            }

            @Override
            public void set(int value) {
                SmartWorkbenchMenu.this.autoRefillEnabled = value != 0;
            }
        });
    }

    /**
     * 菜单类型必须返回我们注册的那个：父类构造函数里写的是原版工作台，
     * 直接沿用它会让服务器告诉客户端"打开的是原版工作台"，界面就变成原版的了。
     */
    @Override
    public MenuType<?> getType() {
        return ModRegistries.SMART_WORKBENCH_MENU.get();
    }

    private final class MatrixSlot extends Slot {
        private MatrixSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void set(ItemStack stack) {
            if (!SmartWorkbenchMenu.this.updatingMatrix) {
                SmartWorkbenchMenu.this.matrixSources[this.getContainerSlot()].clear();
            }
            super.set(stack);
        }
    }

    /* 基本信息 */

    @Override
    public boolean stillValid(Player player) {
        return this.bench != null && this.bench.stillValid(player);
    }

    public BlockPos getBenchPos() {
        return this.benchPos;
    }

    public List<ItemStack> getMatrixItems() {
        return this.matrix.getItems();
    }

    public int getConnectedStorageCount() {
        return this.level.isClientSide
                ? this.clientConnectedStorageCount
                : (this.bench == null ? 0 : this.bench.getConnectedStorageCount());
    }

    public void setClientConnectedStorageCount(int count) {
        this.clientConnectedStorageCount = Math.max(0, count);
    }

    public int getOutputCount() {
        return this.level.isClientSide
                ? this.clientOutputCount
                : (this.bench == null ? 0 : this.bench.getOutputBindingCount());
    }

    public void setClientOutputCount(int count) {
        this.clientOutputCount = Math.max(0, count);
    }

    public boolean isAutoRefillEnabled() {
        return this.autoRefillEnabled;
    }

    public void setAutoRefillEnabled(boolean enabled) {
        this.autoRefillEnabled = enabled;
    }

    public List<CraftableEntry> getCraftables() {
        return this.craftables;
    }

    /** 服务器算好的列表推给客户端，客户端只负责显示。 */
    public void setClientCraftables(List<CraftableEntry> entries) {
        this.craftables = entries;
    }

    /** 服务器推来的接入存储快照（同类已合并），REI 的缺料判断会用到。 */
    public void setClientStorageStacks(List<ItemStack> stacks) {
        this.clientStorageStacks = stacks;
    }

    public List<ItemStack> getClientStorageStacks() {
        return this.clientStorageStacks;
    }

    public boolean hasRecursivePrompt() {
        return this.recursivePromptRecipeId != null;
    }

    public ResourceLocation getRecursivePromptRecipeId() {
        return this.recursivePromptRecipeId;
    }

    public List<S2CRecursiveCraftPromptPacket.MissingEntry> getRecursivePromptAuto() {
        return this.recursivePromptAuto;
    }

    public List<S2CRecursiveCraftPromptPacket.MissingEntry> getRecursivePromptUnavailable() {
        return this.recursivePromptUnavailable;
    }

    public void setRecursivePrompt(ResourceLocation recipeId,
                                   List<S2CRecursiveCraftPromptPacket.MissingEntry> autoCraftable,
                                   List<S2CRecursiveCraftPromptPacket.MissingEntry> unavailable) {
        this.recursivePromptRecipeId = recipeId;
        this.recursivePromptAuto = List.copyOf(autoCraftable);
        this.recursivePromptUnavailable = List.copyOf(unavailable);
    }

    public void clearRecursivePrompt() {
        this.recursivePromptRecipeId = null;
        this.recursivePromptAuto = List.of();
        this.recursivePromptUnavailable = List.of();
    }

    public Level getLevel() {
        return this.level;
    }

    /* 配方书 / REI 联动 */

    // 原版把“工作台”抽象成 RecipeBookMenu 家族。我们继承 CraftingMenu（见类注释），
    // 于是原版配方书和 REI 都会把我们当成合成台，配方上的“+”会走下面的 handlePlacement。

    @Override
    public void fillCraftSlotsStackedContents(StackedContents contents) {
        this.matrix.fillStackedContents(contents);
    }

    @Override
    public void clearCraftingContent() {
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            setMatrixInternal(slot, ItemStack.EMPTY);
        }
        clearMatrixSources();
        markDirty();
        this.matrixSignature = matrixSignature();
    }

    private void clearMatrixSources() {
        for (List<SourcePart> sources : this.matrixSources) {
            sources.clear();
        }
    }

    private void consumeOneSource(int slot) {
        List<SourcePart> sources = this.matrixSources[slot];
        for (int i = 0; i < sources.size(); i++) {
            SourcePart part = sources.get(i);
            if (part.stack().isEmpty()) {
                sources.remove(i--);
                continue;
            }
            part.stack().shrink(1);
            if (part.stack().isEmpty()) {
                sources.remove(i);
            }
            return;
        }
    }

    private void removeSourceCount(int slot, int count) {
        for (int i = 0; i < count; i++) {
            consumeOneSource(slot);
        }
    }

    private void clearMatrixSourcesAt(int slot) {
        this.matrixSources[slot].clear();
    }

    private void setMatrixInternal(int slot, ItemStack stack) {
        this.updatingMatrix = true;
        try {
            this.matrix.setItem(slot, stack);
        } finally {
            this.updatingMatrix = false;
        }
    }

    /** 菜单内部刷新格子时保留已记录的存储来源；玩家或 REI 直接写入仍会清掉来源。 */
    private void setMatrixFromMenu(int slot, ItemStack stack) {
        setMatrixInternal(slot, stack);
    }

    private void trimMatrixSources(int slot, int maxCount) {
        List<SourcePart> sources = this.matrixSources[slot];
        int total = 0;
        for (SourcePart part : sources) {
            total += part.stack().getCount();
        }
        for (int i = sources.size() - 1; total > maxCount && i >= 0; i--) {
            SourcePart part = sources.get(i);
            int remove = Math.min(part.stack().getCount(), total - maxCount);
            part.stack().shrink(remove);
            total -= remove;
            if (part.stack().isEmpty()) {
                sources.remove(i);
            }
        }
    }

    @Override
    public boolean recipeMatches(Recipe<? super CraftingContainer> recipe) {
        return recipe.matches(this.matrix, this.level);
    }

    @Override
    public int getResultSlotIndex() {
        return SLOT_RESULT;
    }

    @Override
    public int getGridWidth() {
        return COLUMNS;
    }

    @Override
    public int getGridHeight() {
        return ROWS;
    }

    @Override
    public int getSize() {
        return SmartWorkbenchBlockEntity.GRID_SIZE;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return true;
    }

    /**
     * 按“+”按钮（原版配方书或 REI）时走这里：材料不从玩家背包拿，而是从接入的存储取。
     * placeAll 为 true 表示尽量把每格摆满。
     */
    @Override
    public void handlePlacement(boolean placeAll, Recipe<?> recipe, ServerPlayer player) {
        if (this.level.isClientSide || this.bench == null
                || !(recipe instanceof CraftingRecipe crafting) || !isPlainCraft(crafting)) {
            // 特殊配方（修装备、复制旗帜之类）不接管
            return;
        }
        placeRecipeIntoGrid(player, crafting, placeAll);
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
    }

    /**
     * 把一份配方摆进合成格：
     * <ol>
     *   <li>格子里已经符合这份配方的材料留在原地接着用；</li>
     *   <li>不符合配方的材料全部退回玩家背包（背包塞不下就掉在脚下）；</li>
     *   <li>还缺的材料从接入的存储里取，placeAll 时尽量把每格补满。</li>
     * </ol>
     * 返回从存储里实际取出的件数。
     */
    private int placeRecipeIntoGrid(ServerPlayer player, CraftingRecipe recipe, boolean placeAll) {
        if (this.bench == null) {
            return 0;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int[] layout = gridLayout(recipe, ingredients);
        // wanted[格子] = 这个格子应该放第几个材料（-1 表示这格不该有东西）
        int[] wanted = new int[SmartWorkbenchBlockEntity.GRID_SIZE];
        Arrays.fill(wanted, -1);
        for (int i = 0; i < ingredients.size(); i++) {
            int slot = layout[i];
            if (slot >= 0 && slot < wanted.length && !ingredients.get(i).isEmpty()) {
                wanted[slot] = i;
            }
        }

        // 1) 先把工作台里已有的堆叠全部暂存。由存储取来的部分保留原槽位来源，
        //    切换配方后优先退回原容器；手动放入的部分才回玩家背包。
        List<TrackedStack> existing = new ArrayList<>();
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            ItemStack inSlot = this.matrix.getItem(slot).copy();
            int trackedCount = 0;
            for (SourcePart part : this.matrixSources[slot]) {
                int count = Math.min(part.stack().getCount(), Math.max(0, inSlot.getCount() - trackedCount));
                if (count > 0) {
                    existing.add(new TrackedStack(part.stack().copyWithCount(count), part.source()));
                    trackedCount += count;
                }
            }
            int manualCount = inSlot.getCount() - trackedCount;
            if (manualCount > 0) {
                existing.add(new TrackedStack(inSlot.copyWithCount(manualCount), null));
            }
            this.matrixSources[slot].clear();
            setMatrixFromMenu(slot, ItemStack.EMPTY);
        }

        // 2) 缺的从接入的存储里补；工作台已有材料优先，真正多出来的最后才退回原容器。
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(this.bench.getStorages());
        int pulled = 0;
        ForgeHooks.setCraftingPlayer(player);
        try {
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                int slot = layout[i];
                if (ingredient.isEmpty() || slot < 0 || slot >= wanted.length) {
                    continue;
                }
                int targetCount = placeAll ? maxIngredientStackSize(ingredient) : 1;
                ItemStack target = this.matrix.getItem(slot);
                while (target.getCount() < targetCount) {
                    int need = targetCount - target.getCount();
                    TrackedStack reused = takeMatching(existing, ingredient, Math.min(need, 1));
                    if (reused == null) {
                        int candidate = pool.find(ingredient);
                        if (candidate < 0) {
                            break;
                        }
                        ItemStack extracted = pool.take(candidate, 1);
                        if (extracted.isEmpty()) {
                            break;
                        }
                        reused = new TrackedStack(extracted, pool.source(candidate));
                        pulled++;
                    }
                    ItemStack reusedStack = reused.stack();
                    if (target.isEmpty()) {
                        target = reusedStack;
                    } else if (ItemStack.isSameItemSameTags(target, reusedStack)
                            && target.getCount() + reusedStack.getCount() <= target.getMaxStackSize()) {
                        target.grow(reusedStack.getCount());
                    } else {
                        // 一个目标格不能容纳不兼容的堆叠，放回暂存列表，避免丢失。
                        existing.add(reused);
                        break;
                    }
                    if (reused.source() != null) {
                        this.matrixSources[slot].add(new SourcePart(reusedStack.copy(), reused.source()));
                    }
                    if (!placeAll) {
                        break;
                    }
                }
                if (!target.isEmpty()) {
                    setMatrixFromMenu(slot, target);
                }
            }
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }

        // 3) 还没有被新配方用掉的旧材料优先回到原提取槽位，手动材料才回玩家背包。
        for (TrackedStack leftover : existing) {
            if (leftover.stack().isEmpty()) {
                continue;
            }
            ItemStack rest = leftover.source() == null ? leftover.stack()
                    : leftover.source().handler().insertItem(leftover.source().slot(), leftover.stack(), false);
            if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                player.drop(rest, false);
            }
        }
        return pulled;
    }

    private record TrackedStack(ItemStack stack, StoragePool.Source source) {
    }

    /** 从暂存的工作台材料里拿出一件符合材料条件的物品。 */
    private static TrackedStack takeMatching(List<TrackedStack> stacks, Ingredient ingredient, int count) {
        for (int i = 0; i < stacks.size(); i++) {
            TrackedStack tracked = stacks.get(i);
            ItemStack stack = tracked.stack();
            if (!ingredient.test(stack)) {
                continue;
            }
            int taken = Math.min(Math.max(1, count), stack.getCount());
            ItemStack result = stack.copyWithCount(taken);
            stack.shrink(taken);
            if (stack.isEmpty()) {
                stacks.remove(i);
            }
            return new TrackedStack(result, tracked.source());
        }
        return null;
    }

    /** placeAll 时每个材料格最多摆满该物品自己的最大堆叠数。 */
    private static int maxIngredientStackSize(Ingredient ingredient) {
        for (ItemStack preview : ingredient.getItems()) {
            if (!preview.isEmpty()) {
                return preview.getMaxStackSize();
            }
        }
        return 1;
    }

    /* 结果计算 */

    @Override
    public void slotsChanged(Container container) {
        if (!this.level.isClientSide) {
            updateResult();
        }
    }

    @Override
    public void broadcastChanges() {
        if (!this.level.isClientSide) {
            String signature = matrixSignature();
            if (!signature.equals(this.matrixSignature)) {
                this.matrixSignature = signature;
                if (this.bench != null) {
                    this.bench.setChanged();
                }
                updateResult();
            }
            if (this.level.getGameTime() >= this.nextRefreshTick) {
                refreshCraftables(false);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!this.level.isClientSide) {
            // 产物槽里的东西只是显示用，材料留在工作台格子里
            this.resultSlots.clearContent();
        }
    }

    /** 合成格变化后重算产物，逻辑与原版 CraftingMenu 一致。 */
    public void updateResult() {
        if (this.level.isClientSide) {
            return;
        }
        ItemStack result = ItemStack.EMPTY;
        if (!this.matrix.isEmpty()) {
            Optional<CraftingRecipe> optional =
                    this.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, this.matrix, this.level);
            if (optional.isPresent()) {
                CraftingRecipe recipe = optional.get();
                boolean allowed = !(this.player instanceof ServerPlayer serverPlayer)
                        || this.resultSlots.setRecipeUsed(this.level, serverPlayer, recipe);
                if (allowed) {
                    ItemStack assembled = recipe.assemble(this.matrix, this.level.registryAccess());
                    if (assembled.isItemEnabled(this.level.enabledFeatures())) {
                        result = assembled;
                    }
                }
            } else {
                this.resultSlots.setRecipeUsed((Recipe<?>) null);
            }
        } else {
            this.resultSlots.setRecipeUsed((Recipe<?>) null);
        }
        this.resultSlots.setItem(0, result);
    }

    /** 拿走一次产物：每个非空合成格减 1，桶之类的剩余物放回格子或塞给玩家。 */
    public void consumeGrid(Player player) {
        if (this.level.isClientSide || this.matrix.isEmpty()) {
            return;
        }
        ForgeHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remaining = this.level.getRecipeManager()
                .getRemainingItemsFor(RecipeType.CRAFTING, this.matrix, this.level);
        ForgeHooks.setCraftingPlayer(null);
        for (int i = 0; i < remaining.size() && i < SmartWorkbenchBlockEntity.GRID_SIZE; i++) {
            ItemStack original = this.matrix.getItem(i);
            ItemStack replacement = remaining.get(i);
            if (original.isEmpty()) {
                setMatrixFromMenu(i, replacement);
                clearMatrixSourcesAt(i);
            } else if (original.getCount() == 1) {
                removeSourceCount(i, 1);
                setMatrixFromMenu(i, replacement);
                clearMatrixSourcesAt(i);
            } else if (ItemStack.isSameItemSameTags(original, replacement)) {
                removeSourceCount(i, 1);
                replacement.grow(original.getCount() - 1);
                setMatrixFromMenu(i, replacement);
                trimMatrixSources(i, replacement.getCount());
            } else {
                removeSourceCount(i, 1);
                setMatrixFromMenu(i, ItemHandlerHelper.copyStackWithSize(original, original.getCount() - 1));
                trimMatrixSources(i, original.getCount() - 1);
                if (!replacement.isEmpty() && !player.getInventory().add(replacement)) {
                    player.drop(replacement, false);
                }
            }
        }
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
    }

    /* 快捷移动 */

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == SLOT_RESULT) {
            return shiftCraftResult(player);
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();
        boolean movingFromGrid = index >= GRID_START && index < GRID_END;
        if (index >= INV_START) {
            if (!this.moveItemStackTo(slotStack, GRID_START, GRID_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!movingFromGrid || !this.moveItemStackTo(slotStack, INV_START, INV_END, true)) {
            return ItemStack.EMPTY;
        }
        if (movingFromGrid) {
            int gridSlot = index - GRID_START;
            trimMatrixSources(gridSlot, slotStack.getCount());
        }
        this.updatingMatrix = true;
        try {
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        } finally {
            this.updatingMatrix = false;
        }
        if (slotStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, slotStack);
        return original;
    }

    /** shift 点产物 = 合成一次，产物进背包，放不下掉脚下。 */
    private ItemStack shiftCraftResult(Player player) {
        if (this.level.isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack display = this.resultSlots.getItem(0);
        if (display.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = display.copy();
        Slot slot = this.slots.get(SLOT_RESULT);
        slot.remove(moved.getCount());
        if (!player.getInventory().add(moved)) {
            player.drop(moved, false);
        }
        slot.onTake(player, display.copy());
        return moved;
    }

    /* 界面按钮（客户端点击 → 服务器执行） */

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.level.isClientSide) {
            return false;
        }
        if (id == BUTTON_REFRESH && player instanceof ServerPlayer serverPlayer && stillValid(player)) {
            if (!acceptAction(serverPlayer)) {
                return false;
            }
            refreshCraftables(true);
            return true;
        }
        if (id == BUTTON_AUTO_REFILL && stillValid(player)) {
            this.autoRefillEnabled = !this.autoRefillEnabled;
            notify(player, this.autoRefillEnabled ? "已开启自动补齐" : "已关闭自动补齐",
                    this.autoRefillEnabled
                            ? "gui." + SmartWorkbenchMod.MOD_ID + ".msg.auto_refill_on"
                            : "gui." + SmartWorkbenchMod.MOD_ID + ".msg.auto_refill_off");
            return true;
        }
        return false;
    }

    /**
     * 客户端点了右侧列表里的某一项（走自定义包，见 C2SCraftRequestPacket）。
     *
     * @param craft true = Shift 点击：直接从存储合成一次，产物进背包
     *              false = 普通点击：把材料配到合成格（不需要的材料退回背包）
     * @param batch true = Ctrl 点击：反复合成，直到材料不够或背包塞不下
     */
    public void onRecipeListClick(ServerPlayer player, ResourceLocation recipeId, boolean craft, boolean batch) {
        if (!acceptAction(player)) {
            return;
        }
        this.activeFeedback = new CraftFeedback();
        if (batch || craft) {
            // 右侧直接合成与当前合成格无关；先把旧材料退回各自来源，
            // 避免同一批材料同时出现在合成格和存储快照里。
            returnMatrixMaterials(player);
            if (batch) {
                batchCraftFromStorage(player, recipeId);
            } else {
                craftFromStorage(player, recipeId);
            }
        } else {
            pullFromStorage(player, recipeId);
        }
    }

    /**
     * REI 的加号按钮走这里：客户端已经确认原料够（见 compat 包），
     * 服务器按配方 id 把材料从接入存储摆进合成格。
     */
    public void placeRecipeFromClient(ServerPlayer player, ResourceLocation recipeId, boolean stackAll) {
        if (!acceptAction(player)) {
            return;
        }
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        Recipe<?> recipe = this.level.getRecipeManager().byKey(recipeId).orElse(null);
        if (!(recipe instanceof CraftingRecipe crafting) || !isPlainCraft(crafting)) {
            notify(player, "这个配方不支持一键取料", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
            return;
        }
        this.bench.refreshStorages(true);
        StoragePool availablePool = StoragePool.collect(this.bench.getStorages());
        if (!hasAllIngredients(crafting, availablePool)) {
            notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
            refreshCraftables(true);
            return;
        }
        placeRecipeIntoGrid(player, crafting, stackAll);
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
    }

    /* 可合成列表 */

    /** 清空当前合成格：取自存储的材料优先退回原槽位，手动放入的材料退回玩家背包。 */
    private void returnMatrixMaterials(ServerPlayer player) {
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            ItemStack inSlot = this.matrix.getItem(slot).copy();
            if (inSlot.isEmpty()) {
                this.matrixSources[slot].clear();
                continue;
            }
            int trackedCount = 0;
            for (SourcePart part : this.matrixSources[slot]) {
                int count = Math.min(part.stack().getCount(), Math.max(0, inSlot.getCount() - trackedCount));
                if (count <= 0) {
                    continue;
                }
                trackedCount += count;
                ItemStack rest = part.source().handler().insertItem(part.source().slot(),
                        part.stack().copyWithCount(count), false);
                if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                    player.drop(rest, false);
                }
            }
            int manualCount = inSlot.getCount() - trackedCount;
            if (manualCount > 0) {
                ItemStack manual = inSlot.copyWithCount(manualCount);
                if (!player.getInventory().add(manual)) {
                    player.drop(manual, false);
                }
            }
            this.matrixSources[slot].clear();
            setMatrixFromMenu(slot, ItemStack.EMPTY);
        }
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
    }

    private void refreshCraftables(boolean forceScan) {
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        long now = this.level.getGameTime();
        if (!forceScan && now < this.nextRefreshTick) {
            return;
        }
        this.bench.refreshStorages(forceScan);
        this.nextRefreshTick = now + 20L;
        List<CraftableEntry> next = new ArrayList<>();
        List<IItemHandler> storages = this.bench.getStorages();
        List<ItemStack> storageSnapshot = List.of();
        if (!storages.isEmpty()) {
            StoragePool pool = StoragePool.collect(storages);
            // 先给客户端抄一份合并后的库存快照（用原始数量，不受下面 plan 扣减的影响）
            storageSnapshot = pool.mergeForClient(STORAGE_SNAPSHOT_LIMIT);
            int max = ModConfig.maxListSize();
            List<CraftingRecipe> recipes = getPlainRecipes();
            for (CraftingRecipe recipe : recipes) {
                if (next.size() >= max) {
                    break;
                }
                if (!isPlainCraft(recipe)) {
                    continue;
                }
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                pool.reset();
                boolean possible = true;
                for (Ingredient ingredient : ingredients) {
                    if (!pool.mayContain(ingredient)) {
                        possible = false;
                        break;
                    }
                }
                // 右侧列表只显示一次合成所需材料全部充足的配方。
                // 缺少基础材料时，即使缺少的中间物品可以递归合成，也不能把它伪装成可直接合成，
                // 否则玩家点击后才发现缺料，甚至会误以为材料被凭空消耗。
                boolean direct = possible && pool.plan(ingredients) != null;
                if (!direct) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (!result.isEmpty()) {
                    next.add(new CraftableEntry(recipe.getId(), result.copy()));
                }
            }
        }
        next.sort(Comparator.comparing(entry -> entry.result().getItem().getDescriptionId()));
        StringBuilder signature = new StringBuilder();
        for (CraftableEntry entry : next) {
            signature.append(entry.signature()).append(';');
        }
        // 库存数量变了也得重发，否则 REI 手里的快照会过期，还会误报缺料
        signature.append('#').append(storages.size()).append(';');
        signature.append('@').append(this.bench == null ? 0 : this.bench.getOutputBindingCount()).append(';');
        for (ItemStack stack : storageSnapshot) {
            signature.append(stack.getItem().getDescriptionId()).append(':').append(stack.getCount())
                    .append(':').append(stack.getTag()).append(',');
        }
        if (!signature.toString().equals(this.listSignature)) {
            this.listSignature = signature.toString();
            this.craftables = next;
            if (this.player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendToPlayer(serverPlayer, new S2CCraftableListPacket(
                        this.benchPos, next, storageSnapshot, storages.size(), getOutputCount()));
            }
        }
    }

    /** 按服务器刚读取的存储内容预检一份配方，避免客户端快照过期时仍开始搬料。 */
    private static boolean hasAllIngredients(CraftingRecipe recipe, StoragePool pool) {
        if (pool.isEmpty()) {
            return false;
        }
        return pool.plan(recipe.getIngredients()) != null;
    }

    /** 只处理能放进 3x3 的普通合成配方，特殊配方（修装备、复制旗帜等）跳过。 */
    private boolean isPlainCraft(CraftingRecipe recipe) {
        if (recipe.isSpecial() || !recipe.canCraftInDimensions(COLUMNS, ROWS)) {
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return false;
        }
        int width = recipeWidth(recipe);
        int height = recipeHeight(recipe, width, ingredients.size());
        return width <= COLUMNS && height <= ROWS;
    }

    /**
     * 给 JEI / EMI 的桥用：这份配方能不能一键从存储取料。
     * 判断规则和服务器端 {@link #placeRecipeFromClient} 完全一致，免得界面上显示能点、
     * 点下去服务端又说不行。
     */
    public boolean canAutoPlace(Recipe<?> recipe) {
        return recipe instanceof CraftingRecipe crafting && isPlainCraft(crafting);
    }

    private static int recipeWidth(CraftingRecipe recipe) {
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shaped.getRecipeWidth();
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth();
        }
        return COLUMNS;
    }

    private static int recipeHeight(CraftingRecipe recipe, int width, int ingredientCount) {
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shaped.getRecipeHeight();
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getHeight();
        }
        return width <= 0 ? ingredientCount : (ingredientCount + width - 1) / width;
    }

    /** 这个配方该摆在 3x3 的哪些格子上，返回每个材料对应的格子序号。 */
    private static int[] gridLayout(CraftingRecipe recipe, NonNullList<Ingredient> ingredients) {
        int[] positions = new int[ingredients.size()];
        int width = recipeWidth(recipe);
        int height = recipeHeight(recipe, width, ingredients.size());
        boolean shaped = recipe instanceof ShapedRecipe || recipe instanceof IShapedRecipe<?>;
        if (!shaped || width <= 0 || height <= 0 || width > COLUMNS || height > ROWS) {
            // 无序配方：从第一个格子往后依次摆
            for (int i = 0; i < positions.length; i++) {
                positions[i] = Math.min(i, SmartWorkbenchBlockEntity.GRID_SIZE - 1);
            }
            return positions;
        }
        int offsetX = (COLUMNS - width) / 2;
        int offsetY = (ROWS - height) / 2;
        for (int i = 0; i < positions.length; i++) {
            positions[i] = (offsetY + i / width) * COLUMNS + offsetX + i % width;
        }
        return positions;
    }

    private CraftingRecipe resolveEntry(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        // 配方列表可能在网络包到达前刷新，因此按服务端真实 ID 重新解析，而不是信任客户端下标。
        Recipe<?> recipe = this.level.getRecipeManager().byKey(id).orElse(null);
        return recipe instanceof CraftingRecipe crafting && isPlainCraft(crafting) ? crafting : null;
    }

    /* 从附近存储取料 */

    /** 点一下列表：把材料从附近存储配到合成格；格子里不符合配方的料退回背包。 */
    private void pullFromStorage(ServerPlayer player, ResourceLocation recipeId) {
        CraftingRecipe recipe = resolveEntry(recipeId);
        if (recipe == null) {
            notify(player, "配方已失效，列表已刷新", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_gone");
            refreshCraftables(false);
            return;
        }
        if (this.bench == null) {
            return;
        }
        placeRecipeIntoGrid(player, recipe, false);
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
        ForgeHooks.setCraftingPlayer(player);
        boolean matches;
        try {
            matches = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
        if (!matches) {
            notify(player, "附近存储材料不足，缺少的中间材料可由其他配方合成",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        }
    }

    /** shift 点列表：直接从存储取材合成一次，产物进背包，不占用合成格。 */
    private void craftFromStorage(ServerPlayer player, ResourceLocation recipeId) {
        CraftingRecipe recipe = resolveEntry(recipeId);
        if (recipe == null) {
            notify(player, "配方已失效，列表已刷新", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_gone");
            refreshCraftables(false);
            return;
        }
        if (this.bench == null) {
            return;
        }
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(this.bench.getStorages());
        int outcome = craftOnce(player, recipe, pool, false);
        if (outcome == CRAFT_OK) {
            notifyFeedback(player, 1);
        } else if (outcome == CRAFT_MISSING) {
            // Shift 点击只允许消耗当前接入存储中的真实材料，不能把合成格中的材料
            // 或递归配方当成额外库存，否则部分材料会被重复计算。
            notify(player, "附近存储材料不足，缺少的中间材料可由其他配方合成",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        } else if (outcome == CRAFT_UNSUPPORTED) {
            notify(player, "这个配方不支持一键合成，请点图标取料到合成格",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
        } else if (outcome == CRAFT_INVENTORY_FULL) {
            notify(player, "产物已掉在脚下，因为背包没有空位",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.inventory_full");
        }
        markDirty();
        updateResult();
        refreshCraftables(false);
    }

    /**
     * Ctrl 点列表：反复取材合成，直到材料不够、背包塞不下或到单次上限。
     * 每轮都重新读一遍库存，因为上一轮真的从箱子里把材料抽走了。
     */
    private void batchCraftFromStorage(ServerPlayer player, ResourceLocation recipeId) {
        CraftingRecipe recipe = resolveEntry(recipeId);
        if (recipe == null) {
            notify(player, "配方已失效，列表已刷新", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_gone");
            refreshCraftables(false);
            return;
        }
        if (this.bench == null) {
            return;
        }
        this.bench.refreshStorages(true);
        int crafted = 0;
        int outcome = CRAFT_OK;
        for (int guard = 0; guard < MAX_BATCH_CRAFTS; guard++) {
            StoragePool pool = StoragePool.collect(this.bench.getStorages());
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                if (crafted == 0) {
                    notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                }
                break;
            }
            // 只有第一次失败时才提示，中途没料了是正常结束
            outcome = craftOnce(player, recipe, pool, crafted == 0);
            if (outcome != CRAFT_OK) {
                break;
            }
            crafted++;
        }
        if (crafted > 0) {
            notifyFeedback(player, crafted);
        }
        markDirty();
        updateResult();
        refreshCraftables(true);
    }

    /**
     * 自动合成：一次点击把目标物品做出来，直到达到配置的上限（默认 64 个）或材料用完。
     * <p>
     * 和 Ctrl 批量合成的区别：
     * <ul>
     *   <li>直接材料不够时，会自动递归补做缺的中间物品（不需要再弹确认框）；</li>
     *   <li>产物优先送进扳手绑定的输出容器，没绑才退回玩家背包。</li>
     * </ul>
     * 上限用配置 {@code craft.autoCraftLimit} 控制（默认 64，最大 12318）。
     */
    public void autoCraftFromStorage(ServerPlayer player, ResourceLocation recipeId) {
        if (!acceptAction(player)) {
            return;
        }
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        CraftingRecipe recipe = resolveEntry(recipeId);
        if (recipe == null) {
            notify(player, "配方已失效，列表已刷新", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_gone");
            refreshCraftables(false);
            return;
        }
        this.bench.refreshStorages(true);

        int limit = ModConfig.autoCraftLimit();
        int crafted = 0;
        int outcome = CRAFT_OK;
        List<ItemStack> missing = List.of();
        this.activeFeedback = new CraftFeedback();
        // 自动合成完全独立于合成格，先归还当前格子，防止把同一份材料重复算入库存。
        returnMatrixMaterials(player);

        for (int i = 0; i < limit; i++) {
            StoragePool pool = StoragePool.collect(this.bench.getStorages());
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                break;
            }
            // 先试直接合成；这一步不成功时 pool 没被消耗（失败会退款）
            outcome = craftOnce(player, recipe, pool, false, true);
            if (outcome == CRAFT_OK) {
                crafted++;
                continue;
            }
            if (outcome != CRAFT_MISSING) {
                break;
            }
            // 直接材料不够：重新读一遍库存，规划整棵依赖树，能补齐就补一次
            StoragePool planPool = StoragePool.collect(this.bench.getStorages());
            RecursivePlanState state = new RecursivePlanState(planPool.snapshotStacks());
            if (!planRecipe(recipe, state, 0, new HashSet<>()) || state.steps.isEmpty()
                    || !state.unavailable.isEmpty() || state.autoCraftable.isEmpty()) {
                missing = RecursivePlanState.copyStacks(state.unavailable);
                outcome = CRAFT_MISSING;
                break;
            }
            int recursive = executeRecursivePlan(player, state.steps, planPool, true);
            if (recursive == CRAFT_OK) {
                crafted++;
                continue;
            }
            outcome = recursive;
            missing = RecursivePlanState.copyStacks(state.unavailable);
            break;
        }

        if (crafted > 0) {
            notifyFeedback(player, crafted);
        } else if (outcome == CRAFT_OUTPUT_FULL) {
            notify(player, "输出容器满了，产物已退回背包", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.output_full");
        } else if (outcome == CRAFT_UNSUPPORTED) {
            notify(player, "这个配方不支持一键合成，请点图标取料到合成格",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
        } else if (!missing.isEmpty()) {
            notifyMissing(player, missing, "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
        } else {
            notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        }

        markDirty();
        updateResult();
        refreshCraftables(false);
    }

    /**
     * 从存储里真取一份材料合成一次，产物进背包（塞不下就掉在脚下）。
     * 返回 {@link #CRAFT_OK} 等结果码，批量合成靠它判断该不该接着做。
     */
    /** 第一次发现直接材料不够时，先在内存里规划整棵配方依赖树。 */
    private void requestRecursiveCraft(ServerPlayer player, CraftingRecipe target) {
        if (this.bench == null) {
            return;
        }
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(this.bench.getStorages());
        RecursivePlanState state = new RecursivePlanState(pool.snapshotStacks());
        if (!planRecipe(target, state, 0, new HashSet<>()) || state.steps.isEmpty()) {
            if (!state.unavailable.isEmpty()) {
                notifyMissing(player, state.unavailable, "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
            } else {
                notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
            }
            return;
        }
        if (!state.autoCraftable.isEmpty() && state.unavailable.isEmpty()) {
            List<S2CRecursiveCraftPromptPacket.MissingEntry> auto = toPromptEntries(state.autoCraftable, true);
            ModNetwork.sendToPlayer(player, new S2CRecursiveCraftPromptPacket(
                    this.benchPos, target.getId(), auto, List.of()));
            return;
        }
        if (!state.unavailable.isEmpty()) {
            notifyMissing(player, state.unavailable, "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
        } else {
            notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        }
    }

    /** 客户端确认后重新规划并真实执行，避免确认期间箱子内容变化造成错误取料。 */
    public void confirmRecursiveCraft(ServerPlayer player, ResourceLocation recipeId, boolean accepted) {
        if (!acceptAction(player)) {
            return;
        }
        if (this.level.isClientSide) {
            return;
        }
        this.clearRecursivePrompt();
        if (!accepted) {
            notify(player, "已取消自动补齐", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_cancel");
            return;
        }
        CraftingRecipe target = resolveEntry(recipeId);
        if (target == null || this.bench == null) {
            notify(player, "配方已失效，请重新刷新", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_gone");
            refreshCraftables(false);
            return;
        }
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(this.bench.getStorages());
        RecursivePlanState state = new RecursivePlanState(pool.snapshotStacks());
        if (!planRecipe(target, state, 0, new HashSet<>()) || !state.unavailable.isEmpty()
                || state.autoCraftable.isEmpty()) {
            if (!state.unavailable.isEmpty()) {
                notifyMissing(player, state.unavailable, "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
            } else {
                notify(player, "材料已经变化，请重新点击合成", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_changed");
            }
            return;
        }
        this.activeFeedback = new CraftFeedback();
        int recursiveOutcome = executeRecursivePlan(player, state.steps, pool);
        if (recursiveOutcome == CRAFT_OK || recursiveOutcome == CRAFT_OUTPUT_FULL) {
            notifyFeedback(player, 1);
        }
        markDirty();
        updateResult();
        refreshCraftables(false);
    }

    /** 递归规划一份配方；steps 按依赖先后排列，最后一项是目标物品。 */
    private boolean planRecipe(CraftingRecipe recipe, RecursivePlanState state, int depth, Set<ResourceLocation> active) {
        if (depth > MAX_RECURSIVE_DEPTH || state.steps.size() >= MAX_RECURSIVE_STEPS
                || recipe == null || !isPlainCraft(recipe)) {
            return false;
        }
        ResourceLocation id = recipe.getId();
        if (id != null && !active.add(id)) {
            return false;
        }
        RecursivePlanState.Mark mark = state.mark();
        boolean complete = true;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!planIngredient(ingredient, state, depth + 1, active)) {
                complete = false;
                break;
            }
        }
        if (complete) {
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (!result.isEmpty()) {
                state.virtual.add(result.copy());
                state.steps.add(recipe);
            } else {
                complete = false;
            }
        }
        if (id != null) {
            active.remove(id);
        }
        if (!complete) {
            List<ItemStack> failure = RecursivePlanState.copyStacks(state.unavailable);
            state.restore(mark);
            state.unavailable.addAll(failure);
        }
        return complete;
    }

    /** 先消耗已有库存；没有时找能产出该材料的普通合成配方。 */
    private boolean planIngredient(Ingredient ingredient, RecursivePlanState state,
                                   int depth, Set<ResourceLocation> active) {
        if (ingredient.isEmpty()) {
            return true;
        }
        int available = findAndConsumeVirtual(state.virtual, ingredient);
        if (available > 0) {
            return true;
        }
        ItemStack[] candidates = ingredient.getItems();
        List<ItemStack> deepestFailure = new ArrayList<>();
        for (ItemStack candidate : candidates) {
            if (candidate.isEmpty()) {
                continue;
            }
            for (CraftingRecipe dependency : findRecipesFor(candidate, active)) {
                RecursivePlanState.Mark mark = state.mark();
                if (planRecipe(dependency, state, depth, active)
                        && findAndConsumeVirtual(state.virtual, ingredient) > 0) {
                    addShortage(state.autoCraftable, candidate, 1);
                    return true;
                }
                // 这一条配方失败时，先保存它递归发现的底层缺口；只有所有候选都失败后才合并。
                for (ItemStack missing : shortageDelta(state.unavailable, mark.unavailable())) {
                    addShortage(deepestFailure, missing, missing.getCount());
                }
                state.restore(mark);
            }
        }
        if (!deepestFailure.isEmpty()) {
            for (ItemStack missing : deepestFailure) {
                addShortage(state.unavailable, missing, missing.getCount());
            }
        } else {
            ItemStack report = candidates.length == 0 ? ItemStack.EMPTY : candidates[0].copy();
            if (!report.isEmpty()) {
                addShortage(state.unavailable, report, 1);
            }
        }
        return false;
    }

    private List<CraftingRecipe> findRecipesFor(ItemStack wanted, Set<ResourceLocation> active) {
        List<CraftingRecipe> matches = new ArrayList<>();
        for (CraftingRecipe recipe : getRecipesByResult().getOrDefault(wanted.getItem(), List.of())) {
            if (recipe.getId() == null || active.contains(recipe.getId())) {
                continue;
            }
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (ItemStack.isSameItemSameTags(result, wanted)) {
                matches.add(recipe);
            }
        }
        return matches;
    }

    private List<CraftingRecipe> getPlainRecipes() {
        long version = this.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).size();
        if (this.plainRecipes == null || version != this.recipeIndexVersion) {
            List<CraftingRecipe> recipes = new ArrayList<>();
            Map<net.minecraft.world.item.Item, List<CraftingRecipe>> byResult = new HashMap<>();
            for (CraftingRecipe recipe : this.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                if (!isPlainCraft(recipe)) {
                    continue;
                }
                recipes.add(recipe);
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (!result.isEmpty()) {
                    byResult.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>()).add(recipe);
                }
            }
            this.plainRecipes = List.copyOf(recipes);
            this.recipesByResult = byResult;
            this.recipeIndexVersion = version;
        }
        return this.plainRecipes;
    }

    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> getRecipesByResult() {
        getPlainRecipes();
        return this.recipesByResult;
    }

    /** 返回并消耗一件匹配材料；列表中的堆叠只属于规划阶段。 */
    private static int findAndConsumeVirtual(List<ItemStack> stacks, Ingredient ingredient) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (ingredient.test(stack)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    stacks.remove(i);
                }
                return 1;
            }
        }
        return 0;
    }

    private static void addShortage(List<ItemStack> list, ItemStack stack, int count) {
        for (ItemStack existing : list) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                existing.grow(count);
                return;
            }
        }
        ItemStack copy = stack.copy();
        copy.setCount(count);
        list.add(copy);
    }

    /** 计算候选分支相对进入前状态新增的缺口，避免把外层缺口重复统计。 */
    private static List<ItemStack> shortageDelta(List<ItemStack> current, List<ItemStack> baseline) {
        List<ItemStack> delta = RecursivePlanState.copyStacks(current);
        for (ItemStack previous : baseline) {
            for (ItemStack candidate : delta) {
                if (!ItemStack.isSameItemSameTags(previous, candidate)) {
                    continue;
                }
                candidate.shrink(Math.min(candidate.getCount(), previous.getCount()));
                break;
            }
        }
        delta.removeIf(ItemStack::isEmpty);
        return delta;
    }

    private static List<S2CRecursiveCraftPromptPacket.MissingEntry> toPromptEntries(List<ItemStack> stacks,
                                                                                       boolean craftable) {
        List<S2CRecursiveCraftPromptPacket.MissingEntry> entries = new ArrayList<>();
        for (ItemStack stack : stacks) {
            entries.add(new S2CRecursiveCraftPromptPacket.MissingEntry(stack.copy(), stack.getCount(), craftable));
        }
        return entries;
    }

    private void notifyMissing(ServerPlayer player, List<ItemStack> stacks, String key) {
        StringBuilder names = new StringBuilder();
        for (ItemStack stack : stacks) {
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(stack.getHoverName().getString()).append("x").append(stack.getCount());
        }
        player.displayClientMessage(Component.translatableWithFallback(key, "缺少基础材料：%s", names.toString()), true);
    }

    /** 按规划顺序合成中间物品，最后一步的产物交给玩家，其余中间产物也会返还。 */
    private int executeRecursivePlan(ServerPlayer player, List<CraftingRecipe> steps, StoragePool pool) {
        return executeRecursivePlan(player, steps, pool, false);
    }

    /**
     * 按规划顺序合成中间物品，最后一步的产物交付出去（toOutput 时优先送输出容器）。
     *
     * @return {@link #CRAFT_OK} 成功；{@link #CRAFT_OUTPUT_FULL} 产物送不进输出容器但已退回背包；
     *         其它表示中途失败、已把抽出的材料全额退回
     */
    private int executeRecursivePlan(ServerPlayer player, List<CraftingRecipe> steps, StoragePool pool,
                                     boolean toOutput) {
        List<ItemStack> generated = new ArrayList<>();
        List<ItemStack> pulled = new ArrayList<>();
        List<Integer> pulledFrom = new ArrayList<>();
        int delivery = CRAFT_OK;
        for (int index = 0; index < steps.size(); index++) {
            CraftingRecipe recipe = steps.get(index);
            NonNullList<ItemStack> tempItems = NonNullList.withSize(SmartWorkbenchBlockEntity.GRID_SIZE, ItemStack.EMPTY);
            TransientCraftingContainer grid = new TransientCraftingContainer(this, COLUMNS, ROWS, tempItems);
            boolean success = true;
            int[] layout = gridLayout(recipe, recipe.getIngredients());
            ForgeHooks.setCraftingPlayer(player);
            try {
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    Ingredient ingredient = recipe.getIngredients().get(i);
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    ItemStack got = takeFromGenerated(generated, ingredient);
                    if (got.isEmpty()) {
                        StoragePool plannedPool = pool;
                        int candidate = plannedPool.find(ingredient);
                        got = candidate < 0 ? ItemStack.EMPTY : plannedPool.take(candidate, 1);
                        if (!got.isEmpty()) {
                            pulled.add(got.copy());
                            pulledFrom.add(candidate);
                            if (this.activeFeedback != null) {
                                this.activeFeedback.addConsumed(got);
                            }
                        }
                    }
                    if (got.isEmpty()) {
                        success = false;
                        break;
                    }
                    grid.setItem(layout[i], got);
                }
                if (success && recipe.matches(grid, this.level)) {
                    ItemStack result = recipe.assemble(grid, this.level.registryAccess());
                    NonNullList<ItemStack> remaining = recipe.getRemainingItems(grid);
                    if (result.isEmpty()) {
                        success = false;
                    } else {
                        result.onCraftedBy(this.level, player, result.getCount());
                        ForgeEventFactory.firePlayerCraftingEvent(player, result, grid);
                        player.awardRecipes(List.<Recipe<?>>of(recipe));
                        if (index == steps.size() - 1) {
                            delivery = deliverResult(player, result, toOutput);
                            if (this.activeFeedback != null) {
                                this.activeFeedback.addResult(result);
                            }
                        } else {
                            generated.add(result);
                        }
                        for (ItemStack leftover : remaining) {
                            if (!leftover.isEmpty()) {
                                generated.add(leftover.copy());
                            }
                        }
                    }
                } else {
                    success = false;
                }
            } finally {
                ForgeHooks.setCraftingPlayer(null);
            }
            if (!success) {
                // 本次操作是事务性的：返还所有已从存储抽出的原料，丢弃尚未交付的中间产物，避免重复返还。
                refund(pool, pulled, pulledFrom, player);
                generated.clear();
                notify(player, "材料已经变化，自动合成已停止", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_changed");
                return CRAFT_MISSING;
            }
        }
        for (ItemStack stack : generated) {
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        return delivery;
    }

    private static ItemStack takeFromGenerated(List<ItemStack> generated, Ingredient ingredient) {
        for (int i = 0; i < generated.size(); i++) {
            ItemStack stack = generated.get(i);
            if (!ingredient.test(stack)) {
                continue;
            }
            ItemStack result = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) {
                generated.remove(i);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    private static final class RecursivePlanState {
        private final List<ItemStack> virtual;
        private final List<CraftingRecipe> steps = new ArrayList<>();
        private final List<ItemStack> autoCraftable = new ArrayList<>();
        private final List<ItemStack> unavailable = new ArrayList<>();

        private RecursivePlanState(List<ItemStack> initial) {
            this.virtual = new ArrayList<>();
            for (ItemStack stack : initial) {
                this.virtual.add(stack.copy());
            }
        }

        private Mark mark() {
            return new Mark(copyStacks(this.virtual), this.steps.size(), copyStacks(this.autoCraftable),
                    copyStacks(this.unavailable));
        }

        private void restore(Mark mark) {
            this.virtual.clear();
            this.virtual.addAll(copyStacks(mark.virtual));
            while (this.steps.size() > mark.stepsSize) {
                this.steps.remove(this.steps.size() - 1);
            }
            this.autoCraftable.clear();
            this.autoCraftable.addAll(copyStacks(mark.autoCraftable));
            this.unavailable.clear();
            this.unavailable.addAll(copyStacks(mark.unavailable));
        }

        private static List<ItemStack> copyStacks(List<ItemStack> source) {
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                copy.add(stack.copy());
            }
            return copy;
        }

        private record Mark(List<ItemStack> virtual, int stepsSize,
                            List<ItemStack> autoCraftable, List<ItemStack> unavailable) {
        }
    }

    private int craftOnce(ServerPlayer player, CraftingRecipe recipe, StoragePool pool, boolean notifyOnFail) {
        return craftOnce(player, recipe, pool, notifyOnFail, false);
    }

    private int craftOnce(ServerPlayer player, CraftingRecipe recipe, StoragePool pool, boolean notifyOnFail,
                          boolean toOutput) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int[] plan = pool.isEmpty() ? null : pool.plan(ingredients);
        if (plan == null) {
            if (notifyOnFail) {
                notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
            }
            return CRAFT_MISSING;
        }
        // 临时合成格只用来摆样子给配方判断，不占用工作台的格子
        NonNullList<ItemStack> tempItems = NonNullList.withSize(SmartWorkbenchBlockEntity.GRID_SIZE, ItemStack.EMPTY);
        TransientCraftingContainer grid = new TransientCraftingContainer(this, COLUMNS, ROWS, tempItems);
        int[] layout = gridLayout(recipe, ingredients);
        List<ItemStack> pulled = new ArrayList<>();
        List<Integer> pulledFrom = new ArrayList<>();
        int[] pulledFromByGridSlot = new int[SmartWorkbenchBlockEntity.GRID_SIZE];
        Arrays.fill(pulledFromByGridSlot, -1);
        ForgeHooks.setCraftingPlayer(player);
        try {
            for (int i = 0; i < ingredients.size(); i++) {
                if (plan[i] < 0) {
                    continue;
                }
                ItemStack got = pool.takePlanned(plan[i], 1);
                if (got.isEmpty()) {
                    refund(pool, pulled, pulledFrom, player);
                    if (notifyOnFail) {
                        notify(player, "附近存储材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                    }
                    return CRAFT_MISSING;
                }
                pulled.add(got);
                pulledFrom.add(plan[i]);
                grid.setItem(layout[i], got);
                pulledFromByGridSlot[layout[i]] = plan[i];
            }
            if (!recipe.matches(grid, this.level)) {
                refund(pool, pulled, pulledFrom, player);
                if (notifyOnFail) {
                    notify(player, "这个配方不支持一键合成，请点图标取料到合成格",
                            "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
                }
                return CRAFT_UNSUPPORTED;
            }
            ItemStack result = recipe.assemble(grid, this.level.registryAccess());
            if (result.isEmpty()) {
                refund(pool, pulled, pulledFrom, player);
                if (notifyOnFail) {
                    notify(player, "这个配方不支持一键合成，请点图标取料到合成格",
                            "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
                }
                return CRAFT_UNSUPPORTED;
            }
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(grid);
            result.onCraftedBy(this.level, player, result.getCount());
            ForgeEventFactory.firePlayerCraftingEvent(player, result, grid);
            player.awardRecipes(List.<Recipe<?>>of(recipe));
            int delivery = deliverResult(player, result, toOutput);
            if (this.activeFeedback != null) {
                this.activeFeedback.addResult(result);
                for (int i = 0; i < ingredients.size(); i++) {
                    Ingredient ingredient = ingredients.get(i);
                    ItemStack consumed = grid.getItem(layout[i]);
                    if (!ingredient.isEmpty() && !consumed.isEmpty()) {
                        this.activeFeedback.addConsumed(consumed);
                    }
                }
            }
            for (int i = 0; i < remaining.size(); i++) {
                ItemStack leftover = remaining.get(i);
                if (leftover.isEmpty()) {
                    continue;
                }
                int candidate = i < pulledFromByGridSlot.length ? pulledFromByGridSlot[i] : -1;
                ItemStack rest = candidate >= 0 ? pool.giveBack(candidate, leftover) : leftover;
                if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                    player.drop(rest, false);
                }
            }
            return delivery;
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
    }

    /**
     * 交付一次合成的产物：toOutput 时先塞进绑定的输出容器，塞不下或没绑才退回玩家背包，最后才掉在地上。
     * 返回 {@link #CRAFT_OK} / {@link #CRAFT_INVENTORY_FULL} / {@link #CRAFT_OUTPUT_FULL}。
     */
    private int deliverResult(ServerPlayer player, ItemStack stack, boolean toOutput) {
        if (stack.isEmpty()) {
            return CRAFT_OK;
        }
        if (toOutput) {
            ItemStack rest = insertIntoOutputs(stack);
            if (rest.isEmpty()) {
                return CRAFT_OK;
            }
            // 输出容器满了：剩下的退回背包，保证不丢，但这次自动合成要停下
            if (!player.getInventory().add(rest)) {
                player.drop(rest, false);
            }
            return CRAFT_OUTPUT_FULL;
        }
        if (player.getInventory().add(stack)) {
            return CRAFT_OK;
        }
        player.drop(stack, false);
        return CRAFT_INVENTORY_FULL;
    }

    /** 把产物塞进所有绑定的输出容器，返回塞不下的部分（没绑输出容器时原样返回）。 */
    private ItemStack insertIntoOutputs(ItemStack stack) {
        ItemStack rest = stack;
        if (this.bench == null) {
            return rest;
        }
        for (IItemHandler handler : this.bench.getOutputStorages()) {
            rest = ItemHandlerHelper.insertItemStacked(handler, rest, false);
            if (rest.isEmpty()) {
                break;
            }
        }
        return rest;
    }

    private void refund(StoragePool pool, List<ItemStack> pulled, List<Integer> pulledFrom, Player player) {
        for (int i = 0; i < pulled.size(); i++) {
            ItemStack stack = pulled.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = pool.giveBack(pulledFrom.get(i), stack);
            if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                player.drop(rest, false);
            }
        }
    }

    /** 限制客户端请求频率；服务器每个菜单每 4 tick 最多处理一次重操作。 */
    private boolean acceptAction(ServerPlayer player) {
        if (this.level.isClientSide || this.bench == null || !stillValid(player)) {
            return false;
        }
        long now = this.level.getGameTime();
        if (now < this.nextActionTick) {
            return false;
        }
        this.nextActionTick = now + 4L;
        return true;
    }

    private void markDirty() {
        if (this.bench != null) {
            this.bench.setChanged();
        }
    }

    private void notify(Player player, String fallback, String key) {
        player.displayClientMessage(Component.translatableWithFallback(key, fallback), true);
    }

    /** 带数字的提示（批量合成做了多少个之类）。 */
    private void notifyCount(Player player, String key, String fallback, int count) {
        player.displayClientMessage(Component.translatableWithFallback(key, fallback, count), true);
    }

    private void notifyFeedback(Player player, int crafts) {
        if (this.activeFeedback == null || this.activeFeedback.isEmpty()) {
            return;
        }
        String consumed = this.activeFeedback.describeConsumed();
        String produced = this.activeFeedback.describeProduced();
        player.displayClientMessage(Component.translatableWithFallback(
                "gui." + SmartWorkbenchMod.MOD_ID + ".msg.craft_feedback",
                "合成完成：%s 次；消耗：%s；产出：%s",
                crafts, consumed, produced), true);
        this.activeFeedback = null;
    }

    private static final class CraftFeedback {
        private final List<ItemStack> consumed = new ArrayList<>();
        private final List<ItemStack> produced = new ArrayList<>();

        private void addConsumed(ItemStack stack) {
            merge(this.consumed, stack);
        }

        private void addResult(ItemStack stack) {
            merge(this.produced, stack);
        }

        private boolean isEmpty() {
            return this.consumed.isEmpty() && this.produced.isEmpty();
        }

        private String describeConsumed() {
            return describe(this.consumed);
        }

        private String describeProduced() {
            return describe(this.produced);
        }

        private static void merge(List<ItemStack> target, ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            for (ItemStack existing : target) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    existing.grow(stack.getCount());
                    return;
                }
            }
            target.add(stack.copy());
        }

        private static String describe(List<ItemStack> stacks) {
            if (stacks.isEmpty()) {
                return "无";
            }
            StringBuilder text = new StringBuilder();
            for (ItemStack stack : stacks) {
                if (text.length() > 0) {
                    text.append("、");
                }
                text.append(stack.getHoverName().getString()).append("x").append(stack.getCount());
            }
            return text.toString();
        }
    }

    private String matrixSignature() {
        StringBuilder builder = new StringBuilder(64);
        for (int i = 0; i < SmartWorkbenchBlockEntity.GRID_SIZE; i++) {
            ItemStack stack = this.matrix.getItem(i);
            if (stack.isEmpty()) {
                builder.append('|');
            } else {
                builder.append(stack.getItem().getDescriptionId()).append(':').append(stack.getCount())
                        .append(':').append(stack.getTag()).append('|');
            }
        }
        return builder.toString();
    }
}
