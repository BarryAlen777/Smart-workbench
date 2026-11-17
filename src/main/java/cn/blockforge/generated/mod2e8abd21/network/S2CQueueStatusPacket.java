package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务器 → 客户端：合成队列的执行状态（状态机 + 当前项 + 每项进度）。
 * <p>
 * 只在状态真变了的时候发（做成一项、卡住、开始、暂停、跳过……），正常做的一 tick 一份，
 * 负载就是几对 varint，不碰可合成列表那条重通道。客户端存进菜单的镜像字段，
 * 界面上的队列状态条和队列面板都从镜像读。
 */
public class S2CQueueStatusPacket {

    private final BlockPos pos;
    private final int state;
    private final int reason;
    private final int index;
    /** 每项进度：与当前队列顺序一致的 (entryId, done) 序列。 */
    private final List<int[]> progress;

    public S2CQueueStatusPacket(BlockPos pos, int state, int reason, int index, List<int[]> progress) {
        this.pos = pos;
        this.state = state;
        this.reason = reason;
        this.index = index;
        this.progress = List.copyOf(progress);
    }

    public S2CQueueStatusPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.state = buf.readVarInt();
        this.reason = buf.readVarInt();
        this.index = buf.readVarInt();
        // 解码在客户端 netty 线程上跑，分配先于校验：畸形包里的巨大 size 能撑爆内存。
        // 钳到队列项上限——合法服务端发出的条数不会超过它（queueSync 那头也裁过）。
        int size = Math.max(0, Math.min(buf.readVarInt(), SmartWorkbenchMenu.QUEUE_MAX_ENTRIES));
        List<int[]> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new int[]{buf.readVarInt(), buf.readVarInt()});
        }
        this.progress = list;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.state);
        buf.writeVarInt(this.reason);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.progress.size());
        for (int[] pair : this.progress) {
            buf.writeVarInt(pair[0]);
            buf.writeVarInt(pair[1]);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos)) {
                Map<Integer, Integer> done = new LinkedHashMap<>();
                for (int[] pair : this.progress) {
                    done.put(pair[0], pair[1]);
                }
                menu.setClientQueueStatus(this.state, this.reason, this.index, done);
                // 客户端账本（列表数据 + 存档文件）跟着对一次账：
                // 进度以这份为准，服务端只是执行器，关界面重开也不会把做到一半的忘掉。
                cn.blockforge.generated.mod2e8abd21.client.CraftQueue.applyServerDone(done);
            }
        });
        context.setPacketHandled(true);
    }
}
