package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 界面右侧“可合成”列表里的一项：配方 ID + 产物预览。
 */
public record CraftableEntry(ResourceLocation recipeId, ItemStack result) {

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.recipeId);
        buf.writeItemStack(this.result, false);
    }

    public static CraftableEntry read(FriendlyByteBuf buf) {
        return new CraftableEntry(buf.readResourceLocation(), buf.readItem());
    }

    /** 用来判断列表有没有变化，变了才发包。 */
    public String signature() {
        return this.recipeId + "|" + this.result.getCount();
    }
}
