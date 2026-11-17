package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.network.S2CTraceResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料溯源的<b>世界高亮</b>登记处：界面里鼠标停在一条配方上时，
 * 把装着这些材料的容器<b>整台占的格组</b>登记在这里，{@link TraceHighlightRenderer} 每帧照单画框。
 * <p>
 * 一组 = 一整台容器（原版/精妙存储的大箱子两格拼一台），渲染时并成<b>一个</b>金框——
 * 跟 F12 总览的绿/红框同一口径（r239，接 r233：以前只登记锚点一格，大箱子只框住一半）。
 * <p>
 * 生命周期跟着悬停走：界面这边每帧续一次 {@link #TOUCH_MS}，指针挪开或关掉界面
 * 最多半秒后框就自己消失——不用等谁去"清理"，也就不会出现切了世界还残留一屏金框。
 * 精妙背包里的材料没有方块坐标，压根不进这张表（按用户要求只在提示框里出文字）。
 */
public final class TraceHighlights {

    /** 一次续命时长：界面每帧都续，真正的"离开"才会让它到期。 */
    private static final long TOUCH_MS = 600L;
    /**
     * 最多同时框多少台容器：跟溯源回包的上限对齐（9 种材料 × 4 个去处），
     * 免得提示框里列出的坐标有一半没描金框、标题却写着「有坐标的已描上金框」。
     * 按<b>台</b>算不按格算：一台大箱子占两格也只算一个框。
     * 实际会被去重压下来（多种材料常躺在同一批箱子里）。
     */
    public static final int MAX_BOXES = S2CTraceResponsePacket.MAX_MATERIALS * S2CTraceResponsePacket.MAX_SPOTS;

    /** 一笔高亮：到期时刻 + 这台容器整台占的格（第一个就是锚点，兼作表键）。 */
    private record Box(long expireAt, List<BlockPos> cells) {
    }

    private static final Map<BlockPos, Box> BOXES = new HashMap<>();

    /** 这批坐标所属的维度：换维度/切存档后旧坐标一律作废，不照着画到当前世界里。 */
    @Nullable
    private static ResourceKey<Level> dimension;

    private TraceHighlights() {
    }

    /**
     * 界面在悬停帧里调用：名单<b>整批替换</b>——给进来的格组登记/续命，
     * 上一批里没被再次点名的立刻撤掉。悬停从配方 A 换到配方 B 时，
     * A 的金框当场熄灭（而不是继续亮半秒误导人）。传空列表＝全撤。
     *
     * @param groups 每台容器一整组格；组首是锚点（去重就按它）
     */
    public static void sync(List<List<BlockPos>> groups) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> current = minecraft.level == null ? null : minecraft.level.dimension();
        if (current == null || !current.equals(dimension)) {
            // 不在同一个世界里：上一批坐标不作数，直接全撤
            BOXES.clear();
            dimension = current;
            return;
        }
        long now = System.currentTimeMillis();
        java.util.Set<BlockPos> wanted = new java.util.LinkedHashSet<>();
        Map<BlockPos, List<BlockPos>> cellsByKey = new HashMap<>();
        for (List<BlockPos> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            BlockPos anchor = group.get(0);
            // 封顶按台算：超过 MAX_BOXES 的新锚点不再登记（口径同旧版按坐标封顶）
            if (!wanted.contains(anchor) && wanted.size() >= MAX_BOXES) {
                continue;
            }
            if (wanted.add(anchor)) {
                cellsByKey.put(anchor, group);
            }
        }
        BOXES.keySet().retainAll(wanted);
        long expireAt = now + TOUCH_MS;
        for (BlockPos anchor : wanted) {
            // 同一锚点重复登记时以最新一批的格组为准（回包可能刚把大箱子两格拼齐）
            BOXES.put(anchor, new Box(expireAt, cellsByKey.get(anchor)));
        }
    }

    /** 关掉界面时调用：立刻清空，一格都不留。 */
    public static void clear() {
        BOXES.clear();
        dimension = null;
    }

    /**
     * 关掉界面时的"续命"：金框不再立刻熄灭，而是再亮 {@code seconds} 秒。
     * <p>
     * 背景：材料溯源的金框只有停在配方上才亮，可镜头在界面里转不动——想绕到箱子背后看，
     * 只能先关界面，一关框就灭，等于白指。续命期间玩家随便走随便看，到期自然熄灭；
     * 再开界面重新悬停会整批对表（{@link #sync}），不会叠出旧框。
     * 填 0 等于旧行为：直接清空。
     *
     * @return 是否真的有金框被留下来（调用方据此决定要不要提示玩家一句）
     */
    public static boolean holdAfterClose(int seconds) {
        if (seconds <= 0 || BOXES.isEmpty()) {
            clear();
            return false;
        }
        long expireAt = System.currentTimeMillis() + seconds * 1000L;
        BOXES.replaceAll((anchor, box) -> new Box(Math.max(box.expireAt(), expireAt), box.cells()));
        return true;
    }

    /** 渲染器逐帧取的快照：还没到期的<b>格组</b>（一组 = 一整台容器 = 一个金框）。 */
    public static List<List<BlockPos>> liveGroups() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> current = minecraft.level == null ? null : minecraft.level.dimension();
        if (current == null || !current.equals(dimension)) {
            BOXES.clear();
            return List.of();
        }
        long now = System.currentTimeMillis();
        BOXES.values().removeIf(box -> box.expireAt() < now);
        List<List<BlockPos>> out = new ArrayList<>(BOXES.size());
        for (Box box : BOXES.values()) {
            out.add(box.cells());
        }
        return out;
    }
}
