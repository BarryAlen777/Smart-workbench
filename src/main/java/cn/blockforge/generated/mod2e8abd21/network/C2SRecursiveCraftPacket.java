package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端确认或取消“自动补齐中间材料”。 */
public class C2SRecursiveCraftPacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;
    private final boolean accepted;

    public C2SRecursiveCraftPacket(BlockPos pos, ResourceLocation recipeId, boolean accepted) {
        this.pos = pos;
        this.recipeId = recipeId;
        this.accepted = accepted;
    }

    public C2SRecursiveCraftPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.accepted = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeBoolean(this.accepted);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.confirmRecursiveCraft(player, this.recipeId, this.accepted);
            }
        });
        context.setPacketHandled(true);
    }
}
