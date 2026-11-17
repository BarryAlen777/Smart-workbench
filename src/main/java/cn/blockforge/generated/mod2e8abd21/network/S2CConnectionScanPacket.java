package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.client.ConnectionOverview;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：一台智能工作台<b>连着哪些容器</b>（世界中 F12「接入总览」的回包）。
 * <p>
 * 两份<b>分组</b>坐标清单：{@code inputs} 是取料容器（自动扫到的 + 扳手绑的），{@code outputs} 是
 * 扳手绑的产物容器；<b>一组 = 一整台容器占的格</b>——原版/精妙存储的大箱子两格拼一台，
 * 组里带两格，客户端合起来描一个框（r233：以前只记锚点一格，大箱子只框住一半）。
 * 清单只含<b>区块已加载、容器还在</b>的可画坐标（r220 问题⑤）。
 * 精妙背包这类<b>没有方块坐标的库存压根不在包里</b>——描不了框的东西不必占带宽。
 * 另带过滤<b>前</b>的绑定总数 {@code totalInputs}/{@code totalOutputs}（r223 问题②）：按<b>台</b>算
 * （一组一台），客户端据此分清"真一台没接"和"接了但都在没加载的区块里"，屏幕下方报的"总数"
 * 也是真实绑定数，而不是过滤后的数。再带两侧"区块加载着、容器却已被拆掉/换掉"的
 * {@code deadInputs}/{@code deadOutputs}（r227 问题①）：空清单时客户端才说得出该重新绑定，
 * 而不是让人白跑一趟去"走近一点"。上限 {@value #MAX_GROUPS} 组一份
 * （配置顶格也到不了，纯粹防畸形包撑内存）。
 */
public class S2CConnectionScanPacket {

    /**
     * r248 问题③：服务端<b>主动推送</b>的回包占用的流水号。玩家的按键请求从 1 往上编号，
     * 永远撞不上这个负数——客户端拿它区分"你问了我才答"和"你正开着总览、绑定变了、
     * 我顺手推一份新账"，后者只在总览开着且是同一台工作台时刷新画面，不弹提示。
     * 必须是编译期常量：内联进调用方，专用服务器不会因此加载客户端类。
     */
    public static final int REFRESH_SEQ = -1;

    /** 单份清单的组数上限：服务端配置顶格（手动 50 + 自动 24）也远小于它。 */
    public static final int MAX_GROUPS = 96;

    /** 单组格数上限：服务端拼组封顶 16，这里留余量；只挡畸形包。 */
    private static final int MAX_CELLS_PER_GROUP = 32;

    /** 绑定总数/损坏数的编码上限：真实值远到不了，只挡畸形包把界面数字撑爆。 */
    private static final int MAX_TOTAL = 4096;

    private final BlockPos benchPos;
    private final int seq;
    private final List<List<BlockPos>> inputs;
    private final int totalInputs;
    private final int deadInputs;
    private final List<List<BlockPos>> outputs;
    private final int totalOutputs;
    private final int deadOutputs;

    public S2CConnectionScanPacket(BlockPos benchPos, int seq, List<List<BlockPos>> inputs, int totalInputs,
                                   int deadInputs, List<List<BlockPos>> outputs, int totalOutputs, int deadOutputs) {
        this.benchPos = benchPos;
        this.seq = seq;
        // 编码端同样钳顶：配置被改出天大的上限也只发前 MAX_GROUPS 台，每台格数也钳。
        this.inputs = capGroups(inputs);
        this.outputs = capGroups(outputs);
        // 总数钳到 [清单长度, MAX_TOTAL]：不许报得比清单还小（把"藏了几台"算成负数），
        // 也不许报出离谱大数。畸形解码同理走这条兜底。
        this.totalInputs = normalizeTotal(totalInputs, this.inputs.size());
        this.totalOutputs = normalizeTotal(totalOutputs, this.outputs.size());
        // 损坏数钳到 [0, 总数]：客户端拿"总数 − 可画 − 损坏"算未加载数，两头越界都会算出负差额。
        this.deadInputs = clampDead(deadInputs, this.totalInputs);
        this.deadOutputs = clampDead(deadOutputs, this.totalOutputs);
    }

    private static List<List<BlockPos>> capGroups(List<List<BlockPos>> groups) {
        int limit = Math.min(groups.size(), MAX_GROUPS);
        List<List<BlockPos>> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            List<BlockPos> group = groups.get(i);
            out.add(List.copyOf(group.subList(0, Math.min(group.size(), MAX_CELLS_PER_GROUP))));
        }
        return List.copyOf(out);
    }

    private static int normalizeTotal(int raw, int listSize) {
        return Math.max(listSize, Math.min(Math.max(0, raw), MAX_TOTAL));
    }

    private static int clampDead(int raw, int total) {
        return Math.max(0, Math.min(Math.min(raw, MAX_TOTAL), total));
    }

    public S2CConnectionScanPacket(FriendlyByteBuf buf) {
        this.benchPos = buf.readBlockPos();
        // 解码跑在 netty 线程上，分配必须先于信任：尺寸钳到上限，畸形包撑不爆内存。
        this.seq = buf.readVarInt();
        this.inputs = readGroups(buf);
        this.outputs = readGroups(buf);
        this.totalInputs = normalizeTotal(buf.readVarInt(), this.inputs.size());
        this.totalOutputs = normalizeTotal(buf.readVarInt(), this.outputs.size());
        this.deadInputs = clampDead(buf.readVarInt(), this.totalInputs);
        this.deadOutputs = clampDead(buf.readVarInt(), this.totalOutputs);
    }

    private static List<List<BlockPos>> readGroups(FriendlyByteBuf buf) {
        int groupCount = Math.max(0, Math.min(buf.readVarInt(), MAX_GROUPS));
        List<List<BlockPos>> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            int cellCount = Math.max(0, Math.min(buf.readVarInt(), MAX_CELLS_PER_GROUP));
            List<BlockPos> cells = new ArrayList<>(cellCount);
            for (int j = 0; j < cellCount && buf.readableBytes() >= 8; j++) {
                cells.add(buf.readBlockPos());
            }
            if (!cells.isEmpty()) {
                groups.add(List.copyOf(cells));
            }
        }
        return List.copyOf(groups);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.benchPos);
        buf.writeVarInt(this.seq);
        writeGroups(buf, this.inputs);
        writeGroups(buf, this.outputs);
        buf.writeVarInt(this.totalInputs);
        buf.writeVarInt(this.totalOutputs);
        buf.writeVarInt(this.deadInputs);
        buf.writeVarInt(this.deadOutputs);
    }

    private static void writeGroups(FriendlyByteBuf buf, List<List<BlockPos>> groups) {
        buf.writeVarInt(groups.size());
        for (List<BlockPos> group : groups) {
            buf.writeVarInt(group.size());
            for (BlockPos pos : group) {
                buf.writeBlockPos(pos);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // r220 审查报告问题③：原来是 DistExecutor.unsafeRunWhenOn——它只保证"服务端不执行"，
        // 对客户端状态类（ConnectionOverview）的引用还留在本类的常量池/lambda 里，类校验阶段
        // 理论上仍可能被解析（专用服务器 NoClassDefFoundError 的隐患）。
        // 换成 safeRunWhenOn + 专属 SafeRunnable：分发逻辑挪进 ConnectionScanSink，
        // 专用服务器上外层 supplier 根本不被调用，这个嵌套类连同它引用的客户端类都永远不会加载。
        context.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> new ConnectionScanSink(this.benchPos, this.seq, this.inputs, this.totalInputs,
                        this.deadInputs, this.outputs, this.totalOutputs, this.deadOutputs)));
        context.setPacketHandled(true);
    }

    /**
     * 只在客户端实例化的一次性投递器（r220 问题③）。服务端连这个类的字节码都不会碰到，
     * 更谈不上解析 {@link ConnectionOverview} → {@code Minecraft} 那条引用链。
     */
    private static final class ConnectionScanSink implements DistExecutor.SafeRunnable {
        private final BlockPos benchPos;
        private final int seq;
        private final List<List<BlockPos>> inputs;
        private final int totalInputs;
        private final int deadInputs;
        private final List<List<BlockPos>> outputs;
        private final int totalOutputs;
        private final int deadOutputs;

        ConnectionScanSink(BlockPos benchPos, int seq, List<List<BlockPos>> inputs, int totalInputs,
                           int deadInputs, List<List<BlockPos>> outputs, int totalOutputs, int deadOutputs) {
            this.benchPos = benchPos;
            this.seq = seq;
            this.inputs = inputs;
            this.totalInputs = totalInputs;
            this.deadInputs = deadInputs;
            this.outputs = outputs;
            this.totalOutputs = totalOutputs;
            this.deadOutputs = deadOutputs;
        }

        @Override
        public void run() {
            if (this.seq == REFRESH_SEQ) {
                // r248 问题③：扳手绑定/解绑后的主动推送——只对"正开着这台总览"的画面生效。
                ConnectionOverview.onRefresh(this.benchPos, this.inputs, this.totalInputs,
                        this.deadInputs, this.outputs, this.totalOutputs, this.deadOutputs);
            } else {
                ConnectionOverview.onResponse(this.benchPos, this.seq, this.inputs, this.totalInputs,
                        this.deadInputs, this.outputs, this.totalOutputs, this.deadOutputs);
            }
        }
    }
}
