package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 滚轮守卫：把「滚操作说明面板」这件事挪到 Forge 事件总线的<b>最高优先级</b>，
 * 赶在整合包里其它模组动手之前先滚掉。
 * <p>
 * 为什么要抢先：Forge 的处理顺序是「总线事件 → 界面自己的 mouseScrolled」，
 * 中途任何模组都能把这记滚轮取消掉（REI、小地图、Mouse Tweaks 在容器界面上
 * 都是出名的滚轮狂）。总线这层滚掉之后<b>直接把事件取消掉</b>：
 * 别的模组就不会在同一记滚轮上再叠自己的滚动（审查报告问题 6），
 * 界面的 mouseScrolled 也不会收到这记、不存在滚两次。
 * <p>
 * 保险丝：万一哪个模组事后把我们的取消"撤销"了，事件还是会传进界面——
 * 界面那头凭 {@link SmartWorkbenchScreen#catchHelpWheel} 留的标记吞下这一记。
 * 标记每见一记新滚轮就先清一次，被半路吞掉的事件不会把标记残留到下一记
 * （审查报告问题 1）。
 * <p>
 * 注意这层<b>只在鼠标压在说明面板上时才动手</b>：面板外（比如右边的可合成列表）
 * 一律放行，滚轮照常交给界面自己分配。上一版不看鼠标位置，面板一开
 * 全屏的滚轮都被抢走，列表就滚不动了。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HelpWheelGuard {

    private HelpWheelGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWheelPre(ScreenEvent.MouseScrolled.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof SmartWorkbenchScreen workbench
                && workbench.catchHelpWheel(event.getMouseX(), event.getMouseY(),
                event.getScrollDelta(), event.isCanceled())) {
            // 面板滚了这一格：取消事件，同一记滚轮不再有二主。
            event.setCanceled(true);
        }
    }
}
