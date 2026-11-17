package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：「不开界面查接入」——世界中按 F12 时问一句
 * <b>这台工作台都连着哪些容器</b>，服务端用 {@link S2CConnectionScanPacket} 回两份坐标清单
 * （输入容器 / 输出容器），客户端在世界里描绿框红框、屏幕下方报个数。
 * <p>
 * 界面都没开，{@code player.containerMenu} 不是工作台菜单，所以这条通路完全不碰菜单——
 * 直接按坐标找方块实体，跟溯源那套「借菜单问话」是两回事。
 * <b>安全闸门</b>（防改包窥探别人家箱子，r217 审查报告问题①③④逐条补强）：
 * <ol>
 *   <li>旁观模式一律不回（问题③：1.20.1 没有 {@code canInteractWithBlock} 这种通用权限查询，
 *       也没有能统一问到的领地/保护模组接口——「靠近即可查看」是有意设计，
 *       但旁观穿墙窥探不在设计之内，这里挡掉；正在被移除的方块实体同样不回）；</li>
 *   <li>方块位置就在收件人自己的维度里（只查 {@code player.level()}）；</li>
 *   <li>离人 9 格以内：客户端挑目标是 8 格口径，服务端放宽 1 格吸收位置同步误差，
 *       正常玩家永远不会「发了包石沉大海」（问题④的两头对齐）；</li>
 *   <li>那个坐标确实是智能工作台方块实体；</li>
 *   <li><b>每个玩家</b>对同一台工作台 300ms（6 tick）内最多回一份清单（r217 问题①防响应放大；
 *       r220 问题①把窗口从"整台共享"改成"一人一格"，多人同按互不干扰）。
 *       扫描另有 {@code refreshStorages} 的 1 秒节流；两份坐标清单在出包前各过一次
 *       已加载区块的存在性过滤，节流窗口里被拆掉的箱子不会画框（r220 问题⑤）；
 *       包同时带上<b>过滤前</b>的绑定总数，客户端才分得清"没接容器"和"接了但都在视距外"（r223 问题②）。</li>
 * </ol>
 * 任何一道不过就静默不回包，客户端 5 秒超时后会明确提示一句。
 */
public class C2SConnectionScanPacket {

    /**
     * 服务端距离校验上限的平方：9 格。口径：客户端按 8 格（原版"离开就关界面"那条线）挑目标，
     * 服务端多放 1 格吸收客户端/服务端位置同步的先后差——两头对齐后，
     * 合法请求要么被答、要么本来就不该发，不会再出现「客户端发了、服务端默默丢、玩家干等 5 秒」。
     */
    private static final double MAX_DISTANCE_SQUARED = 81.0D;

    /** <b>每个玩家</b>对这台工作台的回包最小间隔（tick）：300ms，正常按键碰不到，专治高频刷屏。 */
    private static final int REPLY_COOLDOWN_TICKS = 6;

    private final BlockPos pos;
    /** 请求流水号，服务端原样带回，客户端用它丢掉迟到 / 已被取消的回包。 */
    private final int seq;

    public C2SConnectionScanPacket(BlockPos pos, int seq) {
        this.pos = pos;
        this.seq = seq;
    }

    public C2SConnectionScanPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.seq = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.seq);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 旁观模式没有"使用方块"的立场，隔空窥探别人家的存储网络布局不是本功能的设计（问题③）。
            if (player.isSpectator()) {
                return;
            }
            // 先量距离再查方块实体：9 格外一眼拒掉，不给伪造坐标顺手加载远处区块的机会。
            if (this.pos.distToCenterSqr(player.position()) > MAX_DISTANCE_SQUARED) {
                return;
            }
            // 只查发送者自己所在的维度：坐标伪造指向别的世界，这里根本不去看。
            BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
            if (!(blockEntity instanceof SmartWorkbenchBlockEntity bench) || bench.isRemoved()) {
                return;
            }
            // 回包限频（r217 问题① + r220 问题①）：窗口按玩家记，不再整台共享——
            // 多人同时按互不干扰；改包客户端刷屏也只能刷出自己的回包，饿不到别人。
            if (!bench.claimOverviewReply(player.getUUID(), REPLY_COOLDOWN_TICKS)) {
                return;
            }
            // 带节流的重扫（1 秒内不重做）：被连按刷屏也只是复用上次扫描结果。
            bench.refreshStorages(false);
            // 可画清单 + 过滤前的绑定总数 + 已损坏数一次组齐（r223 问题② + r227 问题①）：
            // 远处未加载区块里的绑定进不了清单，但总数照实报，客户端才分得清"没接"和"都在视距外"；
            // 损坏数让空清单时能说准"是被拆掉了"还是"走近一点就行"。
            SmartWorkbenchBlockEntity.OverviewReport report = bench.buildOverviewReport();
            cn.blockforge.generated.mod2e8abd21.ModNetwork.sendToPlayer(player,
                    new S2CConnectionScanPacket(this.pos, this.seq,
                            report.inputs(), report.totalInputs(), report.deadInputs(),
                            report.outputs(), report.totalOutputs(), report.deadOutputs()));
        });
        context.setPacketHandled(true);
    }
}
