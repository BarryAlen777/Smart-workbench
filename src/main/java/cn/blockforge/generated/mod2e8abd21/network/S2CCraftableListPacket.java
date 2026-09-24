package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务器 → 客户端：推送「附近存储里现在能合成什么」这份列表，
 * 外加一份接入存储的内容快照（同类物品已合并）。
 * <p>
 * 快照是给 REI 用的：REI 判断「原料够不够」时只看玩家背包和菜单里的槽位，
 * 看不到我们接在 IItemHandler 上的箱子，所以它老喊缺料。拿到快照后，
 * {@code compat} 包里的自定义转移处理器就能把箱子里的材料算进去。
 */
public class S2CCraftableListPacket {

    /**
     * 原版网络的 writeItem 数量字段只有一个字节（-128~127），超过 127 会被截断成负数，
     * 客户端读回来就是一个「空堆叠」，于是明明箱子里有料，REI 还是报缺料。
     * 所以物品本身按 1 个写、真实数量另用 varint 传；这里再设个上限防止异常数据把包撑爆。
     */
    private static final int MAX_SNAPSHOT_COUNT = 1_000_000;

    private final BlockPos pos;
    private final List<CraftableEntry> entries;
    private final List<ItemStack> storageStacks;
    private final int storageCount;
    private final int outputCount;

    public S2CCraftableListPacket(BlockPos pos, List<CraftableEntry> entries,
                                  List<ItemStack> storageStacks, int storageCount, int outputCount) {
        this.pos = pos;
        this.entries = List.copyOf(entries);
        this.storageStacks = List.copyOf(storageStacks);
        this.storageCount = Math.max(0, storageCount);
        this.outputCount = Math.max(0, outputCount);
    }

    public S2CCraftableListPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<CraftableEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(CraftableEntry.read(buf));
        }
        this.entries = List.copyOf(list);
        int storageSize = buf.readVarInt();
        List<ItemStack> storage = new ArrayList<>(storageSize);
        for (int i = 0; i < storageSize; i++) {
            ItemStack item = buf.readItem();
            int count = buf.readVarInt();
            if (!item.isEmpty()) {
                item.setCount(Math.max(1, count));
            }
            storage.add(item);
        }
        this.storageStacks = List.copyOf(storage);
        this.storageCount = buf.readVarInt();
        this.outputCount = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.entries.size());
        for (CraftableEntry entry : this.entries) {
            entry.write(buf);
        }
        buf.writeVarInt(this.storageStacks.size());
        for (ItemStack stack : this.storageStacks) {
            buf.writeItem(stack.copyWithCount(1));
            buf.writeVarInt(Math.min(stack.getCount(), MAX_SNAPSHOT_COUNT));
        }
        buf.writeVarInt(this.storageCount);
        buf.writeVarInt(this.outputCount);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos)) {
                menu.setClientCraftables(this.entries);
                menu.setClientStorageStacks(this.storageStacks);
                menu.setClientConnectedStorageCount(this.storageCount);
                menu.setClientOutputCount(this.outputCount);
            }
        });
        context.setPacketHandled(true);
    }
}
