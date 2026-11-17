package cn.blockforge.generated.mod2e8abd21.menu;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 合成队列的发动机：每个玩家 tick 叫醒他正开着的智能工作台菜单一次
 * （{@link SmartWorkbenchMenu#tickCraftQueue}）。
 * <p>
 * 为什么挂在玩家 tick 而不是方块实体 tick：队列的产物要走「优先输出容器、退回背包、掉在脚下」
 * 这条以玩家为中心的路，配方授予成就、统计触发也算在玩家头上——执行者本来就该是开界面的这个人。
 * 语义是「人在工作台前、界面开着，队列替他一件接一件做」：关界面即停
 * （进度由客户端账本带回来，重开界面点「开始」就从原处接上）。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CraftQueueTicker {

    private CraftQueueTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer player && player.containerMenu instanceof SmartWorkbenchMenu menu) {
            menu.tickCraftQueue(player);
        }
    }
}
