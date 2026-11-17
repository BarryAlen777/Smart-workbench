package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 合成队列里的一项（只走网络的瘦数据）：队列项编号 + 配方 ID + 目标数量 + 已产出数量。
 * <p>
 * {@code id} 由客户端递增分配、随队列存档保存——执行进度（{@code done}，已经做出的产物个数）
 * 也一路按它记账。进度以客户端为准随 SYNC 推给服务端：菜单每次打开都会新建一个实例，
 * 服务端那份记账活不过一次关界面，靠客户端把它带回来，中途关界面再重开才不会被重做一遍。
 * {@code target} 按<b>产物个数</b>算（不是配方执行次数）：4 个一排的火把，目标 32 = 做 8 次配方。
 * 图标用的产物物品不走这个包：配方 ID 在服务端本来就要重新解析，客户端自己存着展示用。
 */
public record QueuedCraft(int id, ResourceLocation recipeId, int target, int done) {

    public QueuedCraft(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeResourceLocation(this.recipeId);
        buf.writeVarInt(this.target);
        buf.writeVarInt(this.done);
    }
}
