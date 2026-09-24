package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务器：玩家对某个配方点了「自动合成」
 * （界面上的自动合成按钮、快捷键，或列表里 Alt+左键点击）。
 * <p>
 * 服务器会直接从接入的存储取料，缺的中间材料按依赖递归补齐，
 * 一直做到配置里的上限为止，产物优先送进绑定的输出容器。
 */
public class C2SAutoCraftPacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;

    public C2SAutoCraftPacket(BlockPos pos, ResourceLocation recipeId) {
        this.pos = pos;
        this.recipeId = recipeId;
    }

    public C2SAutoCraftPacket(FriendlyByteBuf buf) {
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
                menu.autoCraftFromStorage(player, this.recipeId);
            }
        });
        context.setPacketHandled(true);
    }
}
