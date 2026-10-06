package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 界面右侧“可合成”列表里的一项：配方 ID + 产物预览 + 是否链式项。
 * <p>
 * {@code chain=true} 表示直接材料不齐、但整棵依赖树的基础材料都在，
 * 点它要先自动补做中间材料（下级链式合成）才能出这份配方；界面上画「链」角标。
 */
public record CraftableEntry(ResourceLocation recipeId, ItemStack result, boolean chain) {

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.recipeId);
        buf.writeItemStack(this.result, false);
        buf.writeBoolean(this.chain);
    }

    public static CraftableEntry read(FriendlyByteBuf buf) {
        return new CraftableEntry(buf.readResourceLocation(), buf.readItem(), buf.readBoolean());
    }

    /** 用来判断列表有没有变化，变了才发包。 */
    public String signature() {
        return this.recipeId + "|" + this.result.getCount() + (this.chain ? "|chain" : "");
    }
}
