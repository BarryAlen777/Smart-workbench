package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把「可合成」列表整理成界面上真正要画的一格一格：先按搜索词过滤，再按需把同类物品折叠成一项。
 * <p>
 * 折叠规则故意做得简单、可预期：看物品注册 ID 的<b>最后一段</b>（{@code oak_stairs -> stairs}、
 * {@code iron_backpack -> backpack}）。同名尾段的物品归成「同类」——所以各种木头的楼梯、
 * 各种材质的背包、各色羊毛都会收成一项，点一下再展开。
 * 尾段是 {@code block} 这种大杂烩的不折，免得把「草方块、黏液块」硬塞成一类。
 */
public final class CraftableListView {

    /** 过短的尾段（比如单个字母）和不适合当分类名的尾段不参与折叠。 */
    private static final Set<String> BLACKLIST = Set.of("block", "item", "thing");

    private static final int MIN_GROUP = 2;
    /** 一类最多收多少个：太多说明这个尾段太笼统（比如各种方块），干脆不折，免得一折就看不见东西。 */
    private static final int MAX_GROUP = 32;

    /**
     * 列表里的一格。
     *
     * @param entry     这一格代表的配方（折叠头用第一个成员代表）
     * @param groupKey  同类折叠的尾段；不折叠的普通项为 null
     * @param header    true 表示这是折叠头（点它展开/收起），false 表示普通项或展开后的成员
     * @param groupSize 这一类的成员数；普通项为 1
     */
    public record Cell(CraftableEntry entry, String groupKey, boolean header, int groupSize) {

        public boolean isMember() {
            return this.groupKey != null && !this.header;
        }
    }

    private CraftableListView() {
    }

    public static List<Cell> build(List<CraftableEntry> entries, String query, boolean folding,
                                   Set<String> expandedGroups) {
        List<CraftableEntry> filtered = new ArrayList<>(entries.size());
        for (CraftableEntry entry : entries) {
            if (CraftableSearch.matches(entry, query)) {
                filtered.add(entry);
            }
        }
        if (!folding) {
            List<Cell> flat = new ArrayList<>(filtered.size());
            for (CraftableEntry entry : filtered) {
                flat.add(new Cell(entry, null, false, 1));
            }
            return flat;
        }

        // 先把每个条目的分类键、每一类的成员和数量一次算好（O(n)），
        // 免得展开分组时又回头对整个列表挨个重算分类键。
        List<String> keys = new ArrayList<>(filtered.size());
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<CraftableEntry>> members = new HashMap<>();
        for (CraftableEntry entry : filtered) {
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

        List<Cell> out = new ArrayList<>(filtered.size());
        Set<String> emitted = new HashSet<>();
        for (int i = 0; i < filtered.size(); i++) {
            CraftableEntry entry = filtered.get(i);
            String key = keys.get(i);
            if (key != null && foldable.contains(key)) {
                if (!emitted.add(key)) {
                    continue; // 这一类的头已经发过了，成员要么跟着头一起发、要么收着
                }
                int size = counts.get(key);
                out.add(new Cell(entry, key, true, size));
                if (expandedGroups.contains(key)) {
                    for (CraftableEntry member : members.get(key)) {
                        out.add(new Cell(member, key, false, size));
                    }
                }
            } else {
                out.add(new Cell(entry, null, false, 1));
            }
        }
        return out;
    }

    /** 物品注册 ID 的最后一段，作为「同类」的分类键；不参与折叠的返回 null。 */
    public static String groupKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = stack.getItem().getDescriptionId();
        int dot = id.lastIndexOf('.');
        if (dot >= 0) {
            id = id.substring(dot + 1);
        }
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
