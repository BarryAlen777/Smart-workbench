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
 *       它单独占一个按键，不再和自动合成共用 Alt+左键；</li>
 *   <li>加入队列键：默认 {@code V}，停在右侧列表某项上按一下，把这件产物排进合成队列；</li>
 *   <li>队列开关键：默认 {@code G}，开始 / 暂停合成队列；</li>
 *   <li>收藏键：默认 {@code B}，钉选 / 取消钉选停在列表上的那一项
 *       （鼠标好使的话原来的 Shift+右键也仍然可用，两个入口并存）；</li>
 *   <li>数量数字样式键：默认 {@code F8}，右下角 / 页签 / 折叠头的数量数字在
 *       「0.7 倍原版字体」和「点阵 3×5」两款之间来回换，换完记住（存客户端配置）；</li>
 *   <li>接入总览键：默认 {@code F12}，<b>只在世界里生效</b>（不开工作台界面也能按）——
 *       按一下把这台工作台连着的容器描成框（输入绿、输出红），屏幕下方报数量，再按关掉。</li>
 * </ul>
 * 批量合成和全部展开/折叠注册成「键盘键」：鼠标点击时实时查询这两个键此刻是否被按住，
 * 玩家在按键绑定界面改绑后立刻生效，不需要重开游戏。
 * 除接入总览键（世界键）外，其余都只在智能工作台界面里生效（冲突上下文是 GUI，免得在外面走路误触）。
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

    /**
     * 就地寻配方（默认 C）：鼠标停在界面下方背包的某件物品上按一下，
     * 立刻在右侧列表里找到「能做出这件东西」的那条配方、选中并翻到它面前，
     * 省得每次先去搜索框打字。键位同样可在按键绑定里改。
     */
    public static final KeyMapping CRAFT_HERE = new KeyMapping(
            "key.smart_workbench.craft_here",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    /**
     * 加入合成队列（默认 V）：鼠标停在右侧列表某项上按一下，把这件产物按「批量数量」框
     * 的个数排进队列（同配方再按就是加量）。队列最多 {@code SmartWorkbenchMenu.QUEUE_MAX_ENTRIES} 项。
     */
    public static final KeyMapping QUEUE_ADD = new KeyMapping(
            "key.smart_workbench.queue_add",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    /** 开始 / 暂停合成队列（默认 G）：按一下开，再按一下停；进度留着，按「开始」随时接上。 */
    public static final KeyMapping QUEUE_TOGGLE = new KeyMapping(
            "key.smart_workbench.queue_toggle",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    /**
     * 收藏 / 取消收藏（默认 B）：鼠标停在右侧列表某项上按一下，钉到列表最前或摘下来。
     * 原来的 Shift+右键没有作废，两个入口都一直可用。
     */
    public static final KeyMapping FAVORITE = new KeyMapping(
            "key.smart_workbench.favorite",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    /**
     * 数量数字样式切换（默认 F8）：在「0.7 倍原版字体 + 九遍描边」和「自绘 3×5 点阵」之间换。
     * 和别的键一样，玩家能在「选项 → 控制 → 按键绑定 → 智能工作台」里改成自己顺手的键；
     * 换完的那一款会写进客户端配置，下次开游戏还是它。
     */
    public static final KeyMapping TOGGLE_DIGIT_STYLE = new KeyMapping(
            "key.smart_workbench.toggle_digit_style",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY);

    /**
     * 接入总览（默认 F12）：<b>不开界面、直接在世界里按</b>——
     * 准星指着哪台智能工作台就查哪台（没指着就退回最后一次打开过的那台），
     * 取料容器描绿框、产物容器描红框（隔着墙也看得见），屏幕下方报各有多少台；
     * 再按一下关掉。键位可在「选项 → 控制 → 按键绑定 → 智能工作台」里改。
     * 冲突上下文是 IN_GAME：开着界面 / 聊天框时不误触，跟上面那批 GUI 键互不干扰。
     */
    public static final KeyMapping CONNECTION_OVERVIEW = new KeyMapping(
            "key.smart_workbench.connection_overview",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            CATEGORY);

    private ModKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(REFRESH);
        event.register(AUTO_CRAFT);
        event.register(BATCH_CRAFT);
        event.register(EXPAND_ALL);
        event.register(CRAFT_HERE);
        event.register(QUEUE_ADD);
        event.register(QUEUE_TOGGLE);
        event.register(FAVORITE);
        event.register(TOGGLE_DIGIT_STYLE);
        event.register(CONNECTION_OVERVIEW);
    }
}
