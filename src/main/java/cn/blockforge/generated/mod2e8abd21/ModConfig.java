package cn.blockforge.generated.mod2e8abd21;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.List;

/**
 * 通用配置（config/smart_workbench-common.toml）。
 * <p>
 * 这里管五件事：
 * <ul>
 *   <li>工作台自动扫描附近容器的范围（默认 5x5x5，最大 50x50x50）；</li>
 *   <li>读取玩家身上/物品栏里精妙背包的范围（默认 3x3x3，最大 50x50x50）；</li>
 *   <li>扳手手动绑定「取料容器」的上限（1~50）；</li>
 *   <li>扳手手动绑定「输出容器」的上限（1~50）；</li>
 *   <li>一次自动合成最多做多少个（默认 64，最大 12318）；</li>
 *   <li>链式合成的递归深度、展开步数、搜索预算（防止又深又绕的配方树拖住服务器）；</li>
 *   <li>右侧列表里「数量够不够」复核的预算。</li>
 * </ul>
 * 另外保留了黑白名单和界面列表容量两项旧配置。
 */
public final class ModConfig {

    public static final ForgeConfigSpec SPEC;

    /**
     * 界面配方列表的扫描上限。
     * <p>
     * 列表现在按块分包发送（每包 {@code S2CCraftableListPacket.CHUNK_SIZE} 条），
     * 不再受单个网络帧的限制，这里的数字只用来兜住「把整座仓库塞满」的极端情况。
     * 32768 条大致对应几百 KB 的内存和一次性的（只在库存变化时发生的）配方匹配，
     * 对正常游玩没有可感知的负担。
     */
    public static final int MAX_LIST_SIZE = 32768;

    /** 新配置的默认列表容量，足够覆盖常见大整合包的全部可合成项。 */
    public static final int DEFAULT_LIST_SIZE = 8192;

    /**
     * 1.5.x 及之前列表的默认上限。配置里写着这个数（或更小）说明是旧版本的默认值，
     * 不是玩家自己调过的：旧默认值会把排在后面的配方整批截掉（例如搜不到樱花木活板门），
     * 所以统一顶到 {@link #DEFAULT_LIST_SIZE}；玩家自己写的大数值照旧尊重。
     */
    private static final int LEGACY_LIST_LIMIT = 1024;

    public static final ForgeConfigSpec.IntValue SCAN_RANGE;
    public static final ForgeConfigSpec.IntValue PLAYER_BACKPACK_RANGE;
    public static final ForgeConfigSpec.IntValue MAX_STORAGES;
    public static final ForgeConfigSpec.IntValue MAX_INPUT_BINDINGS;
    public static final ForgeConfigSpec.IntValue MAX_OUTPUT_BINDINGS;
    public static final ForgeConfigSpec.IntValue AUTO_CRAFT_LIMIT;
    public static final ForgeConfigSpec.BooleanValue BATCH_USE_ALL_MATERIALS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHAIN_CRAFTABLES;
    public static final ForgeConfigSpec.IntValue RECURSIVE_MAX_DEPTH;
    public static final ForgeConfigSpec.IntValue RECURSIVE_MAX_STEPS;
    public static final ForgeConfigSpec.IntValue RECURSIVE_NODE_BUDGET;
    public static final ForgeConfigSpec.IntValue RECURSIVE_TIME_BUDGET_MS;
    public static final ForgeConfigSpec.IntValue CHAIN_VERIFY_BUDGET;
    public static final ForgeConfigSpec.IntValue LIST_SLOTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STORAGE_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("智能工作台 Smart Workbench");

        builder.push("storage");
        SCAN_RANGE = builder
                .comment("工作台自动接入附近容器的范围，按立方体边长算：5 表示 5x5x5，50 表示 50x50x50。",
                        "范围越大，每秒要扫的方块越多，低配机器别拉到 50。默认 5。")
                .defineInRange("scanRange", 5, 1, 50);
        PLAYER_BACKPACK_RANGE = builder
                .comment("工作台读取「玩家身上/物品栏里的精妙背包」的范围，按立方体边长算：",
                        "3 表示 3x3x3（默认，玩家得站在工作台旁边），50 表示 50x50x50。",
                        "只有落在这个范围里的玩家，他身上的背包才会被工作台取料。")
                .defineInRange("playerBackpackRange", 3, 1, 50);
        MAX_STORAGES = builder
                .comment("自动扫描最多同时接入多少个容器，越界的部分会被忽略。")
                .defineInRange("maxStorages", 6, 1, 24);
        MAX_INPUT_BINDINGS = builder
                .comment("扳手最多能给一台工作台绑定多少个「取料容器」（右击容器再右击工作台的绑定）。")
                .defineInRange("maxInputBindings", 24, 1, 50);
        MAX_OUTPUT_BINDINGS = builder
                .comment("扳手最多能给一台工作台绑定多少个「输出容器」（自动合成的产物会送进去）。")
                .defineInRange("maxOutputBindings", 6, 1, 50);
        LIST_SLOTS = builder
                .comment("一次最多在界面上列出多少个可合成配方。",
                        "列表按块分包发送，不受单个网络帧大小限制；这个值只用来兜住极端整合包，",
                        "上限 32768，默认 8192，正常整合包已经足够列出全部可合成项。")
                .defineInRange("maxListSize", DEFAULT_LIST_SIZE, 12, MAX_LIST_SIZE);
        STORAGE_BLACKLIST = builder
                .comment("不接入的方块 ID 列表，例如 [\"minecraft:chest\"]。")
                .defineListAllowEmpty("blacklist", List.of(), s -> s instanceof String);
        builder.pop();

        builder.push("craft");
        AUTO_CRAFT_LIMIT = builder
                .comment("点一次「自动合成」最多做多少个，默认 64，最大 12318。")
                .defineInRange("autoCraftLimit", 64, 1, 12318);
        BATCH_USE_ALL_MATERIALS = builder
                .comment("批量合成（按批量合成键点击）是否一直做到材料用完为止。",
                        "false（默认）：按工作台界面上「批量数量」框里填的次数合成，做满就停；",
                        "true：旧行为，忽略数量框，一直做到材料不够或达到单次上限。")
                .define("batchUseAllMaterials", false);
        SHOW_CHAIN_CRAFTABLES = builder
                .comment("右侧可合成列表是否额外显示「链式」项（带「链」角标）：",
                        "直接材料不齐、但把缺的中间材料沿配方树补做出来后能合成的物品。",
                        "点这些项会先自动补做中间材料（需要界面上「自动补齐」开着）。",
                        "超大整合包里如果觉得列表刷新偏慢，可以关掉它回到只显示直接可合成。")
                .define("showChainCraftables", true);
        RECURSIVE_MAX_DEPTH = builder
                .comment("链式合成（自动补齐）最多顺着配方树往下挖多少层。默认 16，最大 64。",
                        "例：原木 -> 木板 -> 木棍 -> 工具，是 3 层。层数越深，能自动补出来的东西越多，",
                        "搜索也越贵。默认值已经能覆盖原版和绝大多数模组配方。")
                .defineInRange("recursiveMaxDepth", 16, 1, 64);
        RECURSIVE_MAX_STEPS = builder
                .comment("一次链式合成最多补做多少份中间配方。默认 256，最大 4096。",
                        "这是防跑飞的硬上限：像「a 能合成 b，b 能合成 c、d、e、f……」，",
                        "配方树可能组合爆炸，到达上限就停下并提示材料不足，不会卡住服务器。")
                .defineInRange("recursiveMaxSteps", 256, 4, 4096);
        RECURSIVE_NODE_BUDGET = builder
                .comment("一次链式合成最多尝试多少个「规划节点」。默认 200000，最大 20000000。",
                        "深度和步数限住的是「做多少」，这个限住的是「试多少」：",
                        "配方树里备选配方多的时候，回退重试的次数可能远超实际步数。",
                        "到达上限会停止并提示「链式合成太复杂」；正常玩法用不到调它。")
                .defineInRange("recursiveNodeBudget", 200_000, 1_000, 20_000_000);
        RECURSIVE_TIME_BUDGET_MS = builder
                .comment("一次链式合成计算最多允许跑多少毫秒，超时立即中止。默认 2000，可填 200~60000。",
                        "防卡死的硬性时间闸：超大整合包（上万配方、接了存储网络）里单次规划的成本不可预估，",
                        "光靠节点预算可能刹不住车。超时后中止并提示「链式合成太复杂」，",
                        "内置服务器最多卡顿这几毫秒，不会整局死住。正常玩法用不到调它。")
                .defineInRange("recursiveTimeBudgetMs", 2000, 200, 60000);
        CHAIN_VERIFY_BUDGET = builder
                .comment("右侧列表复核「链式项数量到底够不够」时，整轮最多计算多少个节点。",
                        "默认 100000。复核是为了不让「差点材料」的项混进列表（点下去才报缺料）。",
                        "预算用尽时会停止复核、保留剩余项，只慢不丢；",
                        "觉得列表刷新偏慢可以调小，想复核得更彻底可以调大，填 0 表示完全不复核。")
                .defineInRange("chainVerifyBudget", 100_000, 0, 5_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private ModConfig() {
    }

    public static void register() {
        // 用全限定名：本类自己也叫 ModConfig，import 会撞名
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, SPEC, "smart_workbench-common.toml");
    }

    /** 自动扫描立方体的边长（5 表示 5x5x5）。 */
    public static int scanRange() {
        return Math.max(1, SCAN_RANGE.get());
    }

    /** 由边长换算出的半径：5 边长 -> 半径 2，正好覆盖 5x5x5。 */
    public static int scanRadius() {
        return scanRange() / 2;
    }

    /** 读取玩家身上背包的立方体边长（3 表示 3x3x3）。 */
    public static int playerBackpackRange() {
        return Math.max(1, PLAYER_BACKPACK_RANGE.get());
    }

    /** 玩家背包读取范围的半径：3 边长 -> 半径 1，正好覆盖 3x3x3。 */
    public static int playerBackpackRadius() {
        return playerBackpackRange() / 2;
    }

    public static int maxStorages() {
        return Math.max(1, MAX_STORAGES.get());
    }

    public static int maxInputBindings() {
        return Math.max(1, MAX_INPUT_BINDINGS.get());
    }

    public static int maxOutputBindings() {
        return Math.max(1, MAX_OUTPUT_BINDINGS.get());
    }

    public static int autoCraftLimit() {
        return Math.max(1, AUTO_CRAFT_LIMIT.get());
    }

    /** 批量合成是否忽略数量框、一直做到材料用完（旧行为）。 */
    public static boolean batchUseAllMaterials() {
        return Boolean.TRUE.equals(BATCH_USE_ALL_MATERIALS.get());
    }

    /** 列表是否显示需要链式补做的「链」项。 */
    public static boolean showChainCraftables() {
        return Boolean.TRUE.equals(SHOW_CHAIN_CRAFTABLES.get());
    }

    /** 链式合成最多往下挖多少层。 */
    public static int recursiveMaxDepth() {
        return Math.max(1, RECURSIVE_MAX_DEPTH.get());
    }

    /** 一次链式合成最多补做多少份中间配方。 */
    public static int recursiveMaxSteps() {
        return Math.max(1, RECURSIVE_MAX_STEPS.get());
    }

    /** 一次链式合成最多尝试多少个规划节点。 */
    public static int recursiveNodeBudget() {
        return Math.max(1, RECURSIVE_NODE_BUDGET.get());
    }

    /** 一次链式合成规划最多允许耗费的毫秒数（防卡死的硬性时间闸）。 */
    public static int recursiveTimeBudgetMs() {
        return Math.max(200, RECURSIVE_TIME_BUDGET_MS.get());
    }

    /** 整轮列表复核的节点预算；0 表示不复核。 */
    public static int chainVerifyBudget() {
        return Math.max(0, CHAIN_VERIFY_BUDGET.get());
    }

    public static int maxListSize() {
        int configured = LIST_SLOTS.get();
        // 旧版本默认的 1024（以及更早的 384）都是「没被玩家改过就白白截断列表」的遗留值，
        // 统一顶到新默认值；玩家自己写的大数值照旧尊重。
        int effective = configured <= LEGACY_LIST_LIMIT ? DEFAULT_LIST_SIZE : configured;
        return Math.max(12, Math.min(MAX_LIST_SIZE, effective));
    }

    public static boolean isBlacklisted(Block block) {
        List<? extends String> list = STORAGE_BLACKLIST.get();
        if (list == null || list.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        for (String entry : list) {
            if (entry != null && key.toString().equalsIgnoreCase(entry.trim())) {
                return true;
            }
        }
        return false;
    }
}
