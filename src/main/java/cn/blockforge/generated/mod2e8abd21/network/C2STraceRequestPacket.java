package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：鼠标停在右侧某条配方上时，问一句「这份配方的材料都在哪儿」。
 * <p>
 * 服务端翻一遍接入的容器（见 {@link SmartWorkbenchMenu#computeMaterialTrace}），
 * 用 {@link S2CTraceResponsePacket} 回一份「每样材料分别在哪个箱子几格、在不在背包里」。
 * 悬停切换很频繁，菜单里带了节流冷却，连着一串配方拖过去也只扫最近停下的那一条。
 */
public class C2STraceRequestPacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;

    public C2STraceRequestPacket(BlockPos pos, ResourceLocation recipeId) {
        this.pos = pos;
        this.recipeId = recipeId;
    }

    public C2STraceRequestPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeResourceLocation(this.recipeId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.computeMaterialTrace(player, this.recipeId);
            }
        });
        context.setPacketHandled(true);
    }
}
