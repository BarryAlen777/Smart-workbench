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

/**
 * 智能工作台的按键。
 * <ul>
 *   <li>刷新键：默认 {@code R}，等同于界面上的「刷新」按钮；</li>
 *   <li>自动合成键：默认 <b>Alt + 鼠标左键</b>，等同于界面上的「自动合成」按钮，
 *       也可以像别的按键一样在「选项 → 控制 → 按键绑定」里改成顺手的样子。</li>
 * </ul>
 * 只在智能工作台界面里生效（冲突上下文是 GUI）。
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

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(REFRESH);
        event.register(AUTO_CRAFT);
    }
}
