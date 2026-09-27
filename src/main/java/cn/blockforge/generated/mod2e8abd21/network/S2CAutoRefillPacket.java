package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器 → 客户端：把「自动补齐」开关的真实状态告诉界面。
 * <p>
 * 为什么不用原版的 DataSlot 同步：DataSlot 只在数值发生变化时才发包，
 * 而它内部记住的「上一次的值」初值是 0。玩家上次关掉了自动补齐（值是 false = 0），
 * 重开界面时服务端算出来的值又是 0，两边一比较「没变」，这一包就永远不发，
 * 客户端就一直用自己默认的「开」。所以这里在界面打开时明确发一次。
 */
public class S2CAutoRefillPacket {

    private final BlockPos pos;
    private final boolean enabled;

    public S2CAutoRefillPacket(BlockPos pos, boolean enabled) {
        this.pos = pos;
        this.enabled = enabled;
    }

    public S2CAutoRefillPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos)) {
                menu.setAutoRefillEnabled(this.enabled);
            }
        });
        context.setPacketHandled(true);
    }
}
