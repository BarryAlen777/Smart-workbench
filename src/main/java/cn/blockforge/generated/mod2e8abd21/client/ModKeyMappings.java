package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 智能工作台的按键，全部在「选项 → 控制 → 按键绑定 → 智能工作台」里可以改成自己顺手的键。
 * <ul>
 *   <li>刷新键：默认 {@code R}，等同于界面上的「刷新」按钮；</li>
 *   <li>自动合成键：默认 <b>Alt + 鼠标左键</b>，等同于界面上的「自动合成」按钮；</li>
 *   <li>批量合成键：默认 <b>左 Ctrl</b>，按住它再点鼠标左键——点右侧列表项批量合成那一项，
 *       点产物格批量合成合成格里摆好的配方（自己手动摆的料也算），直到材料用完；
 *       想换成 Shift、空格或别的键，在按键绑定里改即可；</li>
 *   <li>全部展开/折叠键：默认 <b>空格</b>，按住它再点鼠标左键（点在右侧列表区域上）——
 *       一次展开所有折叠项；已经全展开时再点一下全部收起。
 *       它单独占一个按键，不再和自动合成共用 Alt+左键。</li>
 * </ul>
 * 批量合成和全部展开/折叠注册成「键盘键」：鼠标点击时实时查询这两个键此刻是否被按住，
 * 玩家在按键绑定界面改绑后立刻生效，不需要重开游戏。
 * 只在智能工作台界面里生效（冲突上下文是 GUI，免得在外面走路误触）。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeyMappings {

    public static final String CATEGORY = "key.categories.smart_workbench";

    public static final KeyMapping REFRESH = new KeyMapping(
            "key.smart_workbench.refresh",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    public static final KeyMapping AUTO_CRAFT = new KeyMapping(
            "key.smart_workbench.auto_craft",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_LEFT,
            CATEGORY);

    /** 批量合成的修饰键（默认左 Ctrl）：按住它 + 鼠标左键触发，键位可自己改。 */
    public static final KeyMapping BATCH_CRAFT = new KeyMapping(
            "key.smart_workbench.batch_craft",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY);

    /** 全部展开 / 全部折叠的修饰键（默认空格）：按住它 + 鼠标左键点列表触发，键位可自己改。 */
    public static final KeyMapping EXPAND_ALL = new KeyMapping(
            "key.smart_workbench.expand_all",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            CATEGORY);

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(REFRESH);
        event.register(AUTO_CRAFT);
        event.register(BATCH_CRAFT);
        event.register(EXPAND_ALL);
    }
}
