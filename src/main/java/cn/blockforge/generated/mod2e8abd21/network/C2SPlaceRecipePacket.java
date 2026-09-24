package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务器：在 REI 的合成表上点了配方旁边的加号。
 * <p>
 * 缺料判断已经在客户端做完了（见 compat 包，那里把接入存储也算成可用材料），
 * 所以这个包只负责说清「把哪份配方摆进合成格」。
 */
public class C2SPlaceRecipePacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;
    private final boolean stackAll;

    public C2SPlaceRecipePacket(BlockPos pos, ResourceLocation recipeId, boolean stackAll) {
        this.pos = pos;
        this.recipeId = recipeId;
        this.stackAll = stackAll;
    }

    public C2SPlaceRecipePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.stackAll = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeBoolean(this.stackAll);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.placeRecipeFromClient(player, this.recipeId, this.stackAll);
            }
        });
        context.setPacketHandled(true);
    }
}
