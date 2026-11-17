package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.block.SmartWorkbenchBlock;
import cn.blockforge.generated.mod2e8abd21.network.C2SConnectionScanPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 「接入总览」（世界中按 F12）的客户端登记处。
 * <p>
 * 玩家不打开界面、站在工作台附近按一下键，这里就存下服务端回来的两份<b>分组</b>坐标清单——
 * <b>输入容器</b>（自动扫到的 + 扳手绑的取料箱）和<b>输出容器</b>（扳手绑的收产物箱），
 * 一组就是一整台容器占的格（大箱子两格一组，画成一个整框，r233），
 * 外加两份过滤前的<b>绑定总数</b>（r223 问题②）——
 * {@link ConnectionOverviewRenderer} 照着在世界里描绿框 / 红框，并在屏幕下方（准星和快捷栏之间）
 * 写出各有多少台。
 * 再按一次同一个键关掉。清单是一次快照：箱子新绑 / 解绑了，再按一次就是新的。
 * <p>
 * 查哪一台工作台（r249 问题②改版）：<b>人周围 8 格内</b>有工作台就能直接开总览——
 * 一台就查那一台，不必对着它看；好几台时优先<b>准星指着的那台</b>，其次退回<b>最后一次打开过的</b>
 * （见 {@link #rememberBench}，界面一打开就记一笔），都没有就查<b>最近的一台</b>。
 * 一条都不满足（8 格内没有工作台）才提示玩家。
 * 服务端收到请求会再验一遍（同维度、9 格内、真是智能工作台、非旁观、<b>按玩家</b>回包限频），
 * 客户端按 8 格挑、服务端按 9 格验，客户端只会更严，合法请求不会被默默丢掉。
 * 这里拦不住的花招到那儿也白搭。
 */
public final class ConnectionOverview {

    /** 一次请求从发出到作废旧够用的毫秒数：服务端不搭理（版本错、位置被验掉）也不会永远吊着。 */
    private static final long PENDING_TIMEOUT_MS = 5000L;

    /**
     * 两次「发起新请求」之间的最小间隔：400ms。服务端<b>按玩家</b>记的回包限频是 300ms（防改包刷屏），
     * 客户端这里比它更宽一档，正常玩家怎么连按都不会撞进自己那个静默窗口。
     */
    private static final long REQUEST_COOLDOWN_MS = 400L;

    /** 总览开着吗。开着才画框、画屏幕下方那行字。 */
    private static boolean active;
    /** 这次总览查的是哪台工作台（画屏幕下方那行字用它的坐标）。 */
    @Nullable
    private static BlockPos bench;
    /** 清单属于哪个世界：换维度 / 退存档立刻作废，绝不把主世界的箱子框到末地去。 */
    @Nullable
    private static ResourceKey<Level> dimension;
    private static List<List<BlockPos>> inputs = List.of();
    private static List<List<BlockPos>> outputs = List.of();
    /**
     * 服务端报的<b>过滤前</b>绑定总数（r223 问题②）：{@link #inputs}/{@link #outputs}
     * 只含区块已加载、容器还在的那批，这两个数才是"这台工作台到底接了多少台"。
     * 按<b>台</b>算（大箱子两格算一台），屏幕下方的"总数"和空清单提示都按它对表。
     */
    private static int totalInputs;
    private static int totalOutputs;
    /**
     * 服务端分好的"区块加载着、容器却已被拆掉/换成别的方块"的台数（r227 问题①）：
     * 空清单提示靠它分清该喊"重新绑定"还是"走近一点"，未加载数 = 总数 − 可画 − 这个数。
     */
    private static int deadInputs;
    private static int deadOutputs;

    /** 已发请求等回包（-1 = 没在等）。回包带的号对不上就丢，防上一次的迟到答案覆盖这一次。 */
    private static int awaitingSeq = -1;
    private static long awaitingSince;
    private static int seqCounter;

    /** 最后一次打开过的工作台：范围内有好几台时的兜底（{@link #pickBench}）。 */
    @Nullable
    private static BlockPos lastBench;

    /** 上一次「发起新请求」的真实时间（毫秒），{@link #REQUEST_COOLDOWN_MS} 冷却用。 */
    private static long lastRequestAt;

    /**
     * 上一次「按键关掉总览」（含取消在途请求）的真实时间（毫秒）。r233 问题④：
     * 开着时快速双击，两下间隔 100~300ms、各自是独立一批，第一下老实关掉——第二下若
     * 没有这道闸，会真发一份新查询变"关→开"（撞上冷却则弹一句莫名其妙的「稍等一下」，
     * 两种都坏）。关掉后同一冷却窗口内的再按视作双击尾巴，<b>静默</b>忽略。
     */
    private static long lastOffAt;

    /**
     * r249 问题②：客户端挑工作台的半径（格）。8 格 = 原版"离开就关界面"那条线；
     * 服务端按 9 格验，客户端只会更严，发出去的请求不会石沉大海。
     */
    private static final double BENCH_RANGE = 8.0D;

    private ConnectionOverview() {
    }

    /** 界面一打开就记一笔（{@link SmartWorkbenchScreen} 构造里调用）：这份坐标之后在世界上用。 */
    public static void rememberBench(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        lastBench = pos.immutable();
    }

    /** 总览此刻开着吗（调试/日志用；渲染器实际按 {@link #viewMatches} 连维度一起对表）。 */
    public static boolean isActive() {
        return active;
    }

    @Nullable
    public static BlockPos benchPos() {
        return bench;
    }

    public static List<List<BlockPos>> inputPositions() {
        return inputs;
    }

    public static List<List<BlockPos>> outputPositions() {
        return outputs;
    }

    /** 服务端报的过滤前绑定总数（r223 问题②）：屏幕下方的"总数"按它报，不只报画得出来的。 */
    public static int inputTotal() {
        return totalInputs;
    }

    public static int outputTotal() {
        return totalOutputs;
    }

    /**
     * 玩家按了「接入总览」键（默认 F12，只在世界里生效，见 {@link ModKeyMappings#CONNECTION_OVERVIEW}）。
     * 开着 → 关掉；关着 → 挑一台工作台，向服务端要一份清单。
     */
    public static void onKeyPress() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (active || awaitingSeq >= 0) {
            // 正在看 → 关掉；正在等回包 → 也当关掉（这一下按的是"取消"）。提示只在真有画面可关时给。
            boolean hadView = active;
            clear();
            lastOffAt = System.currentTimeMillis();
            if (hadView) {
                toast("gui.smart_workbench.msg.overview_off", "接入总览：关");
            }
            return;
        }
        // r220 审查报告问题④：原来 400ms 冷却挡在找目标前面，"根本没对准工作台"的提示
        // 会被冷却一起静默吞掉，玩家分不清是冷却还是没选中。现在先找目标——
        // 没目标永远说实话；有目标但还在冷却里，也只是不回包（防刷屏的初衷不变），
        // 给一句轻提示让人知道发生了什么。
        BlockPos target = pickBench(minecraft);
        if (target == null) {
            toast("gui.smart_workbench.msg.overview_no_target",
                    "附近8格内无智能工作台");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOffAt < REQUEST_COOLDOWN_MS) {
            // r233 问题④：这是刚关掉那一下的双击尾巴（见 lastOffAt 注释），不是新意图——
            // 静默忽略，既不重开也不弹话。
            return;
        }
        if (now - lastRequestAt < REQUEST_COOLDOWN_MS) {
            toast("gui.smart_workbench.msg.overview_cooling", "稍等一下再按（刚发过一份查询）");
            return;
        }
        lastRequestAt = now;
        awaitingSeq = ++seqCounter;
        awaitingSince = now;
        // 记一下按下那一刻在哪个世界：回包半路人换了维度（传送门、回城卷），这份清单就作废。
        pendingDimension = minecraft.level.dimension();
        ModNetwork.sendToServer(new C2SConnectionScanPacket(target, awaitingSeq));
    }

    /**
     * 挑哪一台工作台（r249 问题②：从"必须看向/上次打开"改成"8 格内就能查"）：
     * <ol>
     *   <li><b>准星指着工作台</b> → 看着谁查谁（原版触及不到 5 格，天然在范围内）；</li>
     *   <li>没指着就<b>扫人周围 8 格</b>：只找到一台 → 直接查它，不用看它；</li>
     *   <li>找到多台 → 优先<b>最后打开过的那台</b>（还在范围内、方块还在才算数，
     *       它必然是扫描结果之一，不用另算距离）；</li>
     *   <li>多台又没兜底（从没打开过 / 那台被拆了）→ 查<b>最近的一台</b>，
     *       屏幕下方会写明"工作台 (x, y, z)"，开的哪台一目了然；</li>
     *   <li>8 格内一台都没有返回 null，调用方提示玩家。</li>
     * </ol>
     */
    @Nullable
    private static BlockPos pickBench(Minecraft minecraft) {
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos looking = ((BlockHitResult) minecraft.hitResult).getBlockPos();
            if (minecraft.level.getBlockState(looking).getBlock() instanceof SmartWorkbenchBlock) {
                return looking;
            }
        }
        List<BlockPos> near = findBenchesInRange(minecraft);
        if (near.isEmpty()) {
            return null;
        }
        if (near.size() == 1) {
            return near.get(0);
        }
        BlockPos last = lastBench;
        if (last != null && near.contains(last)) {
            return last;
        }
        BlockPos nearest = near.get(0);
        double nearestD = nearest.distToCenterSqr(minecraft.player.position());
        for (int i = 1; i < near.size(); i++) {
            double d = near.get(i).distToCenterSqr(minecraft.player.position());
            if (d < nearestD) {
                nearestD = d;
                nearest = near.get(i);
            }
        }
        return nearest;
    }

    /**
     * 扫玩家周围 {@link #BENCH_RANGE} 格球内的所有智能工作台（只在按键这一刻跑一遍，
     * 不逐帧扫）。19³≈6900 次取方块状态，按键级开销可以忽略；未加载的区块取到的是
     * 默认空气状态，看了也不会触发加载（r252 已按字节码核实：客户端只会拿到共享的
     * 空区块实例）。距离口径与服务端一致：都是方块中心到<b>玩家脚</b>
     * （{@code player.position()}），不是眼睛。
     */
    private static List<BlockPos> findBenchesInRange(Minecraft minecraft) {
        BlockPos center = minecraft.player.blockPosition();
        // 立方放宽 1 格：扫描中心是人脚所在格，距离判定是以脚为球心的 8 格球——
        // 脚点在本格内取任意小数，球都严格落在 ±8 立方之内，放宽纯属多试几格的保险，
        // 精确取舍仍交给下面的 distToCenterSqr 判定。
        int r = (int) BENCH_RANGE + 1;
        double maxSq = BENCH_RANGE * BENCH_RANGE;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (minecraft.level.getBlockState(cursor).getBlock() instanceof SmartWorkbenchBlock
                            && cursor.distToCenterSqr(minecraft.player.position()) <= maxSq) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }

    /**
     * 服务端回的<b>分组</b>清单（{@link cn.blockforge.generated.mod2e8abd21.network.S2CConnectionScanPacket}）：
     * 一组一台容器、组里可能多格（大箱子）。号对不上（已经关掉 / 又按了一次新的）直接丢；
     * 回来时人换过维度也丢，不画旧世界的框。
     * 两份总数是<b>过滤前</b>的绑定台数（r223 问题②）：清单只装已加载可画的，
     * 总数才反映"到底接了多少台"；两份损坏数是"加载着但容器已不在"的台数（r227 问题①），
     * 空清单的提示据此才说得准。
     */
    public static void onResponse(BlockPos benchPos, int seq, List<List<BlockPos>> inputList, int inputTotal,
                                  int inputDead, List<List<BlockPos>> outputList, int outputTotal, int outputDead) {
        Minecraft minecraft = Minecraft.getInstance();
        if (seq != awaitingSeq || minecraft.level == null) {
            return;
        }
        awaitingSeq = -1;
        ResourceKey<Level> current = minecraft.level.dimension();
        if (pendingDimension == null || !current.equals(pendingDimension)) {
            // 回包在半路、人已经换世界：作废，不画旧世界的框。
            return;
        }
        active = true;
        bench = benchPos.immutable();
        dimension = current;
        inputs = List.copyOf(inputList);
        outputs = List.copyOf(outputList);
        totalInputs = Math.max(inputTotal, inputs.size());
        totalOutputs = Math.max(outputTotal, outputs.size());
        deadInputs = Math.min(Math.max(0, inputDead), totalInputs - inputs.size());
        deadOutputs = Math.min(Math.max(0, outputDead), totalOutputs - outputs.size());
        // 一个容器都画不出来时分三种口径说（r223 问题② + r227 问题①）：真的一台没接 vs
        // 接了但都在没加载的区块 vs 接的容器已经被拆掉/换成了别的方块。一句"走近一点"
        // 对后者是误导——走近了也没有，得重新绑定。
        if (inputs.isEmpty() && outputs.isEmpty()) {
            int raw = totalInputs + totalOutputs;
            int dead = deadInputs + deadOutputs;
            if (raw <= 0) {
                toast("gui.smart_workbench.msg.overview_empty",
                        "这台工作台还没接入任何容器（用扳手绑定或把箱子放进扫描范围）");
            } else if (dead >= raw) {
                toastArg("gui.smart_workbench.msg.overview_broken",
                        "接入的 %s 台容器都已被拆掉或换成了别的方块（重新用扳手绑定一下）", dead);
            } else if (dead > 0) {
                toastArg("gui.smart_workbench.msg.overview_broken_partial",
                        "接入的容器有 %s 台已被拆掉或换成别的方块（需重新绑定），其余在未加载的区块里（走近一点再按）", dead);
            } else {
                toast("gui.smart_workbench.msg.overview_offloaded",
                        "接入的容器都在未加载的区块里（走近一点，或等区块加载后再按）");
            }
        }
    }

    /** 按下那一刻所在的世界，回包对表用（{@link #onKeyPress} 里发请求前记的，换维度回来的迟到包整包作废）。 */
    @Nullable
    private static ResourceKey<Level> pendingDimension;

    /**
     * r248 问题③：服务端<b>主动推</b>来的新账（扳手绑定/解绑/清空落地后紧跟一份
     * {@link cn.blockforge.generated.mod2e8abd21.network.S2CConnectionScanPacket#REFRESH_SEQ} 回包）。
     * 只认"此刻正开着总览、且推的正是这台工作台、还在同一个世界"，别的整包丢弃——
     * 没开着推了也没画面可刷；换过台/换过维度来的迟到包更不许污染当前账本。
     * 刷新是<b>静默</b>的：扳手自己已经报过"已绑定/已取消"，这里不再叠一句，
     * 只有框线和屏幕那行的计数当场跟着变（渲染每帧从这几份清单现算，账一换画就换）。
     */
    public static void onRefresh(BlockPos benchPos, List<List<BlockPos>> inputList, int inputTotal,
                                 int inputDead, List<List<BlockPos>> outputList, int outputTotal, int outputDead) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null) {
            return;
        }
        if (bench == null || !bench.equals(benchPos)) {
            return;
        }
        if (dimension == null || !minecraft.level.dimension().equals(dimension)) {
            return;
        }
        inputs = List.copyOf(inputList);
        outputs = List.copyOf(outputList);
        totalInputs = Math.max(inputTotal, inputs.size());
        totalOutputs = Math.max(outputTotal, outputs.size());
        deadInputs = Math.min(Math.max(0, inputDead), totalInputs - inputs.size());
        deadOutputs = Math.min(Math.max(0, outputDead), totalOutputs - outputs.size());
    }

    /** 关掉总览：框和屏幕下方那行字一起没，一格不留。 */
    public static void clear() {
        active = false;
        awaitingSeq = -1;
        bench = null;
        dimension = null;
        pendingDimension = null;
        inputs = List.of();
        outputs = List.of();
        totalInputs = 0;
        totalOutputs = 0;
        deadInputs = 0;
        deadOutputs = 0;
    }

    /** 这份账此刻是不是正该画在 dim 这个世界里（审查报告问题⑦：渲染器每帧自对表，不等 tick 清账）。 */
    public static boolean viewMatches(ResourceKey<Level> dim) {
        return active && dim != null && dim.equals(dimension);
    }

    /**
     * 离开服务器 / 退出世界：整份作废，<b>包括「最后打开的那台」退路</b>
     * （审查报告问题⑥——从 A 服退到 B 服，维度键可能一模一样，不退这笔旧账
     * 就可能照着 A 服的坐标去戳 B 服的工作台）。
     */
    public static void onDisconnect() {
        clear();
        lastBench = null;
        lastRequestAt = 0L;
        lastOffAt = 0L;
    }

    /**
     * 每帧对表（{@link ConnectionOverviewRenderer} 的客户端 tick 里调）：
     * 退世界 → 连退路一起作废；换维度 → 总览整份作废；等回包超时 → 撤账并提示一句。
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> current = minecraft.level == null ? null : minecraft.level.dimension();
        if (current == null) {
            onDisconnect();
            return;
        }
        if (active) {
            if (!current.equals(dimension)) {
                clear();
                return;
            }
        }
        if (awaitingSeq >= 0 && System.currentTimeMillis() - awaitingSince > PENDING_TIMEOUT_MS) {
            awaitingSeq = -1;
            // 超时不再闷声吞掉（审查报告问题④的尾巴）：给一句提示，玩家知道该再按一次
            toast("gui.smart_workbench.msg.overview_timeout",
                    "接入总览没等到回应（工作台太远、已损坏或不是这台），再按一次试试");
        }
    }

    private static void toast(String key, String fallback) {
        toastArg(key, fallback);
    }

    /** 带占位参数的提示句（r227 问题①：损坏提示要报出台数）。 */
    private static void toastArg(String key, String fallback, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatableWithFallback(key, fallback, args), true);
        }
    }
}
