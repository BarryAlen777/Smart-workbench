package cn.blockforge.generated.mod2e8abd21.menu;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.compat.MaterialAvailability;
import cn.blockforge.generated.mod2e8abd21.compat.PolymorphCompat;
import cn.blockforge.generated.mod2e8abd21.ModRegistries;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import cn.blockforge.generated.mod2e8abd21.network.S2CAutoRefillPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CCraftableListPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CRecursiveCraftPromptPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
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
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** 产物格上的 Ctrl+左键：把合成格里摆好的这份配方批量合成（材料不论来源，手动摆的也算）。 */
    public static final int BUTTON_BATCH_GRID = 2;
    /**
     * 合成格下方的「清空」按钮：把格子里的材料全部退回原处。
     * <p>
     * 走的是切换配方时同一套 {@code returnMatrixMaterials} 管道，所以行为完全一致：
     * 从哪台箱子哪一格拿来的就插回那一格，那一格被占满了才找附近别的容器，
     * 实在无处可去才落到玩家背包/地上；玩家自己手动摆进去的料没有来路，退回背包。
     */
    public static final int BUTTON_CLEAR_GRID = 3;

    /**
     * 自动补齐开关在玩家存档上的键。
     * <p>
     * 存在玩家身上而不是菜单上：菜单每次打开都会新建一个，开关放在菜单里就永远是默认值，
     * 玩家关掉之后再开界面又自己打开了。存到玩家持久数据后，关一次就一直记住。
     */
    private static final String AUTO_REFILL_TAG = "SmartWorkbenchAutoRefill";

    /** 合成格行列数也取自 GuiLayout，改 json 就能整体放大/缩小合成区 */
    private static final int COLUMNS = GuiLayout.GRID_COLS;
    private static final int ROWS = GuiLayout.GRID_ROWS;

    /** 发给客户端的存储快照最多保留多少种物品，防止精妙存储那种大仓库把包撑爆 */
    private static final int STORAGE_SNAPSHOT_LIMIT = 512;
    /** 批量合成一次最多做多少次；数量框和配置「用完材料」都受这个安全上限保护。 */
    private static final int MAX_BATCH_CRAFTS = 1024;
    /** 一份物品的配方做不出来时，最多再自动试几份同产物的配方，免得白烧服务器 CPU。 */
    private static final int AUTO_RECIPE_TRY_LIMIT = 4;

    /**
     * 本次操作已经尝试的规划节点数与是否因预算用尽而中止。
     * <p>
     * 递归深度、展开步数、节点预算都放在配置里（{@code craft.recursiveMaxDepth} 等），
     * 免得又深又绕的配方树把服务器拖住。每次「顶层规划」都从零开始计数，
     * 递归调用共享同一份计数。
     */
    private int planNodes;
    private boolean planAborted;
    /** 本次顶层规划的绝对超时线（nanoTime）；节点数刹不住时的最后保险。 */
    private long planDeadlineNanos;

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
    /** 合成格的只读料源视图，仅供「可不够合成」这类扫描使用（见 {@link #scanHandlers()}）。 */
    private final MatrixScanHandler matrixScanHandler;
    private final ResultContainer resultSlots = new ResultContainer();
    /**
     * 玩家自身背包（主背包 + 快捷栏）的处理器。
     * <p>
     * 很多玩家习惯把材料直接放身上，所以除了箱子/背包之外，玩家自己的物品栏也是一路料源。
     * 用 {@link PlayerMainInvWrapper} 只覆盖 0~35 号槽，不会把身上穿的盔甲也拿去当材料。
     * 菜单是每个玩家各一份的，所以这路料源只对当前打开界面的玩家生效。
     */
    private final IItemHandler playerInventoryHandler;
    /** 合成格中由存储取来的材料来源；切换配方时优先退回原槽位。 */
    private final List<SourcePart>[] matrixSources = createSourceLists();
    private boolean updatingMatrix;
    /** 合成格的来路有没有变动；变动后找机会抄给方块实体，关掉界面也能记住。 */
    private boolean originsDirty = true;

    @SuppressWarnings("unchecked")
    private static List<SourcePart>[] createSourceLists() {
        List<SourcePart>[] sources = (List<SourcePart>[]) new List<?>[SmartWorkbenchBlockEntity.GRID_SIZE];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = new ArrayList<>();
        }
        return sources;
    }

    /**
     * 合成格里一件材料的来路。只记坐标和槽位，不记处理器本身——存储列表每次重扫都会新建
     * 一批包装对象，存老的会指向已经过期的库存。
     */
    private record Origin(BlockPos pos, int slot, SmartWorkbenchBlockEntity.OriginKind kind) {
    }

    private record SourcePart(ItemStack stack, Origin origin) {
    }

    /**
     * 合成格的只读 IItemHandler 视图：让「材料够不够」的模拟扫描把格子里的东西也当成库存。
     * <p>
     * extractItem 一律返回空——真正抽料必须走 {@code placeRecipeIntoGrid} /
     * {@code returnMatrixMaterials} 那两条会同步维护来源记录（matrixSources）的路，
     * 直接从这里掏东西会让格子来路和退料对不上账。insertItem 永远整份退回，表示"这格不收料"。
     */
    private static final class MatrixScanHandler implements IItemHandler {
        private final CraftingContainer matrix;

        private MatrixScanHandler(CraftingContainer matrix) {
            this.matrix = matrix;
        }

        @Override
        public int getSlots() {
            return matrix.getContainerSize();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return matrix.getItem(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    private String matrixSignature = "";
    private List<CraftableEntry> craftables = List.of();
    /** 普通点击缺料时是否询问并自动递归补齐中间材料。 */
    private boolean autoRefillEnabled = true;
    /** 服务端是否已经把开关的真实状态发过给客户端；开界面后补一次，之后靠按钮回包同步。 */
    private boolean autoRefillSynced;
    /** 客户端专用的接入存储快照，给 REI 判断「原料够不够」用 */
    private List<ItemStack> clientStorageStacks = List.of();
    /** 客户端是否已经收到过服务器推来的存储快照；没收到时缺料判断不可信。 */
    private boolean clientStorageSynced;
    /** 客户端显示用；服务端的真实数量仍从方块实体读取。 */
    private int clientConnectedStorageCount;
    /** 客户端显示用：绑定的输出容器数量。 */
    private int clientOutputCount;
    /** 上次扫描时的库存指纹（含配方版本、输出容器数）。没变就跳过重算，省下大整合包里每秒几千次配方匹配。 */
    private long craftableFingerprint;
    private boolean hasCraftableFingerprint;
    /** 分块收列表用的缓冲：最后一块到了才整体换上，界面不会显示到一半的半截列表。 */
    private final List<CraftableEntry> clientCraftableBuffer = new ArrayList<>();
    private String storageInputSignature = "";
    private long nextRefreshTick;
    /** 同一个菜单内复用普通合成配方索引，避免递归规划时反复遍历全量配方。 */
    private List<CraftingRecipe> plainRecipes;
    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> recipesByResult;
    /** 按材料种类反向索引配方，给链式可达性扫描用。 */
    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> recipesUsingItem;
    private long recipeIndexVersion = Long.MIN_VALUE;
    /** 索引建自哪个 RecipeManager 实例；reload 换实例时一并作废旧索引。 */
    private net.minecraft.world.item.crafting.RecipeManager indexedRecipeManager;
    /** 所有客户端操作共用一个服务端冷却，防止连点堆积主线程任务。 */
    private long nextActionTick;
    private CraftFeedback activeFeedback;
    /**
     * 玩家在本界面明确选过的配方（点列表 / REI 取料）。
     * 同一件产物可能有好几个模组的配方都能匹配，没装多态合成时就靠它保证产物格
     * 显示的就是玩家点的那一份，而不是原版随手取的第一份。
     */
    private CraftingRecipe preferredRecipe;
    /** 上一次算产物用的配方：拿走产物时要按同一份配方算剩余物（桶、瓶子之类）。 */
    private CraftingRecipe lastGridRecipe;
    /**
     * 「连续合成」锁定的配方：只有 REI/JEI/EMI 的加号、原版配方书、右侧列表这些
     * <b>明确点配方</b>摆进合成格的料才算数；玩家一格一格手动摆的料不算
     * （见 {@link #beginStickyRecipe}）。拿走产物后按它自动补一份同样的材料，
     * 这样点一次加号就能连续合成，不用反复回 REI 重新点开配方。
     * <p>
     * 手动摆的料想要一口气合成很多个，用产物格上的 Ctrl+左键批量合成
     * （{@link #batchCraftFromGrid}）——那条路看的是格子里摆的配方，不问材料来源。
     */
    private CraftingRecipe stickyRecipe;
    /** 锁定时用的是不是「摆满」取料（Shift+加号）：补料时补到同样的量。 */
    private boolean stickyStackAll;

    /** 客户端工厂：从网络缓冲区读工作台坐标。 */
    public SmartWorkbenchMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public SmartWorkbenchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), pos));
        this.level = inventory.player.level();
        this.player = inventory.player;
        this.playerInventoryHandler = new PlayerMainInvWrapper(inventory);
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
        this.matrixScanHandler = new MatrixScanHandler(this.matrix);

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
        // 自动补齐开关跟着玩家存档走：读上一次的选择，没存过才用默认的「开」。
        // 客户端这半边读不到也不怕，服务端打开界面时会用 S2CAutoRefillPacket 明确补一次。
        if (!this.level.isClientSide) {
            CompoundTag data = inventory.player.getPersistentData();
            if (data.contains(AUTO_REFILL_TAG)) {
                this.autoRefillEnabled = data.getBoolean(AUTO_REFILL_TAG);
            }
            if (this.bench != null) {
                // 先把取料容器扫出来，再把上次摆的料「是从哪台箱子第几格拿的」接回来，
                // 这样关掉界面再打开、切换配方时还是原路退回，不会全塞进玩家背包。
                this.bench.refreshStorages(true);
                restoreOriginsFromBench();
            }
        }
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
                SmartWorkbenchMenu.this.markOriginsDirty();
                // 玩家自己动手往格子里放/挪了东西：连续合成的约定作废
                SmartWorkbenchMenu.this.clearStickyRecipe();
            }
            super.set(stack);
        }

        @Override
        public ItemStack remove(int amount) {
            SmartWorkbenchMenu.this.clearStickyRecipe();
            return super.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            SmartWorkbenchMenu.this.clearStickyRecipe();
            super.onTake(player, stack);
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

    /**
     * 分块列表的一段：{@code first} 时清空缓冲，{@code last} 时把拼好的整份换上。
     * 列表可能好几千条、分包发送，中间帧不能让界面看到只到一半的列表。
     */
    public void appendClientCraftables(List<CraftableEntry> entries, boolean first, boolean last) {
        if (first) {
            this.clientCraftableBuffer.clear();
        }
        this.clientCraftableBuffer.addAll(entries);
        if (last) {
            this.craftables = List.copyOf(this.clientCraftableBuffer);
        }
    }

    /** 服务器推来的接入存储快照（同类已合并），REI 的缺料判断会用到。 */
    public void setClientStorageStacks(List<ItemStack> stacks) {
        this.clientStorageStacks = stacks;
        this.clientStorageSynced = true;
    }

    public List<ItemStack> getClientStorageStacks() {
        return this.clientStorageStacks;
    }

    /** 客户端有没有拿到过这份存储快照。没拿到之前，「缺料」的结论是不准的。 */
    public boolean hasClientStorageSnapshot() {
        return this.clientStorageSynced;
    }

    /**
     * 合成可用的全部料源：接入的容器（扳手绑定 + 附近扫描 + 里面的背包）在前，
     * 玩家自己的背包在最后。因为玩家背包排在后面，箱子里的材料会优先被用掉，
     * 身上的材料留到最后，符合“先花箱子里的存货”的直觉。
     */
    private List<IItemHandler> inputHandlers() {
        List<IItemHandler> handlers = new ArrayList<>();
        if (this.bench != null) {
            handlers.addAll(this.bench.getStorages());
        }
        if (this.playerInventoryHandler != null) {
            handlers.add(this.playerInventoryHandler);
        }
        return handlers;
    }

    /**
     * 「能不能合成」这类<b>只读</b>扫描用的料源：{@link #inputHandlers()} 再加上合成格自己。
     * <p>
     * 点一下列表把材料整份搬进合成格之后，箱子和背包可能已经一滴不剩——这时工作台<b>自己身上</b>
     * （合成格里的料）就是唯一的库存。以前扫描不算合成格，右侧列表会整屏清空，连用同一批料
     * 还能做出的别的东西也看不见了。把合成格算进来之后，切配方来回挑选都不用先把料退回去。
     * <p>
     * 真正要抽料、退料的路径（Shift 直接合成、Ctrl 批量、自动合成）仍然只用 {@link #inputHandlers()}：
     * 它们会绕过合成格的来源记录（matrixSources）直接改格子，退料时就对不上账。合成格对这几条路径
     * 的可用材料贡献不受影响——它们开工前都先 {@code returnMatrixMaterials} 把料退回原容器/背包，
     * 再按整份库存来算，不会重复计数。
     */
    private List<IItemHandler> scanHandlers() {
        List<IItemHandler> handlers = inputHandlers();
        handlers.add(this.matrixScanHandler);
        return handlers;
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
        clearStickyRecipe();
        markDirty();
        this.matrixSignature = matrixSignature();
    }

    private void clearMatrixSources() {
        for (List<SourcePart> sources : this.matrixSources) {
            sources.clear();
        }
        markOriginsDirty();
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
            markOriginsDirty();
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
        markOriginsDirty();
    }

    /* 材料来路：记进方块实体，关掉界面再打开也能原路退回 */

    /** 合成格的来路变了，等这一刻的数据包发完抄给方块实体。 */
    private void markOriginsDirty() {
        this.originsDirty = true;
    }

    private void flushOrigins() {
        if (!this.originsDirty) {
            return;
        }
        this.originsDirty = false;
        syncOriginsToBench();
    }

    /**
     * 把合成格每件材料的来路抄给方块实体。
     * <p>
     * 来路以前只存在菜单里，而菜单每关一次就没了：再打开界面切换配方时，
     * 格子里那些从箱子拿的材料会被当成「玩家手动摆的」，一股脑塞进背包。
     * 抄一份到方块实体上，下次打开就能接着原路退回。
     */
    private void syncOriginsToBench() {
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            ItemStack inSlot = this.matrix.getItem(slot);
            List<SmartWorkbenchBlockEntity.GridOrigin> origins = new ArrayList<>();
            if (!inSlot.isEmpty()) {
                int remaining = inSlot.getCount();
                for (SourcePart part : this.matrixSources[slot]) {
                    Origin origin = part.origin();
                    if (remaining <= 0 || origin == null || part.stack().isEmpty()) {
                        continue;
                    }
                    if (!ItemStack.isSameItemSameTags(part.stack(), inSlot)) {
                        continue;
                    }
                    int count = Math.min(part.stack().getCount(), remaining);
                    if (count <= 0) {
                        continue;
                    }
                    addOrigin(origins, part.stack().copyWithCount(count), origin);
                    remaining -= count;
                }
            }
            this.bench.setGridOrigins(slot, origins);
        }
    }

    /**
     * 同源、同物品的记录合并成一条。
     * <p>
     * Shift 摆满时是一格一格取的料，合成格里同一个格子可能有几十条「同一台箱子同一格」的记录，
     * 不合并的话存档里会堆出一大串只有数量不同的条目。
     */
    private static void addOrigin(List<SmartWorkbenchBlockEntity.GridOrigin> origins,
                                  ItemStack stack, Origin origin) {
        for (SmartWorkbenchBlockEntity.GridOrigin existing : origins) {
            if (existing.kind() == origin.kind() && existing.slot() == origin.slot()
                    && Objects.equals(existing.pos(), origin.pos())
                    && ItemStack.isSameItemSameTags(existing.stack(), stack)) {
                existing.stack().grow(stack.getCount());
                return;
            }
        }
        origins.add(new SmartWorkbenchBlockEntity.GridOrigin(
                stack.copy(), origin.pos(), origin.slot(), origin.kind()));
    }

    /** 打开界面时把上次的来路接回来。 */
    private void restoreOriginsFromBench() {
        if (this.bench == null) {
            return;
        }
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            List<SourcePart> parts = this.matrixSources[slot];
            parts.clear();
            ItemStack inSlot = this.matrix.getItem(slot);
            if (inSlot.isEmpty()) {
                continue;
            }
            int remaining = inSlot.getCount();
            for (SmartWorkbenchBlockEntity.GridOrigin origin : this.bench.getGridOrigins(slot)) {
                if (remaining <= 0 || !ItemStack.isSameItemSameTags(origin.stack(), inSlot)) {
                    break;
                }
                int count = Math.min(origin.stack().getCount(), remaining);
                if (count <= 0) {
                    break;
                }
                parts.add(new SourcePart(origin.stack().copyWithCount(count),
                        new Origin(origin.pos(), origin.slot(), origin.kind())));
                remaining -= count;
            }
        }
        this.originsDirty = false;
    }

    /** 把存储里取料用的「活处理器」翻译成能持久保存的来路。 */
    private Origin originOf(StoragePool.Source source) {
        if (source == null) {
            return null;
        }
        if (this.playerInventoryHandler != null && source.handler() == this.playerInventoryHandler) {
            return new Origin(null, -1, SmartWorkbenchBlockEntity.OriginKind.PLAYER);
        }
        BlockPos pos = this.bench == null ? null : this.bench.storagePositionOf(source.handler());
        return pos == null
                ? new Origin(null, -1, SmartWorkbenchBlockEntity.OriginKind.ANY)
                : new Origin(pos, source.slot(), SmartWorkbenchBlockEntity.OriginKind.STORAGE);
    }

    /**
     * 把一件材料按来路退回去：先插回原来那台容器的原格，原格放不下（或容器没了）再找别的
     * 接入存储，都没有才进玩家背包，最后才掉在脚下。
     * <p>
     * 玩家自己手动摆进格子的材料没有来路（origin 为 null），照旧退回背包——那是他自己身上掏的。
     */
    private void returnToOrigin(ServerPlayer player, Origin origin, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        SmartWorkbenchBlockEntity.OriginKind kind = origin == null
                ? SmartWorkbenchBlockEntity.OriginKind.PLAYER : origin.kind();
        if (kind == SmartWorkbenchBlockEntity.OriginKind.STORAGE) {
            IItemHandler handler = this.bench == null ? null : this.bench.handlerAt(origin.pos());
            if (handler != null) {
                ItemStack rest = stack;
                try {
                    rest = handler.insertItem(origin.slot(), stack, false);
                } catch (RuntimeException ignored) {
                    // 只读容器或没实现插入的包装器：退回原格失败就当没放进去，接着往下找地方
                }
                if (rest.isEmpty()) {
                    return;
                }
                stack = rest;
            }
            kind = SmartWorkbenchBlockEntity.OriginKind.ANY;
        }
        if (kind == SmartWorkbenchBlockEntity.OriginKind.ANY) {
            stack = insertIntoStorages(this.bench == null ? List.of() : this.bench.getStorages(), stack);
        }
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
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
        markOriginsDirty();
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
                || !(recipe instanceof CraftingRecipe crafting) || !isPlainCraft(crafting)
                || !matchesWithOwnPreviews(crafting)) {
            // 特殊配方（修装备、复制旗帜、双箱子升级这类「材料表骗人」的）不接管
            return;
        }
        if (this.autoRefillEnabled) {
            this.bench.refreshStorages(true);
            if (!hasAllIngredients(crafting, StoragePool.collect(scanHandlers()))) {
                // 直接材料不齐但开了自动补齐：先链式补做中间材料，再摆这份配方
                pullChainFromStorage(player, crafting);
                return;
            }
        }
        placeRecipeIntoGrid(player, crafting, placeAll);
        markDirty();
        rememberSelection(player, crafting);
        updateResult();
        beginStickyRecipe(player, crafting, placeAll);
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
        return placeRecipeIntoGrid(player, recipe, placeAll, null);
    }

    /**
     * 摆配方进合成格。{@code preloaded} 是链式补做刚产出、还没进任何容器的中间材料，
     * 优先于存储取料被使用；这份配方没用完的会在结尾退回玩家背包，不会凭空消失。
     */
    private int placeRecipeIntoGrid(ServerPlayer player, CraftingRecipe recipe, boolean placeAll,
                                    List<ItemStack> preloaded) {
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
                    existing.add(new TrackedStack(part.stack().copyWithCount(count), part.origin()));
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
        StoragePool pool = StoragePool.collect(inputHandlers());
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
                    if (reused == null && preloaded != null) {
                        ItemStack crafted = takeFromStacks(preloaded, ingredient);
                        if (!crafted.isEmpty()) {
                            reused = new TrackedStack(crafted, null);
                        }
                    }
                    if (reused == null) {
                        int candidate = pool.find(ingredient);
                        if (candidate < 0) {
                            break;
                        }
                        ItemStack extracted = pool.take(candidate, 1);
                        if (extracted.isEmpty()) {
                            break;
                        }
                        reused = new TrackedStack(extracted, originOf(pool.source(candidate)));
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
                    if (reused.origin() != null) {
                        this.matrixSources[slot].add(new SourcePart(reusedStack.copy(), reused.origin()));
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
            returnToOrigin(player, leftover.origin(), leftover.stack());
        }
        // 4) 链式补做出来、这份配方没用完的中间产物优先塞回箱子，塞不下才进背包，
        //    避免每做一次链式就往玩家背包里塞一堆半成品。
        if (preloaded != null) {
            stowOrReturn(player, preloaded);
        }
        // 5) 把这一格料是从哪台箱子取的记到工作台上：关掉界面再打开，切配方照样能原路退回。
        syncOriginsToBench();
        this.originsDirty = false;
        return pulled;
    }

    /** 从链式补做得到的中间产物里拿出一件符合材料条件的物品。 */
    private static ItemStack takeFromStacks(List<ItemStack> stacks, Ingredient ingredient) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!ingredient.test(stack)) {
                continue;
            }
            ItemStack result = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) {
                stacks.remove(i);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    private record TrackedStack(ItemStack stack, Origin origin) {
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
            return new TrackedStack(result, tracked.origin());
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
            // 界面刚打开时补一包开关状态：客户端菜单是新建的，默认「开」，
            // 而玩家存档里可能是「关」，必须明确告诉他一次。
            if (!this.autoRefillSynced) {
                this.autoRefillSynced = true;
                if (this.player instanceof ServerPlayer serverPlayer) {
                    ModNetwork.sendToPlayer(serverPlayer,
                            new S2CAutoRefillPacket(this.benchPos, this.autoRefillEnabled));
                }
            }
            // 合成格的来路变了就抄给方块实体：界面关掉之后这份记录还得留着。
            flushOrigins();
            String signature = matrixSignature();
            if (!signature.equals(this.matrixSignature)) {
                this.matrixSignature = signature;
                if (this.bench != null) {
                    this.bench.setChanged();
                }
                updateResult();
                // 可合成列表现在也算合成格里的料：格子里东西一变，列表就得跟上，
                // 把节流窗口清零，让下面那句周期刷新当帧就重算，别再等满一秒。
                this.nextRefreshTick = 0L;
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
            // 关界面前把最后的来路记下来，下次打开切换配方才能原路退回
            flushOrigins();
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
        CraftingRecipe chosen = null;
        if (!this.matrix.isEmpty()) {
            chosen = pickGridRecipe();
            if (chosen != null) {
                boolean allowed = !(this.player instanceof ServerPlayer serverPlayer)
                        || this.resultSlots.setRecipeUsed(this.level, serverPlayer, chosen);
                if (allowed) {
                    ItemStack assembled = chosen.assemble(this.matrix, this.level.registryAccess());
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
        this.lastGridRecipe = chosen;
        this.resultSlots.setItem(0, result);
    }

    /**
     * 决定当前合成格该出哪一份配方。一件产物可能同时有多份配方能匹配（不同模组各加了一份），
     * 挑选顺序是：
     * <ol>
     *   <li><b>装了多态合成就听它的</b>：玩家可以用产物格旁边的小按钮在几份配方间切换；</li>
     *   <li>否则用玩家在本界面明确点过的那一份（点列表 / REI 取料时记下的）；</li>
     *   <li>都没有才回到原版逻辑，取第一份匹配的。</li>
     * </ol>
     */
    private CraftingRecipe pickGridRecipe() {
        Optional<CraftingRecipe> polymorph =
                PolymorphCompat.pickRecipe(this, RecipeType.CRAFTING, this.matrix, this.level, this.player);
        if (polymorph.isPresent()) {
            return polymorph.get();
        }
        if (this.preferredRecipe != null && this.preferredRecipe.matches(this.matrix, this.level)) {
            return this.preferredRecipe;
        }
        return this.level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, this.matrix, this.level).orElse(null);
    }

    /** 记下玩家明确选的配方，并同步给多态合成，保证产物格显示的正是这一份。 */
    private void rememberSelection(ServerPlayer player, CraftingRecipe recipe) {
        this.preferredRecipe = recipe;
        PolymorphCompat.selectRecipe(player, recipe);
    }

    /** 拿走一次产物：每个非空合成格减 1，桶之类的剩余物放回格子或塞给玩家。 */
    public void consumeGrid(Player player) {
        if (this.level.isClientSide || this.matrix.isEmpty()) {
            return;
        }
        ForgeHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remaining;
        CraftingRecipe used;
        try {
            // 按「显示产物时用的同一份配方」算剩余物：同产物配方有好几份时，
            // 原版 getRemainingItemsFor 又会去随便挑一份，可能和刚拿走的产物对不上。
            used = this.lastGridRecipe;
            remaining = used != null && used.matches(this.matrix, this.level)
                    ? used.getRemainingItems(this.matrix)
                    : this.level.getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
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
        refillStickyAfterCraft(player, used);
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
    }

    /**
     * 记下「连续合成」锁定的配方：只在材料是从 REI/配方书/右侧列表一键摆进格子的、
     * 并且摆完真能出产物时成立。手动一格一格摆的料走不到这里，也就不会被自动补料。
     */
    private void beginStickyRecipe(ServerPlayer player, CraftingRecipe recipe, boolean stackAll) {
        if (recipe == null || recipe.getId() == null) {
            this.stickyRecipe = null;
            return;
        }
        ForgeHooks.setCraftingPlayer(player);
        boolean matches;
        try {
            matches = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
        if (!matches) {
            this.stickyRecipe = null;
            return;
        }
        this.stickyRecipe = recipe;
        this.stickyStackAll = stackAll;
    }

    private void clearStickyRecipe() {
        this.stickyRecipe = null;
        this.stickyStackAll = false;
    }

    /**
     * 合成拿走产物后，按锁定的配方把同样的材料再补进合成格，让玩家能一口气合成很多次
     * （这就是「点一次 REI 加号只能合成一次」的解药）。
     * <p>
     * 只在<b>凑得齐完整一份</b>时才补：凑不齐就干脆不动，免得从仓库里掏出一半材料
     * 卡在格子里。背包 + 接入存储 + 格子里剩下的料加在一起算，够就补。
     */
    private void refillStickyAfterCraft(Player player, CraftingRecipe justUsed) {
        if (this.stickyRecipe == null || this.bench == null) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (justUsed == null || justUsed.getId() == null
                || !justUsed.getId().equals(this.stickyRecipe.getId())) {
            // 刚合成的不是锁定的那一份（比如用多态按钮换了配方）：约定作废，别再自动补
            clearStickyRecipe();
            return;
        }
        Recipe<?> fresh = this.level.getRecipeManager().byKey(this.stickyRecipe.getId()).orElse(null);
        if (!(fresh instanceof CraftingRecipe crafting) || !isPlainCraft(crafting)) {
            clearStickyRecipe();
            return;
        }
        this.stickyRecipe = crafting;
        this.bench.refreshStorages(true);
        StoragePool available = StoragePool.collect(scanHandlers());
        if (available.isEmpty() || !hasAllIngredients(crafting, available)) {
            clearStickyRecipe();
            return;
        }
        placeRecipeIntoGrid(serverPlayer, crafting, this.stickyStackAll);
        rememberSelection(serverPlayer, crafting);
        markDirty();
        updateResult();
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
            // 玩家自己把身上的料甩进合成格：这一下属于手动摆放，连续合成约定作废
            clearStickyRecipe();
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
            if (player instanceof ServerPlayer serverPlayer) {
                // 写进玩家存档：下次打开界面还是这个状态
                serverPlayer.getPersistentData().putBoolean(AUTO_REFILL_TAG, this.autoRefillEnabled);
                // 立刻回包让界面上的按钮文字跟着变（原版 DataSlot 在值为 0 时不发包，靠不住）
                ModNetwork.sendToPlayer(serverPlayer,
                        new S2CAutoRefillPacket(this.benchPos, this.autoRefillEnabled));
            }
            notify(player, this.autoRefillEnabled ? "已开启自动补齐" : "已关闭自动补齐",
                    this.autoRefillEnabled
                            ? "gui." + SmartWorkbenchMod.MOD_ID + ".msg.auto_refill_on"
                            : "gui." + SmartWorkbenchMod.MOD_ID + ".msg.auto_refill_off");
            return true;
        }
        if (id == BUTTON_BATCH_GRID && player instanceof ServerPlayer serverPlayer && stillValid(player)) {
            if (!acceptAction(serverPlayer)) {
                return false;
            }
            batchCraftFromGrid(serverPlayer, 0);
            return true;
        }
        if (id == BUTTON_CLEAR_GRID && player instanceof ServerPlayer serverPlayer) {
            if (!acceptAction(serverPlayer)) {
                return false;
            }
            int items = countMatrixItemCount();
            if (items == 0) {
                // 格子本来就是空的：给一句反馈，免得玩家以为按钮坏了
                notify(player, "合成格里没有材料", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_nothing");
                return true;
            }
            returnMatrixMaterials(serverPlayer);
            // 材料回到了箱子里，可合成列表自然要多出一些项，立刻重扫一遍
            refreshCraftables(true);
            notifyCount(serverPlayer, "gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_cleared",
                    "已清空合成格：%s 件材料已退回原处", items);
            return true;
        }
        return false;
    }

    /** 合成格里现在一共有多少件材料（用于「清空」的反馈数字）。 */
    private int countMatrixItemCount() {
        int total = 0;
        for (int slot = 0; slot < SmartWorkbenchBlockEntity.GRID_SIZE; slot++) {
            total += this.matrix.getItem(slot).getCount();
        }
        return total;
    }

    /**
     * 客户端点了右侧列表里的某一项（走自定义包，见 C2SCraftRequestPacket）。
     *
     * @param craft true = Shift 点击：直接从存储合成一次，产物进背包
     *              false = 普通点击：把材料配到合成格（不需要的材料退回背包）
     * @param batch true = Ctrl 点击：反复合成，直到材料不够或背包塞不下
     */
    public void onRecipeListClick(ServerPlayer player, ResourceLocation recipeId, boolean craft, boolean batch,
                                  int batchCount) {
        if (!acceptAction(player)) {
            return;
        }
        if (batch && recipeId == null) {
            this.activeFeedback = new CraftFeedback();
            batchCraftFromGrid(player, batchCount);
            return;
        }
        this.activeFeedback = new CraftFeedback();
        if (batch || craft) {
            // 右侧直接合成与当前合成格无关；先把旧材料退回各自来源，
            // 避免同一批材料同时出现在合成格和存储快照里。
            returnMatrixMaterials(player);
            if (batch) {
                batchCraftFromStorage(player, recipeId, batchCount);
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
        if (!(recipe instanceof CraftingRecipe crafting) || !isPlainCraft(crafting)
                || !matchesWithOwnPreviews(crafting)) {
            notify(player, "这个配方不支持一键取料", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
            return;
        }
        this.bench.refreshStorages(true);
        // 预检要算上合成格里已有的料：placeRecipeIntoGrid 本来就会优先复用格子里现成的材料，
        // 预检却只看箱子和背包的话，会出现"明明够、报缺料"的对不上账。
        StoragePool availablePool = StoragePool.collect(scanHandlers());
        boolean ingredientsAvailable = hasAllIngredients(crafting, availablePool);
        if (!ingredientsAvailable && this.autoRefillEnabled) {
            pullChainFromStorage(player, crafting);
            return;
        }
        if (!ingredientsAvailable) {
            notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
            refreshCraftables(true);
            return;
        }
        placeRecipeIntoGrid(player, crafting, stackAll);
        markDirty();
        rememberSelection(player, crafting);
        updateResult();
        beginStickyRecipe(player, crafting, stackAll);
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
                returnToOrigin(player, part.origin(), part.stack().copyWithCount(count));
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
        clearStickyRecipe();
        markDirty();
        updateResult();
        this.matrixSignature = matrixSignature();
        syncOriginsToBench();
        this.originsDirty = false;
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
        List<IItemHandler> storages = this.bench.getStorages();
        // 可合成列表按「接入存储 + 玩家背包 + 合成格自身」一起算：只放身上、不塞箱子的材料能列出来，
        // 一键取料搬进合成格后（箱子和背包可能已经掏空），格子里的料也还能撑起别的配方。
        StoragePool pool = StoragePool.collect(scanHandlers());
        // 推给客户端的快照仍然只抄接入存储：玩家背包在客户端本来就能实时读到，
        // 把它塞进快照反而会慢一拍，让 REI 误判成缺料或多料。
        List<ItemStack> storageSnapshot = storages.isEmpty()
                ? List.of()
                : StoragePool.collect(storages).mergeForClient(STORAGE_SNAPSHOT_LIMIT);
        // 数据包重载会换掉配方索引，先让它把版本号刷新，再和库存一起算指纹
        List<CraftingRecipe> recipes = getPlainRecipes();
        long fingerprint = craftableFingerprint(pool, storageSnapshot, storages.size(), getOutputCount());
        if (this.hasCraftableFingerprint && fingerprint == this.craftableFingerprint) {
            // 库存、配方、输出容器都没变：列表和客户端手上那份一模一样，不用重算也不用重发
            return;
        }
        this.craftableFingerprint = fingerprint;
        this.hasCraftableFingerprint = true;

        List<CraftableEntry> next = new ArrayList<>();
        if (!pool.isEmpty()) {
            // 上限只用来兜住极端整合包；正常情况下直接项和链式项都会尽量列全。
            int max = ModConfig.maxListSize();
            Set<ResourceLocation> directIds = new HashSet<>();
            // 直接项做出来的是什么，也要喂给链式闭包：像「钻石块 -> 9 钻石 -> 钻石剑」，
            // 中间那步本身是直接项，产物不进可达集合的话，后续链子就断了。
            Set<Item> directOutputs = new HashSet<>();
            for (CraftingRecipe recipe : recipes) {
                if (next.size() >= max) {
                    break;
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
                boolean direct = possible && pool.plan(ingredients) != null;
                if (!direct) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (!result.isEmpty()) {
                    next.add(CraftableEntry.of(recipe.getId(), result.copy(), false));
                    directIds.add(recipe.getId());
                    directOutputs.add(result.getItem());
                }
            }
            next.sort(Comparator.comparing(entry -> entry.result().getItem().getDescriptionId()));
            next.addAll(collectChainEntries(pool, directIds, directOutputs, max - next.size()));
            next = dedupeByResult(next);
        }
        this.craftables = next;
        if (this.player instanceof ServerPlayer serverPlayer) {
            sendCraftableList(serverPlayer, next, storageSnapshot, storages.size(), getOutputCount());
        }
    }

    /**
     * 链式可达扫描（问题 1）：从库存里已有的<b>物品种类</b>出发做闭包——
     * 一份配方只要它每种材料都「有某个种类的实例可达」，就算点亮，
     * 它的产物种类也加入可达集合，反复直到没有新增。被点亮、又不在直接列表里的
     * 配方就是「链」项：材料种类齐了，但具体数量够不够这一步不算。
     * <p>
     * 数量够不够交给 {@link #createChainChecker} 在闭包结束后复核一遍：
     * 逐条做数量级递归规划在几万配方的整合包里太贵，所以那份复核带节点预算，
     * 算不完就保留（见该方法说明）。点击时服务端还会再用 {@code planRecipe}
     * 精确规划一次，真缺基础材料会如实提示，不会凭空产出或吞材料。
     * <p>
     * {@code directOutputs} 是「直接可合成配方」的产物种类，也必须算进初始可达集合。
     * 否则像「钻石块 -> 9 钻石 -> 钻石剑」这种链会断：中间那步「钻石块 -> 钻石」是
     * 直接项，钻石从没被标记成可达，于是所有钻石工具/装备都点不亮。
     * <p>
     * 复杂度：每条配方最多被它「已可达的材料种类数」触发重查，配合
     * {@link #getRecipesUsingItem()} 的反向索引，一次扫描在毫秒级；
     * 数量复核额外受配置的总预算约束。
     */
    private List<CraftableEntry> collectChainEntries(StoragePool pool, Set<ResourceLocation> directIds,
                                                     Set<Item> directOutputs, int budget) {
        if (budget <= 0 || !ModConfig.showChainCraftables() || pool.isEmpty()) {
            return List.of();
        }
        Map<net.minecraft.world.item.Item, List<CraftingRecipe>> using = getRecipesUsingItem();
        Set<Item> reachable = pool.distinctItems();
        reachable.addAll(directOutputs);
        Map<ResourceLocation, CraftingRecipe> chainResults = new LinkedHashMap<>();
        Deque<Item> queue = new ArrayDeque<>(reachable);
        while (!queue.isEmpty()) {
            Item item = queue.poll();
            for (CraftingRecipe recipe : using.getOrDefault(item, List.of())) {
                ResourceLocation id = recipe.getId();
                if (id == null || directIds.contains(id) || chainResults.containsKey(id)) {
                    continue;
                }
                boolean ok = true;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    if (!hasReachableType(ingredient, reachable)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (result.isEmpty()) {
                    continue;
                }
                chainResults.put(id, recipe);
                if (reachable.add(result.getItem())) {
                    queue.add(result.getItem());
                }
            }
        }
        if (chainResults.isEmpty()) {
            return List.of();
        }
        // 数量级复核（见 createChainChecker 的说明）。预算 0 表示不做复核，退回旧行为。
        ChainFeasibility checker = createChainChecker(pool);
        List<CraftableEntry> out = new ArrayList<>(chainResults.size());
        for (Map.Entry<ResourceLocation, CraftingRecipe> entry : chainResults.entrySet()) {
            CraftingRecipe recipe = entry.getValue();
            // 预算用尽也照样问一次：复核器会先做不花预算的「一眼假」判定，
            // 把「石头只能熔炉烧」这类必然做不出的项剔掉，剩下的才按未知保留。
            if (checker != null && checker.check(recipe) == ChainFeasibility.Verdict.IMPOSSIBLE) {
                continue;
            }
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (!result.isEmpty()) {
                out.add(CraftableEntry.of(entry.getKey(), result.copy(), true));
            }
        }
        out.sort(Comparator.comparing(entry -> entry.result().getItem().getDescriptionId()));
        return out.size() > budget ? new ArrayList<>(out.subList(0, budget)) : out;
    }

    /**
     * 同一件产物只留一条列表项：排在前的当「当前用的那份」（直接项先于链式项），
     * 后来的收进这条的候选清单。不然玩家会看到两个一模一样的图标——比如精妙存储的
     * 木箱有「木板+拉杆」和「原版箱子+拉杆」两份配方，图标完全相同，分不清点哪个。
     * <p>
     * 候选清单要一路带到客户端：界面上右键那一格就能在几份配方之间轮换
     * （见 {@code SmartWorkbenchScreen#cycleRecipe}）。这跟多态合成无关——
     * 多态合成管「同一把材料出哪个产物」，这里问的是「同一个产物用哪套材料」。
     */
    private static List<CraftableEntry> dedupeByResult(List<CraftableEntry> entries) {
        Map<String, Integer> firstAt = new HashMap<>();
        List<CraftableEntry> out = new ArrayList<>(entries.size());
        for (CraftableEntry entry : entries) {
            String key = CraftableEntry.resultKey(entry.result());
            Integer at = firstAt.get(key);
            if (at == null) {
                firstAt.put(key, out.size());
                out.add(entry);
            } else {
                out.set(at, out.get(at).mergedWith(entry));
            }
        }
        return out;
    }

    /**
     * 建一个链式列表的「数量级复核器」。
     * <p>
     * 类型闭包只看「有没有这种材料」，不看够不够：铁砧＝3 铁块＋4 铁锭，库里只有
     * 8 铁锭＋173 铁粒（≈27 个铁锭），类型上铁块、铁锭都「有」，于是铁砧被列出来，
     * 点下去却弹「缺少基础材料：铁锭x2」——这正是玩家报的假阳性。
     * <p>
     * 复核用和点击时同一套递归规划按真实数量复算，做不出来的链式项不列，
     * 列表里出现的每一项都能真的点出来。单个候选的预算取总预算和 20000 的较小值，
     * 免得一个又深又绕的候选把整轮预算吃光；预算用尽后停止复核、保留剩余项
     * （宁可多留一项也不误删）。配置里把 chainVerifyBudget 填 0 则完全关闭复核。
     */
    private ChainFeasibility createChainChecker(StoragePool pool) {
        int totalBudget = ModConfig.chainVerifyBudget();
        if (totalBudget <= 0) {
            return null;
        }
        int perCandidate = Math.max(2_000, Math.min(totalBudget, 20_000));
        return new ChainFeasibility(pool.snapshotStacks(), getRecipesByResult(),
                this.level.registryAccess(), ModConfig.recursiveMaxDepth(), ModConfig.recursiveMaxSteps(),
                perCandidate, totalBudget);
    }

    /** 这份材料的任意一种可选物品在可达种类集合里吗（只看种类，不看数量和 NBT）。 */
    private static boolean hasReachableType(Ingredient ingredient, Set<Item> reachable) {
        for (ItemStack preview : ingredient.getItems()) {
            if (reachable.contains(preview.getItem())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把「库存 + 存储快照 + 输出容器 + 配方版本」揉成一个 64 位指纹。
     * 指纹一样就说明列表不会变，不必再花几十毫秒重扫配方，也不用重发包。
     */
    private long craftableFingerprint(StoragePool pool, List<ItemStack> snapshot, int storageCount, int outputCount) {
        long hash = pool.fingerprint();
        hash = hash * 31L + this.recipeIndexVersion;
        hash = hash * 31L + storageCount;
        hash = hash * 31L + outputCount;
        for (ItemStack stack : snapshot) {
            hash = hash * 31L + stack.getItem().getDescriptionId().hashCode();
            hash = hash * 31L + stack.getCount();
        }
        return hash;
    }

    /**
     * 列表可能有几千条，按固定块大小分成几个网络包依次发：第一块带存储快照，
     * 客户端累加到「最后一块」才整体换上，避开网络帧 1MB 的硬上限。
     */
    private void sendCraftableList(ServerPlayer player, List<CraftableEntry> entries,
                                   List<ItemStack> snapshot, int storageCount, int outputCount) {
        int chunkSize = S2CCraftableListPacket.CHUNK_SIZE;
        int chunkCount = Math.max(1, (entries.size() + chunkSize - 1) / chunkSize);
        for (int index = 0; index < chunkCount; index++) {
            int from = Math.min(entries.size(), index * chunkSize);
            int to = Math.min(entries.size(), from + chunkSize);
            List<CraftableEntry> chunk = List.copyOf(entries.subList(from, to));
            ModNetwork.sendToPlayer(player, new S2CCraftableListPacket(this.benchPos, chunk, index, chunkCount,
                    index == 0 ? snapshot : List.of(), storageCount, outputCount));
        }
    }

    /** 按服务器刚读取的存储内容预检一份配方，避免客户端快照过期时仍开始搬料。 */
    private static boolean hasAllIngredients(CraftingRecipe recipe, StoragePool pool) {
        if (pool.isEmpty()) {
            return false;
        }
        return pool.plan(recipe.getIngredients()) != null;
    }

    /**
     * 判断一份配方能不能一键取料 / 批量合成：只要是能放进 3x3、材料表完整的合成配方就行。
     * <p>
     * <b>这里刻意不按 {@link CraftingRecipe#isSpecial()} 一刀切。</b>精妙存储的
     * {@code sophisticatedstorage:storage_tier_upgrade}（把木箱升级成铁箱/金箱/钻石箱，
     * 同时保留箱子里的物品）把自己标成 special，但它本质仍是一份 shaped 配方：材料表完整、
     * 结果由 {@code assemble} 在合成格里现算，完全可以一键摆料和批量合成——以前被
     * {@code isSpecial()} 挡掉，才会出现「点加号说这个配方不支持一键合成」。
     * <p>
     * 真正没法自动化的特殊配方（修装备、复制旗帜、染色、烟花、地图扩展等）都是
     * {@code CustomRecipe} 的子类，它们的材料表本来就是空的，会被下面的
     * {@code ingredients.isEmpty()} 拦住；即便有别的模组给特殊配方写了材料表，
     * 取料后也会在 {@code recipe.matches} 复核，不合格就退款并如实提示，不会凭空产出。
     */
    private boolean isPlainCraft(CraftingRecipe recipe) {
        if (!recipe.canCraftInDimensions(COLUMNS, ROWS)) {
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
     * 特殊配方自匹配复核：把「每种材料的第一候选」按一键取料的摆法放进 3x3，
     * 问配方本身 {@code matches()} 认不认。
     * <p>
     * 背景：精妙存储的 {@code sophisticatedstorage:double_chest_tier_upgrade}
     * （双联箱子升档）在 JSON 里写的是「金箱子 + 钻石 + 钻石块」，产物图标和普通的
     * 钻石箱子配方一模一样；但它的 {@code matches()} 实际要求格子里那只箱子必须是
     * <b>双联</b>金箱子——而双联箱子物品做不出配方，只能摆两个箱子合出来。
     * 于是这份配方在列表里是「看着能做、点了永远缺料」的假项，链式规划还会拿它
     * 当中转站，绕出一屏「箱子x4、铜块x336」这种莫名其妙的缺口。
     * <p>
     * 普通（非 special）配方材料表就是全部条件，预览摆法必然匹配，不必复核；
     * special 配方里能正常一键取料的（如 {@code storage_tier_upgrade} 钻石围金箱）
     * 复核同样通过，不受影响。复核抛异常按不通过处理——那种配方连自己的材料表
     * 都不认，摆进真格子只会更糟。
     */
    private boolean matchesWithOwnPreviews(CraftingRecipe recipe) {
        if (!recipe.isSpecial()) {
            return true;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        NonNullList<ItemStack> items = NonNullList.withSize(SmartWorkbenchBlockEntity.GRID_SIZE, ItemStack.EMPTY);
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
        TransientCraftingContainer grid = new TransientCraftingContainer(this, COLUMNS, ROWS, items);
        ServerPlayer feedbackPlayer = this.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        try {
            ForgeHooks.setCraftingPlayer(feedbackPlayer);
            return recipe.matches(grid, this.level);
        } catch (Throwable ignored) {
            return false;
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
    }

    /**
     * 给 JEI / EMI 的桥用：这份配方能不能一键从存储取料。
     * 判断规则和服务器端 {@link #placeRecipeFromClient} 完全一致，免得界面上显示能点、
     * 点下去服务端又说不行。
     */
    public boolean canAutoPlace(Recipe<?> recipe) {
        return recipe instanceof CraftingRecipe crafting && isPlainCraft(crafting)
                && matchesWithOwnPreviews(crafting);
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
        return recipe instanceof CraftingRecipe crafting && isPlainCraft(crafting)
                && matchesWithOwnPreviews(crafting) ? crafting : null;
    }

    /* 从附近存储取料 */

    /**
     * 链式取料：先把当前格子清空退回原来源，再做出目标配方的中间材料，
     * 最后把目标配方完整摆回格子。这里只做 steps 的前 n-1 步，最后目标本身留给格子。
     */
    private void pullChainFromStorage(ServerPlayer player, CraftingRecipe recipe) {
        returnMatrixMaterials(player);
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(inputHandlers());
        PlannedRecipe planned = planBest(recipe, pool);
        // 这个入口是「直接材料不齐」才进来的，一般 autoCraftable 非空；但摆位重试时
        // 也可能材料其实够，所以只要步骤齐、没缺口就继续，交给后面的 matches 复核。
        boolean plannedOk = planned.succeeded()
                || (!planned.state().steps.isEmpty() && planned.missing().isEmpty());
        if (!plannedOk) {
            notifyPlanFailure(player, planned);
            markDirty();
            updateResult();
            this.matrixSignature = matrixSignature();
            refreshCraftables(false);
            return;
        }
        // 首选配方做不出来时 planBest 会换成同产物的另一份，后续摆料、记选择都用这一份
        recipe = planned.recipe();
        List<CraftingRecipe> intermediates = new ArrayList<>(planned.state().steps.subList(
                0, Math.max(0, planned.state().steps.size() - 1)));
        List<ItemStack> handoff = new ArrayList<>();
        if (!intermediates.isEmpty()) {
            this.activeFeedback = null;
            int outcome = executeRecursivePlan(player, intermediates, pool, false, handoff);
            if (outcome != CRAFT_OK) {
                returnHandoff(player, handoff);
                markDirty();
                updateResult();
                this.matrixSignature = matrixSignature();
                refreshCraftables(false);
                return;
            }
        }
        placeRecipeIntoGrid(player, recipe, false, handoff);
        markDirty();
        rememberSelection(player, recipe);
        updateResult();
        // 链式补做后复核一次：规划到执行之间材料可能被别的操作动过，摆不满要如实提示。
        ForgeHooks.setCraftingPlayer(player);
        boolean matched;
        try {
            matched = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
        if (!matched) {
            clearStickyRecipe();
            notify(player, "材料已经变化，链式补做未完成，请重新点击",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_changed");
        } else {
            beginStickyRecipe(player, recipe, false);
        }
        this.matrixSignature = matrixSignature();
        refreshCraftables(false);
    }

    /**
     * 把多出来的东西优先塞回接入的存储（扳手绑定的箱子、附近存储），塞不下的才退玩家背包，
     * 最后才掉在脚下。
     * <p>
     * 玩家要的是最终物品；链式补做时一次配方做出来却又没被下一步用完的中间产物
     * （多做的木棍、铁锭之类）如果直接塞背包，很快就把背包塞满、甚至掉一地。
     * 从哪台箱子取的材料就尽量还回箱子里，和「切换配方原路返回」是一个道理。
     */
    private void stowOrReturn(ServerPlayer player, List<ItemStack> stacks) {
        List<IItemHandler> storages = this.bench == null ? List.of() : this.bench.getStorages();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = insertIntoStorages(storages, stack);
            if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                player.drop(rest, false);
            }
        }
        stacks.clear();
    }

    /** 依次往每个接入存储的空位里插，返回塞不下的部分（不改动传入的堆叠）。 */
    private static ItemStack insertIntoStorages(List<IItemHandler> storages, ItemStack stack) {
        ItemStack rest = stack;
        for (IItemHandler handler : storages) {
            if (rest.isEmpty()) {
                break;
            }
            try {
                for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
                    rest = handler.insertItem(slot, rest, false);
                }
            } catch (RuntimeException ignored) {
                // 只读容器或没实现插入的包装器：跳过它，剩下的继续找能放的地方。
                // 这里绝不能把异常抛出去——它会把整个界面的数据包处理打断。
            }
        }
        return rest;
    }

    /** 链式补做中途失败时，已经做出来的中间产物优先塞回箱子，再退背包。 */
    private void returnHandoff(ServerPlayer player, List<ItemStack> handoff) {
        stowOrReturn(player, handoff);
    }

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
        this.bench.refreshStorages(true);
        if (this.autoRefillEnabled
                && !hasAllIngredients(recipe, StoragePool.collect(scanHandlers()))) {
            pullChainFromStorage(player, recipe);
            return;
        }
        placeRecipeIntoGrid(player, recipe, false);
        markDirty();
        rememberSelection(player, recipe);
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
            clearStickyRecipe();
            if (this.autoRefillEnabled) {
                // 预检把合成格里的料也算进去了，退回后可能反而不够：交给链式补做再试一次
                pullChainFromStorage(player, recipe);
                return;
            }
            notify(player, "附近存储和背包材料不足，缺少的中间材料可由其他配方合成",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        } else {
            // 普通点击列表 = 明确选了这份配方，和 REI 加号一样享受「合成后自动补料」
            beginStickyRecipe(player, recipe, false);
        }
    }

    /** 数量框对应的批量次数；配置打开时保留旧行为，直到材料耗尽或达到安全上限。 */
    private static int resolveBatchLimit(int requested) {
        if (ModConfig.batchUseAllMaterials() || requested <= 0) {
            return MAX_BATCH_CRAFTS;
        }
        return Math.min(MAX_BATCH_CRAFTS, Math.max(1, requested));
    }

    /**
     * 精确执行一轮链式合成。规划失败时只在调用方要求时提示，避免批量循环每轮重复弹消息。
     */
    private int chainCraftOnce(ServerPlayer player, CraftingRecipe recipe, boolean toOutput,
                               boolean notifyOnFail) {
        if (this.bench == null) {
            return CRAFT_MISSING;
        }
        this.bench.refreshStorages(true);
        StoragePool pool = StoragePool.collect(inputHandlers());
        PlannedRecipe planned = planBest(recipe, pool);
        if (!planned.succeeded()) {
            if (notifyOnFail) {
                notifyPlanFailure(player, planned);
            }
            return CRAFT_MISSING;
        }
        return executeRecursivePlan(player, planned.state().steps, pool, toOutput);
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
        StoragePool pool = StoragePool.collect(inputHandlers());
        int outcome = craftOnce(player, recipe, pool, false);
        if (outcome == CRAFT_MISSING && this.autoRefillEnabled) {
            outcome = chainCraftOnce(player, recipe, false, true);
        }
        if (outcome == CRAFT_OK) {
            notifyFeedback(player, 1);
        } else if (outcome == CRAFT_MISSING && !this.autoRefillEnabled) {
            // 关闭自动补齐时保持旧行为：只使用现成的直接材料。
            notify(player, "附近存储和背包材料不足，缺少的中间材料可由其他配方合成",
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
    private void batchCraftFromStorage(ServerPlayer player, ResourceLocation recipeId, int requestedCount) {
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
        int limit = resolveBatchLimit(requestedCount);
        for (int guard = 0; guard < limit; guard++) {
            StoragePool pool = StoragePool.collect(inputHandlers());
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                if (crafted == 0) {
                    notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                }
                break;
            }
            outcome = craftOnce(player, recipe, pool, crafted == 0 && !this.autoRefillEnabled);
            if (outcome == CRAFT_MISSING && this.autoRefillEnabled) {
                outcome = chainCraftOnce(player, recipe, false, crafted == 0);
            }
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
     * 产物格上的 Ctrl+左键：把合成格里<b>已经摆好的这份配方</b>批量合成。
     * <p>
     * 材料从哪来的不重要——REI 加号摆进来的、右侧列表点进来的、自己一格一格
     * 从背包里拖进来的，只要格子里能出产物，就按这份配方一直做到材料用完。
     * 格子里现成的料会先退回（来源记录的回原容器，手动放的回背包），再和
     * 接入存储、背包一起按「整份库存」反复取材，产物进背包。
     */
    private void batchCraftFromGrid(ServerPlayer player, int requestedCount) {
        if (this.bench == null) {
            return;
        }
        updateResult();
        CraftingRecipe recipe = this.lastGridRecipe;
        if (recipe == null || recipe.getId() == null || this.resultSlots.getItem(0).isEmpty()) {
            notify(player, "合成格里还没有能合成的配方", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_empty");
            return;
        }
        if (!isPlainCraft(recipe)) {
            notify(player, "这个配方不支持一键合成，请点图标取料到合成格",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.unsupported");
            return;
        }
        this.activeFeedback = new CraftFeedback();
        // 格子里的料也算库存：先整份退回（来源记录的回原容器，手动放的回背包），
        // 之后每一轮都按「接入存储 + 背包」取材，刚退回的那份一定会被用上，不会被丢掉。
        returnMatrixMaterials(player);
        this.bench.refreshStorages(true);
        int crafted = 0;
        int limit = resolveBatchLimit(requestedCount);
        for (int guard = 0; guard < limit; guard++) {
            StoragePool pool = StoragePool.collect(inputHandlers());
            if (pool.isEmpty()) {
                if (crafted == 0) {
                    notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                }
                break;
            }
            // 只有第一次失败时才提示，中途没料了是正常结束
            int outcome = craftOnce(player, recipe, pool, crafted == 0 && !this.autoRefillEnabled);
            if (outcome == CRAFT_MISSING && this.autoRefillEnabled) {
                outcome = chainCraftOnce(player, recipe, false, crafted == 0);
            }
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
        this.matrixSignature = matrixSignature();
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
        runAutoCraftFromStorage(player, recipeId);
    }

    /** 产物格 Alt+左键入口：服务端以当前真正匹配的配方为准，避免客户端伪造配方 ID。 */
    public void autoCraftFromGrid(ServerPlayer player) {
        if (!acceptAction(player)) {
            return;
        }
        updateResult();
        CraftingRecipe recipe = this.lastGridRecipe;
        if (recipe == null || recipe.getId() == null || this.resultSlots.getItem(0).isEmpty()) {
            notify(player, "合成格里还没有能合成的配方", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_empty");
            return;
        }
        runAutoCraftFromStorage(player, recipe.getId());
    }

    private void runAutoCraftFromStorage(ServerPlayer player, ResourceLocation recipeId) {
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
        // 预算中止要就地记下来：这个循环可能因为背包/输出容器而提前退出，
        // 一次都没走到规划那一步，直接读 planAborted 会读到上一次操作留下的旧值。
        boolean planAborted = false;
        this.activeFeedback = new CraftFeedback();
        // 自动合成完全独立于合成格，先归还当前格子，防止把同一份材料重复算入库存。
        returnMatrixMaterials(player);

        for (int i = 0; i < limit; i++) {
            StoragePool pool = StoragePool.collect(inputHandlers());
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
            StoragePool planPool = StoragePool.collect(inputHandlers());
            PlannedRecipe planned = planBest(recipe, planPool);
            if (!planned.succeeded()) {
                missing = planned.missing();
                planAborted = planned.aborted();
                outcome = CRAFT_MISSING;
                break;
            }
            // 玩家点的那份做不出来时，换成同产物里真正能做的那份，后续几轮也沿用
            recipe = planned.recipe();
            int recursive = executeRecursivePlan(player, planned.state().steps, planPool, true);
            if (recursive == CRAFT_OK || recursive == CRAFT_OUTPUT_FULL) {
                // 输出容器满时产物已退回背包，这次合成仍然算数
                crafted++;
                continue;
            }
            outcome = recursive;
            missing = planned.missing();
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
        } else if (planAborted) {
            notifyPlanAborted(player);
        } else {
            notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
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
        StoragePool pool = StoragePool.collect(inputHandlers());
        PlannedRecipe planned = planBest(target, pool);
        if (!planned.succeeded()) {
            notifyPlanFailure(player, planned);
            return;
        }
        // 玩家点的那份做不出来时，planBest 已经替他换到同产物里能做的那份配方
        target = planned.recipe();
        List<S2CRecursiveCraftPromptPacket.MissingEntry> auto =
                toPromptEntries(planned.state().autoCraftable, true);
        ModNetwork.sendToPlayer(player, new S2CRecursiveCraftPromptPacket(
                this.benchPos, target.getId(), auto, List.of()));
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
        StoragePool pool = StoragePool.collect(inputHandlers());
        PlannedRecipe planned = planBest(target, pool);
        if (!planned.succeeded()) {
            if (!planned.missing().isEmpty()) {
                notifyPlanFailure(player, planned);
            } else {
                notify(player, "材料已经变化，请重新点击合成", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_changed");
            }
            return;
        }
        this.activeFeedback = new CraftFeedback();
        int recursiveOutcome = executeRecursivePlan(player, planned.state().steps, pool);
        if (recursiveOutcome == CRAFT_OK || recursiveOutcome == CRAFT_OUTPUT_FULL) {
            notifyFeedback(player, 1);
        }
        markDirty();
        updateResult();
        refreshCraftables(false);
    }

    /**
     * 顶层规划：先重置本次操作的搜索预算，再递归规划。
     * <p>
     * 只有顶层入口走这里，{@link #planRecipe} 之间的递归调用共享同一份预算，
     * 否则预算会在每一层被重新填满，形同虚设。时间线同样只在顶层设一次。
     */
    private boolean planTopLevel(CraftingRecipe target, RecursivePlanState state) {
        this.planNodes = 0;
        this.planAborted = false;
        this.planDeadlineNanos = System.nanoTime()
                + Math.max(200L, ModConfig.recursiveTimeBudgetMs()) * 1_000_000L;
        return planRecipe(target, state, 0, new HashSet<>());
    }

    /**
     * 同产物有好几份配方时，自动挑一份当前库存真能做出来的。
     * <p>
     * 玩家在列表里点的是「物品」，不是「用哪一份配方」——同一件物品可能有好几种做法
     * （精妙存储的箱子既能用木板拉杆、也能用原版箱子拉杆）。界面上没有、也不该有
     * 多态合成的切换按钮，所以由这里替玩家按掉：先试玩家点的那份，做不出来再按索引
     * 顺序试同产物的其他配方，第一份规划成功的就是它。
     * <p>
     * 只在首选失败时才往下试，正常路径不会多花时间；最多试
     * {@link #AUTO_RECIPE_TRY_LIMIT} 份，免得一堆缺料配方把服务器 CPU 白烧掉。
     * 首选要是已经「算到超时」，就不再换配方重算了——那说明配方树本身就大，
     * 换一份多半还会超时，越试越卡。
     */
    private PlannedRecipe planBest(CraftingRecipe preferred, StoragePool pool) {
        // 库存快照只抄一份，几份候选共用；RecursivePlanState 自己会再复制，不会被改脏
        List<ItemStack> snapshot = pool.snapshotStacks();
        PlannedRecipe fallback = planOne(preferred, snapshot);
        if (fallback.succeeded() || fallback.aborted()) {
            return fallback;
        }
        ItemStack result = preferred.getResultItem(this.level.registryAccess());
        if (result.isEmpty()) {
            return fallback;
        }
        int tried = 0;
        for (CraftingRecipe candidate : getRecipesByResult().getOrDefault(result.getItem(), List.of())) {
            if (tried >= AUTO_RECIPE_TRY_LIMIT) {
                break;
            }
            ResourceLocation id = candidate.getId();
            if (id == null || id.equals(preferred.getId())) {
                continue;
            }
            ItemStack other = candidate.getResultItem(this.level.registryAccess());
            if (other.isEmpty() || !ItemStack.isSameItemSameTags(other, result)) {
                continue;
            }
            tried++;
            PlannedRecipe attempt = planOne(candidate, snapshot);
            if (attempt.succeeded()) {
                return attempt;
            }
            if (attempt.aborted()) {
                // 这份配方自己就复杂到超时，别再拿别的配方喂更多时间
                break;
            }
            // 首选是「算不完」，换出来的这份却有明确缺口：缺料比「太复杂」对玩家有用得多
            if (fallback.missing().isEmpty() && !attempt.missing().isEmpty()) {
                fallback = attempt;
            }
        }
        return fallback;
    }

    /** 单份配方的规划尝试。成功判据和链式补做一致：有步骤、没缺口、确实要补做中间材料。 */
    private PlannedRecipe planOne(CraftingRecipe recipe, List<ItemStack> snapshot) {
        RecursivePlanState state = new RecursivePlanState(snapshot);
        boolean ok = planTopLevel(recipe, state);
        boolean succeeded = ok && !state.steps.isEmpty() && state.unavailable.isEmpty()
                && !state.autoCraftable.isEmpty();
        return new PlannedRecipe(recipe, state, this.planAborted, succeeded,
                succeeded ? List.of() : RecursivePlanState.copyStacks(state.unavailable));
    }

    /**
     * 规划失败的提示。优先级：明确缺哪些基础材料 &gt; 预算用尽 &gt; 泛泛的「材料不足」。
     * 「太复杂」放在缺料后面：预算中止时往往也已经发现了缺口，先告诉玩家差什么，
     * 别让一句「太复杂」把真正的原因盖住。
     */
    private void notifyPlanFailure(ServerPlayer player, PlannedRecipe planned) {
        if (!planned.missing().isEmpty()) {
            notifyMissing(player, planned.missing(), "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
        } else if (planned.aborted()) {
            notifyPlanAborted(player);
        } else {
            notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
        }
    }

    /**
     * 一次「自动挑配方」的结果。
     *
     * @param recipe    最终选中的配方（首选做不出来时可能是同产物的另一份）
     * @param state     这份配方的完整规划，{@code steps} 按依赖顺序排列
     * @param aborted   最后一次规划是否因为预算用尽而中止
     * @param succeeded 是否规划成功、可以直接执行
     * @param missing   失败时列着缺的基础材料；成功时为空
     */
    private record PlannedRecipe(CraftingRecipe recipe, RecursivePlanState state,
                                 boolean aborted, boolean succeeded, List<ItemStack> missing) {
    }

    /** 递归规划一份配方；steps 按依赖先后排列，最后一项是目标物品。 */
    private boolean planRecipe(CraftingRecipe recipe, RecursivePlanState state, int depth, Set<ResourceLocation> active) {
        if (this.planAborted) {
            return false;
        }
        if (++this.planNodes > ModConfig.recursiveNodeBudget()) {
            this.planAborted = true;
            return false;
        }
        // 时间硬闸：节点预算限「量」，但超大整合包里单个节点的成本不可预估（材料标签
        // 可能摊开成上百个候选），只有绝对耗时能兜住不把服务器线程拖死。
        if ((this.planNodes & 127) == 0 && System.nanoTime() > this.planDeadlineNanos) {
            this.planAborted = true;
            return false;
        }
        if (depth > ModConfig.recursiveMaxDepth() || state.steps.size() >= ModConfig.recursiveMaxSteps()
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
                state.addVirtual(result.copy());
                state.steps.add(recipe);
            } else {
                complete = false;
            }
        }
        if (id != null) {
            active.remove(id);
        }
        if (!complete) {
            state.rollbackKeepingShortages(mark);
        }
        return complete;
    }

    /** 先消耗已有库存；没有时找能产出该材料的普通合成配方。 */
    private boolean planIngredient(Ingredient ingredient, RecursivePlanState state,
                                   int depth, Set<ResourceLocation> active) {
        if (ingredient.isEmpty()) {
            return true;
        }
        if (state.consumeVirtual(ingredient) > 0) {
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
                        && state.consumeVirtual(ingredient) > 0) {
                    state.addAutoCraftable(candidate, 1);
                    return true;
                }
                // 这一条配方失败时，先保存它递归发现的底层缺口；只有所有候选都失败后才合并。
                for (ItemStack missing : state.unavailableDeltaSince(mark)) {
                    addShortage(deepestFailure, missing, missing.getCount());
                }
                state.restore(mark);
            }
        }
        if (!deepestFailure.isEmpty()) {
            for (ItemStack missing : deepestFailure) {
                state.addUnavailable(missing, missing.getCount());
            }
        } else {
            ItemStack report = candidates.length == 0 ? ItemStack.EMPTY : candidates[0].copy();
            if (!report.isEmpty()) {
                state.addUnavailable(report, 1);
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
        net.minecraft.world.item.crafting.RecipeManager manager = this.level.getRecipeManager();
        // byName 的 values().size() 是 O(1)。以前这里用 getAllRecipesFor(CRAFTING).size() 取
        // 「版本号」，原版每次调用都整表拷贝配方清单——上万配方时每个规划节点白白拷一遍，
        // 点一下链式项能把内置服务器线程卡住两分钟以上。
        long version = manager.getRecipes().size();
        if (this.plainRecipes == null || manager != this.indexedRecipeManager
                || version != this.recipeIndexVersion) {
            List<CraftingRecipe> recipes = new ArrayList<>();
            Map<net.minecraft.world.item.Item, List<CraftingRecipe>> byResult = new HashMap<>();
            Map<net.minecraft.world.item.Item, List<CraftingRecipe>> usingItem = new HashMap<>();
            for (CraftingRecipe recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                // 材料表完整 + 特殊配方自匹配复核：双箱子升级这类「材料表骗人」的假配方
                // 在这一处就被挡掉，列表、链式闭包、点击规划三条路共用这份索引，一起干净。
                if (!isPlainCraft(recipe) || !matchesWithOwnPreviews(recipe)) {
                    continue;
                }
                recipes.add(recipe);
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (!result.isEmpty()) {
                    byResult.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>()).add(recipe);
                }
                // 材料种类 → 用到它的配方：链式可达性扫描沿「新点亮的种类」找候选配方，
                // 不用每点亮一种就把全部配方重扫一遍。同一份配方只登记一次。
                Set<net.minecraft.world.item.Item> touched = new HashSet<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    for (ItemStack preview : ingredient.getItems()) {
                        touched.add(preview.getItem());
                    }
                }
                for (net.minecraft.world.item.Item used : touched) {
                    usingItem.computeIfAbsent(used, ignored -> new ArrayList<>()).add(recipe);
                }
            }
            this.plainRecipes = List.copyOf(recipes);
            this.recipesByResult = byResult;
            this.recipesUsingItem = usingItem;
            this.recipeIndexVersion = version;
            this.indexedRecipeManager = manager;
        }
        return this.plainRecipes;
    }

    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> getRecipesUsingItem() {
        getPlainRecipes();
        return this.recipesUsingItem;
    }

    private Map<net.minecraft.world.item.Item, List<CraftingRecipe>> getRecipesByResult() {
        getPlainRecipes();
        return this.recipesByResult;
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
        return executeRecursivePlan(player, steps, pool, toOutput, null);
    }

    /**
     * 带中转清单的执行：{@code handoff} 不为 null 时，中间产物不塞给玩家，而是收进这份清单，
     * 由调用方直接接着用（例如紧接着摆进合成格）。这样链式补做就不必依赖「先进背包、
     * 再从背包捞出来」，背包塞满、同类堆叠合并都不会再让中间产物半路掉在脚下。
     */
    private int executeRecursivePlan(ServerPlayer player, List<CraftingRecipe> steps, StoragePool pool,
                                     boolean toOutput, List<ItemStack> handoff) {
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
                        if (index == steps.size() - 1 && handoff == null) {
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
        if (handoff != null) {
            // 中间产物整批交给调用方（例如随后直接摆进合成格），不再经玩家背包中转
            for (ItemStack stack : generated) {
                if (!stack.isEmpty()) {
                    handoff.add(stack);
                }
            }
            return delivery;
        }
        // 没用完的中间产物先还回箱子，塞不下才进背包，最后才掉地上
        stowOrReturn(player, generated);
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

    /**
     * 递归规划的临时状态：虚拟库存快照、已定步骤、自动合成明细、缺口明细。
     * <p>
     * 分支回退用「撤销日志」而不是整表拷贝：以前每个候选分支进入前都把三张表
     * 全量 snapshot 一遍，接上存储网络后库存快照动辄几千堆，单个规划节点的成本
     * 被放大成整仓扫描——点一下链式项能把内置服务器线程卡死两分钟以上。
     * 现在进分支只记一个日志长度，回滚时精确退回本分支真正动过的条目。
     */
    private static final class RecursivePlanState {
        private final List<ItemStack> virtual;
        private final List<CraftingRecipe> steps = new ArrayList<>();
        private final List<ItemStack> autoCraftable = new ArrayList<>();
        private final List<ItemStack> unavailable = new ArrayList<>();

        private final List<Undo> virtualUndo = new ArrayList<>();
        private final List<Undo> autoUndo = new ArrayList<>();
        private final List<Undo> unavailUndo = new ArrayList<>();

        /** 一条撤销记录：stack 上动了 amount 个；pushed 表示它是当时新加进列表的。 */
        private record Undo(ItemStack stack, int amount, boolean pushed) {
        }

        private RecursivePlanState(List<ItemStack> initial) {
            this.virtual = new ArrayList<>(initial.size());
            for (ItemStack stack : initial) {
                this.virtual.add(stack.copy());
            }
        }

        private Mark mark() {
            return new Mark(this.virtualUndo.size(), this.steps.size(),
                    this.autoUndo.size(), this.unavailUndo.size());
        }

        private void restore(Mark mark) {
            while (this.virtualUndo.size() > mark.virtualUndo()) {
                Undo undo = this.virtualUndo.remove(this.virtualUndo.size() - 1);
                if (undo.pushed()) {
                    this.virtual.remove(this.virtual.size() - 1);
                } else {
                    undo.stack().grow(undo.amount());
                }
            }
            while (this.steps.size() > mark.stepsSize()) {
                this.steps.remove(this.steps.size() - 1);
            }
            while (this.autoUndo.size() > mark.autoUndo()) {
                applyPop(this.autoCraftable, this.autoUndo.remove(this.autoUndo.size() - 1));
            }
            while (this.unavailUndo.size() > mark.unavailUndo()) {
                applyPop(this.unavailable, this.unavailUndo.remove(this.unavailUndo.size() - 1));
            }
        }

        private static void applyPop(List<ItemStack> list, Undo undo) {
            if (undo.pushed()) {
                list.remove(list.size() - 1);
            } else {
                undo.stack().shrink(undo.amount());
            }
        }

        /** 规划成功产出的中间材料进入虚拟库存；记一条压入，可被 restore 精确退回。 */
        private void addVirtual(ItemStack stack) {
            this.virtual.add(stack);
            this.virtualUndo.add(new Undo(stack, 1, true));
        }

        /** 返回并消耗一件匹配材料。空堆留在表里等退回（空堆过不了 ingredient.test）。 */
        private int consumeVirtual(Ingredient ingredient) {
            for (ItemStack stack : this.virtual) {
                if (ingredient.test(stack)) {
                    stack.shrink(1);
                    this.virtualUndo.add(new Undo(stack, 1, false));
                    return 1;
                }
            }
            return 0;
        }

        private void addAutoCraftable(ItemStack stack, int count) {
            appendJournal(this.autoCraftable, this.autoUndo, stack, count);
        }

        private void addUnavailable(ItemStack stack, int count) {
            appendJournal(this.unavailable, this.unavailUndo, stack, count);
        }

        private static void appendJournal(List<ItemStack> list, List<Undo> journal,
                                         ItemStack stack, int count) {
            for (ItemStack existing : list) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    existing.grow(count);
                    journal.add(new Undo(existing, count, false));
                    return;
                }
            }
            ItemStack copy = stack.copy();
            copy.setCount(count);
            list.add(copy);
            journal.add(new Undo(copy, count, true));
        }

        /** 本分支自 mark 起往 unavailable 新增的缺口明细（不含更早的基线）。 */
        private List<ItemStack> unavailableDeltaSince(Mark mark) {
            List<ItemStack> delta = new ArrayList<>();
            for (int i = mark.unavailUndo(); i < this.unavailUndo.size(); i++) {
                Undo undo = this.unavailUndo.get(i);
                addShortage(delta, undo.stack(), undo.amount());
            }
            return delta;
        }

        /**
         * 规划失败的回退：虚拟库存、步骤、自动合成明细全部撤销，
         * 但保留这一支发现的缺口，供上层报「还差什么」。
         */
        private void rollbackKeepingShortages(Mark mark) {
            List<ItemStack> delta = unavailableDeltaSince(mark);
            restore(mark);
            for (ItemStack missing : delta) {
                addUnavailable(missing, missing.getCount());
            }
        }

        private static List<ItemStack> copyStacks(List<ItemStack> source) {
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                copy.add(stack.copy());
            }
            return copy;
        }

        private record Mark(int virtualUndo, int stepsSize, int autoUndo, int unavailUndo) {
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
                notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
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
                        notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
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

    /**
     * 规划因搜索预算用尽而中止时的提示。
     * <p>
     * 和「材料不足」要分清楚：这次是没算完，不代表真的做不出来。
     * 提示里直接点出配置项，玩家遇到超大配方树时知道去哪里调。
     */
    private void notifyPlanAborted(Player player) {
        notify(player, "链式合成太复杂或太耗时，已停止（可调配置 craft.recursiveNodeBudget / recursiveTimeBudgetMs）",
                "gui." + SmartWorkbenchMod.MOD_ID + ".msg.recursive_too_complex");
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
