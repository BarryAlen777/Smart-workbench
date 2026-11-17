package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.QueuedCraft;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务器：合成队列的一次指令。
 * <p>
 * 一条通道四种指令（op 用 {@link SmartWorkbenchMenu} 里的 QUEUE_OP_* 常量）：
 * <ul>
 *   <li>SYNC：客户端把整份队列推给服务器（增删项、调数量、换序、重开游戏都走它）；</li>
 *   <li>START / PAUSE：开始 / 暂停执行；</li>
 *   <li>SKIP：跳过 entryId 那一项（卡住时最常用）。</li>
 * </ul>
 * 执行进度只在服务端记（按队列项 id 记账），做完多少由 S2C 状态包回推给客户端显示。
 * 关界面后服务端那份队列状态随菜单销毁，重开界面客户端会重新 SYNC 一份。
 */
public class C2SQueuePacket {

    private final BlockPos pos;
    private final int op;
    private final List<QueuedCraft> entries;
    private final int entryId;

    public C2SQueuePacket(BlockPos pos, int op, List<QueuedCraft> entries, int entryId) {
        this.pos = pos;
        this.op = op;
        this.entries = entries == null ? List.of() : entries;
        this.entryId = entryId;
    }

    public C2SQueuePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.op = buf.readVarInt();
        // 解码在 netty 线程上跑，分配发生在使用之前：畸形包里的巨大 size 会直接撑爆内存。
        // 钳到队列上限（多出来的条目本来也会被服务端裁掉）；不抛异常，免得掐断整条连接。
        int size = Math.max(0, Math.min(buf.readVarInt(), SmartWorkbenchMenu.QUEUE_MAX_ENTRIES));
        List<QueuedCraft> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new QueuedCraft(buf));
        }
        this.entries = list;
        this.entryId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.op);
        buf.writeVarInt(this.entries.size());
        for (QueuedCraft entry : this.entries) {
            entry.write(buf);
        }
        buf.writeVarInt(this.entryId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.handleQueuePacket(player, this.op, this.entries, this.entryId);
            }
        });
        context.setPacketHandled(true);
    }
}
