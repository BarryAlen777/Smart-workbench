package cn.blockforge.generated.mod2e8abd21.network;

import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务器：玩家点了右侧「可合成」列表里的某一项。
 * <p>
 * 这里没有走原版的 {@code clickMenuButton}，因为原版按钮通道只适合很少的固定按钮；
 * 自定义包直接传配方 ID，列表刷新或排序后也不会把点击错配到另一条配方。
 * <p>
 * 三种点击：普通 = 把材料配到合成格；Shift = 从存储合成一次；Ctrl = 批量合成直到材料用完。
 */
public class C2SCraftRequestPacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;
    /** true 表示不经过合成格，直接从接入的存储取材合成 */
    private final boolean craft;
    /** true 表示反复合成（Ctrl 点击），craft 必为 true */
    private final boolean batch;

    public C2SCraftRequestPacket(BlockPos pos, ResourceLocation recipeId, boolean craft, boolean batch) {
        this.pos = pos;
        this.recipeId = recipeId;
        this.craft = craft || batch;
        this.batch = batch;
    }

    public C2SCraftRequestPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.craft = buf.readBoolean();
        this.batch = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeBoolean(this.craft);
        buf.writeBoolean(this.batch);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // 只认玩家当前真正打开的那台工作台，避免拿着旧界面乱点
            if (player != null && player.containerMenu instanceof SmartWorkbenchMenu menu
                    && menu.getBenchPos().equals(this.pos) && menu.stillValid(player)) {
                menu.onRecipeListClick(player, this.recipeId, this.craft, this.batch);
            }
        });
        context.setPacketHandled(true);
    }
}
