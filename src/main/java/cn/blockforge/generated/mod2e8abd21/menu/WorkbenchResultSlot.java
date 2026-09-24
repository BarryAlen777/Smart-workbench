package cn.blockforge.generated.mod2e8abd21.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * 产物槽：合成格变化时只是显示结果，真正拿走时才消耗材料。
 * 逻辑参照原版 ResultSlot（匠魂工作台的 LazyResultSlot 也是同一套语义）。
 */
public class WorkbenchResultSlot extends Slot {

    private final SmartWorkbenchMenu menu;
    private final Player player;
    private int removeCount;

    public WorkbenchResultSlot(SmartWorkbenchMenu menu, Player player, Container resultContainer, int x, int y) {
        super(resultContainer, 0, x, y);
        this.menu = menu;
        this.player = player;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }
        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void onSwapCraft(int amount) {
        this.removeCount += amount;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (this.removeCount > 0) {
            stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            ForgeEventFactory.firePlayerCraftingEvent(this.player, stack, this.container);
        }
        if (this.container instanceof RecipeHolder holder) {
            holder.awardUsedRecipes(this.player, this.menu.getMatrixItems());
        }
        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        this.checkTakeAchievements(stack);
        this.menu.consumeGrid(player);
    }
}
