package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求普通取料、直接合成或按数量批量合成。recipeId 为空表示使用合成格当前配方。 */
public class C2SCraftRequestPacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;
    private final boolean craft;
    private final boolean batch;
    private final int batchCount;

    public C2SCraftRequestPacket(BlockPos pos, ResourceLocation recipeId, boolean craft, boolean batch,
                                 int batchCount) {
        this.pos = pos;
        this.recipeId = recipeId;
        this.craft = craft || batch;
        this.batch = batch;
        this.batchCount = Math.max(0, batchCount);
    }

    public C2SCraftRequestPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readNullable(FriendlyByteBuf::readResourceLocation);
        this.craft = buf.readBoolean();
        this.batch = buf.readBoolean();
        this.batchCount = Math.max(0, buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNullable(this.recipeId, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(this.craft);
        buf.writeBoolean(this.batch);
        buf.writeVarInt(this.batchCount);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.onRecipeListClick(player, this.recipeId, this.craft, this.batch, this.batchCount);
            }
        });
        context.setPacketHandled(true);
    }
}
