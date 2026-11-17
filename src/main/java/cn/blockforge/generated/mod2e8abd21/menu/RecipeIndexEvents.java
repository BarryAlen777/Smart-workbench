package cn.blockforge.generated.mod2e8abd21.menu;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@link RecipeIndexCache} 的失效接线（审查报告问题①）。
 * <p>
 * 缓存的标脏/清空方法之前只有定义、没订到任何事件上——「数据包重载后自动重建」
 * 实际全靠「RecipeManager 实例换了 / 配方总数变了」两道兜底，重载后总数恰好没变
 * （改配方 JSON 内容不改数量、或增删相抵）时旧索引会一直留着。这里把正门装上：
 * <ul>
 *   <li>{@code TagsUpdatedEvent}：数据包应用完成（含 /reload）后必发——事件戳 +1，
 *       下次用到索引时懒重建；</li>
 *   <li>{@code ServerStoppedEvent}：停服整代丢弃，不把旧世界的配方和 RecipeManager
 *       扣在静态字段上（退服再进、换存档都算新开始）。</li>
 * </ul>
 * 只订游戏总线（FORGE）；两个回调都只做一次自增/置空，不重活。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeIndexEvents {

    private RecipeIndexEvents() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        RecipeIndexCache.onDataPacksUpdated();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RecipeIndexCache.clear();
    }
}
