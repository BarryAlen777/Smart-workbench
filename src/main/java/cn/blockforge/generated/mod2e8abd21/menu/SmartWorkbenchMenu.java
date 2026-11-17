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
import cn.blockforge.generated.mod2e8abd21.network.S2CQueueStatusPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CRecursiveCraftPromptPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CStorageSnapshotPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CTraceResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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

    /* ===================== 合成队列 ===================== */

    /** 队列指令：整份队列同步给服务端（增删项、调数量、换序都走它）。 */
    public static final int QUEUE_OP_SYNC = 0;
    /** 队列指令：开始 / 继续执行。 */
    public static final int QUEUE_OP_START = 1;
    /** 队列指令：暂停执行。 */
    public static final int QUEUE_OP_PAUSE = 2;
    /** 队列指令：跳过 entryId 那一项（卡住时最常用）。 */
    public static final int QUEUE_OP_SKIP = 3;

    /** 队列状态：没在跑（还没开始或已被玩家暂停）。 */
    public static final int QUEUE_STATE_IDLE = 0;
    /** 队列状态：正在逐项合成（每 tick 出手一次，缺中间材料自动补做）。 */
    public static final int QUEUE_STATE_RUNNING = 1;
    /** 队列状态：卡住了（缺料、输出容器满……），处理完再点「开始」。 */
    public static final int QUEUE_STATE_STUCK = 2;
    /** 队列状态：全部做完。 */
    public static final int QUEUE_STATE_FINISHED = 3;

    /** 卡住原因编号（客户端照它出文案）。 */
    public static final int QUEUE_REASON_NONE = 0;
    public static final int QUEUE_REASON_MISSING = 1;
    public static final int QUEUE_REASON_OUTPUT_FULL = 2;
    public static final int QUEUE_REASON_INVENTORY_FULL = 3;
    public static final int QUEUE_REASON_RECIPE_GONE = 4;
    public static final int QUEUE_REASON_UNSUPPORTED = 5;
    public static final int QUEUE_REASON_PLAN_ABORTED = 6;

    /** 队列最多排几项：备货场景一排够用，也免得面板画不下。 */
    public static final int QUEUE_MAX_ENTRIES = 6;
    /** 单项目标数量上限（按产物个数算）。 */
    public static final int QUEUE_MAX_TARGET = 640;

    /**
     * 全服每 tick 最多推进多少个队列步骤。一个步骤 = 一次 {@code craftOnce}，缺料时还会连带
     * {@code planBest}（整棵依赖树规划），都不是白给的。单机、小服根本碰不到这条上限；
     * 公开服上几十个人同时开着队列时，没有它主线程会被排队堆满。
     * 轮到上限的玩家这一 tick 先不动，下一 tick 接着做，进度一点不丢。
     */
    private static final int QUEUE_GLOBAL_STEPS_PER_TICK = 8;
    /** 上面那份预算属于哪个服务器 tick、这一 tick 还剩几次。只在主线程读写，不用加锁。 */
    private static long queueBudgetTick = Long.MIN_VALUE;
    private static int queueBudgetLeft;

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

    /** 合成队列：服务端执行侧。列表由客户端 SYNC 推来，这里只按项 id 记账已产出数量。 */
    private final List<QueuedCraft> queueEntries = new ArrayList<>();
    private final Map<Integer, Integer> queueDone = new HashMap<>();
    private int queueState = QUEUE_STATE_IDLE;
    private int queueReason = QUEUE_REASON_NONE;
    private int queueIndex = -1;
    /** 队列专用的库存快照：开跑和换项时重建，同一项连续几 tick 只增量刷新。 */
    private StoragePool queuePool;
    /** 本项累计出手次数（给完成时的反馈文案用）。 */
    /** 上一个出手的 tick，保证每 tick 最多合成一次。 */
    private long queueLastStepTick = Long.MIN_VALUE;
    /** 「开始」指令的独立冷却：START 是全量存储扫描，比别的重操作更贵，10 tick 才接一次。 */
    private long nextQueueStartTick;
    /** 状态有没有变过，变过才回推客户端（每 tick 都发就成刷屏了）。 */
    private boolean queueStatusDirty;

    /** 合成队列：客户端镜像（界面渲染读这里，服务端每有变化回推一份）。 */
    private int clientQueueState = QUEUE_STATE_IDLE;
    private int clientQueueReason = QUEUE_REASON_NONE;
    private int clientQueueIndex = -1;
    private Map<Integer, Integer> clientQueueDone = Map.of();

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
    /**
     * 正在整批摆料。摆一份配方要往 9 个格子里写，每写一格都会触发 slotsChanged → updateResult →
     * 原版 getRecipeFor（O(配方数) 线性扫描）；大整合包里一次点击就是 9 次全表扫描。
     * 摆料期间先挂起结果重算，摆完由调用方统一算一次。
     */
    private boolean fillingGrid;
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

    /**
     * 合成格内容的 64 位指纹（方案六：原先每 tick 拼一条含 NBT toString 的长字符串，
     * 大标签物品摆进格子时一次广播就是九次整棵 NBT 序列化）。等价判据：种类+数量+NBT 内容。
     */
    private long matrixSignature = Long.MIN_VALUE;
    private List<CraftableEntry> craftables = List.of();
    /** 普通点击缺料时是否询问并自动递归补齐中间材料。 */
    private boolean autoRefillEnabled = true;
    /** 服务端是否已经把开关的真实状态发过给客户端；开界面后补一次，之后靠按钮回包同步。 */
    private boolean autoRefillSynced;
    /** 客户端专用的接入存储快照，给 REI 判断「原料够不够」用 */
    private List<ItemStack> clientStorageStacks = List.of();
    /** 客户端是否已经收到过服务器推来的存储快照；没收到时缺料判断不可信。 */
    private boolean clientStorageSynced;
    /** 快照是否因为超过 512 种被截断；截断时客户端不能把「缺料」当真。 */
    private boolean clientStorageTruncated;
    /** 客户端显示用；服务端的真实数量仍从方块实体读取。 */
    private int clientConnectedStorageCount;
    /**
     * 客户端侧旗标：接入存储快照刚更新过，悬停溯源那份答案当场过期。
     * 存量的"包直接敲界面"写法（公共包类 {@code instanceof} 客户端 Screen）改成了这里放话、
     * 界面下一帧自己来取——发包这侧不再 {@code instanceof} 客户端界面类（审查报告问题 3）。
     * 取本地玩家仍要 {@code Minecraft.getInstance().player}，那是 S2C 包收件人的正常入口，
     * 不属于"隔着包判断界面"。
     */
    private boolean clientStorageTraceDirty;
    /** 客户端显示用：绑定的输出容器数量。 */
    private int clientOutputCount;
    /** 上次扫描时的库存指纹（含配方版本、输出容器数）。没变就跳过重算，省下大整合包里每秒几千次配方匹配。 */
    private long craftableFingerprint;
    private boolean hasCraftableFingerprint;
    /**
     * 可合成列表的“轻量”指纹：种类 + 每格最多 9 个 + 配方索引版本。
     * 和它一样时直接复用上一份列表，只把新的库存快照发给客户端，
     * 避免漏斗/农场导致数量抖动时每秒重扫全部配方。
     */
    private long craftableListFingerprint;
    private boolean hasCraftableListFingerprint;
    /** 分块收列表用的缓冲：最后一块到了才整体换上，界面不会显示到一半的半截列表。 */
    private final List<CraftableEntry> clientCraftableBuffer = new ArrayList<>();
    private String storageInputSignature = "";
    private long nextRefreshTick;
    /**
     * 配方索引现在住在 {@link RecipeIndexCache}（优化方案一）：全服一份、数据包重载事件负责标脏，
     * 菜单只读引用。以前这三份索引挂在每个菜单实例上——每次重开工作台都要把上万配方
     * 重新遍历一遍，五个人同时开着就是五份重复内存，「首次打开卡 1.5 秒」的主体。
     */
    /** 所有客户端操作共用一个服务端冷却，防止连点堆积主线程任务。 */
    private long nextActionTick;
    private CraftFeedback activeFeedback;
    /**
     * 材料溯源的服务端节流：鼠标沿列表滑动时悬停请求会很密，
     * 每 3 tick 最多算一条，只扫最近真正停下来的那一份配方。
     * 节流窗口里来的请求<b>不丢弃</b>：记在 {@link #pendingTraceRecipe}，
     * 冷却一到就在菜单 tick 里补算——快速划列表停下来后最多 3 tick 必有答案，
     * 不会出现停在配方上却要空等两秒的"溯源空窗"。
     */
    private long nextTraceTick;
    /** 被节流挡下、等补算的最后一个溯源请求（null＝没有）。总以最后悬停的那条为准。 */
    private ResourceLocation pendingTraceRecipe;
    /**
     * 材料溯源的客户端缓存（服务端算好推来的那一份）：只对 {@link #clientTraceRecipeId}
     * 这个配方有效；列表一刷新就清空（库存变了，去处也不作数了）。
     */
    private ResourceLocation clientTraceRecipeId;
    private List<S2CTraceResponsePacket.MaterialHit> clientTrace = List.of();
    /**
     * 手里那份 {@link StoragePool} 已经不可信：批量/链式合成期间往箱子里塞过东西
     * （中间产物、剩余材料），而 {@link StoragePool#refreshCounts()} 只会回读「收集时就有东西」
     * 的那些格子——原本空着、刚被塞进中间产物的格子它永远看不到。
     * <p>
     * 后果不是报错而是「算错账」：箱里明明有木板却报缺基础材料提前停下，
     * 或者反过来把木材当成不够、多消耗基础材料。置位后下一轮强制整份重扫一次。
     */
    private boolean poolStale;
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
                // 先把「谁在用这台工作台」记下来：默认自动扫描只接这名玩家自己的背包，
                // 不把附近其他玩家的背包当公共料源（多人服上的物品归属问题）；
                // 只有在配置里明确打开 allowOtherPlayerBackpacks 时才会连其他玩家一起读。
                this.bench.setLastUser(this.player);
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
            // 库存刚变过，上一份"材料在哪儿"多半已经不作数：直接作废，玩家再悬停会重新问
            this.clientTraceRecipeId = null;
            this.clientTrace = List.of();
        }
    }

    /**
     * 轻量存储快照包在客户端落账后调用：立旗「悬停溯源过期」。
     * 界面下一帧用 {@link #consumeClientStorageTraceDirty()} 取走，效果等同旧版
     * 发包直接敲界面（审查报告问题 3：公共包类不该 instanceof 客户端类）。
     */
    public void markClientStorageTraceDirty() {
        this.clientStorageTraceDirty = true;
    }

    /** 界面每帧问一次：有旗标就取走并复位。没人悬停时旗标留着也不碍事。 */
    public boolean consumeClientStorageTraceDirty() {
        boolean dirty = this.clientStorageTraceDirty;
        this.clientStorageTraceDirty = false;
        return dirty;
    }

    /** 服务器推来的接入存储快照（同类已合并），REI 的缺料判断会用到。 */
    public void setClientStorageStacks(List<ItemStack> stacks, boolean truncated) {
        this.clientStorageStacks = stacks;
        this.clientStorageTruncated = truncated;
        this.clientStorageSynced = true;
    }

    public List<ItemStack> getClientStorageStacks() {
        return this.clientStorageStacks;
    }

    /** 客户端有没有拿到过这份存储快照。没拿到之前，「缺料」的结论是不准的。 */
    public boolean hasClientStorageSnapshot() {
        return this.clientStorageSynced;
    }

    /** 快照有没有被截断。截断时客户端的「缺料」判断必须降级放行，交给服务端裁决。 */
    public boolean isClientStorageTruncated() {
        return this.clientStorageTruncated;
    }

    /* 材料溯源 */

    /**
     * 服务端：客户端把鼠标停在某条配方上时问「每样材料都在哪儿」，这里翻一遍接入的容器答它。
     * <p>
     * 只扫<b>接入的存储</b>（扳手绑定 + 附近扫描 + 里面的精妙背包）加<b>玩家自己身上</b>；
     * 合成格里的料不报——那本来就摆在界面上。每台容器只报总数、不精确到格，
     * 精妙背包这类没有方块坐标的库存照实写名字、不画高亮（按用户要求只提示文字）。
     * 悬停请求很密，这里按 tick 节流：窗口内的请求记成待补算、冷却一到就补，
     * 快速划过列表停下来最多 3 tick 必有答案；每样材料最多回 4 个去处、每种配方最多回 9 样
     * （3×3 满摆也不丢料），扫描预算按材料均分，大整合包的一次悬停烧不掉服务器。
     */
    public void computeMaterialTrace(ServerPlayer player, ResourceLocation recipeId) {
        if (this.level.isClientSide || this.bench == null || recipeId == null) {
            return;
        }
        long now = this.level.getGameTime();
        if (now < this.nextTraceTick) {
            // 节流窗口内不硬算也不丢弃：记下最后悬停的那条，冷却一到由 tickCraftQueue 补算
            this.pendingTraceRecipe = recipeId;
            return;
        }
        this.pendingTraceRecipe = null;
        this.nextTraceTick = now + 3L;
        runMaterialTrace(player, recipeId);
    }

    /**
     * 每 tick 顺带看一眼：有没有被节流挡下的溯源请求到期了。
     * 玩家走远了就不补（扫了也没人看，还白烧预算）。
     */
    private void tickPendingTrace(ServerPlayer player, long now) {
        if (this.pendingTraceRecipe != null && now >= this.nextTraceTick) {
            ResourceLocation pending = this.pendingTraceRecipe;
            this.pendingTraceRecipe = null;
            if (stillValid(player)) {
                this.nextTraceTick = now + 3L;
                runMaterialTrace(player, pending);
            }
        }
    }

    /** 溯源扫描本体。调用方负责节流（{@link #computeMaterialTrace} / {@link #tickPendingTrace}）。 */
    private void runMaterialTrace(ServerPlayer player, ResourceLocation recipeId) {
        if (!(this.level.getRecipeManager().byKey(recipeId).orElse(null) instanceof CraftingRecipe crafting)) {
            return;
        }
        // 同种材料摆几格合并成一种（和界面提示框同一口径），每种记下要几个。
        // 上限 9：3×3 摆满正好九种材料，一种都不能被静默挤掉。
        List<ItemStack> previews = new ArrayList<>();
        List<Integer> need = new ArrayList<>();
        List<Ingredient> reps = new ArrayList<>();
        for (Ingredient ingredient : crafting.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                continue;
            }
            ItemStack preview = items[0];
            int at = -1;
            for (int i = 0; i < previews.size(); i++) {
                if (ItemStack.isSameItemSameTags(previews.get(i), preview)) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                if (previews.size() >= S2CTraceResponsePacket.MAX_MATERIALS) {
                    continue;
                }
                previews.add(preview.copy());
                reps.add(ingredient);
                need.add(1);
            } else {
                need.set(at, need.get(at) + 1);
            }
        }
        List<IItemHandler> storages = this.bench.getStorages();
        // 扫描预算<b>按材料均分</b>（每种至少 6000 格）：以前 60000 格全材料共用，
        // 大仓库接得多时前几种材料把预算吃光，后面的材料一格没扫就报「附近没有」——误报。
        int perMaterialBudget = Math.max(6_000, 60_000 / Math.max(1, previews.size()));
        List<S2CTraceResponsePacket.MaterialHit> hits = new ArrayList<>(previews.size());
        for (int i = 0; i < previews.size(); i++) {
            Ingredient ingredient = reps.get(i);
            previews.get(i).setCount(need.get(i));
            // 按去处聚合：一台容器一行，写名字 + 总数；坐标能查到的进世界高亮，查不到的只出文字
            Map<IItemHandler, int[]> counts = new java.util.IdentityHashMap<>();
            // 先扫玩家自己身上：背包撑死几十个格子，不占预算，保证「在身上」这一行永远准。
            int own = 0;
            for (int s = 0; s < this.playerInventoryHandler.getSlots(); s++) {
                ItemStack stack = this.playerInventoryHandler.getStackInSlot(s);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    own += stack.getCount();
                }
            }
            int budget = perMaterialBudget;
            boolean partial = false;
            for (IItemHandler handler : storages) {
                if (budget <= 0) {
                    partial = true;
                    break;
                }
                int total = 0;
                int slots = handler.getSlots();
                int scanned = Math.min(slots, budget);
                if (scanned < slots) {
                    partial = true;
                }
                for (int s = 0; s < scanned; s++) {
                    ItemStack stack = handler.getStackInSlot(s);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        total += stack.getCount();
                    }
                }
                budget -= scanned;
                if (total > 0) {
                    counts.put(handler, new int[]{Math.min(999, total)});
                }
            }
            List<S2CTraceResponsePacket.Spot> spots = new ArrayList<>();
            for (Map.Entry<IItemHandler, int[]> entry : counts.entrySet()) {
                BlockPos pos = this.bench.storagePositionOf(entry.getKey());
                Component name = pos == null
                        ? Component.translatable("gui." + SmartWorkbenchMod.MOD_ID + ".trace.backpack")
                        : containerNameAt(pos);
                // 整台容器占的格（大箱子两格）跟着去处一起回，客户端才描得出一个整框（r239）；
                // 背包内部没有坐标，storageShapeOf 给空组，天然不画框。
                spots.add(new S2CTraceResponsePacket.Spot(name, pos, entry.getValue()[0],
                        pos == null ? List.of() : this.bench.storageShapeOf(entry.getKey())));
            }
            if (own > 0) {
                spots.add(new S2CTraceResponsePacket.Spot(
                        Component.translatable("gui." + SmartWorkbenchMod.MOD_ID + ".trace.on_person"),
                        null, Math.min(999, own), List.of()));
            }
            spots.sort(Comparator.comparingInt(S2CTraceResponsePacket.Spot::count).reversed());
            if (spots.size() > S2CTraceResponsePacket.MAX_SPOTS) {
                spots = new ArrayList<>(spots.subList(0, S2CTraceResponsePacket.MAX_SPOTS));
            }
            hits.add(new S2CTraceResponsePacket.MaterialHit(previews.get(i).copy(), need.get(i), spots, partial));
        }
        ModNetwork.sendToPlayer(player, new S2CTraceResponsePacket(this.benchPos, recipeId, hits));
    }

    /** 容器显示名：有名字牌用名字牌上的，不然用方块名（客户端按玩家语言翻译）。 */
    private Component containerNameAt(BlockPos pos) {
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (blockEntity instanceof Nameable container) {
            Component custom = container.getCustomName();
            if (custom != null) {
                return custom.copy();
            }
            return container.getDisplayName().copy();
        }
        return this.level.getBlockState(pos).getBlock().getName().copy();
    }

    /** 客户端：取当前配方对应的溯源结果；服务端还没答回来就是空。 */
    public List<S2CTraceResponsePacket.MaterialHit> getClientTraceFor(ResourceLocation recipeId) {
        return this.clientTraceRecipeId != null && this.clientTraceRecipeId.equals(recipeId)
                ? this.clientTrace : List.of();
    }

    /** 客户端收到溯源回包。只对当前悬停那条配方有用，列表一换就清（见 {@link #appendClientCraftables}）。 */
    public void setClientTraceResult(ResourceLocation recipeId, List<S2CTraceResponsePacket.MaterialHit> hits) {
        this.clientTraceRecipeId = recipeId;
        this.clientTrace = List.copyOf(hits);
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
            // 退进「随便哪台接入存储」时可能落进原本空着的格子，
            // 批量合成手里的库存快照就漏了这批材料，下一轮必须整份重扫。
            this.poolStale = true;
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
                || !(recipe instanceof CraftingRecipe crafting)
                || !RecipeIndexCache.accepts(this.level, crafting, player)) {
            // 特殊配方（修装备、复制旗帜、双箱子升级这类「材料表骗人」的）不接管。
            // 判定直接查全局配方索引（方案一/四）：索引成员本来就是「普通配方 + 自匹配复核过」，
            // 不再每次点击现场摆 3x3 复核一遍。
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
        // 整批摆料期间挂起 slotsChanged 的结果重算，摆完由调用方统一 updateResult() 一次。
        this.fillingGrid = true;
        try {
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
        StoragePool pool = collectInputPool();
        int pulled = 0;
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
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
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        } finally {
            this.fillingGrid = false;
        }
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
        if (!this.level.isClientSide && !this.fillingGrid) {
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
            long signature = matrixSignature();
            if (signature != this.matrixSignature) {
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
            // 合成队列只在界面开着的时候跑：关界面就停，进度由客户端记账带回来
            if (this.queueState == QUEUE_STATE_RUNNING) {
                this.queueState = QUEUE_STATE_IDLE;
                this.queuePool = null;
            }
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
        // 多态合成可能还记着上一份配方；材料换过之后它不一定还匹配。不匹配就往下走，
        // 否则产物格会按旧配方 assemble 出一个当前材料根本做不出来的东西。
        if (polymorph.isPresent() && matchesGrid(polymorph.get())) {
            return polymorph.get();
        }
        if (this.preferredRecipe != null && matchesGrid(this.preferredRecipe)) {
            return this.preferredRecipe;
        }
        return this.level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, this.matrix, this.level).orElse(null);
    }

    /** 配方是否匹配当前合成格；第三方配方 matches 抛异常时当作不匹配，别把整个界面带崩。 */
    private boolean matchesGrid(CraftingRecipe recipe) {
        try {
            return recipe.matches(this.matrix, this.level);
        } catch (Throwable ignored) {
            return false;
        }
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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
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
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
        ForgeHooks.setCraftingPlayer(player);
        boolean matches;
        try {
            matches = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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

    /**
     * 查全局配方索引时带上的玩家（审查报告问题②）：索引脏了要重建时，重建里的
     * 特殊配方自匹配复核会读合成玩家身份。以前一律传 null，这类配方按「无玩家」
     * 判定，个别会被误剔出索引、还被全服共享不自愈。菜单手上有真实玩家就传进去。
     */
    private ServerPlayer indexOpener() {
        return this.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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
        // 快照同时带回「有没有被 512 种上限截断」：截断时客户端必须放行，不能误报缺料。
        // 以前这里要对着全部箱子<b>再扫第二遍</b>建一份单独的池子；scanHandlers 的排布是
        // 「接入存储在最前、玩家背包和合成格垫后」，从同一份池子里截前 storages.size() 路即可。
        StoragePool.ClientSnapshot snapshot = pool.mergeForClientSnapshotPrefix(
                storages.size(), STORAGE_SNAPSHOT_LIMIT);
        List<ItemStack> storageSnapshot = snapshot.stacks();
        // 数据包重载会换掉配方索引，先让全局缓存把世代刷新，再和库存一起算指纹
        List<RecipeFacts> recipes = RecipeIndexCache.plain(this.level, indexOpener());
        long fingerprint = craftableFingerprint(pool, storageSnapshot, storages.size(), getOutputCount());
        if (this.hasCraftableFingerprint && fingerprint == this.craftableFingerprint) {
            // 库存、配方、输出容器都没变：列表和客户端手上那份一模一样，不用重算也不用重发
            return;
        }
        this.craftableFingerprint = fingerprint;
        this.hasCraftableFingerprint = true;

        // 列表结果只取决于「有哪些种类、每种够不够做一次」和配方索引版本，
        // 大数量抖动（漏斗抽走几块、农场塞进几根）不影响列表，直接复用上一份。
        // 这份指纹还<b>与东西摆在哪个箱子无关</b>（审查报告优化第 4 条）：
        // 把同一批材料在箱子之间搬来搬去、总量不变，也不再触发整张表重发。
        long listPrint = pool.listFingerprint();
        listPrint = listPrint * 31L + RecipeIndexCache.version();
        listPrint = listPrint * 31L + ModConfig.maxListSize();
        boolean reuseList = this.hasCraftableListFingerprint && listPrint == this.craftableListFingerprint;
        this.craftableListFingerprint = listPrint;
        this.hasCraftableListFingerprint = true;

        List<CraftableEntry> next;
        if (reuseList) {
            next = this.craftables;
        } else {
            next = new ArrayList<>();
            if (!pool.isEmpty()) {
                // 上限只用来兜住极端整合包；正常情况下直接项和链式项都会尽量列全。
                int max = ModConfig.maxListSize();
                Set<ResourceLocation> directIds = new HashSet<>();
                // 直接项做出来的是什么，也要喂给链式闭包：像「钻石块 -> 9 钻石 -> 钻石剑」，
                // 中间那步本身是直接项，产物不进可达集合的话，后续链子就断了。
                Set<Item> directOutputs = new HashSet<>();
                // canCraft 用完就地释放本地计数，整轮扫描可用计数恒定，不再需要每份配方 reset 整表。
                for (RecipeFacts facts : recipes) {
                    if (next.size() >= max) {
                        break;
                    }
                    if (!pool.canCraft(facts)) {
                        continue;
                    }
                    if (facts.resultItem == null || facts.result.isEmpty()) {
                        continue;
                    }
                    next.add(CraftableEntry.of(facts.id, facts.result.copy(), false));
                    directIds.add(facts.id);
                    directOutputs.add(facts.resultItem);
                }
                next.sort(Comparator.comparing(entry -> entry.result().getItem().getDescriptionId()));
                next.addAll(collectChainEntries(pool, directIds, directOutputs, max - next.size()));
                next = dedupeByResult(next);
            }
        }
        this.craftables = next;
        if (this.player instanceof ServerPlayer serverPlayer) {
            if (reuseList) {
                // 只是数量在动、列表内容一模一样：发一份轻量快照包就够
                // （REI 的库存视图和两个计数照常刷新），整张表不再重发——
                // 大整合包里这一下原本每秒要编解码几千条对象、客户端还要跟着重排视图。
                ModNetwork.sendToPlayer(serverPlayer, new S2CStorageSnapshotPacket(
                        this.benchPos, storageSnapshot, storages.size(), getOutputCount(),
                        snapshot.truncated()));
            } else {
                sendCraftableList(serverPlayer, next, storageSnapshot, storages.size(), getOutputCount(),
                        snapshot.truncated());
            }
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
     * {@link RecipeIndexCache#usingItem} 的反向索引，一次扫描在毫秒级；
     * 数量复核额外受配置的总预算约束。
     */
    private List<CraftableEntry> collectChainEntries(StoragePool pool, Set<ResourceLocation> directIds,
                                                     Set<Item> directOutputs, int budget) {
        if (budget <= 0 || !ModConfig.showChainCraftables() || pool.isEmpty()) {
            return List.of();
        }
        Map<Item, List<RecipeFacts>> using = RecipeIndexCache.usingItem(this.level, indexOpener());
        Set<Item> reachable = pool.distinctItems();
        reachable.addAll(directOutputs);
        Map<ResourceLocation, RecipeFacts> chainResults = new LinkedHashMap<>();
        Deque<Item> queue = new ArrayDeque<>(reachable);
        while (!queue.isEmpty()) {
            Item item = queue.poll();
            for (RecipeFacts facts : using.getOrDefault(item, List.of())) {
                ResourceLocation id = facts.id;
                if (id == null || directIds.contains(id) || chainResults.containsKey(id)) {
                    continue;
                }
                if (facts.resultItem == null || !facts.reachableWith(reachable)) {
                    continue;
                }
                chainResults.put(id, facts);
                if (reachable.add(facts.resultItem)) {
                    queue.add(facts.resultItem);
                }
            }
        }
        if (chainResults.isEmpty()) {
            return List.of();
        }
        // 数量级复核（见 createChainChecker 的说明）。预算 0 表示不做复核，退回旧行为。
        ChainFeasibility checker = createChainChecker(pool);
        List<CraftableEntry> out = new ArrayList<>(chainResults.size());
        for (Map.Entry<ResourceLocation, RecipeFacts> entry : chainResults.entrySet()) {
            RecipeFacts facts = entry.getValue();
            // 预算用尽也照样问一次：复核器会先做不花预算的「一眼假」判定，
            // 把「石头只能熔炉烧」这类必然做不出的项剔掉，剩下的才按未知保留。
            if (checker != null && checker.check(facts) == ChainFeasibility.Verdict.IMPOSSIBLE) {
                continue;
            }
            if (facts.resultItem != null && !facts.result.isEmpty()) {
                out.add(CraftableEntry.of(entry.getKey(), facts.result.copy(), true));
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
        // 账本直接从 StoragePool 聚合（方案三：不再每轮复核前整仓 snapshotStacks 深拷贝）
        return new ChainFeasibility(pool, RecipeIndexCache.byResult(this.level, indexOpener()),
                ModConfig.recursiveMaxDepth(), ModConfig.recursiveMaxSteps(),
                perCandidate, totalBudget);
    }

    /**
     * 把「库存 + 存储快照 + 输出容器 + 配方版本」揉成一个 64 位指纹。
     * 指纹一样就说明列表不会变，不必再花几十毫秒重扫配方，也不用重发包。
     */
    private long craftableFingerprint(StoragePool pool, List<ItemStack> snapshot, int storageCount, int outputCount) {
        long hash = pool.fingerprint();
        hash = hash * 31L + RecipeIndexCache.version();
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
                                   List<ItemStack> snapshot, int storageCount, int outputCount,
                                   boolean snapshotTruncated) {
        int chunkSize = S2CCraftableListPacket.CHUNK_SIZE;
        int chunkCount = Math.max(1, (entries.size() + chunkSize - 1) / chunkSize);
        for (int index = 0; index < chunkCount; index++) {
            int from = Math.min(entries.size(), index * chunkSize);
            int to = Math.min(entries.size(), from + chunkSize);
            List<CraftableEntry> chunk = List.copyOf(entries.subList(from, to));
            ModNetwork.sendToPlayer(player, new S2CCraftableListPacket(this.benchPos, chunk, index, chunkCount,
                    index == 0 ? snapshot : List.of(), storageCount, outputCount, snapshotTruncated));
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
        // 材料表比 3x3 的格子还多：畸形配方（个别模组的 IShapedRecipe 尺寸和材料表对不上）
        // 会让 gridLayout 回退成「依次摆」，多出来的材料被夹到最后一格——前面取出的那份
        // 还没进格子就被覆盖，等于白吞玩家的材料。这种配方直接不接一键取料。
        if (ingredients.size() > COLUMNS * ROWS) {
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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
        try {
            ForgeHooks.setCraftingPlayer(feedbackPlayer);
            return recipe.matches(grid, this.level);
        } catch (Throwable ignored) {
            return false;
        } finally {
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        // 第三方 IShapedRecipe 的 getIngredients().size() 可能和 width*height 对不上；
        // 这种畸形配方按形状摆会算到 3x3 外面（IndexOutOfBounds），直接退回“依次摆”。
        if (!shaped || width <= 0 || height <= 0 || width > COLUMNS || height > ROWS
                || width * height != ingredients.size()) {
            // 无序配方：从第一个格子往后依次摆
            for (int i = 0; i < positions.length; i++) {
                positions[i] = Math.min(i, SmartWorkbenchBlockEntity.GRID_SIZE - 1);
            }
            return positions;
        }
        int offsetX = (COLUMNS - width) / 2;
        int offsetY = (ROWS - height) / 2;
        for (int i = 0; i < positions.length; i++) {
            int slot = (offsetY + i / width) * COLUMNS + offsetX + i % width;
            // 上面已经校验过 size == width*height，这里夹一次只是最后一道保险
            positions[i] = Math.max(0, Math.min(SmartWorkbenchBlockEntity.GRID_SIZE - 1, slot));
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
        StoragePool pool = collectInputPool();
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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
        ForgeHooks.setCraftingPlayer(player);
        boolean matched;
        try {
            matched = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        boolean wroteBack = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = insertIntoStorages(storages, stack);
            if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                player.drop(rest, false);
            }
            wroteBack = true;
        }
        // 塞进箱子就可能占了原本空着的格子，手里的库存快照要整份重扫才看得见它们。
        // 这里宁可多标一次：重扫一轮的开销远小于算错库存（少做/多做）的代价。
        if (wroteBack) {
            this.poolStale = true;
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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
        ForgeHooks.setCraftingPlayer(player);
        boolean matches;
        try {
            matches = recipe.matches(this.matrix, this.level);
        } finally {
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        return chainCraftOnce(player, recipe, toOutput, notifyOnFail, collectInputPool());
    }

    /**
     * 复用调用方刚 collect 好的库存（批量循环里每轮重扫全部处理器很贵）。
     * {@code pool} 必须已经反映当前真实库存；规划只读快照，不会把它改脏。
     */
    private int chainCraftOnce(ServerPlayer player, CraftingRecipe recipe, boolean toOutput,
                               boolean notifyOnFail, StoragePool pool) {
        if (this.bench == null) {
            return CRAFT_MISSING;
        }
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
        StoragePool pool = collectInputPool();
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
        boolean inventoryFull = false;
        int limit = resolveBatchLimit(requestedCount);
        StoragePool pool = collectInputPool();
        for (int guard = 0; guard < limit; guard++) {
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                if (crafted == 0) {
                    notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                }
                break;
            }
            outcome = craftOnce(player, recipe, pool, crafted == 0 && !this.autoRefillEnabled);
            if (outcome == CRAFT_MISSING && this.autoRefillEnabled) {
                // 失败的合成会退款，但本地计数在退款时不会回加；规划前先回读真实数量
                pool = refreshPool(pool);
                if (pool.isEmpty()) {
                    break;
                }
                outcome = chainCraftOnce(player, recipe, false, crafted == 0, pool);
            }
            if (outcome == CRAFT_INVENTORY_FULL) {
                // 背包放不下，产物已掉在脚下；这一次材料确实用掉了，算一次再停
                crafted++;
                inventoryFull = true;
                break;
            }
            if (outcome != CRAFT_OK) {
                break;
            }
            crafted++;
            pool = refreshPool(pool);
        }
        if (crafted > 0) {
            notifyFeedback(player, crafted);
        }
        if (inventoryFull) {
            notify(player, "背包没有空位了，产物掉在脚下，批量合成已停止",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.inventory_full");
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
        boolean inventoryFull = false;
        int limit = resolveBatchLimit(requestedCount);
        StoragePool pool = collectInputPool();
        for (int guard = 0; guard < limit; guard++) {
            if (pool.isEmpty()) {
                if (crafted == 0) {
                    notify(player, "附近存储和背包材料不足", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.not_enough");
                }
                break;
            }
            // 只有第一次失败时才提示，中途没料了是正常结束
            int outcome = craftOnce(player, recipe, pool, crafted == 0 && !this.autoRefillEnabled);
            if (outcome == CRAFT_MISSING && this.autoRefillEnabled) {
                // 退款不会回加本地计数，先按真实库存修正
                pool = refreshPool(pool);
                if (pool.isEmpty()) {
                    break;
                }
                outcome = chainCraftOnce(player, recipe, false, crafted == 0, pool);
            }
            if (outcome == CRAFT_INVENTORY_FULL) {
                // 背包放不下，产物已掉在脚下；这一次材料确实用掉了，算一次再停
                crafted++;
                inventoryFull = true;
                break;
            }
            if (outcome != CRAFT_OK) {
                break;
            }
            crafted++;
            pool = refreshPool(pool);
        }
        if (crafted > 0) {
            notifyFeedback(player, crafted);
        }
        if (inventoryFull) {
            notify(player, "背包没有空位了，产物掉在脚下，批量合成已停止",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.inventory_full");
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

        StoragePool pool = collectInputPool();
        for (int i = 0; i < limit; i++) {
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                break;
            }
            // 先试直接合成；这一步不成功时 pool 会退款，本地计数可能偏低，下一轮前回读真实数量
            outcome = craftOnce(player, recipe, pool, false, true);
            if (outcome == CRAFT_OK) {
                crafted++;
                pool = refreshPool(pool);
                continue;
            }
            if (outcome == CRAFT_OUTPUT_FULL) {
                // 产物已经退回背包，这一次是算数的，但输出容器满了就到此为止
                crafted++;
                break;
            }
            if (outcome == CRAFT_INVENTORY_FULL) {
                // 背包也满了，产物掉在脚下；材料确实消耗掉，算一次再停
                crafted++;
                break;
            }
            if (outcome != CRAFT_MISSING) {
                break;
            }
            // 直接材料不够：先按真实库存修正账目，再规划整棵依赖树，能补齐就补一次
            pool = refreshPool(pool);
            if (pool.isEmpty()) {
                outcome = CRAFT_MISSING;
                break;
            }
            PlannedRecipe planned = planBest(recipe, pool);
            if (!planned.succeeded()) {
                missing = planned.missing();
                planAborted = planned.aborted();
                outcome = CRAFT_MISSING;
                break;
            }
            // 玩家点的那份做不出来时，换成同产物里真正能做的那份，后续几轮也沿用
            recipe = planned.recipe();
            int recursive = executeRecursivePlan(player, planned.state().steps, pool, true);
            if (recursive == CRAFT_OK) {
                crafted++;
                pool = refreshPool(pool);
                continue;
            }
            if (recursive == CRAFT_OUTPUT_FULL) {
                // 输出容器满时产物已退回背包，这次合成仍然算数，但后面不再继续
                crafted++;
                outcome = CRAFT_OUTPUT_FULL;
                break;
            }
            outcome = recursive;
            missing = planned.missing();
            break;
        }

        if (crafted > 0) {
            notifyFeedback(player, crafted);
            if (outcome == CRAFT_OUTPUT_FULL) {
                notify(player, "输出容器满了，产物已退回背包", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.output_full");
            } else if (outcome == CRAFT_INVENTORY_FULL) {
                notify(player, "背包没有空位了，产物掉在脚下，自动合成已停止",
                        "gui." + SmartWorkbenchMod.MOD_ID + ".msg.inventory_full");
            }
        } else if (outcome == CRAFT_OUTPUT_FULL) {
            notify(player, "输出容器满了，产物已退回背包", "gui." + SmartWorkbenchMod.MOD_ID + ".msg.output_full");
        } else if (outcome == CRAFT_INVENTORY_FULL) {
            notify(player, "背包没有空位了，产物掉在脚下",
                    "gui." + SmartWorkbenchMod.MOD_ID + ".msg.inventory_full");
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
        StoragePool pool = collectInputPool();
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
        StoragePool pool = collectInputPool();
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

    /* ==================== 合成队列：服务端执行侧 ==================== */

    /**
     * 客户端的队列指令。包处理器已确认「菜单就是玩家正开着的那份、坐标对得上、界面还有效」，
     * 这里只管状态机：op 用 {@code QUEUE_OP_*} 常量。
     */
    public void handleQueuePacket(ServerPlayer player, int op, List<QueuedCraft> entries, int entryId) {
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        switch (op) {
            case QUEUE_OP_SYNC:
                queueSync(player, entries);
                break;
            case QUEUE_OP_START:
                // 开始要全量刷新存储（50 容器配置下一次约 13 万个方块位）+ 退回合成格材料，
                // 是四种指令里唯一的重操作。给它一个独立的 10 tick 冷却（比通用 acceptAction 更长）：
                // 交替 START/PAUSE 也刷不出第二次全量扫描。被冷却挡下时界面无变化（状态只由服务端
                // 回包驱动），玩家点第二次就生效。
                if (acceptQueueStart(player)) {
                    queueStart(player);
                }
                break;
            case QUEUE_OP_PAUSE:
                queuePause(player);
                break;
            case QUEUE_OP_SKIP:
                queueSkip(player, entryId);
                break;
            default:
                break;
        }
    }

    /**
     * 每 tick 被 {@link CraftQueueTicker} 叫一次：正在跑就出手一次（每 tick 最多合成一次配方，
     * 进度平滑、随时能停，服务端也不会被一口气几百次合成卡住），并把攒下的状态变化回推客户端。
     * 出手还受全服每 tick 预算（{@link #QUEUE_GLOBAL_STEPS_PER_TICK}）约束，多人服里排队轮到谁就谁做。
     */
    public void tickCraftQueue(ServerPlayer player) {
        if (this.level.isClientSide || this.bench == null) {
            return;
        }
        long now = this.level.getGameTime();
        // 每 tick 重置一次全服预算（用服务器 tick 计数，跨维度也算同一 tick）
        long serverTick = this.level.getServer() == null ? now : this.level.getServer().getTickCount();
        if (serverTick != queueBudgetTick) {
            queueBudgetTick = serverTick;
            queueBudgetLeft = QUEUE_GLOBAL_STEPS_PER_TICK;
        }
        // 顺手看一眼有没有到期的溯源补算请求（节流挡下的那一条）
        tickPendingTrace(player, now);
        if (this.queueState == QUEUE_STATE_RUNNING && now != this.queueLastStepTick) {
            this.queueLastStepTick = now;
            if (!stillValid(player)) {
                // 走远了、工作台被拆了：安静地停下，重开界面还能从原进度继续
                this.queueState = QUEUE_STATE_IDLE;
                this.queuePool = null;
                this.queueStatusDirty = true;
            } else if (this.queueEntries.isEmpty()) {
                this.queueState = QUEUE_STATE_IDLE;
                this.queueStatusDirty = true;
            } else {
                if (this.queueIndex < 0 || this.queueIndex >= this.queueEntries.size()) {
                    this.queueIndex = queueFirstIncomplete(0);
                    if (this.queueIndex < 0) {
                        queueFinish(player);
                    }
                }
                if (this.queueState == QUEUE_STATE_RUNNING && this.queueIndex >= 0) {
                    // 全服共用的每 tick 预算：轮不到就下一 tick 再出手，队列只会慢一点，不会丢进度
                    if (queueBudgetLeft > 0) {
                        queueBudgetLeft--;
                        queueStep(player);
                    }
                }
            }
        }
        // 攒下的状态变化（做成一项、卡住、停下…）统一在这里回推：界面镜像跟着动，
        // 状态条进度才是一跳一跳活的，而不是半天不更新、最后一口气全弹出来。
        if (this.queueStatusDirty) {
            this.queueStatusDirty = false;
            sendQueueStatus(player);
        }
    }

    /** 整份队列推过来：按项 id 保住进度，重算当前指向。跑着的时候也允许改（加项、删项都不打断当前项）。 */
    private void queueSync(ServerPlayer player, List<QueuedCraft> incoming) {
        int prevId = (this.queueState == QUEUE_STATE_RUNNING && this.queueIndex >= 0
                && this.queueIndex < this.queueEntries.size())
                ? this.queueEntries.get(this.queueIndex).id() : -1;
        this.queueEntries.clear();
        // 服务端已做掉的进度先抄下来：SYNC 里同 id 的项取两边较大值。
        // 网络卡顿时客户端账本可能比服务端旧（刚做完一件、状态包还没到），
        // 照单全收客户端的 done 会把进度回退一格——下一 tick 重做一件，白吃一份材料。
        // 「再来一轮」要清零进度，由客户端在发 START 前先推一份 done 全 0 的 SYNC，
        // 这里的 max 只保住服务端真做掉的，不影响正常重跑。
        Map<Integer, Integer> carried = new HashMap<>(this.queueDone);
        this.queueDone.clear();
        Set<Integer> ids = new HashSet<>();
        int limit = Math.min(incoming.size(), QUEUE_MAX_ENTRIES);
        for (int i = 0; i < limit; i++) {
            QueuedCraft e = incoming.get(i);
            if (e.recipeId() == null || !ids.add(e.id())) {
                continue;
            }
            int target = Math.max(1, Math.min(QUEUE_MAX_TARGET, e.target()));
            this.queueEntries.add(new QueuedCraft(e.id(), e.recipeId(), target, 0));
            int fromClient = Math.max(0, Math.min(target, e.done()));
            int fromServer = Math.min(target, carried.getOrDefault(e.id(), 0));
            this.queueDone.put(e.id(), Math.max(fromClient, fromServer));
        }
        this.queuePool = null;
        if (this.queueState == QUEUE_STATE_RUNNING) {
            if (this.queueEntries.isEmpty()) {
                this.queueState = QUEUE_STATE_IDLE;
                this.queueReason = QUEUE_REASON_NONE;
                this.queueIndex = -1;
            } else {
                int idx = queueIndexOf(prevId);
                this.queueIndex = idx >= 0 ? idx : queueFirstIncomplete(0);
                if (this.queueIndex < 0) {
                    this.queueState = QUEUE_STATE_FINISHED;
                    this.queueIndex = -1;
                }
            }
        } else {
            // 停着的时候玩家改了队列：过期的「卡住/完成」标记不作数，照当前进度重算
            this.queueReason = QUEUE_REASON_NONE;
            if (this.queueEntries.isEmpty()) {
                this.queueState = QUEUE_STATE_IDLE;
                this.queueIndex = -1;
            } else {
                int idx = queueFirstIncomplete(0);
                this.queueState = idx < 0 ? QUEUE_STATE_FINISHED : QUEUE_STATE_IDLE;
                this.queueIndex = idx;
            }
        }
        sendQueueStatus(player);
    }

    /** 点「开始」：全做完的队列等于再跑一轮；中途接上就从没做完的那一项、那一进度继续。 */
    private void queueStart(ServerPlayer player) {
        if (this.queueState == QUEUE_STATE_RUNNING || this.queueEntries.isEmpty()) {
            return;
        }
        if (queueFirstIncomplete(0) < 0) {
            this.queueDone.clear();
        }
        // 自动合成同款开场：先把合成格的材料退回原处，免得同一份料被算两遍
        returnMatrixMaterials(player);
        this.bench.refreshStorages(true);
        this.queuePool = null;
        this.queueIndex = queueFirstIncomplete(0);
        this.queueState = QUEUE_STATE_RUNNING;
        this.queueReason = QUEUE_REASON_NONE;
        notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.started",
                "合成队列开始：共 %s 项", this.queueEntries.size());
        sendQueueStatus(player);
    }

    /** 点「暂停」：跑着的停下，卡住的把红字撤了；进度都留着。 */
    private void queuePause(ServerPlayer player) {
        if (this.queueState == QUEUE_STATE_RUNNING || this.queueState == QUEUE_STATE_STUCK) {
            this.queueState = QUEUE_STATE_IDLE;
            this.queueReason = QUEUE_REASON_NONE;
            this.queuePool = null;
        }
        sendQueueStatus(player);
    }

    /** 点「跳过」：这一项按已完成记账；卡住时顺手让队列接着跑下去。 */
    private void queueSkip(ServerPlayer player, int entryId) {
        if (this.queueEntries.isEmpty()) {
            return;
        }
        int idx = queueIndexOf(entryId);
        if (idx < 0) {
            idx = this.queueIndex >= 0 && this.queueIndex < this.queueEntries.size()
                    ? this.queueIndex : queueFirstIncomplete(0);
        }
        if (idx < 0) {
            return;
        }
        QueuedCraft entry = this.queueEntries.get(idx);
        this.queueDone.put(entry.id(), entry.target());
        this.queuePool = null;
        notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.skipped",
                "队列跳过：%s", queueEntryName(entry));
        if (this.queueState == QUEUE_STATE_STUCK) {
            this.queueState = QUEUE_STATE_RUNNING;
            this.queueReason = QUEUE_REASON_NONE;
        }
        if (this.queueState == QUEUE_STATE_RUNNING) {
            int next = queueFirstIncomplete(idx);
            if (next < 0) {
                queueFinish(player);
            } else {
                this.queueIndex = next;
            }
        } else {
            int first = queueFirstIncomplete(0);
            this.queueState = first < 0 ? QUEUE_STATE_FINISHED : this.queueState;
            this.queueIndex = first < 0 ? -1 : first;
        }
        sendQueueStatus(player);
    }

    /**
     * 出手一次：照「自动合成」的同一条管道做一轮——先直接合成，缺料就地规划依赖树补做；
     * 产物优先送扳手绑定的输出容器。区别只有一次只做一份，做完按个数累计进度。
     */
    private void queueStep(ServerPlayer player) {
        QueuedCraft entry = this.queueEntries.get(this.queueIndex);
        int done = this.queueDone.getOrDefault(entry.id(), 0);
        if (done >= entry.target()) {
            queueAdvanceOrFinish(player);
            return;
        }
        CraftingRecipe recipe = resolveEntry(entry.recipeId());
        if (recipe == null) {
            queueStuck(player, QUEUE_REASON_RECIPE_GONE, entry);
            return;
        }
        if (this.queuePool == null) {
            this.bench.refreshStorages(true);
            this.queuePool = collectInputPool();
        } else {
            this.queuePool = refreshPool(this.queuePool);
        }
        if (this.queuePool.isEmpty()) {
            queueStuck(player, QUEUE_REASON_MISSING, entry);
            return;
        }
        CraftingRecipe madeRecipe = recipe;
        int outcome = craftOnce(player, recipe, this.queuePool, false, true);
        if (outcome == CRAFT_MISSING) {
            // 直接材料不够：和自动合成一样，规划整棵依赖树，缺的中间材料就地补做
            this.queuePool = refreshPool(this.queuePool);
            PlannedRecipe planned = this.queuePool.isEmpty() ? null : planBest(recipe, this.queuePool);
            if (planned == null || !planned.succeeded()) {
                if (planned != null && planned.aborted()) {
                    queueStuck(player, QUEUE_REASON_PLAN_ABORTED, entry);
                } else {
                    queueStuck(player, QUEUE_REASON_MISSING, entry);
                    if (planned != null && !planned.missing().isEmpty()) {
                        notifyMissing(player, planned.missing(),
                                "gui." + SmartWorkbenchMod.MOD_ID + ".msg.missing_base");
                    }
                }
                return;
            }
            madeRecipe = planned.recipe();
            outcome = executeRecursivePlan(player, planned.state().steps, this.queuePool, true);
        }
        if (outcome == CRAFT_OK || outcome == CRAFT_OUTPUT_FULL || outcome == CRAFT_INVENTORY_FULL) {
            // 材料确实消耗了、东西确实做出来了（OUTPUT/INVENTORY_FULL 只是产物没落到该去的地方），都要记账
            int produced = Math.max(1, madeRecipe.getResultItem(this.level.registryAccess()).getCount());
            int total = this.queueDone.merge(entry.id(), produced, Integer::sum);
            markDirty();
            updateResult();
            if (total >= entry.target()) {
                queueEntryComplete(player, entry);
                return;
            }
        }
        switch (outcome) {
            case CRAFT_OK:
                this.queueStatusDirty = true;
                break;
            case CRAFT_OUTPUT_FULL:
                queueStuck(player, QUEUE_REASON_OUTPUT_FULL, entry);
                break;
            case CRAFT_INVENTORY_FULL:
                queueStuck(player, QUEUE_REASON_INVENTORY_FULL, entry);
                break;
            case CRAFT_UNSUPPORTED:
                queueStuck(player, QUEUE_REASON_UNSUPPORTED, entry);
                break;
            default:
                queueStuck(player, QUEUE_REASON_MISSING, entry);
                break;
        }
    }

    /** 一项做完：报一次完成，切到下一项（或整队完成）。 */
    private void queueEntryComplete(ServerPlayer player, QueuedCraft entry) {
        notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.entry_done",
                "队列完成：%1$s ×%2$s", queueEntryName(entry), entry.target());
        this.queuePool = null; // 换项重新扫一遍存储
        refreshCraftables(false);
        queueAdvanceOrFinish(player);
        this.queueStatusDirty = true;
    }

    private void queueAdvanceOrFinish(ServerPlayer player) {
        int next = queueFirstIncomplete(this.queueIndex + 1);
        if (next < 0) {
            // 往后找不到活干不等于真干完了：玩家可能把<b>前面</b>已做完的项目标调大
            // （滚轮加量），那时要回头补做，不能直接报「全部做完」。
            int wrap = queueFirstIncomplete(0);
            if (wrap >= 0 && wrap < this.queueIndex) {
                this.queueIndex = wrap;
                this.queuePool = null;
                return;
            }
            queueFinish(player);
        } else {
            this.queueIndex = next;
        }
    }

    private void queueFinish(ServerPlayer player) {
        this.queueState = QUEUE_STATE_FINISHED;
        this.queueReason = QUEUE_REASON_NONE;
        this.queueIndex = -1;
        this.queuePool = null;
        notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.finished",
                "合成队列全部做完（共 %s 项）", this.queueEntries.size());
        refreshCraftables(true);
        this.queueStatusDirty = true;
    }

    /** 做不动了：停下并说明原因，等玩家处理完点「开始」，或点「跳过」继续后面的项。 */
    private void queueStuck(ServerPlayer player, int reason, QueuedCraft entry) {
        this.queueState = QUEUE_STATE_STUCK;
        this.queueReason = reason;
        this.queuePool = null;
        notifyQueueStuck(player, reason, entry);
        refreshCraftables(false);
        this.queueStatusDirty = true;
    }

    private void notifyQueueStuck(ServerPlayer player, int reason, QueuedCraft entry) {
        String name = queueEntryName(entry);
        switch (reason) {
            case QUEUE_REASON_OUTPUT_FULL:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.output_full",
                        "队列停下：输出容器满了（%s），腾个空位点「开始」继续", name);
                break;
            case QUEUE_REASON_INVENTORY_FULL:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.inventory_full",
                        "队列停下：背包满了，产物掉在脚下（%s），腾个空位点「开始」继续", name);
                break;
            case QUEUE_REASON_RECIPE_GONE:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.recipe_gone",
                        "队列停下：这份配方已经不存在了（%s），删掉这一项再点「开始」", name);
                break;
            case QUEUE_REASON_UNSUPPORTED:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.unsupported",
                        "队列停下：配方不支持一键合成（%s），跳过它继续后面的", name);
                break;
            case QUEUE_REASON_PLAN_ABORTED:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.plan_aborted",
                        "队列停下：链式配方太复杂算不动（%s），可调递归预算配置或跳过它", name);
                break;
            default:
                notifyArgs(player, "gui." + SmartWorkbenchMod.MOD_ID + ".queue.stuck.missing",
                        "队列停下：材料不够（%s），补料后点「开始」，或跳过继续", name);
                break;
        }
    }

    /** 队列项的显示名：优先拿配方产物名，配方已经没了就退回配方 ID。 */
    private String queueEntryName(QueuedCraft entry) {
        CraftingRecipe recipe = resolveEntry(entry.recipeId());
        ItemStack result = recipe == null ? ItemStack.EMPTY : recipe.getResultItem(this.level.registryAccess());
        return result.isEmpty() ? entry.recipeId().toString() : result.getHoverName().getString();
    }

    /** 从 from 起第一个还没做完的下标；全做完返回 -1。 */
    private int queueFirstIncomplete(int from) {
        for (int i = Math.max(0, from); i < this.queueEntries.size(); i++) {
            QueuedCraft e = this.queueEntries.get(i);
            if (this.queueDone.getOrDefault(e.id(), 0) < e.target()) {
                return i;
            }
        }
        return -1;
    }

    private int queueIndexOf(int entryId) {
        if (entryId < 0) {
            return -1;
        }
        for (int i = 0; i < this.queueEntries.size(); i++) {
            if (this.queueEntries.get(i).id() == entryId) {
                return i;
            }
        }
        return -1;
    }

    private void sendQueueStatus(ServerPlayer player) {
        List<int[]> progress = new ArrayList<>(this.queueEntries.size());
        for (QueuedCraft e : this.queueEntries) {
            progress.add(new int[]{e.id(), this.queueDone.getOrDefault(e.id(), 0)});
        }
        ModNetwork.sendToPlayer(player,
                new S2CQueueStatusPacket(this.benchPos, this.queueState, this.queueReason, this.queueIndex, progress));
    }

    /* —— 客户端镜像：界面渲染只读这里 —— */

    public void setClientQueueStatus(int state, int reason, int index, Map<Integer, Integer> done) {
        this.clientQueueState = state;
        this.clientQueueReason = reason;
        this.clientQueueIndex = index;
        this.clientQueueDone = Map.copyOf(done);
    }

    public int getClientQueueState() {
        return this.clientQueueState;
    }

    public int getClientQueueReason() {
        return this.clientQueueReason;
    }

    public int getClientQueueIndex() {
        return this.clientQueueIndex;
    }

    public Map<Integer, Integer> getClientQueueDone() {
        return this.clientQueueDone;
    }

    /**
     * 顶层规划：先重置本次操作的搜索预算，再递归规划。
     * <p>
     * 只有顶层入口走这里，{@link #planRecipe} 之间的递归调用共享同一份预算，
     * 否则预算会在每一层被重新填满，形同虚设。时间线同样只在顶层设一次。
     */
    private boolean planTopLevel(CraftingRecipe target, RecursivePlanState state) {
        // 记下本次点击要做的东西：之后任何一条补料配方（深度 ≥ 1），只要材料里
        // 会出现它，规划阶段就直接跳过这条配方。没有这条闸门，「1 压缩 → 9 加速」
        // 这类拆解配方会被当成加速火把的来源，把玩家刚攒出来的目标物品拆了再合回去，
        // 批量点 8 次就空转 8 轮，东西一个没多、材料倒折腾一圈。
        ItemStack targetResult = target == null ? ItemStack.EMPTY
                : target.getResultItem(this.level.registryAccess());
        state.blockedTarget = targetResult.isEmpty() ? null : targetResult.getItem();
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
        for (RecipeFacts facts : RecipeIndexCache.byResult(this.level, indexOpener()).getOrDefault(result.getItem(), List.of())) {
            if (tried >= AUTO_RECIPE_TRY_LIMIT) {
                break;
            }
            CraftingRecipe candidate = facts.recipe;
            ResourceLocation id = candidate.getId();
            if (id == null || id.equals(preferred.getId())) {
                continue;
            }
            ItemStack other = facts.result;
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
        // 拆自己的闸门：补料配方的材料里只要出现本次的目标物品，这条配方就当不可用。
        // 顶层那条（depth == 0）不受影响——它是玩家明确要做的那一步。
        // 这里不报缺口：缺的是什么由上层调用方沿现有路径继续算，避免把「不能拆自己」
        // 错报成「缺少压缩加速火把」（玩家缺的其实是中间材料）。
        if (depth > 0 && state.blockedTarget != null && usesBlockedTarget(recipe, state.blockedTarget)) {
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

    /**
     * 这条配方的任意一个材料槽位，候选物品里是否出现指定物品。
     * 只看直接材料即可：材料链再深，最深处那条「吃目标」的配方被拦住后，
     * 上面各层要么换别的来源、要么一起失败，效果等价于整链跳过。
     */
    private static boolean usesBlockedTarget(CraftingRecipe recipe, Item blocked) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack candidate : ingredient.getItems()) {
                if (!candidate.isEmpty() && candidate.getItem() == blocked) {
                    return true;
                }
            }
        }
        return false;
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
        // 反向索引现在住在全局缓存 RecipeIndexCache 里（全服一份），这里只读不建。
        for (RecipeFacts facts : RecipeIndexCache.byResult(this.level, indexOpener()).getOrDefault(wanted.getItem(), List.of())) {
            CraftingRecipe recipe = facts.recipe;
            if (recipe.getId() == null || active.contains(recipe.getId())) {
                continue;
            }
            if (ItemStack.isSameItemSameTags(facts.result, wanted)) {
                matches.add(recipe);
            }
        }
        return matches;
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
            Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
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
                ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
        /** 本次点击要做成什么（顶层规划时写入）。补料配方碰它一律跳过，防「拆自己」。 */
        private Item blockedTarget;

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
        Player prevCraftingPlayer = ForgeHooks.getCraftingPlayer();
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
            ForgeHooks.setCraftingPlayer(prevCraftingPlayer);
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
            // 没绑输出容器时不能走「塞不进输出容器」的分支：那会把每一次都判成
            // CRAFT_OUTPUT_FULL，自动合成做完 1 个就停（默认配置下正是没绑输出容器）。
            // 没绑就按普通交付处理，产物退回背包，和 README 的说明一致。
            if (this.bench == null || this.bench.getOutputStorages().isEmpty()) {
                if (player.getInventory().add(stack)) {
                    return CRAFT_OK;
                }
                player.drop(stack, false);
                return CRAFT_INVENTORY_FULL;
            }
            ItemStack rest = insertIntoOutputs(stack);
            if (rest.isEmpty()) {
                return CRAFT_OK;
            }
            // 输出容器满了：剩下的退回背包，保证不丢；调用方会把这个产物计入本次合成后停止
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

    /**
     * 批量/自动合成的下一轮沿用同一份 {@link StoragePool}：只回读每个已知格子的当前数量，
     * 不重新扫描全部处理器（上千轮时这部分才是大头）。格子里被换成别的物品时才整份重扫。
     * <p>
     * 但只要本轮往箱子里写回过东西（{@link #stowOrReturn}、原路退回），快照就可能漏掉
     * 「原本空着、刚塞进去」的格子，必须整份重扫——见 {@link #poolStale}。
     */
    private StoragePool refreshPool(StoragePool pool) {
        if (this.poolStale) {
            this.poolStale = false;
            return StoragePool.collect(inputHandlers());
        }
        if (pool != null && pool.refreshCounts()) {
            return pool;
        }
        return StoragePool.collect(inputHandlers());
    }

    /** 收集一份当前库存快照，并清掉「需要重扫」的标记（新快照本来就不可能是脏的）。 */
    private StoragePool collectInputPool() {
        this.poolStale = false;
        return StoragePool.collect(inputHandlers());
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

    /**
     * 「开始」专用冷却（10 tick）：START 背后的 {@code refreshStorages(true)} 是全量扫描，
     * 比通用重操作贵一个量级，单独限更狠一点。包处理器已经查过界面有效性，这里只看时间。
     */
    private boolean acceptQueueStart(ServerPlayer player) {
        long now = this.level.getGameTime();
        if (now < this.nextQueueStartTick) {
            return false;
        }
        this.nextQueueStartTick = now + 10L;
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

    /** 带任意占位符的提示（队列那些「物品名 + 数量」的文案）。 */
    private void notifyArgs(Player player, String key, String fallback, Object... args) {
        player.displayClientMessage(Component.translatableWithFallback(key, fallback, args), true);
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

    /**
     * 合成格内容的 64 位指纹：种类（注册表数字 ID）+ 数量 + NBT 内容哈希，FNV-1a 混合。
     * 与旧的字符串签名等价，但每 tick 不再为每格拼描述串、更不会把整棵 NBT
     * {@code toString()} 序列化一遍（方案六）。
     */
    private long matrixSignature() {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < SmartWorkbenchBlockEntity.GRID_SIZE; i++) {
            ItemStack stack = this.matrix.getItem(i);
            // 物品是注册表单例，identity hash 在会话内稳定且免查注册表
            long h = stack.isEmpty() ? 0L : (long) System.identityHashCode(stack.getItem()) * 131L + stack.getCount();
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                h = h * 31L + tag.hashCode();
            }
            hash = (hash ^ h) * 0x100000001b3L;
        }
        return hash;
    }
}
