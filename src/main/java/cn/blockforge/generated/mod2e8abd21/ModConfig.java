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
 *   <li>一次自动合成最多做多少个（默认 64，最大 12318）。</li>
 * </ul>
 * 另外保留了黑白名单和界面列表容量两项旧配置。
 */
public final class ModConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SCAN_RANGE;
    public static final ForgeConfigSpec.IntValue PLAYER_BACKPACK_RANGE;
    public static final ForgeConfigSpec.IntValue MAX_STORAGES;
    public static final ForgeConfigSpec.IntValue MAX_INPUT_BINDINGS;
    public static final ForgeConfigSpec.IntValue MAX_OUTPUT_BINDINGS;
    public static final ForgeConfigSpec.IntValue AUTO_CRAFT_LIMIT;
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
                .comment("一次最多在界面上列出多少个可合成配方。")
                .defineInRange("maxListSize", 96, 12, 384);
        STORAGE_BLACKLIST = builder
                .comment("不接入的方块 ID 列表，例如 [\"minecraft:chest\"]。")
                .defineListAllowEmpty("blacklist", List.of(), s -> s instanceof String);
        builder.pop();

        builder.push("craft");
        AUTO_CRAFT_LIMIT = builder
                .comment("点一次「自动合成」最多做多少个，默认 64，最大 12318。")
                .defineInRange("autoCraftLimit", 64, 1, 12318);
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

    public static int maxListSize() {
        return Math.max(12, LIST_SLOTS.get());
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
