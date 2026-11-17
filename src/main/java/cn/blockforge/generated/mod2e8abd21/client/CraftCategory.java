package cn.blockforge.generated.mod2e8abd21.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ShearsItem;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 「可合成」列表的分类过滤（界面上八个键：上排 4、下排 4）。
 * <p>
 * <b>方案二：分类直接照搬原版创造模式标签页，不再自己合并。</b>玩家在游戏里怎么翻创造页，
 * 这里就怎么分——花和矿石在「自然方块」、染色羊毛在「染色方块」，不会再出现"甜浆果算建筑"
 * 这种硬塞。创造页一共十来个、去掉刷怪蛋和搜索后正好铺满两排：
 * <ul>
 *   <li>上排：全部 ｜ <b>建筑（一个键轮三档：建筑方块 → 染色方块 → 自然方块）</b> ｜ 功能方块 ｜ 红石方块</li>
 *   <li>下排：工具与实用物品 ｜ 战斗 ｜ 食物与饮品 ｜ 其他（模组物品和不好归类的）</li>
 * </ul>
 * 键（{@link Key}）是界面上的按钮，类别（本枚举）是物品归属的那一档——普通键一档一个键，
 * 「建筑」键一个键管三档，连点轮换。
 * <p>
 * <b>物品归属怎么判（A 为主、B 兜底）：</b>
 * <ul>
 *   <li>A：看产物物品在哪个<b>原版创造模式标签页</b>里——和创造菜单同一份数据，最准。
 *       查的顺序是「食物 → 自然 → 染色 → 建筑 → 功能 → 红石 → 战斗 → 工具」：
 *       甜浆果、发光浆果这类"食物页+自然页都上"的，先被食物捞走，和玩家直觉一致。
 *       旧式标签页（decorations/food/tools 那套 ID）的环境也认，缺新 ID 就退回旧 ID。</li>
 *   <li>B：模组物品多半只在<b>模组自己的</b>创造页里，A 查不到——退而按物品类型猜：
 *       能吃的算食物，刀剑甲弓算战斗，镐锄剪切竿算工具，方块归建筑方块，剩下进「其他」。</li>
 * </ul>
 * 分类只在<b>客户端</b>算（纯看列表项的产物图标），不掺和任何网络协议：
 * 服务端发来的可合成列表一个字节都不用改。
 * <p>
 * 「全部」不参与判定，它是过滤器的关闭态（不过滤）。
 */
public enum CraftCategory {

    ALL,
    /** 建筑方块（原版 building_blocks 页）。 */
    BUILDING,
    /** 染色方块（彩色/羊毛/玻璃那页）。 */
    COLORED,
    /** 自然方块（泥土花草原木矿石那页）。 */
    NATURAL,
    FUNCTIONAL,
    REDSTONE,
    /** 工具与实用物品。 */
    TOOLS,
    /** 战斗（武器、盔甲）。 */
    COMBAT,
    FOOD,
    OTHER;

    /**
     * 界面上的一个按键：管一到三档类别。「建筑」键一个键轮三档，其余一档一键。
     */
    public enum Key {
        KEY_ALL(ALL),
        KEY_BUILDING(BUILDING, COLORED, NATURAL),
        KEY_FUNCTIONAL(FUNCTIONAL),
        KEY_REDSTONE(REDSTONE),
        KEY_TOOLS(TOOLS),
        KEY_COMBAT(COMBAT),
        KEY_FOOD(FOOD),
        KEY_OTHER(OTHER);

        /** 这个键管的档位；轮换键按玩家能看懂的顺序排（建筑 → 染色 → 自然）。 */
        public final CraftCategory[] subs;

        Key(CraftCategory... subs) {
            this.subs = subs;
        }

        /** 这个键是不是"连点轮换"的那种。 */
        public boolean cycles() {
            return subs.length > 1;
        }

        /** 某一档归不归这个键管。 */
        public boolean covers(CraftCategory category) {
            for (CraftCategory sub : this.subs) {
                if (sub == category) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 上排四个键（全部 / 建筑轮换 / 功能 / 红石）。 */
    public static final Key[] TOP_KEYS = {Key.KEY_ALL, Key.KEY_BUILDING, Key.KEY_FUNCTIONAL, Key.KEY_REDSTONE};
    /** 下排四个键（工具 / 战斗 / 食物 / 其他），和上排同样首尾贴边、步进 24。 */
    public static final Key[] BOTTOM_KEYS = {Key.KEY_TOOLS, Key.KEY_COMBAT, Key.KEY_FOOD, Key.KEY_OTHER};
    /** 两排键合在一起的遍历用常量：以前画角标那一趟每帧新建一个数组，纯白丢。 */
    public static final Key[][] ROWS = {TOP_KEYS, BOTTOM_KEYS};

    /** 这一档在「建筑」轮换组里的位置；不在组里返回 -1。 */
    public int cycleIndex() {
        return switch (this) {
            case BUILDING -> 0;
            case COLORED -> 1;
            case NATURAL -> 2;
            default -> -1;
        };
    }

    /** 每个键吸收哪些原版创造标签页（新式 + 旧式 ID 都列上，缺的注册表里没有就自然跳过）。 */
    private static final List<ResourceKey<CreativeModeTab>> TABS_BUILDING = List.of(
            CreativeModeTabs.BUILDING_BLOCKS);
    private static final List<ResourceKey<CreativeModeTab>> TABS_COLORED = List.of(
            CreativeModeTabs.COLORED_BLOCKS);
    private static final List<ResourceKey<CreativeModeTab>> TABS_NATURAL = List.of(
            CreativeModeTabs.NATURAL_BLOCKS);
    private static final List<ResourceKey<CreativeModeTab>> TABS_FUNCTIONAL = List.of(
            CreativeModeTabs.FUNCTIONAL_BLOCKS, legacyTab("decorations"));
    private static final List<ResourceKey<CreativeModeTab>> TABS_REDSTONE = List.of(
            CreativeModeTabs.REDSTONE_BLOCKS, legacyTab("redstone"));
    private static final List<ResourceKey<CreativeModeTab>> TABS_FOOD = List.of(
            CreativeModeTabs.FOOD_AND_DRINKS, legacyTab("food"));
    private static final List<ResourceKey<CreativeModeTab>> TABS_TOOLS = List.of(
            CreativeModeTabs.TOOLS_AND_UTILITIES, legacyTab("tools"), legacyTab("equipment"));
    private static final List<ResourceKey<CreativeModeTab>> TABS_COMBAT = List.of(
            CreativeModeTabs.COMBAT);

    /** item -&gt; 类别。创造标签页的展示物品实例在一局游戏里稳定，按引用哈希即可。 */
    private static final Map<Item, CraftCategory> MEMO = new IdentityHashMap<>();
    /** A 路线的查找表：八个内容档各一个物品集合（标签页没构建出来时为 null，全部走 B）。 */
    private static Map<CraftCategory, Set<Item>> tabSets;

    /**
     * 每次打开界面调一次：让原版把所有标签页内容构建出来，然后重建查找表。
     * 原版配方书/创造模式菜单也会走同一句 {@code tryRebuildTabContents}——参数没变时它直接返回
     * false，几乎零开销；资源包重载导致参数变了才真正重建。
     */
    public static void onScreenOpen() {
        MEMO.clear();
        // 每次打开都重试（原版 tryRebuildTabContents 自带参数缓存，参数没变几乎零开销）：
        // 上次失败不代表这次失败——资源包重载、模组热更新后环境可能已经修好了。
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return; // 还没进世界（理论上不会发生）：下次打开再试
            }
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),
                    mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            tabSets = buildTabSets();
        } catch (Exception | LinkageError e) {
            // 建不出来也别拖垮界面：A 整条路关掉，分类全走 B 的类型猜测，功能照常、只是粗一点。
            tabSets = null;
        }
    }

    /** 产物属于哪一档（结果按物品种缓存，一个物品只判一次）。 */
    public static CraftCategory of(ItemStack stack) {
        if (stack.isEmpty()) {
            return OTHER;
        }
        Item item = stack.getItem();
        CraftCategory memo = MEMO.get(item);
        if (memo != null) {
            return memo;
        }
        CraftCategory result = classify(stack, item);
        MEMO.put(item, result);
        return result;
    }

    /**
     * 查表判定顺序（审查报告问题 7：以前每判一个未缓存物品都新建一个数组，白丢）。
     * 食物排最前：原版有些物品"两页都上"（甜浆果、发光浆果同时在「食物与饮品」与
     * 「自然方块」两页），玩家直觉上它们是吃的，先被食物捞走才不偏心。
     * 自然排在建筑/染色前面，木头花草这类"两边都上"的就不会被建筑抢先。
     */
    private static final CraftCategory[] CLASSIFY_ORDER = {
            FOOD, NATURAL, COLORED, BUILDING, FUNCTIONAL, REDSTONE, COMBAT, TOOLS};

    private static CraftCategory classify(ItemStack stack, Item item) {
        // A：在原版哪个创造页里，按 CLASSIFY_ORDER 的先后捞人。
        if (tabSets != null) {
            for (CraftCategory cat : CLASSIFY_ORDER) {
                Set<Item> set = tabSets.get(cat);
                if (set != null && set.contains(item)) {
                    return cat;
                }
            }
        }
        // B：模组物品多半只在自家创造页（不在上面八张表里），按物品类型兜底。
        if (stack.isEdible()) {
            return FOOD; // 蛋糕、蜂蜜瓶这类同时是方块的也算食物，和玩家直觉一致
        }
        if (item instanceof SwordItem || item instanceof ArmorItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof ShieldItem) {
            return COMBAT; // 打打杀杀的家伙事儿归战斗，和原版「战斗」页对齐
        }
        if (item instanceof TieredItem || item instanceof FishingRodItem || item instanceof ShearsItem
                || item instanceof ElytraItem) {
            return TOOLS;
        }
        if (item instanceof BlockItem) {
            return BUILDING; // 没登记进原版页的方块，归建筑方块最不离谱
        }
        return OTHER;
    }

    /** 把每个创造标签页的展示物品倒进对应的集合（一次性，几毫秒）。 */
    private static Map<CraftCategory, Set<Item>> buildTabSets() {
        Map<CraftCategory, Set<Item>> sets = new EnumMap<>(CraftCategory.class);
        put(sets, TABS_BUILDING, BUILDING);
        put(sets, TABS_COLORED, COLORED);
        put(sets, TABS_NATURAL, NATURAL);
        put(sets, TABS_FUNCTIONAL, FUNCTIONAL);
        put(sets, TABS_REDSTONE, REDSTONE);
        put(sets, TABS_FOOD, FOOD);
        put(sets, TABS_TOOLS, TOOLS);
        put(sets, TABS_COMBAT, COMBAT);
        return sets;
    }

    private static void put(Map<CraftCategory, Set<Item>> sets, List<ResourceKey<CreativeModeTab>> keys,
                            CraftCategory cat) {
        for (ResourceKey<CreativeModeTab> key : keys) {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
            if (tab == null) {
                continue; // 这套 1.20.1 没有的旧 ID（或反之），跳过即可
            }
            Set<Item> target = sets.computeIfAbsent(cat, ignored -> new HashSet<>(512));
            for (ItemStack stack : tab.getDisplayItems()) {
                if (!stack.isEmpty()) {
                    target.add(stack.getItem());
                }
            }
        }
    }

    private static ResourceKey<CreativeModeTab> legacyTab(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("minecraft", path));
    }

    /** 键面上的图标：直接用各档代表物，一眼认出是干什么的。
     *  <b>缓存成静态常量</b>：这八个键每帧都要画图标，以前一帧 alloc 八个 ItemStack，
     *  纯给 GC 添活；图标内容一局游戏里根本不会变，建一次用到底。 */
    private static final Map<CraftCategory, ItemStack> ICONS = new EnumMap<>(CraftCategory.class);

    public ItemStack icon() {
        return ICONS.computeIfAbsent(this, CraftCategory::createIcon);
    }

    private ItemStack createIcon() {
        return switch (this) {
            case ALL -> new ItemStack(Items.CRAFTING_TABLE);
            case BUILDING -> new ItemStack(Items.BRICKS);
            case COLORED -> new ItemStack(Items.MAGENTA_TERRACOTTA);
            case NATURAL -> new ItemStack(Items.OAK_LOG);
            case FUNCTIONAL -> new ItemStack(Items.CHEST);
            case REDSTONE -> new ItemStack(Items.REDSTONE);
            case TOOLS -> new ItemStack(Items.IRON_PICKAXE);
            case COMBAT -> new ItemStack(Items.IRON_SWORD);
            case FOOD -> new ItemStack(Items.APPLE);
            case OTHER -> new ItemStack(Items.KNOWLEDGE_BOOK);
        };
    }

    /** 档名（悬停提示标题、空列表提示用）。 */
    public Component label() {
        return Component.translatableWithFallback(
                "gui.smart_workbench.tab." + name().toLowerCase(Locale.ROOT), fallbackName());
    }

    private String fallbackName() {
        return switch (this) {
            case ALL -> "All";
            case BUILDING -> "Building Blocks";
            case COLORED -> "Colored Blocks";
            case NATURAL -> "Natural Blocks";
            case FUNCTIONAL -> "Functional Blocks";
            case REDSTONE -> "Redstone";
            case TOOLS -> "Tools & Utilities";
            case COMBAT -> "Combat";
            case FOOD -> "Food & Drinks";
            case OTHER -> "Other";
        };
    }

    /** 「全部」接受任何档；其余只接受自己。 */
    public boolean accepts(CraftCategory category) {
        return this == ALL || this == category;
    }
}
