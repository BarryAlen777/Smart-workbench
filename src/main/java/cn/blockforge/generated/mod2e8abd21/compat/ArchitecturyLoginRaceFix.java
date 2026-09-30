package cn.blockforge.generated.mod2e8abd21.compat;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 针对 Architectury API 已知并发缺陷（architectury-api issue #286）的进服兜底。
 * <p>
 * <b>它治什么病：</b>装了 REI / FTB 这类基于 Architectury 的模组后，玩家进世界的瞬间，
 * Architectury 会在服务端线程上执行 {@code Lists.newArrayList(C2S.keySet())} 来同步网络注册表；
 * 而这张表（一个普通 {@code HashMap}，不是线程安全的）是启动时由别的线程（modloading-worker，
 * 装了 Lightspeed 这类并行加载模组时尤其明显）写入的。跨线程可见性一抖，
 * 读到的 size 字段比表里实际条目少 1，新版 JDK 17 的 {@code HashMap.keysToArray}
 * 又是无保护直写 {@code r[idx++] = e.key}，于是 ArrayIndexOutOfBoundsException，
 * Forge 把它包装成「无效的玩家数据」踢出世界——日志里整个堆栈一行我们的代码都没有。
 * <p>
 * <b>怎么兜：</b>在同一个服务端线程上、比 Architectury 的登录监听器（NORMAL 优先级）更早
 * （HIGHEST）跑一次体检：用 entrySet 迭代器逐节点读出真实条目（迭代器不走 toArray，
 * 正是为了绕开出界的那行代码），一旦发现「迭代条数 != size 字段」就把表清空重建。
 * 重建全部发生在当前线程，size 和内容从此一致，Architectury 紧接着的拷贝就不会再炸。
 * <p>
 * 没装 Architectury、或它换了字段名 / 换成了线程安全实现时，这里全部静默跳过，绝不影响进服。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArchitecturyLoginRaceFix {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartWorkbench");

    private static final String TARGET_CLASS = "dev.architectury.networking.forge.NetworkManagerImpl";
    private static final String[] TARGET_FIELDS = {"C2S", "S2C", "C2S_TRANSFORMERS", "S2C_TRANSFORMERS"};

    /** 只在真正修过一次的时候打一条日志，避免每次进服刷屏。 */
    private static boolean repairedOnce;

    private ArchitecturyLoginRaceFix() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        for (String fieldName : TARGET_FIELDS) {
            try {
                repairField(fieldName);
            } catch (Throwable ignored) {
                // Architectury 不存在、字段结构对不上、或正被并发写：放弃这次兜底，
                // 我们的监听器绝不抛异常，进服流程照常往下走。
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void repairField(String fieldName) throws Exception {
        Class<?> clazz = Class.forName(TARGET_CLASS);
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        if (!(field.get(null) instanceof HashMap map)) {
            // 没装 Architectury 或新版本已改成线程安全表：无事可做
            return;
        }
        // 逐节点迭代读出真实条目：HashMap 的迭代器直接走 table 链表，
        // 不依赖可能失同步的 size 字段，也不会触发 keysToArray 的越界写法。
        List<Map.Entry<?, ?>> entries = new ArrayList<>();
        for (Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator(); it.hasNext(); ) {
            entries.add(it.next());
        }
        if (entries.size() == map.size()) {
            return; // 表本身是自洽的，不动它
        }
        map.clear();
        for (Map.Entry entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        if (!repairedOnce) {
            repairedOnce = true;
            LOGGER.info("Fixed Architectury network registry race ({}): rebuilt {} entries before player login.",
                    fieldName, entries.size());
        }
    }
}
