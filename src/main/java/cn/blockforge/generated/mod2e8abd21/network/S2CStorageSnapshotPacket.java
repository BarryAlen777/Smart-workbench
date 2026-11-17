package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务器 → 客户端：<b>只更新接入存储快照</b>的轻量包。
 * <p>
 * 原版行为（见 {@link S2CCraftableListPacket}）：可合成列表的"种类指纹"没变、
 * 只是数量在动（漏斗抽走几块、农场塞进几根）时，列表内容其实一模一样，
 * 可上一版还是把整张表重新编包发一遍——大整合包里一次就是一两百 KB、几千条对象，
 * 客户端解完还要换列表对象、重排整个视图，每秒一次纯烧 CPU（界面开着就掉帧的元凶之一）。
 * <p>
 * 现在列表没换就只发这一小包：REI 要的库存快照、存储/输出计数照常刷新，
 * 列表对象原封不动，客户端连视图重算都省了。
 */
public class S2CStorageSnapshotPacket {

    /** 解码上限：和列表包的快照上限同一口径，防异常数据撑爆内存。 */
    private static final int MAX_DECODE_STACKS = 8192;

    /** 数量字段单独用 varint 的原因同 S2CCraftableListPacket（writeItem 的数量只有一字节）。 */
    private static final int MAX_SNAPSHOT_COUNT = 1_000_000;

    private final BlockPos pos;
    private final List<ItemStack> storageStacks;
    private final int storageCount;
    private final int outputCount;
    private final boolean storageTruncated;

    public S2CStorageSnapshotPacket(BlockPos pos, List<ItemStack> storageStacks, int storageCount,
                                    int outputCount, boolean storageTruncated) {
        this.pos = pos;
        this.storageStacks = List.copyOf(storageStacks);
        this.storageCount = Math.max(0, storageCount);
        this.outputCount = Math.max(0, outputCount);
        this.storageTruncated = storageTruncated;
    }

    public S2CStorageSnapshotPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        int size = Math.max(0, buf.readVarInt());
        int cap = Math.min(size, MAX_DECODE_STACKS);
        List<ItemStack> stacks = new ArrayList<>(Math.min(cap, 1024));
        for (int i = 0; i < size; i++) {
            if (buf.readableBytes() <= 0) {
                break;
            }
            ItemStack item = buf.readItem();
            int count = buf.readVarInt();
            if (!item.isEmpty()) {
                item.setCount(Math.min(Math.max(1, count), MAX_SNAPSHOT_COUNT));
            }
            if (i < cap) {
                stacks.add(item);
            }
        }
        this.storageStacks = List.copyOf(stacks);
        // 计数下限钳到 0：异常/异构服务端给负数时，客户端拿它当「接了几台箱子」显示，
        // 负数会一路传染到界面排版（审查报告问题 3）。
        this.storageCount = Math.max(0, buf.readVarInt());
        this.outputCount = Math.max(0, buf.readVarInt());
        this.storageTruncated = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.storageStacks.size());
        for (ItemStack stack : this.storageStacks) {
            buf.writeItem(stack.copyWithCount(1));
            buf.writeVarInt(Math.min(stack.getCount(), MAX_SNAPSHOT_COUNT));
        }
        buf.writeVarInt(this.storageCount);
        buf.writeVarInt(this.outputCount);
        buf.writeBoolean(this.storageTruncated);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S2C 包的收件人就是本地玩家，这里按客户端自己的入口取人
            // （不能用 context.getSender()：那个返回 ServerPlayer，是"服务端查发包玩家"的语义）。
            // 审查报告问题 3 点名的不是这句取人，而是原先紧接着的
            // Minecraft.getInstance().screen instanceof SmartWorkbenchScreen——
            // 公共包类隔着包去 instanceof 客户端界面类。那句已经整个删掉：
            // 溯源过期改成在菜单（公共类）上立旗，界面下一帧自己来取。
            // 这里保留的 Minecraft.getInstance().player 是 S2C 包取本地玩家（收件人）的正常入口。
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos)) {
                menu.setClientStorageStacks(this.storageStacks, this.storageTruncated);
                menu.setClientConnectedStorageCount(this.storageCount);
                menu.setClientOutputCount(this.outputCount);
                // 仓库内容变了，悬停溯源那份答案（材料在哪个箱子、还剩几个）当场过期：
                // 在菜单上立旗，界面下一帧自己来取（同样是问题 3 的修法语义——
                // 发包只碰公共的菜单，不隔着包去敲客户端的界面类）。
                // 旧的先照常显示，新答案到了无缝换上，不闪白（审查报告问题 5 的行为不变）。
                menu.markClientStorageTraceDirty();
            }
        });
        context.setPacketHandled(true);
    }
}
