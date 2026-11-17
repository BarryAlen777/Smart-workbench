package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把「可合成」列表整理成界面上真正要画的一格一格：先按搜索词过滤，再按需把同类物品折叠成一项。
 * <p>
 * 折叠规则故意做得简单、可预期：看物品注册 ID 的<b>最后一段</b>（{@code oak_stairs -> stairs}、
 * {@code iron_backpack -> backpack}）。同名尾段的物品归成「同类」——所以各种木头的楼梯、
 * 各种材质的背包、各色羊毛都会收成一项，点一下再展开。
 * 尾段是 {@code block} 这种大杂烩的不折，免得把「草方块、黏液块」硬塞成一类。
 * <p>
 * <b>搜索时一律不折叠</b>：折叠头只摆一个代表，会把别的命中项藏起来——搜「铁剑」命中
 * 原版铁剑和模组铁剑两样，全归 {@code sword} 一类，折叠后只见代表那一格，而分类键上的
 * 数字明明写着 2，玩家自然以为搜索把原版铁剑弄丢了。折叠本是为浏览长列表去杂的，
 * 搜索命中的列表已经很短，直接把每一样都摆出来才对得上数字。
 */
public final class CraftableListView {

    /** 过短的尾段（比如单个字母）和不适合当分类名的尾段不参与折叠。 */
    private static final Set<String> BLACKLIST = Set.of("block", "item", "thing");

    private static final int MIN_GROUP = 2;
    /** 一类最多收多少个：太多说明这个尾段太笼统（比如各种方块），干脆不折，免得一折就看不见东西。 */
    private static final int MAX_GROUP = 32;

    /**
     * 「按名称」排序用的比较器。排序键已经先转成拼音了（见 {@link CraftableSearch#pinyinSortKey}），
     * 这里再用中文 Collator 比较，主要是让大小写、数字和没进拼音表的字符也有个稳定顺序。
     */
    private static final Collator NAME_COLLATOR = Collator.getInstance(Locale.CHINA);

    /**
     * 列表里的一格。
     *
     * @param entry       这一格代表的配方（折叠头用第一个成员代表）
     * @param groupKey    同类折叠的尾段；不折叠的普通项为 null
     * @param header      true 表示这是折叠头（点它展开/收起），false 表示普通项或展开后的成员
     * @param groupSize   这一类的成员数；普通项为 1
     * @param pinnedExtra 这一类里另有几项被「常用收藏」钉在列表最前了（因此没算进 groupSize）：
     *                    折叠头角标只数没钉的，提示框要用它说明「不是总共就这么多」
     * @param optionIndex 这一格是「这件产物的第几套配方」（从 1 起；只有一格的普通项恒为 1）
     * @param optionCount 这件产物一共有几套配方（≥1）。展开列表时每套各占一格，折叠着时合成一格
     */
    public record Cell(CraftableEntry entry, String groupKey, boolean header, int groupSize, int pinnedExtra,
                       int optionIndex, int optionCount) {

        /** 这一格背后还有别的材料能做同样的产物（界面上画「换」角标、提示框列出来）。 */
        public boolean hasOtherOptions() {
            return this.optionCount > 1;
        }

        public boolean isMember() {
            return this.groupKey != null && !this.header;
        }
    }

    private CraftableListView() {
    }

    /**
     * 一次整理出来的完整结果：要画的格子，外加<b>每个分类各有多少项</b>——按「只过了搜索词、
     * 还没过分类」的集合统计，分类键上的数字和置灰判断都用它（玩家打字时数字跟着搜索走）。
     */
    public record View(List<Cell> cells, Map<CraftCategory, Integer> categoryCounts) {

        public int count(CraftCategory category) {
            return this.categoryCounts.getOrDefault(category, 0);
        }
    }

    public static View build(List<CraftableEntry> entries, String query, boolean folding,
                             Set<String> expandedGroups, CraftCategory category, CraftSortMode sort) {
        // 搜索词只归一化一次：以前每条物品都重新压一遍空格/大小写，几千条 × 每次按键白烧 CPU。
        String needle = CraftableSearch.normalize(query);
        // 有搜索词时强制关掉折叠（见类注释）：命中几样就摆几样，别让折叠头把别的代表藏了。
        boolean foldingActive = folding && needle.isEmpty();
        // 先按搜索词过滤、同时逐类计数，再按选中的分类过滤。两个过滤都是对单条的独立判断，
        // 先后交换结果集一样（界面上呈现的就是「先分类后搜索」），但计数必须在只过搜索的
        // 集合上做，才对得上玩家点分类键前看到的数字。
        Map<CraftCategory, Integer> catCounts = new EnumMap<>(CraftCategory.class);
        List<CraftableEntry> matched = new ArrayList<>(entries.size());
        for (CraftableEntry entry : entries) {
            if (!CraftableSearch.matchesNormalized(entry, needle)) {
                continue;
            }
            matched.add(entry);
            catCounts.merge(CraftCategory.of(entry.result()), 1, Integer::sum);
        }
        catCounts.put(CraftCategory.ALL, matched.size());
        List<CraftableEntry> filtered = category == CraftCategory.ALL ? matched : new ArrayList<>(matched.size());
        if (category != CraftCategory.ALL) {
            for (CraftableEntry entry : matched) {
                if (category.accepts(CraftCategory.of(entry.result()))) {
                    filtered.add(entry);
                }
            }
        }
        // 收藏钉顶（用户定的规矩：不管哪种排法，钉住的几样永远在最前面）：
        // 收藏项单独成段、不参与同类折叠，免得被折进折叠头里"看不见也点不着"。
        List<CraftableEntry> pinned = new ArrayList<>();
        List<CraftableEntry> rest = new ArrayList<>(filtered.size());
        for (CraftableEntry entry : filtered) {
            if (Favorites.isFavorite(entry.result().getItem())) {
                pinned.add(entry);
            } else {
                rest.add(entry);
            }
        }
        pinned.sort((a, b) -> Favorites.comparePinned(a.result(), b.result()));
        // 钉在最前的项不参与折叠，所以折叠头的成员数天然少算了它们。这里按尾段记一下
        // 「这一类里被钉走几项」，折叠头的提示框据此补一句，角标和提示框的口径就不会打架。
        Map<String, Integer> pinnedCounts = new HashMap<>();
        for (CraftableEntry entry : pinned) {
            String key = groupKey(entry.result());
            if (key != null) {
                pinnedCounts.merge(key, 1, Integer::sum);
            }
        }
        // 排序键先算好再排（decorate-sort-undecorate）：getHoverName().getString() 每比一次
        // 就翻译一次组件，几千条就是 O(n log n) 次；预算一遍只翻 n 次，能省几十毫秒。
        // 用「键+条目」的临时对子而不是 Map 缓存：record 的 equals 是深比较，
        // 拿条目当哈希键查找反而更贵。
        if (sort == CraftSortMode.NAME || sort == CraftSortMode.RECENT) {
            String[] nameKeys = null;
            long[] recentKeys = null;
            if (sort == CraftSortMode.NAME) {
                nameKeys = new String[rest.size()];
                for (int i = 0; i < rest.size(); i++) {
                    // 排序键用拼音（和搜索同一张表），不是原始中文名；比较仍交给 Collator
                    nameKeys[i] = CraftableSearch.pinyinSortKey(rest.get(i).result());
                }
            } else {
                recentKeys = new long[rest.size()];
                for (int i = 0; i < rest.size(); i++) {
                    recentKeys[i] = Favorites.lastUse(rest.get(i).result().getItem());
                }
            }
            // 位置对子按键排，再照排好的顺序装回（List.sort 稳定：键相同的保持原序）
            Integer[] order = new Integer[rest.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            final String[] names = nameKeys;
            final long[] recent = recentKeys;
            Comparator<Integer> byKey = names != null
                    // 名称一样时按原序（稳定需求靠不比较原序兜不住 equals 的平局）
                    ? Comparator.comparing((Integer i) -> names[i], NAME_COLLATOR).thenComparingInt(i -> i)
                    : Comparator.<Integer>comparingLong(i -> recent[i]).reversed().thenComparingInt(i -> i);
            java.util.Arrays.sort(order, byKey);
            List<CraftableEntry> sorted = new ArrayList<>(rest.size());
            for (int i = 0; i < order.length; i++) {
                sorted.add(rest.get(order[i]));
            }
            rest = sorted;
        }
        List<Cell> cells = new ArrayList<>(filtered.size());
        for (CraftableEntry entry : pinned) {
            emitOptions(cells, entry, null, 1, 0, false);
        }
        if (!foldingActive) {
            for (CraftableEntry entry : rest) {
                emitOptions(cells, entry, null, 1, 0, false);
            }
            return new View(cells, catCounts);
        }

        // 先把每个条目的分类键、每一类的成员和数量一次算好（O(n)），
        // 免得展开分组时又回头对整个列表挨个重算分类键。
        List<String> keys = new ArrayList<>(rest.size());
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<CraftableEntry>> members = new HashMap<>();
        for (CraftableEntry entry : rest) {
            String key = groupKey(entry.result());
            keys.add(key);
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
                members.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
            }
        }
        Set<String> foldable = new HashSet<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            int size = count.getValue();
            if (size >= MIN_GROUP && size <= MAX_GROUP) {
                foldable.add(count.getKey());
            }
        }

        Set<String> emitted = new HashSet<>();
        for (int i = 0; i < rest.size(); i++) {
            CraftableEntry entry = rest.get(i);
            String key = keys.get(i);
            if (key != null && foldable.contains(key)) {
                int size = counts.get(key);
                if (!emitted.add(key)) {
                    continue; // 这一类的头已经发过了，成员要么跟着头一起发、要么收着
                }
                cells.add(new Cell(entry, key, true, size, pinnedCounts.getOrDefault(key, 0),
                        1, entry.optionCount()));
                if (expandedGroups.contains(key)) {
                    // 折叠头代表的那份产物自己也有好几套配方时，其余几套跟着一起铺开
                    emitOptions(cells, entry, key, size, 0, true);
                    for (CraftableEntry member : members.get(key)) {
                        // 折叠头用的就是第一个成员，这里再发一遍会让橡木楼梯之类的项显示两格
                        if (member == entry) {
                            continue;
                        }
                        emitOptions(cells, member, key, size, 0, false);
                    }
                }
            } else {
                emitOptions(cells, entry, null, 1, 0, false);
            }
        }
        return new View(cells, catCounts);
    }

    /**
     * 把一条列表项铺成格子：只有一套配方就是普通一格；同一件产物有好几套材料时，
     * <b>每套各占一格</b>——
     * <p>
     * 以前这里是「合并成一格 + 右键轮换」，提示框里虽然写了「另有 N 份」，玩家还是以为
     * 别的配方压根没读出来（用户：下界合金锭明明有方块分解、碎片+金锭两种做法，
     * 点开折叠却只看得见一种）。现在只要列表处于展开状态，就把每套材料都摆出来，
     * 各自带着自己的材料清单，点哪格就摆哪套料。
     *
     * @param skipFirst 折叠头已经用第一套配方代表这一格了，不要再重复铺一次
     */
    private static void emitOptions(List<Cell> cells, CraftableEntry entry, String groupKey,
                                    int groupSize, int pinnedExtra, boolean skipFirst) {
        List<CraftableEntry.Option> options = entry.options();
        int count = options.size();
        if (count <= 1) {
            if (!skipFirst) {
                cells.add(new Cell(entry, groupKey, false, groupSize, pinnedExtra, 1, 1));
            }
            return;
        }
        for (int index = 0; index < count; index++) {
            if (skipFirst && index == 0) {
                continue;
            }
            CraftableEntry.Option option = options.get(index);
            // 每格只代表一份配方：清空候选，免得「右键换配方」把整组又切回同一份、叠成重复格
            CraftableEntry single = new CraftableEntry(option.recipeId(), entry.result(), option.chain(), List.of());
            cells.add(new Cell(single, groupKey, false, groupSize,
                    index == 0 ? pinnedExtra : 0, index + 1, count));
        }
    }

    /** 物品注册 ID 的最后一段，作为「同类」的分类键；不参与折叠的返回 null。 */
    public static String groupKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        // 用注册 ID（modid:path）而不是 getDescriptionId()：后者是翻译键，
        // 模组物品完全可以自定义成和注册 ID 无关的名字，那时按翻译键分组会把
        // 「同尾段」的杂牌货硬凑成一类。注册 ID 才是文档里承诺的口径。
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            // 理论上只有注册表里不存在的物品会走到这儿；判空免得整个列表构建被 NPE 打断
            return null;
        }
        String id = key.getPath();
        int underscore = id.lastIndexOf('_');
        String suffix = underscore >= 0 ? id.substring(underscore + 1) : id;
        if (suffix.length() < 3 || BLACKLIST.contains(suffix)) {
            return null;
        }
        return suffix;
    }

    /** 折叠头的名字：优先用语言文件里翻译好的说法（stairs -> 楼梯），没有就退回英文尾段。 */
    public static Component groupLabel(String groupKey) {
        String key = "gui.smart_workbench.group." + groupKey;
        return Component.translatableWithFallback(key, prettify(groupKey));
    }

    private static String prettify(String raw) {
        String text = raw.replace('_', ' ').trim();
        if (text.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
