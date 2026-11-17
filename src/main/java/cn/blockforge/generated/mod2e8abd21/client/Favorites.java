package cn.blockforge.generated.mod2e8abd21.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 常用收藏：记住玩家用这台工作台<b>合成过</b>每件物品的次数和最后一次使用时间。
 * <p>
 * 两条来路决定一件物品算不算「收藏」（钉在列表最前面）：
 * <ul>
 *   <li><b>自动</b>：点过 {@value #AUTO_PIN_COUNT} 次合成（Shift 直接合成、批量、自动合成都算一次）——
 *       天天做木板火把的人不用学任何新操作，做够次数它自己就钉上去了；</li>
 *   <li><b>手动</b>：右键那格旁边按 Shift+右键 钉选/取消，想立刻钉住或钉走就用这个。</li>
 * </ul>
 * 存 {@code config/smart_workbench-favorites.txt}（一行一件物品：
 * {@code 物品ID|次数|手动|最后使用时间}），放在全局 config 而不是存档目录——
 * 按用户要求<b>跨存档记住</b>，换个世界打开工作台还是那一排常用。
 * <p>
 * 只在客户端计数：合成请求都是界面上点出去的，点一次记一次；不找服务端要回执，
 * 免得为这点数据改一遍合成协议。偶尔点下去没做成（材料刚好被抽走）会多记一次，
 * 对「常用」的判定无关紧要。
 */
public final class Favorites {

    /** 自动钉选的门槛：点过这么多次合成就算常用。（玩家反馈 3 次太容易触发，改成 5。） */
    public static final int AUTO_PIN_COUNT = 5;
    /** 文件里最多记多少件物品：再常用也不可能几百件，超了先扔最久没用的非手动项。 */
    private static final int MAX_ENTRIES = 512;

    private static final class Rec {
        int count;
        boolean manual;
        long lastUse;
    }

    private static Map<String, Rec> data;

    /**
     * 落盘节流：连点批量合成时每件产物都要记一笔，每次都全文件重写就是在渲染线程上
     * 反复做文件 IO（几百件就是一次可见的卡顿）。这里改成「记账只置脏，最多一秒真写一次」，
     * 关界面时再强制补一次（{@link #flush()}），所以最多丢一秒的账。
     */
    private static final long SAVE_THROTTLE_MS = 1000L;
    private static boolean saveDirty;
    private static long lastSaveMs;

    private Favorites() {
    }

    /** 点了一次合成：次数 +1、记下使用时间。返回 {@code true} 表示这一下刚好让它「转正」（钉到最前）。 */
    public static boolean recordUse(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        Rec rec = ensure().computeIfAbsent(key(result.getItem()), k -> new Rec());
        boolean wasPinned = rec.manual || rec.count >= AUTO_PIN_COUNT;
        rec.count++;
        rec.lastUse = System.currentTimeMillis();
        boolean pinned = rec.manual || rec.count >= AUTO_PIN_COUNT;
        save();
        return pinned && !wasPinned;
    }

    /**
     * Shift+右键 / 收藏键：钉选与取消<b>看的是「现在钉没钉」</b>，不是「是不是手动钉的」。
     * 自动钉选（做够次数）的那格提示同样写着可取消，按一次就该摘掉——
     * 取消时把自动次数一起清零，不然下一件做完又自己钉回来了。
     */
    public static boolean toggle(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        Rec rec = ensure().computeIfAbsent(key(result.getItem()), k -> new Rec());
        if (rec.manual || rec.count >= AUTO_PIN_COUNT) {
            rec.manual = false;
            rec.count = 0;
        } else {
            rec.manual = true;
            if (rec.lastUse == 0L) {
                rec.lastUse = System.currentTimeMillis();
            }
        }
        save();
        return rec.manual;
    }

    /** 这件物品现在算不算收藏（要不要钉在列表最前面）。 */
    public static boolean isFavorite(Item item) {
        Rec rec = ensure().get(key(item));
        return rec != null && (rec.manual || rec.count >= AUTO_PIN_COUNT);
    }

    /** 手动钉的（列表提示用）。 */
    public static boolean isManual(Item item) {
        Rec rec = ensure().get(key(item));
        return rec != null && rec.manual;
    }

    /** 最后一次点合成的时间（毫秒）；没点过返回 0。「最近用过」排序用它。 */
    public static long lastUse(Item item) {
        Rec rec = ensure().get(key(item));
        return rec == null ? 0L : rec.lastUse;
    }

    /** 一共点过多少次（提示框里显示「做过 N 次」）。 */
    public static int count(Item item) {
        Rec rec = ensure().get(key(item));
        return rec == null ? 0 : rec.count;
    }

    /** 钉选区内部的顺序：手动在前，然后按最后使用时间从新到旧。 */
    public static int comparePinned(ItemStack a, ItemStack b) {
        Rec ra = ensure().get(key(a.getItem()));
        Rec rb = ensure().get(key(b.getItem()));
        boolean ma = ra != null && ra.manual;
        boolean mb = rb != null && rb.manual;
        if (ma != mb) {
            return ma ? -1 : 1;
        }
        long la = ra == null ? 0L : ra.lastUse;
        long lb = rb == null ? 0L : rb.lastUse;
        return Long.compare(lb, la);
    }

    private static String key(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? item.toString() : id.toString();
    }

    private static Map<String, Rec> ensure() {
        if (data == null) {
            data = read();
        }
        return data;
    }

    private static Map<String, Rec> read() {
        Map<String, Rec> map = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String raw = line.trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String[] parts = raw.split("\\|");
                if (parts.length < 2) {
                    continue;
                }
                // 物品 ID 已经不在了（装过的模组被删）：丢掉，别长期占着 512 条配额
                // 把真收藏挤出去。读档只发生在开界面时，注册表这时早已就绪。
                ResourceLocation id = ResourceLocation.tryParse(parts[0]);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                try {
                    Rec rec = new Rec();
                    rec.count = Math.max(0, Integer.parseInt(parts[1]));
                    rec.manual = parts.length > 2 && "1".equals(parts[2]);
                    rec.lastUse = parts.length > 3 ? Long.parseLong(parts[3]) : 0L;
                    map.put(parts[0], rec);
                } catch (NumberFormatException ignored) {
                    // 一行坏了就丢这一行，别拖累整个文件
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 第一次玩、文件没有或被手改坏：从零开始
        }
        return map;
    }

    /** 记了账：置脏，够久了才真写一次（节流），别在渲染线程上连点就连写。 */
    private static void save() {
        saveDirty = true;
        long now = System.currentTimeMillis();
        if (now - lastSaveMs >= SAVE_THROTTLE_MS) {
            flush();
        }
    }

    /** 渲染循环每帧叫一次：脏账且距上次落盘够久了才真写（节流的时间推进点）。 */
    public static void flushIfDue() {
        if (saveDirty && System.currentTimeMillis() - lastSaveMs >= SAVE_THROTTLE_MS) {
            flush();
        }
    }

    /** 强制把脏账落盘（关界面时调一次，最多就丢这一秒内的记录）。 */
    public static void flush() {
        if (!saveDirty) {
            return;
        }
        saveDirty = false;
        lastSaveMs = System.currentTimeMillis();
        try {
            List<Map.Entry<String, Rec>> entries = new ArrayList<>(data.entrySet());
            if (entries.size() > MAX_ENTRIES) {
                // 先扔最久没用的非手动项，手动钉的留到最后
                entries.sort(Comparator.<Map.Entry<String, Rec>>comparingInt(e -> e.getValue().manual ? 1 : 0)
                        .thenComparingLong(e -> e.getValue().lastUse));
                entries = entries.subList(entries.size() - MAX_ENTRIES, entries.size());
            }
            List<String> lines = new ArrayList<>(entries.size());
            for (Map.Entry<String, Rec> entry : entries) {
                Rec rec = entry.getValue();
                lines.add(entry.getKey() + "|" + rec.count + "|" + (rec.manual ? 1 : 0) + "|" + rec.lastUse);
            }
            ConfigFiles.writeAtomic(file(), lines);
        } catch (IOException | RuntimeException ignored) {
            // 写不进去就算了，最多下次打开不记得，不影响玩
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("smart_workbench-favorites.txt");
    }
}
