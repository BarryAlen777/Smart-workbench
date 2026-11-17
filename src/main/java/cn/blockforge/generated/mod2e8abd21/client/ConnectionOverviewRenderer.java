package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 「接入总览」（世界中按 F12）的全部画面活计，一件事一个事件：
 * <ul>
 *   <li><b>按键</b>：{@link InputEvent.Key} / {@link InputEvent.MouseButton.Pre} 上收 GLFW 的
 *       真实按下边沿（只认 PRESS，长按自动重复是 REPEAT 天然不理，r225 问题②），
 *       按下只做记账，<b>同一 tick 收尾</b>看事件定夺（r233 根治，见 {@link #onClientTick}）：
 *       原版开本地界面（背包/聊天/暂停）就发生在这一 tick 内，当场拦下；
 *       服务端才回的容器界面（箱子/村民/工作台）靠 {@link PlayerInteractEvent.RightClickBlock}
 *       这个"这枚键被原版拿去做使用了"的实时信号拦下，落地前再对一次准星
 *       （r233 新问题清单①④：总览键绑在鼠标键上时，对着方块/实体、或右键时手里拿着东西，
 *       这枚键让给原版、总览不触发，见 {@link #pressYieldedToVanilla}）——
 *       全程看事件、看指向，不猜毫秒，
 *       慢服快服行为一致（r231 问题①②的根因就是拿时间窗猜界面归属）；
 *       同一帧内的连按按<b>奇偶</b>定净效果：偶数下＝什么都没按，奇数下＝开关一次
 *       （r231 问题③④）；
 *       按一下问服务端要清单、再按一下关掉（{@link ConnectionOverview} 管账）；
 *       {@link TickEvent.ClientTickEvent} 兼管每 tick 对账（超时、换维度、退世界）；</li>
 *   <li><b>世界描框</b>：{@link RenderLevelStageEvent}，输入容器绿框、输出容器红框，
 *       和材料溯源的金框同一段、同样走 {@link SeeThroughLineBoxes} 做<b>穿墙可见</b>——
 *       箱子埋在墙后、地下也看得见，这正是"我想知道哪些箱子接了工作台"的主场
 *       （r243 实拍：老写法深度测试没真关掉，绿红框被墙挡了个严实，r244 起换方案）；
 *       清单按<b>组</b>画，大箱子这种两格拼一台的容器合成一个整框（r233：以前只框得住一半）；</li>
 *   <li><b>屏幕报数</b>：{@link RenderGuiEvent.Post}，一行小字挂在屏幕下方（准星和快捷栏之间，
 *       r238 从准星上方挪下来的，不挡视线）「工作台 (x, y, z)　输入容器 N　输出容器 M」，
 *       数字用绿/红和框对色。</li>
 * </ul>
 * 超出渲染距离的容器一律不画（关深度的框浮在几百米外只会碍眼，剔除口径和溯源一致）；
 * 报数和画框用同一个函数算（审查报告问题⑧），数字才真等于眼前的框；
 * 同一时刻人换到了别的世界，画和报数都当场停笔（每帧对维度表，不依赖 tick 清账，审查报告问题⑦）；
 * <b>任何界面开着时画框和报数都不渲染</b>（r233 问题②的兜底，见 {@link #onRenderLevel}）——
 * 总览开着时玩家去开背包/箱子，画面让位给界面，关掉界面回来框还在。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConnectionOverviewRenderer {

    /** 输入容器：绿。和界面里"绿色 = 材料够用"同族，玩家不用学就知道是能拿东西的那头。 */
    private static final float IN_R = 0.25F;
    private static final float IN_G = 0.85F;
    private static final float IN_B = 0.35F;
    /** 输出容器：红。收产物的去处，和绿框成对出现，一眼分清谁进谁出。 */
    private static final float OUT_R = 0.92F;
    private static final float OUT_G = 0.30F;
    private static final float OUT_B = 0.25F;

    /**
     * 角标比溯源金框稍大一圈（0.012 vs 0.004）：同一台箱子两边都框时（悬停配方的金框
     * 叠上总览的绿框）两种颜色的线各画各的、互相都盖不掉，不至于近处细看时只剩一种色。
     */
    private static final double INFLATE = 0.012D;

    private ConnectionOverviewRenderer() {
    }

    /**
     * 往屏幕下方那行字里接一段计数：眼前的框全部画得下就报一个数，
     * 有超出视距没画的报「可见/总数」（问题⑧的"玩家所见即所得"）。
     */
    private static void appendCount(MutableComponent line, String suffix, String allFallback,
                                    String partialFallback, int visible, int total, int color) {
        MutableComponent text = visible < total
                ? Component.translatableWithFallback("gui." + SmartWorkbenchMod.MOD_ID + ".overview."
                        + suffix + "_visible", partialFallback, visible, total)
                : Component.translatableWithFallback("gui." + SmartWorkbenchMod.MOD_ID + ".overview."
                        + suffix, allFallback, visible);
        line.append(text.withStyle(style -> style.withColor(color)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // r233 根治 A：按下只记账，同一 tick 的收尾就是落地时刻——已核实 Forge 1.20.1 的
        // Minecraft.tick() 里 handleKeybinds() 先于 ClientTickEvent END 执行，所以原版
        // 按 E/T/Esc 开的本地界面此刻必然已经开出来了：开出来了 → 这一下是被界面用掉的，
        // 整批作废；还清净 → 落地。落地延迟从押 3 tick（150ms）缩到不足一个 tick，
        // 快服的"界面迟到"和慢服的"界面赶不上窗口"两头问题一起消失。
        // r233 根治 E：一批按奇偶定净效果——偶数下＝当没按过（双击不该既开又关，
        // r231 问题③④），奇数下＝执行一次开关。
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (overviewPendingPresses > 0) {
            int presses = overviewPendingPresses;
            overviewPendingPresses = 0;
            Minecraft minecraft = Minecraft.getInstance();
            if ((presses & 1) == 1 && minecraft.screen == null
                    && minecraft.level != null && minecraft.player != null
                    && !pressYieldedToVanilla(minecraft)) {
                // r233 新问题清单①④：落地前最后看一眼准星——这枚键要是正被原版
                // 拿去交互/挖掘/使用，就让位，总览不抢（判定见 pressYieldedToVanilla）。
                ConnectionOverview.onKeyPress();
            }
        }
        // 每 tick 对账：超时、换维度、退世界的清理，与按键何时触发无关。
        ConnectionOverview.tick();
    }

    /**
     * 任何界面打开的那一刻：还押着待触发的按键就当场作废（这一下被原版拿去开界面了）。
     * r233 根治 C：不再有任何"保护期"，也绝不动已经开着的总览——玩家自己开背包、
     * 开箱子、开聊天，总览照常保留，界面一关（渲染兜底见 {@link #onRenderLevel}）
     * 框原样回来。旧版"落地后 16 tick 内一开界面就 clear"正是 r231 问题①的元凶。
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        overviewPendingPresses = 0;
    }

    /**
     * r233 根治 B：容器界面（箱子、工作台、村民交易）要等服务端回屏幕包才开，
     * 单 tick 复核看不见它——但"发起这次打开"的信号在客户端是即时的：
     * {@code MultiPlayerGameMode.useItemOn/interactAt} 处理这枚右键的同一处
     * 就触发 {@link PlayerInteractEvent.RightClickBlock} / {@link PlayerInteractEvent.EntityInteractSpecific}
     * （已核实 Forge 1.20.1 源码）。总览键和"使用物品"绑的是同一个物理按钮时，
     * 事件一响就说明这枚键被原版拿去交互了，整批 pending 当场作废——
     * <b>跟网络延迟多少毫秒无关</b>。语义从此固定：改绑右键后，对着方块/实体按＝
     * 让给原版交互（总览不触发）；空手对着空地按＝正常开总览——手里拿着东西时
     * 连对空地也让位（右键物品走"使用手持物品"，见 {@link #pressYieldedToVanilla}）。
     * 绑的是别的键（默认 F12）时这里的对不上号，一切照旧。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 单机内部服务器的同一事件也会在服务端线程上重放一遍——那与客户端按键账无关，
        // 也别在别的线程上摸 Minecraft 实例，只认客户端这一头。
        if (event.getLevel().isClientSide()) {
            dropPendingIfUseKey();
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) {
            dropPendingIfUseKey();
        }
    }

    /** 总览键此刻是否就是"使用物品"那个物理按钮（改绑后立刻按新键位对表）。 */
    private static void dropPendingIfUseKey() {
        if (overviewPendingPresses <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (ModKeyMappings.CONNECTION_OVERVIEW.getKey().equals(minecraft.options.keyUse.getKey())) {
            overviewPendingPresses = 0;
        }
    }

    /**
     * r233 新问题清单①④：落地那一刻（{@link #onClientTick} 收尾）看一眼准星正指着什么。
     * 看的是 {@code Minecraft.tick()} 本 tick 开头 {@code GameRenderer.pick()} 刚算好的
     * {@code hitResult}（字节码已核实：pick 在偏移 64、{@code handleKeybinds()} 在 335、
     * ClientTickEvent.END 在 828）——原版处理这枚点击用的就是同一份命中结果，
     * 口径天然对齐，不用为"右键物品""左键方块"这两个入口再订任何事件。
     * <p>
     * 只有总览键改绑到原版鼠标按钮时才需要让位：
     * <ul>
     *   <li>绑在"使用物品"（右键）：准星对着<b>方块/实体</b>——这枚键要被原版拿去交互
     *       （对着方块那半 {@link #onRightClickBlock} 已拦，这里是同口径兜底，顺手补上
     *       空手摸实体那条它不覆盖的路）；对着<b>空地</b>但<b>手上拿着东西</b>——原版照样会把它
     *       当"使用手持物品"发出去，书、潜影盒、随身工作台这类"右键空气就开界面"的物品全走
     *       这条路（问题①：此前挡不住，总览和界面一起开，关界面后框还挂着）。让位之后
     *       这种绑法的语义固定为：<b>空手 + 对空气</b>才开总览。嫌挤就把总览键放去 F12
     *       或鼠标侧键 4/5，下面这些判断一条都不沾；</li>
     *   <li>绑在"攻击"（左键）：准星对着<b>方块/实体</b>——这枚键是去挖/打的，作废
     *       （问题④：r233 只给右键发了通行证，左键绑动作废、挖矿顺带开关总览，现在补齐）；
     *       对着<b>空地</b>——左键在原地只是挥手臂，不占用任何界面，总览照常触发。</li>
     * </ul>
     * 普通键盘绑定（默认 F12 在内）和这两个按钮都对不上号，永远放行。
     */
    private static boolean pressYieldedToVanilla(Minecraft minecraft) {
        InputConstants.Key bound = ModKeyMappings.CONNECTION_OVERVIEW.getKey();
        boolean boundToUse = bound.equals(minecraft.options.keyUse.getKey());
        boolean boundToAttack = bound.equals(minecraft.options.keyAttack.getKey());
        if (!boundToUse && !boundToAttack) {
            return false;
        }
        HitResult hit = minecraft.hitResult;
        HitResult.Type type = hit == null ? HitResult.Type.MISS : hit.getType();
        if (type == HitResult.Type.BLOCK || type == HitResult.Type.ENTITY) {
            return true;
        }
        // 剩下的就是对空气（或够不着的远处，反正原版也打不到）：右键绑法再看一下手里——
        // 拿着东西，这枚右键就是"使用物品"的那一下，开不开界面归服务端定，总览不抢。
        return boundToUse
                && (!minecraft.player.getMainHandItem().isEmpty()
                        || !minecraft.player.getOffhandItem().isEmpty());
    }

    /**
     * 键盘按下（r225 问题②修复：轮询改事件，边沿从此是真的）。
     * <p>
     * 只认 {@code GLFW_PRESS}——GLFW 在物理层就把"首次按下"和"长按期间的系统重复
     * （{@code GLFW_REPEAT}）"分成了两种 action，这正是轮询方案怎么都补不齐的那条线：
     * 上一版逐 tick 问一次"此刻按着吗 + 有没有点击"，"长按中途的重复"和"松手后立刻
     * 又按下"拿到的信号一模一样（都是按下中 + 有点击），r220②、r223①、r225② 三条
     * 其实是同一个坑的三面。现在直接订阅输入事件：一次真实按下 = 一个 PRESS = 触发一次；
     * 长按期间全是 REPEAT，一个都不理；"松手又秒按"是第二个独立 PRESS，照样触发。
     * 顺带删掉 overviewHeld / wasDown / physicallyDown 这一整套记账——物理边沿有了，
     * 推测边沿的脚手架就不需要了。
     * <p>
     * 这同样早于原版键位处理（按 E 紧接着就开背包），所以也只记账不即发：
     * 同一 tick 收尾看事件定夺（r233，见 {@link #onClientTick}）。
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return; // 长按自动重复（REPEAT）和松手（RELEASE）都不算新的一次
        }
        InputConstants.Key bound = ModKeyMappings.CONNECTION_OVERVIEW.getKey();
        // 实时取当前绑定：玩家改绑后立刻生效，和旧的轮询口径一样。
        if (bound.getType() != InputConstants.Type.KEYSYM || event.getKey() != bound.getValue()) {
            return;
        }
        triggerOverviewIfInGame();
    }

    /**
     * 鼠标按下：接入总览键被改绑成鼠标按钮时走这条（r223 问题③的口径保留到事件方案里）。
     * <p>
     * 必须用 {@link InputEvent.MouseButton.Pre} 而不是 Post（r227 问题②）：Post 在原版界面
     * 处理完这次点击<b>之后</b>才跑——玩家点界面上"完成"这类会关界面的按钮时，界面已经先被
     * 置空，闸门 {@code IN_GAME} 当场翻成 true，这一下"关界面的点击"就被当成世界里的总览键
     * 误触发。Pre 在界面处理<b>之前</b>触发，界面还开着、闸门还是 false，天然跳过；
     * 和键盘路径（{@link InputEvent.Key} 同样早于界面处理）口径对齐。我们只读不拦，
     * 按钮该照常点的照常点。但 Pre 也早于界面打开的那一刻——右键箱子这一下，界面是
     * 之后才开的：本地那部分在同一 tick 的键位处理里开（{@link #onClientTick} 收尾看得见），
     * 容器类要等服务端回屏幕包，靠 {@link #onRightClickBlock} 的即时信号作废整批
     * （r233 根治，见 {@link #MAX_HELD_PRESSES} 处的记账模型）。
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        InputConstants.Key bound = ModKeyMappings.CONNECTION_OVERVIEW.getKey();
        if (bound.getType() != InputConstants.Type.MOUSE || event.getButton() != bound.getValue()) {
            return;
        }
        triggerOverviewIfInGame();
    }

    /** 两种设备共用的闸门：和 KeyMapping 计 click 的门槛完全同语义（IN_GAME = 没开任何界面），开着界面按不触发。 */
    private static void triggerOverviewIfInGame() {
        if (!ModKeyMappings.CONNECTION_OVERVIEW.getKeyConflictContext().isActive()) {
            return;
        }
        // r233 问题③：按<b>次数</b>记账，同一 tick 内多按的不会被并掉；上限 4（防宏，
        // 人手一个 tick 内按不出 4 下），落地时按奇偶定净效果（{@link #onClientTick}）。
        overviewPendingPresses = Math.min(overviewPendingPresses + 1, MAX_HELD_PRESSES);
    }

    /**
     * r233 起的记账模型（替换 r231 的"押 3 tick + 800ms 保护期"时间窗，根治其 ①②⑤）：
     * <ol>
     *   <li>按下 → 只记次数（这里），不触发；</li>
     *   <li><b>同一 tick 收尾</b>（{@link #onClientTick}）落地：背包/聊天/暂停这类本地界面
     *       原版就在这一 tick 的 {@code handleKeybinds()} 里开出来，看一眼 {@code screen}
     *       就够——押窗从 150ms 缩到不足一个 tick，快服上几乎无感；</li>
     *   <li>箱子/工作台/村民这类<b>服务端才回屏幕包</b>的界面，单 tick 看不见，
     *       但发起它的那枚右键在同一 tick 就触发 {@link PlayerInteractEvent.RightClickBlock} /
     *       {@link PlayerInteractEvent.EntityInteractSpecific}——总览键与"使用物品"绑同一物理
     *       按钮时，事件一响整批作废（{@link #dropPendingIfUseKey}），与延迟毫秒数无关；</li>
     *   <li><b>落地前对一次准星</b>（{@link #pressYieldedToVanilla}，r233 新问题清单①④）：
     *       对着方块/实体按＝这枚键是原版要拿去交互/挖掘的，整批作废；右键绑法下手里
     *       拿着东西也作废——"右键物品开界面"和"左键挖矿"这两个入口都不发交互事件，
     *       只有看指向才拦得住；</li>
     *   <li>落地后<b>不再有保护期</b>：玩家之后开什么界面都不动总览（{@link #onScreenOpening}），
     *       界面开着时渲染层自己让位（{@link #onRenderLevel} / {@link #onRenderGui} 的
     *       {@code screen != null} 兜底），关掉界面框原样回来。</li>
     * </ol>
     * 默认 F12 不碰任何原版界面，第 2、3、4 条都不触发，玩家只觉得响应更快了。
     */
    private static final int MAX_HELD_PRESSES = 4;
    /** 押着待落地、还没消费的按下次数（同一 tick 收尾结算，见 {@link #onClientTick}）。 */
    private static int overviewPendingPresses;

    /** 离开服务器 / 断线：总览连同"最后打开的那台"退路一起作废（审查报告问题⑥）；
     *  押着的按键同样清零——旧服的余温不许带进下一个世界。 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        overviewPendingPresses = 0;
        ConnectionOverview.onDisconnect();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // r233 根治 D：任何界面开着（背包、箱子、工作台、别的模组弹的）都不画世界框——
        // 旧版绿红框会照画在箱子界面背后透出来（r231 问题②），现在结构上不可能。
        // 总览的账不动：关掉界面回来，框原样还在（这才是 r231 想表达的行为）。
        if (minecraft.screen != null) {
            return;
        }
        // 每帧先对维度表（审查报告问题⑦）：tick 清账要等下一个端点，画框却是每帧的事——
        // 人换到新世界后的头几帧，viewMatches（含"开着吗"）挡在画之前，旧世界的框一格都不许露。
        if (minecraft.level == null || !ConnectionOverview.viewMatches(minecraft.level.dimension())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double cullSq = cullDistanceSquared(minecraft);
        List<SeeThroughLineBoxes.Box> boxes = new ArrayList<>();
        collectBoxes(boxes, ConnectionOverview.inputPositions(), camera, cullSq, IN_R, IN_G, IN_B);
        collectBoxes(boxes, ConnectionOverview.outputPositions(), camera, cullSq, OUT_R, OUT_G, OUT_B);
        if (!boxes.isEmpty()) {
            // 穿墙描框走 SeeThroughLineBoxes：r243 用户实拍绿红框被墙挡——
            // 老写法用 RenderType.LINES 攒批，提交瞬间渲染类型会把深度测试重新打开，
            // 外面那句 disableDepthTest 等于没关。怎么关深度、何时提交，那边统一管。
            SeeThroughLineBoxes.render(event.getPoseStack(), camera, boxes);
        }
    }

    /**
     * 描框/计数的共同口径：渲染距离 + 2 chunks 之外不收（那边的常量注释讲了为什么）。
     * 报数和画框用同一个函数算（审查报告问题⑧），数字才真等于眼前的框。
     */
    private static double cullDistanceSquared(Minecraft minecraft) {
        double cull = (minecraft.options.getEffectiveRenderDistance() + 2) * 16.0D;
        return cull * cull;
    }

    /** 这个坐标的方块中心离相机是否落在剔除圈里。 */
    private static boolean withinCull(BlockPos pos, Vec3 camera, double cullSq) {
        double dx = pos.getX() + 0.5 - camera.x;
        double dy = pos.getY() + 0.5 - camera.y;
        double dz = pos.getZ() + 0.5 - camera.z;
        return dx * dx + dy * dy + dz * dz <= cullSq;
    }

    /**
     * 一组 = 一整台容器（大箱子两格拼一台），把组里所有格的方块 AABB 并起来描<b>一个</b>框（r233）。
     * 剔除按锚点（组第一格）算——两格紧挨着，锚点在圈里整台就在圈里，不必逐格判。
     * 只收集不绘制，交给 {@link SeeThroughLineBoxes} 一批画完（r243 之后的分工）。
     */
    private static void collectBoxes(List<SeeThroughLineBoxes.Box> out, List<List<BlockPos>> groups,
                                     Vec3 camera, double cullSq, float r, float g, float b) {
        for (List<BlockPos> group : groups) {
            if (group.isEmpty() || !withinCull(group.get(0), camera, cullSq)) {
                continue;
            }
            AABB box = unionBox(group).inflate(INFLATE);
            out.add(new SeeThroughLineBoxes.Box(box, r, g, b));
        }
    }

    /** 组里所有方块的并集 AABB：大箱子两格并成 2x1x1 的一整台。 */
    private static AABB unionBox(List<BlockPos> group) {
        AABB union = new AABB(group.get(0));
        for (int i = 1; i < group.size(); i++) {
            union = union.minmax(new AABB(group.get(i)));
        }
        return union;
    }

    /** 会被画出来的有几<b>台</b>：和 drawBoxes 逐组同口径。 */
    private static int countDrawn(List<List<BlockPos>> groups, Vec3 camera, double cullSq) {
        int n = 0;
        for (List<BlockPos> group : groups) {
            if (!group.isEmpty() && withinCull(group.get(0), camera, cullSq)) {
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // r233 根治 D：界面开着时这行字不许盖在界面上（r231 问题②的另一半——
        // 报数那行是 RenderGuiEvent.Post 画的，会直接浮在箱子界面上）。
        if (minecraft.screen != null) {
            return;
        }
        // 维度对表同描框（问题⑦）：数字和框是一家的，要停一起停
        if (minecraft.level == null || minecraft.player == null
                || !ConnectionOverview.viewMatches(minecraft.level.dimension())) {
            return;
        }
        BlockPos bench = ConnectionOverview.benchPos();
        if (bench == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;

        // r248 问题②（r245 提的）：0.77 屏高的落点和扳手绑定/解绑的动作条提示（H-31，居中）
        // 在小 GUI 高度下会挤到一块互相盖（用户实拍图2）。现在改挂到<b>护甲条正上方</b>：
        // 原版护甲条顶边 ≈ 屏高 − 49（血条在 −39，护甲再上 10），这行字压在它上面 3px，
        // 左对齐（x=2）跟血条/护甲同一竖列，彻底躲开居中动作条的所有可能长度。
        // 数字按描框同一剔除口径现算（审查报告问题⑧）：远处没画出来的不混进"眼前的数"；
        // 分母用服务端报的<b>过滤前绑定总数</b>（r223 问题②）——绑在未加载区块里的箱子
        // 画不出框，但总数里算它一个，"可见/总数"两头都对得上事实。
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double cullSq = cullDistanceSquared(minecraft);
        List<List<BlockPos>> inputs = ConnectionOverview.inputPositions();
        List<List<BlockPos>> outputs = ConnectionOverview.outputPositions();
        int visibleIn = countDrawn(inputs, camera, cullSq);
        int visibleOut = countDrawn(outputs, camera, cullSq);
        MutableComponent line = Component.empty();
        line.append(Component.translatableWithFallback("gui." + SmartWorkbenchMod.MOD_ID + ".overview.title",
                        "工作台 (%s, %s, %s)", bench.getX(), bench.getY(), bench.getZ())
                .withStyle(style -> style.withColor(0xB0B0B0)));
        line.append(Component.literal("   "));
        appendCount(line, "inputs", "输入容器 %s", "输入容器 %s/%s",
                visibleIn, Math.max(visibleIn, ConnectionOverview.inputTotal()), 0x5ADB63);
        line.append(Component.literal("   "));
        appendCount(line, "outputs", "输出容器 %s", "输出容器 %s/%s",
                visibleOut, Math.max(visibleOut, ConnectionOverview.outputTotal()), 0xEB645F);

        int screenH = minecraft.getWindow().getGuiScaledHeight();
        int x = 2;
        // 护甲条顶边 ≈ 屏高−49；有吸收之心的话血条整摞（连带上面的护甲）再高一格 10px，
        // 照那个位置让，免得金苹果buff时字被护甲条骑脸。
        int absorptionRows = Mth.ceil(Math.max(0.0F, minecraft.player.getAbsorptionAmount()) / 20.0F);
        int y = screenH - 49 - absorptionRows * 10 - 3 - font.lineHeight;
        // 深色衬底：世界背景亮（雪原、白天天空）时白字会洗白；半透明黑垫一层就稳。
        // 衬底先画（同类型攒一批再换渲染类型），文字后画带阴影。
        int text = font.width(line);
        graphics.fill(x - 2, y - 3, x + text + 2, y + font.lineHeight + 2, 0x90101010);
        graphics.drawString(font, line, x, y, 0xFFFFFF, true);
    }
}
