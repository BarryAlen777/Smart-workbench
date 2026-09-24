package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务器告诉客户端：缺少的材料可以递归合成，询问是否自动补齐。 */
public class S2CRecursiveCraftPromptPacket {

    /** 和可合成列表一样，原版 writeItem 一个字节装不下超过 127 的数量，物品按 1 个写、真实数量走 varint。 */
    private static final int MAX_STACK_COUNT = 1_000_000;

    public record MissingEntry(net.minecraft.world.item.ItemStack stack, int count, boolean craftable) {
        public MissingEntry(FriendlyByteBuf buf) {
            this(readStack(buf), buf.readVarInt(), buf.readBoolean());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeItem(this.stack.copyWithCount(1));
            buf.writeVarInt(Math.min(Math.max(1, this.stack.getCount()), MAX_STACK_COUNT));
            buf.writeVarInt(this.count);
            buf.writeBoolean(this.craftable);
        }

        private static net.minecraft.world.item.ItemStack readStack(FriendlyByteBuf buf) {
            net.minecraft.world.item.ItemStack item = buf.readItem();
            int count = buf.readVarInt();
            if (!item.isEmpty()) {
                item.setCount(Math.max(1, count));
            }
            return item;
        }
    }

    private final BlockPos pos;
    private final ResourceLocation recipeId;
    private final List<MissingEntry> autoCraftable;
    private final List<MissingEntry> unavailable;

    public S2CRecursiveCraftPromptPacket(BlockPos pos, ResourceLocation recipeId,
                                         List<MissingEntry> autoCraftable, List<MissingEntry> unavailable) {
        this.pos = pos;
        this.recipeId = recipeId;
        this.autoCraftable = List.copyOf(autoCraftable);
        this.unavailable = List.copyOf(unavailable);
    }

    public S2CRecursiveCraftPromptPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.autoCraftable = readEntries(buf);
        this.unavailable = readEntries(buf);
    }

    private static List<MissingEntry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MissingEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new MissingEntry(buf));
        }
        return List.copyOf(entries);
    }

    private static void writeEntries(FriendlyByteBuf buf, List<MissingEntry> entries) {
        buf.writeVarInt(entries.size());
        for (MissingEntry entry : entries) {
            entry.write(buf);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeResourceLocation(this.recipeId);
        writeEntries(buf, this.autoCraftable);
        writeEntries(buf, this.unavailable);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos)) {
                menu.setRecursivePrompt(this.recipeId, this.autoCraftable, this.unavailable);
            }
        });
        context.setPacketHandled(true);
    }
}
