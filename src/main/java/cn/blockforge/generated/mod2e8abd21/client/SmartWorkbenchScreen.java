package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.ModConfig;
import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.compat.EmiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.JeiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.ReiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftGridPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SCraftRequestPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2STraceRequestPacket;
import cn.blockforge.generated.mod2e8abd21.network.S2CTraceResponsePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.NonNullList;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 智能工作台界面：左边 3x3 合成区（照用户参考图的比例），右边 5 列可合成列表。
 * <p>
 * <b>所有坐标都来自 {@link GuiLayout}</b>，而 GuiLayout 和 GUI 贴图是 tools/gen_gui_texture.py
 * 从同一份 tools/gui_layout.json 生成的 —— 贴图上的格子框就是按这些坐标画的，两边不可能错位。
 * 想调布局请改 json 再跑脚本，不要在这里填数字。
 */
public class SmartWorkbenchScreen extends AbstractContainerScreen<SmartWorkbenchMenu> {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(SmartWorkbenchMod.MOD_ID, "textures/gui/smart_workbench.png");

    private static final int TEXTURE_W = GuiLayout.TEXTURE_WIDTH;
    private static final int TEXTURE_H = GuiLayout.TEXTURE_HEIGHT;

    /** 右侧面板与列表 */
    private static final int PANEL_X = GuiLayout.PANEL_X;
    private static final int PANEL_Y = GuiLayout.PANEL_Y;
    private static final int PANEL_W = GuiLayout.PANEL_WIDTH;
    private static final int PANEL_H = GuiLayout.PANEL_HEIGHT;

    private static final int LIST_COLS = GuiLayout.LIST_COLS;
    private static final int LIST_ROWS = GuiLayout.LIST_ROWS;
    private static final int CELL = GuiLayout.LIST_PITCH;
    private static final int LIST_X = GuiLayout.LIST_ITEM_X;
    private static final int LIST_Y = GuiLayout.LIST_ITEM_Y;

    private static final int SCROLL_X = GuiLayout.SCROLL_X;
    private static final int SCROLL_W = GuiLayout.SCROLL_WIDTH;

    private static final int BUTTON_X = GuiLayout.BTN_X;
    private static final int BUTTON_Y = GuiLayout.BTN_Y;
    private static final int BUTTON_W = GuiLayout.BTN_WIDTH;
    private static final int BUTTON_H = GuiLayout.BTN_HEIGHT;

    private static final int AUTO_BUTTON_X = GuiLayout.BTN_AUTO_X;
    private static final int AUTO_BUTTON_Y = GuiLayout.BTN_AUTO_Y;
    private static final int AUTO_BUTTON_W = GuiLayout.BTN_AUTO_WIDTH;
    private static final int AUTO_BUTTON_H = GuiLayout.BTN_AUTO_HEIGHT;
    private static final int REFILL_BUTTON_X = GuiLayout.BTN_REFILL_X;
    private static final int REFILL_BUTTON_Y = GuiLayout.BTN_REFILL_Y;
    private static final int REFILL_BUTTON_W = GuiLayout.BTN_REFILL_WIDTH;
    private static final int REFILL_BUTTON_H = GuiLayout.BTN_REFILL_HEIGHT;

    /** 合成格正下方的「清空」按钮（底色已画进贴图，这里只管高亮和文字）。 */
    private static final int CLEAR_BUTTON_X = GuiLayout.BTN_CLEAR_X;
    private static final int CLEAR_BUTTON_Y = GuiLayout.BTN_CLEAR_Y;
    private static final int CLEAR_BUTTON_W = GuiLayout.BTN_CLEAR_WIDTH;
    private static final int CLEAR_BUTTON_H = GuiLayout.BTN_CLEAR_HEIGHT;

    private static final int SEARCH_X = GuiLayout.SEARCH_X;
    private static final int SEARCH_Y = GuiLayout.SEARCH_Y;
    private static final int SEARCH_W = GuiLayout.SEARCH_WIDTH;
    private static final int SEARCH_H = GuiLayout.SEARCH_HEIGHT;

    private static final int FOLD_BUTTON_X = GuiLayout.BTN_FOLD_X;
    private static final int FOLD_BUTTON_Y = GuiLayout.BTN_FOLD_Y;
    private static final int FOLD_BUTTON_W = GuiLayout.BTN_FOLD_WIDTH;
    private static final int FOLD_BUTTON_H = GuiLayout.BTN_FOLD_HEIGHT;

    private static final int SORT_BUTTON_X = GuiLayout.BTN_SORT_X;
    private static final int SORT_BUTTON_Y = GuiLayout.BTN_SORT_Y;
    private static final int SORT_BUTTON_W = GuiLayout.BTN_SORT_WIDTH;
    private static final int SORT_BUTTON_H = GuiLayout.BTN_SORT_HEIGHT;

    /** 分类键行（宽松版）：上排 4 个（28~45）、下排 4 个（141~158）各占一行，步进都是 24；frame 是 18×18 外框左上角，图标画在 +1。 */
    private static final int TAB_TOP_X = GuiLayout.TABS_TOP_FRAME_X;
    private static final int TAB_TOP_Y = GuiLayout.TABS_TOP_FRAME_Y;
    private static final int TAB_TOP_PITCH = GuiLayout.TABS_TOP_PITCH;
    private static final int TAB_BOT_X = GuiLayout.TABS_BOTTOM_FRAME_X;
    private static final int TAB_BOT_Y = GuiLayout.TABS_BOTTOM_FRAME_Y;
    private static final int TAB_BOT_PITCH = GuiLayout.TABS_BOTTOM_PITCH;
    private static final int TAB_CELL = 18;

    // 新贴图是浅色水彩 + 深紫灰格子：浅底上的字用深色，深色格子上的字用白色，
    // 两边都补一层阴影，中文才不会发虚、也不会和贴图纹理糊在一起。
    private static final int TEXT_MAIN = GuiLayout.TEXT_COLOR;
    private static final int TEXT_DIM = GuiLayout.TEXT_DIM_COLOR;
    /** 深色格子上的文字（列表为空时的提示）。 */
    private static final int TEXT_ON_DARK = 0xFFFFFFFF;
    private static final int HOVER_BG = 0x408A86B8;
    /** 选中项的描边色（金）。 */
    private static final int SELECT_COLOR = 0xFFE0A83C;
    /** 链式可合成项的角标颜色。 */
    private static final int CHAIN_COLOR = 0xFFB07CFF;
    /** 「可换配方」角标的颜色：和链式的紫色分得开的草绿。 */
    private static final int ALT_COLOR = 0xFF8FE6A6;
    private static final int BATCH_DEFAULT = 16;
    private static final int BATCH_MAX = 1024;
    /** 同类折叠头的描边色（青），和选中的金色区分开。 */
    private static final int GROUP_COLOR = 0xFF4FC3F7;
    /** 折叠头角上的数量数字。 */
    private static final int GROUP_COUNT_COLOR = 0xFFFFFFFF;
    /**
     * 「底线」距离：像素款是 5 行（{@code pixelTopRow .. pixelTopRow + 4}），
     * 0.7 倍字体款按自己的倍率往上顶到同一条底线，贴底效果和像素款一致。
     * <p>
     * 早前那款是 1:1 不缩放（宽到必然压进邻格），之后依次试过 0.5、0.6、0.65、0.8，
     * 玩家最后挑定 0.7 倍，见 {@link #BADGE_SCALE}。
     */
    private static final int DIGIT_BASELINE_GAP = 5;
    /**
     * 空分类键的灰纱：一层半透明深色罩在图标上（画在 z=200 角标层，物品层 z=150 压不住它），
     * 玩家一眼看出"这一类现在点不动"。数量数字叠在这层纱上用灰紫，选中键不罩纱。
     */
    private static final int TAB_EMPTY_VEIL = 0xB035304A;
    /** 空分类键上的"0"。 */
    private static final int TAB_EMPTY_COUNT_COLOR = 0xFFB9B6C8;
    /**
     * 折叠头的「+/-」和数量数字的阴影色。
     * <p>
     * 上一版给它们垫了一块<b>不透明</b>的深色方底，字是清楚了，可那块底把折叠项的图标盖掉
     * 将近一半（一个数字连底约 8x10 像素，格子图标总共才 16x16），玩家看得见数字、
     * 却认不出这一堆折叠的到底是什么东西。改成给字描纯黑阴影：
     * 笔画是实心白/青、下方和左右各一道黑，落在任何颜色的图标上都有对比度，
     * 而图标只在字本身那几个像素上被让开，整体轮廓照样看得清。
     * 自绘像素角标（链式、箭头、收藏星）描整圈时也用这个色。
     */
    private static final int CHIP_OUTLINE_COLOR = 0xFF000000;
    /**
     * 搜索框底色与提示色。原版 EditBox 不聚焦时是一块纯黑，压在浅色水彩面板上很割裂；
     * 这里关掉它自带的边框，改用和右侧列表格子同族的深紫灰，白字和浅灰提示都读得清，
     * 连 EditBox 那个改不了的浅灰光标也才看得见。
     */
    private static final int SEARCH_BG = 0xFF5A5674;
    private static final int SEARCH_HINT_COLOR = 0xFFC7C5D8;
    /** 按钮悬停/按下：贴图上的按钮底色是浅灰白，高亮必须用深色半透明才看得见。 */
    private static final int BUTTON_HOVER = 0x264C4B63;
    private static final int BUTTON_PRESSED = 0x4D4C4B63;
    private static final int TEXT_PRESSED = 0xFF33324A;
    private static final int SCROLL_TRACK = GuiLayout.LINE_SOFT_COLOR;
    private static final int SCROLL_THUMB = GuiLayout.ACCENT_COLOR;

    /**
     * 队列面板每行的「浅紫卡片」配色：深面板底上一行一张淡紫小纸片，
     * 和右侧那套浅色水彩同一个族；纸片上的字全部换成深紫灰，白字留在深色区。
     */
    private static final int ROW_CARD = 0xFFDAD1F2;
    private static final int ROW_CARD_EDGE = 0xFF8E82BC;
    /** 悬停行：卡片上罩一层淡紫，比直接变白柔和。 */
    private static final int ROW_HOVER_TINT = 0x33463A78;
    /** 卡片上的物品名（完成项压淡）。 */
    private static final int ROW_TEXT = 0xFF33304A;
    private static final int ROW_TEXT_DONE = 0xFF847E9C;
    /** 卡片上的进度数字：平时灰紫、完成转绿。 */
    private static final int ROW_NUM = 0xFF5A5478;
    private static final int ROW_NUM_DONE = 0xFF2F7D4F;
    /** ▲▼ 与 × 的常态/悬停色；悬停时那一小块底。 */
    private static final int ROW_ARROW = 0xFF5A5478;
    private static final int ROW_ARROW_HOVER = 0xFF1D1A33;
    private static final int ROW_ARROW_HOVER_BG = 0x26000000;
    private static final int ROW_DEL_HOVER = 0xFFA03030;
    private static final int ROW_DEL_HOVER_BG = 0x33C03030;

    /**
     * 说明文字的 z 层。原版物品图标画在 z=150，原版提示框靠 translate(0,0,400) 浮到图标之上
     * （见 GuiGraphics.renderTooltipInternal）；我们只有站到同一个高度，文字才不会被图标压住。
     */
    private static final float TOOLTIP_Z = 400.0F;

    /**
     * 队列管理面板专用的 z 层，必须<b>高于</b>提示框的 400：
     * GUI 的深度测试是 GL_LESS，两笔同高判不过——面板和提示框都站在 400 时，
     * 先画的物品说明会在面板上"透出"一层字（用户截图里的图层问题）。
     * 抬到 600 后面板稳稳压住一切；面板自己的行提示框再 +400 到 1000，照旧浮在面板上。
     */
    private static final float QUEUE_PANEL_Z = 600.0F;

    /**
     * 折叠头角标（「+/-」和成员数）专用的 z 层：压在物品图标（z=150）之上，但仍在提示框（z=400）之下。
     * <p>
     * 上一版把角标抬到和提示框同高的 400，结果角标浮到了物品说明上面。原因在深度测试：
     * GUI 用的是 GL_LESS，只有<b>更靠前</b>的那一笔才画得进去，两笔同高（400 == 400）时判不过，
     * 后画的提示框就盖不住先画的角标。回到 200——正好和原版数量角标同层
     * （见 GuiGraphics#renderItemDecorations 里的 translate(0,0,200)）：
     * 图标盖不住它，提示框能像盖住原版角标那样正常盖住它。
     */
    private static final float GROUP_CHIP_Z = 200.0F;

    private int scroll;
    /** 刷新按钮是否正被按住，用来做原版那种"按下去"的视觉反馈。 */
    private boolean refreshPressed;
    /** 自动合成按钮是否正被按住。 */
    private boolean autoPressed;
    /** 鼠标是否悬停在"自动"/"自动补齐"/"刷新"按钮上；说明文字要留到 render 最末尾那一层才画。 */
    private boolean autoHovered;
    private boolean refillHovered;
    /** 「刷新」按钮悬停说明（含快捷键提示），画在 render 最末尾那一层。 */
    private boolean refreshHovered;
    /**
     * 这一帧是否已经把列表项的提示框画掉了。
     * 右侧列表里的图标不是真的 Slot，原版的槽位提示框管不到它们，所以由我们覆写的
     * {@link #renderTooltip} 补一份；画过就记一笔，按钮说明就不必再挤上来。
     */
    private boolean listHintDrawn;
    /* ==================== 掉帧方案 A2/A4/A5：静态文字与提示的缓存 ==================== */
    /**
     * 文案和宽度都预先量好的文字（方案 A2）。
     * 原来这些字每帧都要新建组件、走一遍排版量宽——字没变，纯属白烧 CPU。
     */
    private record Fitted(Component text, int width) {
    }
    /** 表键 → 预制文字。切语言整表作废（{@link #probeLang} 发现语言变了就清）。 */
    private final Map<String, Fitted> staticTexts = new HashMap<>();
    /** 上一次渲染时观察到的语言代码。 */
    private String probeLang;
    /** 悬停按钮说明的预制排版：提示框位置尺寸只跟语言和按钮状态有关，跟鼠标坐标无关，可以缓存（方案 A5）。 */
    private record HintLines(Object probe, List<FormattedCharSequence> lines,
                             int boxX, int boxY, int boxW, int boxH) {
    }
    private final Map<String, HintLines> hintCaches = new HashMap<>();
    /** 下面都是单槽缓存：各自带探针，探针变了才重建（语言变化时统一作废，见 {@link #refreshStatics}）。 */
    private String storageProbe;
    private Fitted storageLineText;
    private String queueStripProbe;
    private Fitted queueStripLabel;
    private Fitted inventoryLabelCache;
    private String searchHintClip;
    private String emptyHintProbe;
    private List<FormattedCharSequence> emptyHintLines = List.of();
    private List<Integer> emptyHintWidths = List.of();
    private String tabTipProbe;
    private List<Component> tabTipLines = List.of();
    private String groupTipProbe;
    private List<Component> groupTipLines = List.of();
    private List<Component> batchBoxTip;
    private String resultTip1Probe;
    private Component resultTip1;
    private String resultTip2Probe;
    private Component resultTip2;
    /** 是否正在按住右侧滚动条拖动列表。 */
    private boolean draggingScroll;
    /**
     * 客户端镜像服务端的「每 4 tick 最多处理一个重操作」冷却。
     * 以前客户端在发包前就改了选中项、放了音效，但服务端会把 200ms 内的第二个包直接丢掉，
     * 于是界面金边和合成格对不上。这里同步冷却，没轮到的点击干脆不改界面状态。
     */
    private long clientNextActionTick;
    /** 当前选中的配方（自动合成按钮 / 快捷键就作用于它）；null 表示还没选。按配方 id 记，列表过滤/折叠后也找得回来。 */
    private ResourceLocation selectedRecipe;
    /** 折叠按钮是否正被按住。 */
    private boolean foldPressed;
    /** 鼠标是否悬停在折叠按钮上。 */
    private boolean foldHovered;
    /** 「清空」按钮是否正被按住 / 悬停（按住要有原版那种下沉反馈，悬停要弹说明）。 */
    private boolean clearPressed;
    private boolean clearHovered;
    /** 搜索框：只负责过滤右侧列表，不参与网络协议。 */
    private EditBox searchBox;
    /** 批量合成次数输入框：按批量合成键时，默认做 16 次。 */
    private EditBox batchBox;
    /** 同类折叠开关，默认开（列表里同一种尾段的物品收成一项）。 */
    private boolean folding = true;
    /**
     * 列表排序方式（「排序」按钮循环切换：默认 → 按名称 → 按常用）。
     * 第一次 init 时从 {@link ClientPrefs} 恢复上次的选择，和分类一样跨会话记住。
     */
    private CraftSortMode sortMode = CraftSortMode.DEFAULT;
    /** 排序按钮是否正被按住 / 悬停。 */
    private boolean sortPressed;
    private boolean sortHovered;
    /**
     * 材料溯源：上一次因为哪条配方的悬停发过查询包。
     * 只对<b>真正变了</b>的配方发一次 C2S（服务端还有自己的节流），
     * 沿列表快速拖动鼠标时不会每帧一个包。
     */
    private ResourceLocation traceRequestedFor;
    /** 上一次发溯源查询的游戏 tick；配合「问而未答」（缓存被列表刷新作废）时隔半秒补问一次。 */
    private long lastTraceRequestTick;
    /**
     * 存储快照刚更新过，这一帧必须重问一次溯源。
     * <p>
     * 不能只靠 {@code lastTraceRequestTick = 0} 打回「刚开局」来触发：缓存里还有旧答案时
     * {@code unanswered} 不成立，{@code stale} 又要等世界时间跨过 {@link #TRACE_REFRESH_TICKS}——
     * 世界刚开（gameTime 还不到 40）时就会拖到第 40 tick 才发，最长慢 2 秒。
     * 这个旗标让「快照到了」本身成为一次明确的强制重问，发出去就清掉。
     */
    private boolean traceRefreshPending;
    /**
     * 悬停同一条配方时的重问间隔（tick）。溯源结果按配方缓存在菜单里，
     * 只有列表刷新才作废；可玩家可能在悬停期间往箱子里塞/拿材料，那时列表未必变，
     * 提示框的「在哪、几个」就会一直是旧的。每 2 秒重问一次，让数字跟上眼前的世界。
     */
    private static final long TRACE_REFRESH_TICKS = 40L;
    /**
     * 当前分类过滤档（八个键里点亮的那一档）。
     * 第一次 init 时从 {@link ClientPrefs} 恢复上次的选择；之后窗口重排（resize 会重跑 init）
     * 不再覆盖，免得玩家刚点的键被一次改窗口大小冲掉。
     */
    private CraftCategory category = CraftCategory.ALL;
    /**
     * 「建筑」轮换键眼下显示哪一档（0 建筑方块 / 1 染色方块 / 2 自然方块）。
     * 只有这一个轮换键，所以一个下标就够；记住的分类落在轮换组里时，恢复会顺带把指针摆到那一档。
     */
    private int buildingCycle;
    /** 这个界面实例是否已经从配置文件恢复过分类（只在第一次 init 时读）。 */
    private boolean categoryRestored;
    /** 每个分类当前有多少项（按搜索过滤后统计；键上的小角标和置灰看这个）。 */
    private Map<CraftCategory, Integer> categoryCounts = Map.of();
    /**
     * 恢复的分类这局是否已经"验过"：列表第一次有货时，若恢复的那类正好是空的，
     * 就安静地回到「全部」（只改这一次显示，不改玩家记下的那个偏好）。
     */
    private boolean categoryChecked;
    /** 已经点开、正在展开显示成员的分类。 */
    private final Set<String> expandedGroups = new HashSet<>();
    /** 过滤 + 折叠之后真正要画的一格一格。 */
    private List<CraftableListView.Cell> display = List.of();
    /** display 是否需要按当前搜索词 / 折叠状态重算。 */
    private boolean displayDirty = true;
    /** display 是照着哪一份 craftables 算的，换了对象就重算。 */
    private List<CraftableEntry> displaySource;
    /**
     * 玩家右键换过的配方：产物 → 选用的那份配方 ID。
     * <p>
     * 同一件产物有好几份材料不同的配方时，服务端只发一条列表项（外加候选清单），
     * 具体在用哪一份由玩家在这个界面里临时决定，不用回服务端——点哪份配方取料，
     * 发出去的就是当前选中的那个 ID。列表刷新后仍然记得住：按产物的身份找回来，
     * 那份配方没了就自动回到默认的。
     */
    private final Map<String, ResourceLocation> chosenRecipes = new HashMap<>();
    /** 把候选换成玩家选中那份之后的列表，提示框和快捷键都从这里找。 */
    private List<CraftableEntry> effectiveCraftables = List.of();

    /* ==================== 合成队列（界面侧） ==================== */

    /** 队列面板是否展开：盖在界面正中的管理窗，Esc、点窗内 ×、点窗外都能关。 */
    private boolean queuePanelOpen;
    /** 队列状态条（「物品栏」那行右侧的 114×13）悬停中——最上层说明文字用它。 */
    private boolean queueStripHovered;
    /** 每帧记下鼠标位置：按队列键 / 收藏键要知道此刻停在哪个列表项上。 */
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    /** 上次看到的每项进度：判断「这一项刚做完」放一声轻响用。 */
    private final Map<Integer, Integer> queueDoneWatch = new HashMap<>();
    /** 上一刻的队列状态：切换到「全部完成」时放一声收尾音。 */
    private int lastQueueState = SmartWorkbenchMenu.QUEUE_STATE_IDLE;
    /**
     * 双击数量弹出的就地输入框（问题 3：要做大数量时不用再滚轮滚半天）。
     * 编辑期间行不会挪位——面板开着时一切点击要么先提交数字、要么被吞掉，
     * 所以开框那一刻记下坐标、画到关框为止。同一时刻至多有一个在编辑。
     */
    private EditBox queueQtyBox;
    private int queueQtyEntryId = -1;
    private int queueQtyX;
    private int queueQtyY;
    /**
     * 上一次点数量区的时间/项（手动双击检测：1.20.1 的 Screen 没有 doubleMouseDown 回调，
     * 只能按「同一项 400 毫秒内两下左键」自己认）。
     */
    private int lastQtyClickId = -1;
    private long lastQtyClickMs;
    private static final long DOUBLE_CLICK_MS = 400L;
    /**
     * 列表项提示框缓存：buildTooltip 一次要翻材料表、拼十几个组件，
     * 悬停时原来<b>每帧</b>重来一遍（几百 fps 下纯烧 CPU）。现在只有
     * 悬停配方、溯源回包（列表换了对象）、可合成列表、收藏/计数（uiVersion）
     * 任何一个真变了才重建。
     */
    private ResourceLocation tipCacheRecipe;
    private List<S2CTraceResponsePacket.MaterialHit> tipCacheTrace;
    private List<CraftableEntry> tipCacheSource;
    private int tipCacheUi = -1;
    private int uiVersion;
    private List<Component> tipCacheLines = List.of();

    /* ==================== 操作说明面板 ==================== */

    /** 问号按钮：画在左侧工作台标题栏的左上角（合成格上方的空位），点开/收起说明面板。 */
    private static final int HELP_BTN_X = 3;
    private static final int HELP_BTN_Y = 2;
    private static final int HELP_BTN_SIZE = 11;
    /** 按钮底色：深紫小牌，压在浅色标题栏上，一眼是个"键"。 */
    private static final int HELP_BTN_BG = 0xFF3B3357;
    /** 常态边框：淡紫色；悬停和点开：金色（和选中框同色）。 */
    private static final int HELP_BTN_EDGE = 0xFFB9A6E8;
    private static final int HELP_BTN_EDGE_LIT = 0xFFE0A83C;
    /** 问号字形：淡黄色。 */
    private static final int HELP_BTN_GLYPH = 0xFFF4E2A2;
    /** 滑出形态的面板宽（右边缘紧贴工作台左侧）；左边空位不够时自动改成盖在界面正中的浮层。 */
    private static final int HELP_PANEL_W = 140;
    /** 面板正文区左右内边距。 */
    private static final int HELP_PAD = 5;
    /** 正文缩排字号：原版只有一档字，这里用 0.8 倍缩放模拟"小一号"。 */
    private static final float HELP_TEXT_SCALE = 0.8F;

    /** 操作说明面板是否展开。 */
    private boolean helpPanelOpen;
    /** 面板正文滚动位置（像素）。 */
    private int helpScroll;
    /** 面板内容：点开那一刻拼一次，平时不重建（悬停框里的固定段落挪到了这里）。 */
    private List<HelpRow> helpLines = List.of();
    /** 面板一行：正文 + 是否分组标题（标题前画分隔横线，不编序号）。 */
    private record HelpRow(Component text, boolean header) {}
    /**
     * 正文<b>排版缓存</b>（掉帧问题 3）：19 条文字逐条折行是一次 40 行的量字断行活，
     * 以前每帧重摊一遍再扔掉，面板一开掉几十帧。现在点开或折行宽度变了才重排，
     * 每帧只照缓存画、视口外的行整行跳过。
     */
    private List<HelpDispRow> helpDispRows = List.of();
    /** 缓存正文的总高（像素），滚动范围 = 总高 - 视口高。 */
    private int helpDispTotalH;
    /** 上次排版用的折行宽度；-1 表示缓存作废（重开面板就置 -1）。 */
    private int helpLayoutWidth = -1;
    /** 缓存里的一行显示行：折好的一段字 + 是否组标题 + 是否这条目的首行。 */
    private record HelpDispRow(FormattedCharSequence seq, boolean header, boolean first) {}
    /** 浮层形态右上角关闭 × 是否悬停中。 */
    private boolean helpCloseHovered;
    /** 问号按钮是否悬停中（悬停变金框 + 弹一句说明）。 */
    private boolean helpBtnHovered;
    /** 正文右缘按住滚动条滑块拖动中。 */
    private boolean helpScrollDragging;
    /** 滚动条轨道/滑块的几何，画正文时顺手记下来，鼠标事件拿来判命中和换算位置。 */
    private int helpBarTrackX;
    private int helpBarTrackTop;
    private int helpBarTrackH;
    private int helpBarThumbH;
    private int helpBarTotalH;
    private int helpBarVisibleH;
    /**
     * 画正文时真实用的视口高（{@code bottom - top}）。
     * 滚轮原来是「事件发生时按面板几何再算一遍视口高」，两套算法一旦差一点，
     * 滚轮就会算出「不用滚」。现在统一以这里记下的值为准，见 {@link #helpMaxScroll()}。
     */
    private int helpViewH;
    /**
     * 这一记滚轮已经被事件总线的抢先层（{@link HelpWheelGuard}）滚过了。
     * {@link #mouseScrolled} 见到这个标记就原样吞掉，绝不二次滚动。
     */
    private boolean helpWheelCaught;
    /**
     * 排查用诊断（写进 logs/latest.log）：面板每展开一次，
     * 「总线」和「界面」两层各收到第一记滚轮时写一行。两行对比就能看出
     * 滚轮是不是在路上被整合包里别的模组截走——只剩一行、或一行都没有，都有说法。
     */
    private boolean helpBusProbeLogged;
    private boolean helpGuiProbeLogged;

    /** 队列状态条：「物品栏」三个字右侧那段空格（右边沿到背包行最右格前）。 */
    private static final int QS_X = 38;
    private static final int QS_Y = 85;
    private static final int QS_W = 114;
    private static final int QS_H = 13;
    /** 队列面板：176×160，盖在整个界面正中。 */
    private static final int QP_W = 176;
    private static final int QP_H = 160;
    /** 面板里队列行的起点和行高（六行正好到页脚按钮前：20 + 6×20 = 140，页脚从 145 开始）。 */
    private static final int QP_ROW_Y = 20;
    /**
     * 队列行里文字/按钮的额外 z：物品图标是单独一批画的，站在 150（这里 +600 = 750），
     * 超出 16 像素格子的模型会伸到文字上；文字抬到 760 就永远在图标之上，
     * 又仍低于「做完」的深色罩 + ✓（+199 → 799）。
     */
    private static final float QP_ROW_TEXT_Z = 160.0F;
    private static final int QP_ROW_H = 20;
    /** 双击可编辑的数量区：进度数字所在那一小块（右边挨着 ▲）。 */
    private static final int QEB_X_OFF = 88;
    private static final int QEB_W = 38;
    private static final int QEB_H = 12;

    public SmartWorkbenchScreen(SmartWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TEXTURE_W;
        this.imageHeight = TEXTURE_H;
        this.titleLabelX = GuiLayout.LABEL_TITLE_X;
        this.titleLabelY = GuiLayout.LABEL_TITLE_Y;
        this.inventoryLabelX = GuiLayout.LABEL_INVENTORY_X;
        this.inventoryLabelY = GuiLayout.LABEL_INVENTORY_Y;
        // 装了 REI / JEI / EMI 的话，顺手把我们的配方转移处理器挂上（没装就静默跳过）
        ensureRecipeViewerBridges();
        // 记一笔"最后打开的是这台"：关掉界面后在世界上按 F12 查接入总览，
        // 准星没指着工作台时就退回这里记的那台（见 ConnectionOverview.pickBench）。
        ConnectionOverview.rememberBench(menu.getBenchPos());
    }

    /** 三套物品管理器都靠 compat 包里的反射桥挂处理器；没装的那家会自己静默跳过。 */
    private void ensureRecipeViewerBridges() {
        ReiTransferBridge.ensureRegistered();
        JeiTransferBridge.ensureRegistered();
        EmiTransferBridge.ensureRegistered(this.menu);
    }

    /**
     * 建搜索框。坐标同样来自 GuiLayout，和贴图一个来源。
     * 搜索框不挂进 vanilla 的 widget 列表，而是我们自己渲染、自己转发鼠标和键盘：
     * 这样它的层级、焦点、还有"打字时别触发刷新快捷键"都好控制。
     */
    @Override
    protected void init() {
        super.init();
        String previous = this.searchBox == null ? "" : this.searchBox.getValue();
        this.searchBox = new EditBox(this.font,
                this.leftPos + SEARCH_X, this.topPos + SEARCH_Y, SEARCH_W, SEARCH_H,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".search.hint", "搜索 / 拼音"));
        this.searchBox.setMaxLength(32);
        // 边框/底色、提示文字都由我们自己画（见 drawSearchBoxFrame）：原版那套黑底配浅色面板太丑。
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setHint(Component.empty());
        this.searchBox.setValue(previous);
        this.searchBox.setResponder(text -> this.displayDirty = true);

        String previousBatch = this.batchBox == null ? "" : this.batchBox.getValue();
        this.batchBox = new EditBox(this.font,
                this.leftPos + GuiLayout.BATCH_INPUT_X, this.topPos + GuiLayout.BATCH_INPUT_Y,
                GuiLayout.BATCH_INPUT_WIDTH, GuiLayout.BATCH_INPUT_HEIGHT,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".label.batch_count", "批量数量"));
        this.batchBox.setMaxLength(4);
        this.batchBox.setBordered(false);
        this.batchBox.setTextColor(0xFFFFFFFF);
        this.batchBox.setHint(Component.empty());
        this.batchBox.setFilter(text -> text.matches("[0-9]*"));
        this.batchBox.setValue(previousBatch.isEmpty() ? String.valueOf(BATCH_DEFAULT) : previousBatch);
        // 第一次打开这个界面：把上次点的分类键和排序方式恢复出来，并预热分类查找表（原版创造标签页
        // 的内容这时才保证构建好）。窗口改大小会重跑 init，恢复和预热都只认第一次。
        if (!this.categoryRestored) {
            this.categoryRestored = true;
            this.category = ClientPrefs.lastCategory();
            // 记住的正好是轮换组里的一档（染色/自然方块）：指针跟着摆过去，重开还在原来那一档。
            int cycle = this.category.cycleIndex();
            this.buildingCycle = Math.max(0, cycle);
            this.sortMode = ClientPrefs.lastSortMode();
            CraftCategory.onScreenOpen();
        }
        this.displayDirty = true;
        this.displaySource = null;
        // 改窗口大小会重跑 init：界面原点变了，正开着的数量框要跟着挪到同一项那一行（按 id 找）。
        repositionQueueQtyEdit();
        // 跨会话记住的合成队列：开界面就把账本推给服务端（空的不用推，服务端本来就没有）
        if (!CraftQueue.isEmpty()) {
            CraftQueue.syncToServer(this.menu);
        }
    }

    /** 物品管理器可能比界面晚准备好，或中途重载；每 tick 补挂一次，已经在就只做一次字段比较。 */
    @Override
    protected void containerTick() {
        super.containerTick();
        ensureRecipeViewerBridges();
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        if (this.batchBox != null) {
            this.batchBox.tick();
        }
        if (this.queueQtyBox != null) {
            this.queueQtyBox.tick();
        }
        ensureDisplay();
        watchQueueSounds();
    }

    /** 队列进度回声：某项刚跨过目标 → 一声轻响；整队做完 → 一颗经验球的动静。 */
    private void watchQueueSounds() {
        if (this.minecraft == null || this.minecraft.getSoundManager() == null) {
            return;
        }
        for (CraftQueue.Entry entry : CraftQueue.entries()) {
            int prev = this.queueDoneWatch.getOrDefault(entry.id, 0);
            if (prev < entry.target && entry.done >= entry.target) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.3F, 0.7F));
            }
            this.queueDoneWatch.put(entry.id, entry.done);
        }
        int state = this.menu.getClientQueueState();
        if (state == SmartWorkbenchMenu.QUEUE_STATE_FINISHED
                && this.lastQueueState != SmartWorkbenchMenu.QUEUE_STATE_FINISHED) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.8F));
        }
        this.lastQueueState = state;
    }

    /** 搜索词、折叠状态或分类变了才重算列表，平时就用上一帧的结果。 */
    private void ensureDisplay() {
        List<CraftableEntry> source = this.menu.getCraftables();
        if (!this.displayDirty && source == this.displaySource) {
            return;
        }
        this.displaySource = source;
        this.displayDirty = false;
        this.effectiveCraftables = applyChoices(source);
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        CraftableListView.View view = CraftableListView.build(this.effectiveCraftables, query,
                this.folding, this.expandedGroups, this.category, this.sortMode);
        // 记住的分类在这台工作台眼下没货（刚做完一顿红石、这次箱子里只剩建材）：
        // 列表第一次拿到货时安静地回到「全部」看一眼。偏好文件不动，
        // 那类东西回来之后，下次打开还是回到那一类。
        if (!this.categoryChecked && this.category != CraftCategory.ALL
                && view.count(CraftCategory.ALL) > 0) {
            this.categoryChecked = true;
            if (view.count(this.category) == 0) {
                CraftCategory fallback = CraftCategory.ALL;
                // 记住的是「建筑」轮换组里的一档：先试同组别的档，组里全空才回「全部」。
                if (this.category.cycleIndex() >= 0) {
                    for (CraftCategory sub : CraftCategory.Key.KEY_BUILDING.subs) {
                        if (view.count(sub) > 0) {
                            fallback = sub;
                            this.buildingCycle = sub.cycleIndex();
                            break;
                        }
                    }
                }
                if (fallback != this.category) {
                    this.category = fallback;
                    view = CraftableListView.build(this.effectiveCraftables, query,
                            this.folding, this.expandedGroups, this.category, this.sortMode);
                }
            }
        }
        this.display = view.cells();
        this.categoryCounts = view.categoryCounts();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    /**
     * 把玩家右键选过的配方套到列表项上：换过去的那份变成这一项「当前用的配方」，
     * 图标不变、材料清单和取料走的配方都跟着换。选过的那份配方这次没了（材料变化、
     * 模组重载）就退回服务端给的默认那份。
     */
    private List<CraftableEntry> applyChoices(List<CraftableEntry> source) {
        if (this.chosenRecipes.isEmpty()) {
            return source;
        }
        List<CraftableEntry> out = new ArrayList<>(source.size());
        for (CraftableEntry entry : source) {
            out.add(entry.hasAlternatives()
                    ? entry.withActive(this.chosenRecipes.get(entry.resultKey()))
                    : entry);
        }
        return out;
    }

    /**
     * 右键列表项：这件产物有好几份材料不同的配方，换用下一份。
     * <p>
     * 为什么自己做这个：多态合成（Polymorph）那个产物格旁的小按钮管的是
     * <b>同一把材料能出哪几个不同产物</b>；我们这里正相反——<b>同一个产物有几套不同材料</b>，
     * 摆好料之后永远只有一份配方匹配，多态合成压根不会冒出按钮。所以切换只能在这份列表里做。
     */
    private void cycleRecipe(CraftableEntry entry) {
        CraftableEntry next = entry.cycled();
        if (next.recipeId().equals(entry.recipeId())) {
            return;
        }
        this.chosenRecipes.put(entry.resultKey(), next.recipeId());
        // 选中项描边按配方 id 记，跟着换过去，免得换完配方金边就跑没了
        if (this.selectedRecipe != null && this.selectedRecipe.equals(entry.recipeId())) {
            this.selectedRecipe = next.recipeId();
        }
        this.displayDirty = true;
        ensureDisplay();
        playClickSound();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.recipe_switched",
                            "换用配方：%s（这件产物共 %s 份配方，右键继续换）",
                            next.recipeId(), entry.optionCount()), true);
        }
    }

    @Override
    public void removed() {
        super.removed();
        EmiTransferBridge.clearActiveMenu();
        cancelQueueQtyEdit();
        // 关掉界面，金框不再立刻熄灭：再亮配置的几秒（0 = 旧行为，关界面立灭）。
        // 镜头在界面里转不动，想绕到箱子背后看一眼，就得先关界面——以前一关框就没了，
        // 等于白指（用户问题 6）。续命期间到期自然熄灭，重开界面悬停会整批对表。
        // 这句提示不再单独弹字（用户嫌解释太细），功能照旧静默生效。
        TraceHighlights.holdAfterClose(ModConfig.traceHighlightSeconds());
        // 节流攒下的脏账（收藏计数、队列进度）在窗口还开着时才会顺带落盘，
        // 关窗这一刻必须补一次，不然最后几秒的账就丢了
        CraftQueue.flushSave();
        Favorites.flush();
    }

    /** 列表按行滚动：最多能往下滚几行。 */
    private int maxScroll() {
        int rows = (this.display.size() + LIST_COLS - 1) / LIST_COLS;
        return Math.max(0, rows - LIST_ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 关键：必须把贴图真实尺寸传给 blit。7 参数的 blit 内部按 256x256 换算 UV，
        // 我们这张是 292x166，于是整张贴图会被纵向拉伸 256/166≈1.54 倍、右边裁掉 12%，
        // 每一个格子都会跟着偏——这就是之前"重画贴图还是错位"的真正原因。
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);

        ensureDisplay();
        refreshStatics();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());

        // 上移 1 像素：贴图在标题区下沿（y=26）有一条装饰横线，字贴太近会像被线切掉。
        // 标题按用户要求左移，右边整段让给搜索框（坐标都在 GuiLayout 里）。
        // 标题可用宽度只到搜索框左边缘，英文「Craftable」放不下时就等比缩小，不压到搜索框上。
        // 标题、搜索框、批量数量框这一行全是「底 fill + 字」，不和任何物品图标交叠：
        // 整段攒进一个 drawManaged（掉帧方案 A4）。EditBox 也放进批次里——1.20.1 的
        // 它内部只按宽度截字符串、不依赖裁剪框，批内渲染安全（A5）；没焦点又空着时
        // 干脆连调用都省了（本来就画不出任何东西）。
        graphics.drawManaged(() -> {
            drawCentredFitted(graphics, fitted("gui." + SmartWorkbenchMod.MOD_ID + ".panel.craftable", "可合成"),
                    this.leftPos + GuiLayout.LIST_TITLE_X, this.topPos + GuiLayout.LIST_TITLE_Y - 1, TEXT_MAIN,
                    // 标题居中在 LIST_TITLE_X，左右两边到面板边缘/搜索框的距离取小的一边
                    2 * Math.min(GuiLayout.LIST_TITLE_X - GuiLayout.PANEL_X,
                            GuiLayout.SEARCH_X - GuiLayout.LIST_TITLE_X) - 2);
            // 搜索框画在背景层：它只占标题行，不和任何物品格重叠，不会挡东西
            if (this.searchBox != null) {
                drawSearchBoxFrame(graphics);
                if (this.searchBox.isFocused() || !this.searchBox.getValue().isEmpty()) {
                    this.searchBox.render(graphics, mouseX, mouseY, partialTick);
                }
            }
            drawBatchCountBox(graphics, mouseX, mouseY, partialTick);
        });

        // 分类键：上排 4 个（全部/建筑轮换/功能/红石）在列表头顶，下排 4 个（工具/战斗/食物/其他）
        // 单独占列表底下一行，功能按钮再往下一行。先铺高亮和图标，数量角标和空类灰纱随后叠到角标层。
        drawCategoryTabs(graphics, mouseX, mouseY);

        // 5x5 的配方图标。
        // 露出来的这一窗格子下标是连续的（row*COLS+col），所以从 scroll*COLS 往后数就行；
        // 第一遍只画每格的底和高亮加物品图标，角标（像素图标和数字）留到第二遍一起画（见 drawCellBadges）。
        int hovered = cellIndexAt(mouseX, mouseY);
        int firstCell = this.scroll * LIST_COLS;
        int visibleCells = Math.max(0,
                Math.min(this.display.size() - firstCell, LIST_ROWS * LIST_COLS));
        int lastCell = firstCell + visibleCells;
        // 分两遍画：悬停底和金边先在 drawManaged 里攒成一批提交，物品图标再逐格画。
        // 混在一起画的话每格的 fill 都会被后面的 renderItem 冲掉一次批次（掉帧问题 5）。
        graphics.drawManaged(() -> {
            for (int index = firstCell; index < lastCell; index++) {
                CraftableListView.Cell cell = this.display.get(index);
                int offset = index - firstCell;
                int x = this.leftPos + LIST_X + (offset % LIST_COLS) * CELL;
                int y = this.topPos + LIST_Y + (offset / LIST_COLS) * CELL;
                if (hovered == index) {
                    // 贴图上的格子框是 18x18，左上角比物品坐标各小 1 像素，高亮正好盖住整格
                    graphics.fill(x - 1, y - 1, x + CELL - 1, y + CELL - 1, HOVER_BG);
                }
                if (!cell.header() && this.selectedRecipe != null
                        && this.selectedRecipe.equals(cell.entry().recipeId())) {
                    // 选中的那格描一圈金边：自动合成按钮和快捷键要作用在它身上
                    graphics.renderOutline(x - 1, y - 1, CELL, CELL, SELECT_COLOR);
                }
            }
        });
        for (int index = firstCell; index < lastCell; index++) {
            CraftableListView.Cell cell = this.display.get(index);
            int offset = index - firstCell;
            int x = this.leftPos + LIST_X + (offset % LIST_COLS) * CELL;
            int y = this.topPos + LIST_Y + (offset / LIST_COLS) * CELL;
            graphics.renderItem(cell.entry().result(), x, y);
        }
        drawCellBadges(graphics, firstCell, lastCell);
        drawCategoryTabOverlays(graphics);

        if (this.display.isEmpty()) {
            drawEmptyHint(graphics);
        }

        // 滚动条（贴图已画好轨道，这里只画滑块）
        int maxScroll = maxScroll();
        boolean refreshHovered = isPointInButton(mouseX, mouseY);
        boolean autoHovered = isPointInAutoButton(mouseX, mouseY);
        boolean refillHovered = isPointInRefillButton(mouseX, mouseY);
        boolean foldHovered = isPointInFoldButton(mouseX, mouseY);
        boolean sortHovered = isPointInSortButton(mouseX, mouseY);
        boolean clearHovered = isPointInClearButton(mouseX, mouseY);
        // 滚动条滑块 + 六个功能按钮 + 存储计数行 + 队列状态条：这一段全是底和高亮文字、
        // 不再和任何物品图标交叠，整块攒进一个 drawManaged —— 原来这里每帧要十几次
        // "画字即提交"（drawString 的 flushIfUnmanaged），是界面掉帧的大头之一。
        graphics.drawManaged(() -> {
            if (maxScroll > 0) {
                int trackTop = this.topPos + LIST_Y - 1;
                int trackHeight = LIST_ROWS * CELL;
                int thumbHeight = Math.max(10, (trackHeight - 2) * LIST_ROWS / (maxScroll + LIST_ROWS));
                int thumbTop = trackTop + 1 + (trackHeight - 2 - thumbHeight) * this.scroll / maxScroll;
                graphics.fill(this.leftPos + SCROLL_X + 1, thumbTop,
                        this.leftPos + SCROLL_X + SCROLL_W - 1, thumbTop + thumbHeight, SCROLL_THUMB);
                graphics.fill(this.leftPos + SCROLL_X + 1, thumbTop,
                        this.leftPos + SCROLL_X + SCROLL_W - 1, thumbTop + 1, SCROLL_TRACK);
            }

            // 四个功能按钮：宽松版恢复长条、写全称（刷新/自动/折叠/排序），自己占一行；
            // 悬停说明仍然保留（还带快捷键）。底色已经画进贴图，这里补高亮和文字。
            // 按钮文案是静态的，走 {@link #fitted} 缓存（掉帧方案 A2），不再每帧现做组件现量宽。
            drawButton(graphics, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, refreshHovered,
                    this.refreshPressed, fitted("gui." + SmartWorkbenchMod.MOD_ID + ".button.refresh", "刷新"));
            drawButton(graphics, AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H,
                    autoHovered, this.autoPressed,
                    fitted("gui." + SmartWorkbenchMod.MOD_ID + ".button.auto", "自动"));
            drawButton(graphics, FOLD_BUTTON_X, FOLD_BUTTON_Y, FOLD_BUTTON_W, FOLD_BUTTON_H,
                    foldHovered, this.foldPressed,
                    fitted("gui." + SmartWorkbenchMod.MOD_ID + ".button.fold", "折叠"));
            if (this.folding) {
                // 按钮本身没有"开/关"字样，用金边表示折叠正开着
                graphics.renderOutline(this.leftPos + FOLD_BUTTON_X, this.topPos + FOLD_BUTTON_Y,
                        FOLD_BUTTON_W, FOLD_BUTTON_H, SELECT_COLOR);
            }
            // 排序按钮：字就是当前档位（默认/名称/常用），点一下换下一档；非默认档描金边提醒列表换过序了
            drawButton(graphics, SORT_BUTTON_X, SORT_BUTTON_Y, SORT_BUTTON_W, SORT_BUTTON_H,
                    sortHovered, this.sortPressed,
                    fitted(this.sortMode.langKey(), this.sortMode.fallbackName()));
            if (this.sortMode != CraftSortMode.DEFAULT) {
                graphics.renderOutline(this.leftPos + SORT_BUTTON_X, this.topPos + SORT_BUTTON_Y,
                        SORT_BUTTON_W, SORT_BUTTON_H, SELECT_COLOR);
            }
            drawButton(graphics, REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H,
                    refillHovered, false,
                    fitted(this.menu.isAutoRefillEnabled()
                                    ? "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_on"
                                    : "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_off",
                            this.menu.isAutoRefillEnabled() ? "自动补齐：开" : "自动补齐：关"));
            // 「清空」：格子里没料时把字压淡，一眼就知道这一按不会有任何东西退回去
            drawButton(graphics, CLEAR_BUTTON_X, CLEAR_BUTTON_Y, CLEAR_BUTTON_W, CLEAR_BUTTON_H,
                    clearHovered, this.clearPressed,
                    fitted("gui." + SmartWorkbenchMod.MOD_ID + ".button.clear", "清空"),
                    isCraftGridEmpty() ? TEXT_DIM : TEXT_MAIN);
            // 按钮的说明文字不在这里画：renderBg 是背景层，画在这里会被上面的物品图标
            // 和数量角标压住（之前"自动合成"的说明就是这么糊在图标上的）。真正的最上层是
            // render 的末尾（所有图标、提示框都画完、并且把 z 抬到 TOOLTIP_Z 之后），那里才画按钮说明。
            this.autoHovered = autoHovered;
            this.refillHovered = refillHovered;
            this.foldHovered = foldHovered;
            this.sortHovered = sortHovered;
            this.clearHovered = clearHovered;
            this.refreshHovered = refreshHovered;

            // 接入存储 / 输出容器计数：宽松版恢复整行全称「接入存储: N  输出: N」，
            // 单独占面板最底下一行，一眼看懂，不用悬停。英文等长译文放不下 100 像素时等比缩小。
            // 整行字只有两个数字会变：数字没变就连组件带宽度一起沿用上一帧的（方案 A2）。
            drawLeftFitted(graphics, storageLine(),
                    this.leftPos + GuiLayout.STORAGE_X, this.topPos + GuiLayout.STORAGE_Y, TEXT_DIM,
                    GuiLayout.STORAGE_WIDTH);

            // 队列状态条：「物品栏」那行右侧的 114×13，队里排了东西才现身。
            drawQueueStrip(graphics, mouseX, mouseY);
            // 问号按钮：工作台标题栏左上角，点开/收起操作说明面板。
            // 浮层形态下面板自己会在暗层之上重画一次，这里照旧画底一份，省得再分叉。
            drawHelpButton(graphics, mouseX, mouseY);
        });

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 按队列键 / 收藏键要认「此刻鼠标停在哪个列表项上」：每帧把坐标记下来。
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        // 节流攒下的脏账在这找个顺手的时间点落盘：够久了才真写，不脏直接返回。
        CraftQueue.flushIfDue();
        Favorites.flushIfDue();
        // renderBg 里会把这两个悬停状态算好；这里先清标记。
        this.listHintDrawn = false;
        // super.render 依次画：背景贴图 → 列表 → 玩家背包里的格子。
        // 注意：AbstractContainerScreen.render 自己【不会】画物品提示框——1.20.1 里
        // 箱子、发射器这些原版界面，都是各自在自己的 render 里再补一次 renderTooltip。
        // 我们之前漏了这一句，所以整个界面（背包、合成格、右侧列表）鼠标移上去全都没有提示框。
        super.render(graphics, mouseX, mouseY, partialTick);
        // 光把调用顺序排到最后还不够：GuiGraphics 是按批次提交的，先提交的批次先画出来。
        // 这里先把这一帧已经排队的图标、数量角标统统提交掉，再画提示框和说明文字，
        // 保证它们落在图标之后的那一批里。
        graphics.flush();
        // 「物品栏」三个字放在这里：父类的 renderLabels 已经画过一轮，别的模组要往容器界面上
        // 叠东西也叠完了（它们挂在 Forge 的 ContainerScreenEvent.Render.Foreground 上，
        // 在 super.render 内部就执行完了）。我们这时候再画，就一定在最上层——之前那版字画在
        // renderLabels 里，真机上整行都看不见，这就是兜底。
        drawInventoryLabel(graphics);
        if (this.queuePanelOpen) {
            // 队列面板开着：它就是这一帧的主角。底下的物品提示框、按钮说明统统不画——
            // 以前面板和提示框同站 z=400，GL_LESS 同深判不过，先画的物品说明会"透出"面板
            // （用户截图里的图层问题）；现在面板抬到 z=600 稳稳压住一切，底下那两层干脆省掉，
            // 顺带把长文案的逐帧排版也免了（掉帧问题 5 的一环）。
            // 这里的 flush 删掉了（复核报告隐患 4）：drawQueuePanel 第一个 drawManaged
            // 入口自己就会先 flush，这一句只是多一次空提交。
            drawQueuePanel(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // 补上原版那一句：画本次悬停的物品提示框（背包/合成格是真的 Slot，右侧列表由覆写的
        // renderTooltip 补一份）。这一句同时会把 listHintDrawn 标记好，下面就不用再弹按钮说明。
        this.renderTooltip(graphics, mouseX, mouseY);
        // 真正的最上层：所有图标和提示框都画完之后，最后补按钮说明，绝不会被谁盖住。
        drawButtonHints(graphics, mouseX, mouseY);
        // 操作说明面板压在一切之上（z 同队列面板 600，和提示框 400 不再抢深度）。
        if (this.helpPanelOpen) {
            if (helpSlideFits()) {
                drawHelpPanelSlide(graphics, mouseX, mouseY);
            } else {
                drawHelpPanelOverlay(graphics, mouseX, mouseY);
            }
        }
    }

    /** 按钮说明：鼠标停在"自动"或"自动补齐"上时弹一段说明，画在整帧的最上层。 */
    private void drawButtonHints(GuiGraphics graphics, int mouseX, int mouseY) {
        // 鼠标停在列表项上时，列表本来就有自己的物品提示框，这时不弹按钮说明，免得两个框叠在一起
        if (this.listHintDrawn) {
            return;
        }
        // 按钮只剩一个字后，「刷新」的说明全指望这条提示；快捷键名跟着玩家的绑定走。
        if (this.refreshHovered) {
            String refreshKey = gestureKeyName(ModKeyMappings.REFRESH);
            hint(graphics, "refresh", refreshKey, () -> refreshKey == null
                            ? label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.refresh_button",
                                    "刷新：重新扫描接入的容器和背包，重建右边的可合成列表")
                            : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.refresh_key",
                                    "刷新：重新扫描接入的容器和背包，重建右边的可合成列表（快捷键：%s）", refreshKey),
                    BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H);
        } else if (this.autoHovered) {
            hint(graphics, "auto", null, () -> label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.auto_button",
                            "自动合成：先点右侧要合成的物品选中它，再点这个按钮。缺少的中间材料会自动补齐，产物送进输出容器"),
                    AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H);
        } else if (this.refillHovered) {
            hint(graphics, "refill", null, () -> label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.refill",
                            "缺少的中间材料可由其他配方合成"),
                    REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H);
        } else if (this.sortHovered) {
            hint(graphics, "sort", this.sortMode, () ->
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.sort_button",
                            "排序方式：%s。点一下换下一档：默认（材料齐的先、链式垫底）→ 按名称（中文按拼音）→ 按常用（最近做过的先）。"
                                    + "收藏的物品不受排序影响，永远排在最前面。",
                            this.sortMode.label().getString()),
                    SORT_BUTTON_X, SORT_BUTTON_Y, SORT_BUTTON_W, SORT_BUTTON_H);
        } else if (this.foldHovered) {
            hint(graphics, "fold", this.folding, () -> label(this.folding
                            ? "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fold_on"
                            : "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fold_off",
                    this.folding
                            ? "同类折叠：开。把同一种东西（比如各种木头的楼梯）收成一项，点它展开；再点这个按钮可关掉。搜索时自动不折叠，命中几样摆几样"
                            : "同类折叠：关。列表里的每一项都单独显示"),
                    FOLD_BUTTON_X, FOLD_BUTTON_Y, FOLD_BUTTON_W, FOLD_BUTTON_H);
        } else if (this.clearHovered) {
            hint(graphics, "clear", null, () -> label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.clear",
                            "清空合成格：从箱子拿来的材料原路退回那一台箱子那一格；自己手动摆进去的退回背包。产物格还没拿走的成品不受影响"),
                    CLEAR_BUTTON_X, CLEAR_BUTTON_Y, CLEAR_BUTTON_W, CLEAR_BUTTON_H);
        } else if (this.queueStripHovered) {
            String toggleKey = gestureKeyName(ModKeyMappings.QUEUE_TOGGLE);
            hint(graphics, "queue_strip", toggleKey, () -> toggleKey == null
                            ? label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.queue_strip",
                                    "合成队列：点开展开管理面板，逐件自动合成（缺料自动补齐、产物进输出容器；卡住会停下等你处理）")
                            : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.queue_strip_key",
                                    "合成队列：点开展开管理面板，逐件自动合成（%s 开始 / 暂停）", toggleKey),
                    QS_X, QS_Y, QS_W, QS_H);
        } else if (this.helpBtnHovered && !this.helpPanelOpen) {
            hint(graphics, "help", null, () -> label("gui." + SmartWorkbenchMod.MOD_ID + ".help.button_hint",
                            "操作说明：点开看看全部操作方法，再点收回"),
                    HELP_BTN_X, HELP_BTN_Y, HELP_BTN_SIZE, HELP_BTN_SIZE);
        }
    }

    /**
     * 悬停说明的缓存入口（掉帧方案 A5）：一行说明的文案只跟语言和按钮状态有关，
     * 跟鼠标坐标无关——分行、量宽、算提示框位置全都做一次存进 {@code hintCaches}，
     * 悬停期间每帧只剩"画顶点"。探针为 null 表示内容纯静态（语言变化整表作废即可）。
     */
    private void hint(GuiGraphics graphics, String tag, Object probe,
                      java.util.function.Supplier<Component> message,
                      int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        HintLines h = this.hintCaches.get(tag);
        if (h == null || !java.util.Objects.equals(h.probe(), probe)) {
            h = layoutHint(probe, message.get(), buttonX, buttonY, buttonWidth, buttonHeight);
            this.hintCaches.put(tag, h);
        }
        drawHint(graphics, h);
    }

    /**
     * 画八个分类键（上排 4、下排 4）：选中的一项描金边、鼠标悬停的一项铺亮底，图标是各档代表物。
     * 「建筑」是轮换键——键面上永远摆着 {@link #buildingCycle} 指到的那一档。
     * <p>
     * 分两趟画是有原因的：图标（renderItem）自己会提交一批，亮底和描边若画在它之后就压不住
     * 图标（深度测试），所以底和图标先画，「灰纱 + 数量角标」统一留到
     * {@link #drawCategoryTabOverlays} 抬到角标层再叠上去。
     */
    private void drawCategoryTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        CraftCategory.Key hovered = tabAt(mouseX, mouseY);
        // 先攒一批画完两排的底和高亮/描边（全是 fill，同一种缓冲），再逐个摆图标。
        // 底和图标交错画的话，每个图标的提交都会把前面没画完的底冲掉一次（掉帧方案 A4）。
        graphics.drawManaged(() -> {
            drawCategoryRowBases(graphics, CraftCategory.TOP_KEYS, TAB_TOP_X, TAB_TOP_Y, TAB_TOP_PITCH, hovered);
            drawCategoryRowBases(graphics, CraftCategory.BOTTOM_KEYS, TAB_BOT_X, TAB_BOT_Y, TAB_BOT_PITCH, hovered);
        });
        drawCategoryRowIcons(graphics, CraftCategory.TOP_KEYS, TAB_TOP_X, TAB_TOP_Y, TAB_TOP_PITCH);
        drawCategoryRowIcons(graphics, CraftCategory.BOTTOM_KEYS, TAB_BOT_X, TAB_BOT_Y, TAB_BOT_PITCH);
    }

    /** 键面上眼下摆的那一档：轮换键看指针，普通键就是它自己。 */
    private CraftCategory tabFace(CraftCategory.Key key) {
        return key.subs[key.cycles() ? this.buildingCycle : 0];
    }

    /** 键位的第一遍（合批）：悬停亮底 + 选中金边。图标见 {@link #drawCategoryRowIcons}。 */
    private void drawCategoryRowBases(GuiGraphics graphics, CraftCategory.Key[] row, int frameX, int frameY,
                                      int pitch, CraftCategory.Key hovered) {
        for (int i = 0; i < row.length; i++) {
            CraftCategory c = tabFace(row[i]);
            int ix = this.leftPos + frameX + 1 + i * pitch;
            int iy = this.topPos + frameY + 1;
            if (row[i] == hovered && c != this.category) {
                graphics.fill(ix - 1, iy - 1, ix - 1 + TAB_CELL, iy - 1 + TAB_CELL, HOVER_BG);
            }
            if (c == this.category) {
                // 选中的键描金边，和列表里选中项同一套颜色
                graphics.renderOutline(ix - 1, iy - 1, TAB_CELL, TAB_CELL, SELECT_COLOR);
            }
        }
    }

    /** 键位的第二遍（不合批）：图标走 renderItem，自己会提交，放底之后画正好盖在底上。 */
    private void drawCategoryRowIcons(GuiGraphics graphics, CraftCategory.Key[] row, int frameX, int frameY,
                                      int pitch) {
        for (int i = 0; i < row.length; i++) {
            CraftCategory c = tabFace(row[i]);
            int ix = this.leftPos + frameX + 1 + i * pitch;
            int iy = this.topPos + frameY + 1;
            graphics.renderItem(c.icon(), ix, iy);
        }
    }

    /**
     * 分类键的第二遍：空键盖灰纱（点不动的那个视觉来源）+ 每键右下角的数量数字。
     * 轮换键的灰纱看<b>整组</b>有没有货：摆出来的这档是 0 但同组别的档有货，键还是点得动的
     * （点一下跳到有货那档），就不该盖纱骗人。
     * 全画在 {@code drawManaged} 里合并提交，理由和 {@link #drawCellBadges} 一样——
     * 每个带阴影的字要画 2 遍，8 个键逐笔提交一帧就是几十次显卡提交，攒一批只交一次。
     */
    private void drawCategoryTabOverlays(GuiGraphics graphics) {
        graphics.drawManaged(() -> {
            // 先所有灰纱（fill），再所有数字（字形）——同类型的攒齐再换，整批两次提交（掉帧方案 A4）。
            for (CraftCategory.Key[] row : CraftCategory.ROWS) {
                int pitch = row == CraftCategory.TOP_KEYS ? TAB_TOP_PITCH : TAB_BOT_PITCH;
                int frameX = row == CraftCategory.TOP_KEYS ? TAB_TOP_X : TAB_BOT_X;
                int frameY = row == CraftCategory.TOP_KEYS ? TAB_TOP_Y : TAB_BOT_Y;
                for (int i = 0; i < row.length; i++) {
                    CraftCategory.Key key = row[i];
                    CraftCategory face = tabFace(key);
                    int ix = this.leftPos + frameX + 1 + i * pitch;
                    int iy = this.topPos + frameY + 1;
                    // 角标口径：正在看的键报"眼里这一档"的数量；没点亮的键报"点进去总共能看到"
                    // 的数量——轮换键没点亮时摆出的那档可能正好是空的，单档数会显示 0 却点得动。
                    int count = face == this.category
                            ? this.categoryCounts.getOrDefault(face, 0)
                            : keyTotal(key);
                    if (count == 0 && face != this.category) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
                        graphics.fill(ix, iy, ix + 16, iy + 16, TAB_EMPTY_VEIL);
                        graphics.pose().popPose();
                    }
                }
            }
            for (CraftCategory.Key[] row : CraftCategory.ROWS) {
                int pitch = row == CraftCategory.TOP_KEYS ? TAB_TOP_PITCH : TAB_BOT_PITCH;
                int frameX = row == CraftCategory.TOP_KEYS ? TAB_TOP_X : TAB_BOT_X;
                int frameY = row == CraftCategory.TOP_KEYS ? TAB_TOP_Y : TAB_BOT_Y;
                for (int i = 0; i < row.length; i++) {
                    CraftCategory.Key key = row[i];
                    CraftCategory face = tabFace(key);
                    int ix = this.leftPos + frameX + 1 + i * pitch;
                    int iy = this.topPos + frameY + 1;
                    int count = face == this.category
                            ? this.categoryCounts.getOrDefault(face, 0)
                            : keyTotal(key);
                    drawTabCount(graphics, face, ix, iy, count);
                }
            }
        });
    }

    /** 一个键眼下总共有没有货：普通键就是自己那档，轮换键看三档合计。 */
    private int keyTotal(CraftCategory.Key key) {
        int total = 0;
        for (CraftCategory sub : key.subs) {
            total += this.categoryCounts.getOrDefault(sub, 0);
        }
        return total;
    }

    /** 键右下角的数量数字：三款画法由 {@link #drawCountDigits} 分发（默认 0.7 倍字体），超过 999 显示「999+」。 */
    private void drawTabCount(GuiGraphics graphics, CraftCategory c, int ix, int iy, int count) {
        String text = count > 999 ? "999+" : Integer.toString(count);
        int color = count == 0 ? TAB_EMPTY_COUNT_COLOR
                : (c == this.category ? SELECT_COLOR : TEXT_ON_DARK);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        // 右缘钉在 ix + 14（描边后到 ix + 15，正好压在图标右沿）。
        // 三款画法现在都落在 5 行的框里，顶行统一 iy + 9（到 iy + 13），贴底效果一致。
        drawCountDigits(graphics, text, ix + 14, iy + 9, color);
        graphics.pose().popPose();
    }

    /** 鼠标落在哪个分类键上；没有就返回 null。命中区域是 18×18 的整框（含描边）。 */
    private CraftCategory.Key tabAt(double mouseX, double mouseY) {
        for (int i = 0; i < CraftCategory.TOP_KEYS.length; i++) {
            if (inTabCell(mouseX, mouseY, TAB_TOP_X + i * TAB_TOP_PITCH, TAB_TOP_Y)) {
                return CraftCategory.TOP_KEYS[i];
            }
        }
        for (int i = 0; i < CraftCategory.BOTTOM_KEYS.length; i++) {
            if (inTabCell(mouseX, mouseY, TAB_BOT_X + i * TAB_BOT_PITCH, TAB_BOT_Y)) {
                return CraftCategory.BOTTOM_KEYS[i];
            }
        }
        return null;
    }

    private boolean inTabCell(double mouseX, double mouseY, int frameX, int frameY) {
        int x = this.leftPos + frameX;
        int y = this.topPos + frameY;
        return mouseX >= x && mouseX < x + TAB_CELL && mouseY >= y && mouseY < y + TAB_CELL;
    }

    /**
     * 点分类键：换过去、滚回顶部、重算列表、立刻记进偏好文件。
     * 空分类（角标 0、盖灰纱那个）点不动——按用户要求，别让玩家点了才发现是空的；
     * 反馈只给一行热栏提示，不弹窗打断。
     */
    private void selectCategory(CraftCategory clicked) {
        if (clicked == this.category) {
            return;
        }
        if (this.categoryCounts.getOrDefault(clicked, 0) == 0) {
            tabEmptyFeedback();
            return;
        }
        this.category = clicked;
        this.scroll = 0;
        this.displayDirty = true;
        ensureDisplay();
        playClickSound();
        ClientPrefs.saveCategory(clicked);
    }

    /**
     * 点一个键（界面按的入口）：普通键直接切过去；「建筑」是轮换键，在建筑方块 → 染色方块 →
     * 自然方块里连点转圈，<b>空档自动跳过</b>——永远转到下一个眼下有货的那一档。
     */
    private void selectTabKey(CraftCategory.Key key) {
        if (!key.cycles()) {
            selectCategory(key.subs[0]);
            return;
        }
        if (keyTotal(key) == 0) {
            tabEmptyFeedback();
            return;
        }
        CraftCategory target = cycleTarget(key);
        if (target == null) {
            // 组里就眼下这一档有货：没得可转，给一行热栏提示
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.tab_cycle_alone",
                                "「建筑」这一组眼下只有这一档有货，转不动"), true);
            }
            return;
        }
        this.buildingCycle = target.cycleIndex();
        selectCategory(target);
    }

    /**
     * 轮换键下一次点击会落到哪一档：从指针的下一格起找第一个<b>有货</b>的，转一圈找不到返回 null。
     * 键没点亮时从指针那一格起找（第一次点击先进这组、停在摆出来的那档，前提它有货）。
     */
    private CraftCategory cycleTarget(CraftCategory.Key key) {
        CraftCategory[] subs = key.subs;
        int start = this.buildingCycle + (key.covers(this.category) ? 1 : 0);
        for (int step = 0; step < subs.length; step++) {
            CraftCategory cand = subs[Math.floorMod(start + step, subs.length)];
            if (this.categoryCounts.getOrDefault(cand, 0) > 0) {
                return cand == this.category ? null : cand;
            }
        }
        return null;
    }

    /**
     * 右键一个键（"往回切"的入口，用户点名要的功能）：普通键和左键等效；
     * 「建筑」轮换键沿反方向走（自然方块 → 染色方块 → 建筑方块），空档同样自动跳过。
     */
    private void selectTabKeyBack(CraftCategory.Key key) {
        if (!key.cycles()) {
            selectCategory(key.subs[0]);
            return;
        }
        if (keyTotal(key) == 0) {
            tabEmptyFeedback();
            return;
        }
        CraftCategory target = cycleTargetBack(key);
        if (target == null) {
            // 组里就眼下这一档有货：没得可转，给一行热栏提示（和左键同一句）
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.tab_cycle_alone",
                                "「建筑」这一组眼下只有这一档有货，转不动"), true);
            }
            return;
        }
        this.buildingCycle = target.cycleIndex();
        selectCategory(target);
    }

    /**
     * 轮换键<b>右键</b>会落到哪一档：{@link #cycleTarget} 的镜像——从指针的<b>上一格</b>起
     * 倒着找第一个有货的；键没点亮时和左键一样先落进摆出来的那档。找不到返回 null。
     */
    private CraftCategory cycleTargetBack(CraftCategory.Key key) {
        CraftCategory[] subs = key.subs;
        int start = this.buildingCycle - (key.covers(this.category) ? 1 : 0);
        for (int step = 0; step < subs.length; step++) {
            CraftCategory cand = subs[Math.floorMod(start - step, subs.length)];
            if (this.categoryCounts.getOrDefault(cand, 0) > 0) {
                return cand == this.category ? null : cand;
            }
        }
        return null;
    }

    /** 空键点不动的那行热栏提示。 */
    private void tabEmptyFeedback() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.tab_empty",
                            "这一类现在没有能合成的东西"), true);
        }
    }

    /** 分类键的悬停提示：档名、点了会怎样；轮换键还报"组里连点轮换"的路线和下一站。 */
    private List<Component> buildTabTooltip(CraftCategory.Key key) {
        List<Component> lines = new ArrayList<>();
        CraftCategory face = tabFace(key);
        boolean active = face == this.category;
        boolean groupCount = !active && key.cycles();
        int shown = groupCount ? keyTotal(key) : this.categoryCounts.getOrDefault(face, 0);
        lines.add(face.label().copy().withStyle(active ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        lines.add(labelArgs(groupCount
                        ? "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_count_group"
                        : "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_count",
                        groupCount ? "这一组合计：%s 项" : "当前可合成：%s 项", shown)
                .withStyle(shown == 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        if (key.cycles()) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_cycle",
                            "连点轮换：%1$s → %2$s → %3$s（空的自动跳过；右键反着切）",
                            key.subs[0].label().getString(), key.subs[1].label().getString(),
                            key.subs[2].label().getString())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (keyTotal(key) == 0) {
            lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_empty",
                    "空的，暂时点不动").withStyle(ChatFormatting.GRAY));
        } else if (key.cycles()) {
            CraftCategory next = cycleTarget(key);
            if (next != null) {
                lines.add(labelArgs(active
                                ? "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_next"
                                : "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_click_cycle",
                        active ? "左键：转到下一档「%s」" : "左键：只看这一组，会停在「%s」",
                        next.label().getString())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_alone",
                        "这一组只有这一档有货，再点切不走").withStyle(ChatFormatting.GRAY));
            }
            // 右键往回切：只在"回得去"时提示，切不动时别写一句做不到的话
            CraftCategory prev = active ? cycleTargetBack(key) : null;
            if (prev != null) {
                lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_prev",
                                "右键：切回上一档「%s」", prev.label().getString())
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (active) {
            lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_active",
                    "正在看这一类").withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.tab_click",
                    "左键：只看这一类").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    /**
     * 列表空的时候，把提示文字在 5x5 区域里居中排，避免压在格子上显得乱。
     * <p>
     * 提示内容只跟「语言 + 分类 + 搜索词」有关，行排版和行宽先量好存起来
     * （掉帧方案 A2）；字本来就两三行，缓存的意义主要是<b>别每帧走排版器</b>。
     * 绘制包进 drawManaged，两三行字一次提交（方案 A4）。
     */
    private void drawEmptyHint(GuiGraphics graphics) {
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        String probe = this.probeLang + "|" + this.category.name() + "|" + query;
        if (!probe.equals(this.emptyHintProbe)) {
            this.emptyHintProbe = probe;
            Component message;
            if (!query.isBlank() && this.category != CraftCategory.ALL) {
                message = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty_search_cat",
                        "「%2$s」里没有匹配「%1$s」的配方，换个分类键看看", query, this.category.label().getString());
            } else if (!query.isBlank()) {
                message = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty_search",
                        "没有找到匹配「%s」的配方", query);
            } else if (this.category != CraftCategory.ALL) {
                message = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty_cat",
                        "「%s」这一类暂时没有能直接合成的东西", this.category.label().getString());
            } else {
                message = label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty",
                        "接入存储和背包里暂时没有能直接合成的东西");
            }
            int width = LIST_COLS * CELL - 8;
            List<FormattedCharSequence> lines = this.font.split(message, width);
            List<Integer> widths = new ArrayList<>(lines.size());
            for (FormattedCharSequence line : lines) {
                widths.add(this.font.width(line));
            }
            this.emptyHintLines = lines;
            this.emptyHintWidths = widths;
        }
        graphics.drawManaged(() -> {
            int width = LIST_COLS * CELL - 8;
            int areaTop = this.topPos + LIST_Y;
            int total = emptyHintLines.size() * (this.font.lineHeight + 1);
            int y = areaTop + (LIST_ROWS * CELL - total) / 2 + 2;
            for (int i = 0; i < emptyHintLines.size(); i++) {
                // 提示文字压在下方的深紫灰格子上，必须用白色 + 阴影才看得清
                graphics.drawString(this.font, emptyHintLines.get(i),
                        this.leftPos + LIST_X + (width - emptyHintWidths.get(i)) / 2, y, TEXT_ON_DARK, false);
                y += this.font.lineHeight + 1;
            }
        });
    }

    /**
     * 自己画搜索框的底、边和占位提示。
     * EditBox 关掉边框后只剩光标和文字，底和边在这里补；不聚焦且没输入时，
     * 用浅灰把「搜索 / 拼音」写在框里，玩家才知道这里能打字。
     * <p>
     * 这段画在 {@code drawManaged} 批次里，裁剪框要到批次末尾出画时才生效、而那时
     * 又已经解除了——所以提示的"裁进框内"改成画之前就把字符串按像素宽度截短
     * （截在字符边界上，比裁掉半个字还干净）。截断结果按语言缓存一次（方案 A2/A5）。
     */
    private void drawSearchBoxFrame(GuiGraphics graphics) {
        int x = this.leftPos + SEARCH_X;
        int y = this.topPos + SEARCH_Y;
        int border = this.searchBox.isFocused() ? SELECT_COLOR : GuiLayout.LINE_SOFT_COLOR;
        graphics.fill(x - 1, y - 1, x + SEARCH_W + 1, y + SEARCH_H + 1, border);
        graphics.fill(x, y, x + SEARCH_W, y + SEARCH_H, SEARCH_BG);
        if (this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            if (this.searchHintClip == null) {
                Fitted hint = fitted("gui." + SmartWorkbenchMod.MOD_ID + ".search.hint", "搜索 / 拼音");
                this.searchHintClip = hint.width() <= SEARCH_W - 8
                        ? hint.text().getString()
                        : this.font.plainSubstrByWidth(hint.text().getString(), SEARCH_W - 8);
            }
            graphics.drawString(this.font, this.searchHintClip, x + 4, y + (SEARCH_H - 8) / 2,
                    SEARCH_HINT_COLOR, false);
        }
    }

    /** 画产物格下方的批量数量框和标签。 */
    private void drawBatchCountBox(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.batchBox == null) {
            return;
        }
        int x = this.leftPos + GuiLayout.BATCH_INPUT_X;
        int y = this.topPos + GuiLayout.BATCH_INPUT_Y;
        int w = GuiLayout.BATCH_INPUT_WIDTH;
        int h = GuiLayout.BATCH_INPUT_HEIGHT;
        int border = this.batchBox.isFocused() ? SELECT_COLOR : GuiLayout.LINE_SOFT_COLOR;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        graphics.fill(x, y, x + w, y + h, SEARCH_BG);
        // 「批量数量 / Bulk qty」的可用宽度只到输入框左边，英文放不下就缩小，别压进输入框
        drawLeftFitted(graphics, fitted("gui." + SmartWorkbenchMod.MOD_ID + ".label.batch_count", "批量数量"),
                this.leftPos + GuiLayout.BATCH_LABEL_X, this.topPos + GuiLayout.BATCH_LABEL_Y, TEXT_MAIN,
                GuiLayout.BATCH_INPUT_X - GuiLayout.BATCH_LABEL_X - 2);
        // 批内渲染（掉帧方案 A5）：外面那段 drawManaged 已经把它包进批次了，这里只管画。
        this.batchBox.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 链式项角标图案：6×3 的简化链环——左右两枚 2×3 实心小块、
     * 中间一根 2×1 短横杆连着，还带点"一级一级接上去"的链味。
     * 全是实心像素，不走真圆环那条路（5 像素直径留不出洞，会重蹈「链」字糊掉的覆辙）。
     * 试过阶梯、菱形两款后玩家拍板留这一款；描边后实际占 8×5，和原阶梯一样宽，
     * 右缘与数量数字对齐。
     */
    private static final String[] CHAIN_LINK = {
            "##..##",
            "######",
            "##..##",
    };

    /** {@link #CHAIN_LINK} 的按行合并区间，启动时算一次（见 {@link #buildRuns}）。 */
    private static final int[][] CHAIN_LINK_RUNS = buildRuns(CHAIN_LINK);

    /**
     * 「可换配方」角标图案：7×3 的左右双向箭头，表示这件产物还能换别的材料组合。
     * 9×5 的版本玩家反馈「快把旁边数字占了」——描边后实际占 11×7，18 像素的格子里
     * 右下贴边的数字没剩多少地方。这版砍到 7×3：中间通宽横杆是箭身，两头各一枚
     * 3 像素高的小箭镞（尖端落在杆端），描边后只占 9×5，方向感靠「中间一条直线、
     * 两端张开」照样一眼是左右。
     */
    private static final String[] ALT_ARROW = {
            ".#...#.",
            "#######",
            ".#...#.",
    };

    /** {@link #ALT_ARROW} 的按行合并区间。 */
    private static final int[][] ALT_ARROW_RUNS = buildRuns(ALT_ARROW);

    /**
     * 链式项角标：右上角一枚紫色简化链环（图案见 {@link #CHAIN_LINK}）。
     * <p>
     * 位置对齐：图案按宽度右靠，图案右缘落在 x + CELL - 2（像素列 x + 16），再往外描 1 像素
     * 黑边后填到 x + CELL - 1（不含），和右下角数量数字的右缘同一条竖线；
     * 上边缘 y + 1，占 y + 0..y + 4。右列自上而下只剩链环（顶）→ 数量数字（底），
     * 中间一段空着——玩家反馈"右边看着很挤"后，「可换配方」箭头已搬回左侧星星下方
     * （见 {@link #drawAltBadge}）。描边见 {@link #drawPixelBadge}。
     */
    private void drawChainBadge(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        drawPixelBadge(graphics, CHAIN_LINK_RUNS, x + CELL - 2 - CHAIN_LINK[0].length(), y + 1, CHAIN_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 「可换配方」角标：<b>左侧</b>一枚草绿色双向箭头（图案见 {@link #ALT_ARROW}）。
     * <p>
     * 玩家在右列版本看过后反馈"右边看着很挤"，又搬回左边——上一版在右边时，
     * 它和头顶链环、脚下数字挤在同一条竖线附近，中间只剩三行高，怎么看都局促。
     * 现在贴格子左缘（x + 1），和右上角的链环、右下角的数量数字一左一右分开。
     * <p>
     * 纵向排在星星和数量数字<b>之间</b>，身位占 y + 6..y + 8，八方向描边后占 y + 5..y + 9：
     * <ul>
     *   <li>上面收藏星星（5×5 含描边到 y + 5）的下缘黑边正好就是箭头描边的上缘那一行
     *       ——<b>同一行黑叠同一行黑，看不出两条</b>，不会糊成一根柱子；没星星的格子里
     *       箭头自带完整描边，两种情况都齐整；</li>
     *   <li>下面数量数字的字体款是<b>九遍描边</b>（{@code FastGlyphText.drawNine}），
     *       0.7 倍时描边的最高一行落在 y + 9、和本图的下描边同一行（同样是黑压黑），
     *       字形墨迹从 y + 10 起，永远碰不到绿色部分；点阵款最高才 y + 11，更宽裕。
     *       三位数、「999+」也只是把那条 y + 9 的黑边横着接满，不会啃到箭头。</li>
     * </ul>
     * 看到箭头就知道右键这一格有别的东西可换，具体换法写在提示框里。
     */
    private void drawAltBadge(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        drawPixelBadge(graphics, ALT_ARROW_RUNS, x + 1, y + 6, ALT_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 收藏角标的星形图案（5×5，'#' 是实心像素）。原版字体里的 ★ 不可靠，
     * 自己排像素反而干净；用户嫌「藏」字角标怪，换成金色小星星。
     */
    private static final String[] FAV_STAR = {
            "..#..",
            ".###.",
            "#####",
            ".###.",
            ".#.#.",
    };

    /**
     * 常用收藏角标：左上角一颗金色小星星，贴顶（y + 0，描边后压到格子内边框上 1 行）。
     * <p>
     * 星星 5×5 含描边到 y + 5，占的是左上角；「可换配方」箭头在它正下方
     * （见 {@link #drawAltBadge}，身位 y + 6..y + 8），两者共用 y + 5 那条黑边，
     * 一上一下叠在左侧，右侧整列留给链环和数字。
     * 收藏项不参与折叠、永远在最前面，这角标只可能出现在列表头几格，好找。
     */
    private void drawFavBadge(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        // 先描一圈黑边再填金色：落在任何颜色的图标上都有对比度（阶梯、箭头角标同一套做法）
        drawPixelBadge(graphics, FAV_STAR_RUNS, x + 2, y, SELECT_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 图案的按行合并区间：每项是 {行, 起始列, 结束列（不含）}。
     * 同一行里连续的 '#' 合成一条横条，启动时算一次，绘制时每帧不再扫图案字符串。
     */
    private static final int[][] FAV_STAR_RUNS = buildRuns(FAV_STAR);

    private static int[][] buildRuns(String[] pattern) {
        List<int[]> runs = new ArrayList<>();
        for (int r = 0; r < pattern.length; r++) {
            String row = pattern[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int start = c;
                while (c < row.length() && row.charAt(c) == '#') {
                    c++;
                }
                runs.add(new int[] {r, start, c});
            }
        }
        return runs.toArray(new int[0][]);
    }

    /**
     * 画一个自绘像素角标（链式、箭头、星星共用）：先在 8 个方向各铺一遍纯黑描一圈
     * 硬边，再在正中填本色——落在任何颜色的物品图标上都有对比度。
     * <p>
     * 全部是 {@link GuiGraphics#fill}，处在 {@link #drawCellBadges} 的 drawManaged 批次里：
     * 一次 fill 只是往缓冲区添 4 个顶点，<b>不会</b>像 drawString 那样逐笔提交显卡。
     * 同行连续像素已合成横条（见 {@link #buildRuns}）：链环 5 条、箭头 5 条、星星 6 条，
     * 连描边一个角标最多也就九十来个矩形，比原来一个缩小的汉字（字体本身上百个三角面）还轻。
     * <p>
     * 调用方要先把姿势栈抬到 {@link #GROUP_CHIP_Z}，否则会被物品图标盖住。
     */
    private static void drawPixelBadge(GuiGraphics graphics, int[][] runs, int sx, int sy, int color) {
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) {
                    continue;
                }
                fillRuns(graphics, sx + ox, sy + oy, runs, CHIP_OUTLINE_COLOR);
            }
        }
        fillRuns(graphics, sx, sy, runs, color);
    }

    /** 按合并好的横条填一块图案（全在 drawManaged 批次里）。 */
    private static void fillRuns(GuiGraphics graphics, int sx, int sy, int[][] runs, int color) {
        for (int[] run : runs) {
            graphics.fill(sx + run[1], sy + run[0], sx + run[2], sy + run[0] + 1, color);
        }
    }

    /**
     * 自绘点阵数字用的 3×5 图案：0–9 和「+」，和星星、链环、箭头同一套做法。
     * <p>
     * 字体款的替代款（按键可切）：字体像素被非整数缩放后笔画会少列，点阵数字一格像素
     * 就是一格像素，硬边、不缩放，落在任何物品图标上都清楚。中间试过压矮到 3×4，结果「1」
     * 看着像缺笔的「3」、「6」顶横没了——3×5 就是数字不丢笔画的最小尺寸，压不得，已回退。
     * "显大"的问题改由描边侧解决：只描字形外轮廓、不填内部空洞（见 {@link #buildOutline}）。
     * <p>
     * <b>「1」只能是一竖（r196 玩家拍板）：</b>原来写「旗头 + 一竖 + 底横」，
     * 可这字形左右两侧各有黑描边一列、从上到下都通着，远看就成了「彐」——
     * 玩家反复反馈"1 看着像残缺的 3"。去掉两条白横之后只剩中间一竖，
     * 描边在它左右各贴一列、底下一行，整体 3 像素宽，凑不出 3 的形状。
     * 宽度仍是 3（{@link #DIGIT_W}），所以右对齐和拼字间距跟别的数字完全一致。
     * 下标 0–9 是数字，10 是「+」（999 封顶那一下用）。
     */
    private static final String[][] DIGIT_PATTERNS = {
            {"###", "#.#", "#.#", "#.#", "###"}, // 0
            {"..#", "..#", "..#", "..#", "..#"}, // 1
            {"###", "..#", "###", "#..", "###"}, // 2
            {"###", "..#", "###", "..#", "###"}, // 3
            {"#.#", "#.#", "###", "..#", "..#"}, // 4
            {"###", "#..", "###", "..#", "###"}, // 5
            {"###", "#..", "###", "#.#", "###"}, // 6
            {"###", "..#", ".#.", ".#.", ".#."}, // 7
            {"###", "#.#", "###", "#.#", "###"}, // 8
            {"###", "#.#", "###", "..#", "..#"}, // 9
            {"...", ".#.", "###", ".#.", "..."}, // +
    };

    /** {@link #DIGIT_PATTERNS} 的按行合并区间，启动时算一次。 */
    private static final int[][][] DIGIT_RUNS = buildDigitRuns();

    /** 一个点阵数字的宽（3 列字形，高 5 行），以及相邻两位的间距（空档 1 列，正好当描边位）。 */
    private static final int DIGIT_W = 3;
    private static final int DIGIT_ADVANCE = 4;

    private static int[][][] buildDigitRuns() {
        int[][][] out = new int[DIGIT_PATTERNS.length][][];
        for (int i = 0; i < DIGIT_PATTERNS.length; i++) {
            out[i] = buildRuns(DIGIT_PATTERNS[i]);
        }
        return out;
    }

    /** 数字串里第 i 个字符对应的图案横条；不认识的字（理论上不会有）返回 null，跳过不画。 */
    private static int[][] digitRunsAt(String text, int i) {
        char c = text.charAt(i);
        if (c >= '0' && c <= '9') {
            return DIGIT_RUNS[c - '0'];
        }
        if (c == '+') {
            return DIGIT_RUNS[10];
        }
        return null;
    }

    /** 每个数字的"只描外轮廓"描边横条，和 {@link #DIGIT_RUNS} 同下标；启动时算一次。 */
    private static final int[][][] DIGIT_OUTLINE_RUNS = buildDigitOutlines();

    private static int[][][] buildDigitOutlines() {
        int[][][] out = new int[DIGIT_PATTERNS.length][][];
        for (int i = 0; i < DIGIT_PATTERNS.length; i++) {
            out[i] = buildRuns(buildOutline(DIGIT_PATTERNS[i]));
        }
        return out;
    }

    /** 数字串里第 i 个字符对应的外轮廓横条；和 {@link #digitRunsAt} 同一个查法。 */
    private static int[][] digitOutlineAt(String text, int i) {
        char c = text.charAt(i);
        if (c >= '0' && c <= '9') {
            return DIGIT_OUTLINE_RUNS[c - '0'];
        }
        if (c == '+') {
            return DIGIT_OUTLINE_RUNS[10];
        }
        return null;
    }

    /** 字形格子里 (x, y) 是否落墨；越出 3×5 框一律算空。 */
    private static boolean glyphAt(String[] pattern, int x, int y) {
        return x >= 0 && x < DIGIT_W && y >= 0 && y < pattern.length && pattern[y].charAt(x) == '#';
    }

    /**
     * 给一个 3×5 字形算"只描外轮廓、不填内部空洞"的描边图案：
     * 往四周各扩一格（宽 5、高 7，左上角对应字形 (-1, -1)），空格子满足两条才落墨——
     * <ol>
     *   <li>贴着字形：它的左、右、上、左上、右上五个方位里有笔画格——和原来"整体左移/右移/下移
     *       各铺一遍"的可见描边完全同一套位置（含两个下边角、不含顶边）；</li>
     *   <li>不在夹缝里：夹缝＝框内空格且左右都是笔画（「0」「6」「8」「9」的中孔）
     *       或上下都是笔画（「3」「2」「5」「7」的凹口）。原来的整体平移法会在这些格子上
     *       铺黑，孔和凹口被堵死，「3」看着缺一块——这条一免，数字立刻瘦一圈。</li>
     * </ol>
     * 代价：孔洞改成透出底下的物品像素，白羊毛、铁块这类浅色图标上字没那么抢眼。
     * 每个数字的描边只算这一遍（原来五遍整体平移），矩形数量反而少了。
     */
    private static String[] buildOutline(String[] pattern) {
        int rows = pattern.length;
        String[] outline = new String[rows + 2];
        for (int y = -1; y <= rows; y++) {
            StringBuilder row = new StringBuilder(DIGIT_W + 2);
            for (int x = -1; x <= DIGIT_W; x++) {
                boolean ink = false;
                if (!glyphAt(pattern, x, y)) {
                    boolean touch = glyphAt(pattern, x - 1, y) || glyphAt(pattern, x + 1, y)
                            || glyphAt(pattern, x, y - 1) || glyphAt(pattern, x - 1, y - 1)
                            || glyphAt(pattern, x + 1, y - 1);
                    boolean inBox = x >= 0 && x < DIGIT_W && y >= 0 && y < rows;
                    boolean pinched = inBox && ((glyphAt(pattern, x - 1, y) && glyphAt(pattern, x + 1, y))
                            || (glyphAt(pattern, x, y - 1) && glyphAt(pattern, x, y + 1)));
                    ink = touch && !pinched;
                }
                row.append(ink ? '#' : '.');
            }
            outline[y + 1] = row.toString();
        }
        return outline;
    }

    /**
     * 画一串点阵数字：右对齐到 {@code rightCol}（字形最右一列落在这一列），顶行 {@code topRow}。
     * <p>
     * 描边只描字形外轮廓，不填内部空洞（做法见 {@link #buildOutline}）：
     * 先每位一遍黑轮廓（预计算好的横条，向左上各错一格），再每位一遍本色。
     * 一个两位数的角标约 30 个小矩形，都在 drawManaged 批次里，比原来五遍整体平移还省一半。
     * <p>
     * <b>外轮廓不含上缘那一行</b>（{@link #buildOutline} 的 y = -1 行恒为空格，已按 0–9 逐一核过）：
     * 所以描边的最高一行就落在 {@code topRow}、和字形顶行同高（y + 11）；左侧的
     * 「可换配方」箭头下描边最低到 y + 9，两者之间还空着一行，字体款、点阵款都不会压到它。
     */
    private static void drawPixelDigits(GuiGraphics graphics, String text, int rightCol, int topRow, int color) {
        int n = text.length();
        // 第 i 位字形的左上角列：整体宽度 = DIGIT_ADVANCE*(n-1) + DIGIT_W，右缘钉在 rightCol
        int baseX = rightCol - (DIGIT_ADVANCE * (n - 1) + DIGIT_W - 1);
        for (int i = 0; i < n; i++) {
            int[][] outline = digitOutlineAt(text, i);
            if (outline != null) {
                fillRuns(graphics, baseX + i * DIGIT_ADVANCE - 1, topRow - 1, outline, CHIP_OUTLINE_COLOR);
            }
        }
        for (int i = 0; i < n; i++) {
            int[][] runs = digitRunsAt(text, i);
            if (runs != null) {
                fillRuns(graphics, baseX + i * DIGIT_ADVANCE, topRow, runs, color);
            }
        }
    }

    /**
     * 右下角、页签、折叠头三处数量数字的统一入口：玩家选的两款之一。
     * <p>
     * 只剩两款（玩家比过几轮后定下来的组合，别款都删了）：
     * <ul>
     *   <li><b>0.7 倍原版字体</b>（{@link #drawScaledFontDigits}，默认）：笔画细、和原版风格一致；</li>
     *   <li><b>自绘 3×5 点阵</b>（{@link #drawPixelDigits}）：硬边、不缩放，一格像素就是一格像素。</li>
     * </ul>
     * 按哪个键切换、当前是哪一款都存在 {@link DigitStylePref} 里（客户端配置，重开游戏仍记得）；
     * 两款都落在同一个 5 行的框里（点阵 5 行，字体款按倍率顶到同一条底线），
     * 所以只需要一个锚点 {@code pixelTopRow}（点阵款的顶行）。
     */
    private void drawCountDigits(GuiGraphics graphics, String text, int rightCol, int pixelTopRow, int color) {
        if (DigitStylePref.pixelDigits()) {
            drawPixelDigits(graphics, text, rightCol, pixelTopRow, color);
            return;
        }
        drawScaledFontDigits(graphics, text, rightCol, pixelTopRow, BADGE_SCALE, color);
    }

    /**
     * 字体款画法（倍率见 {@link #BADGE_SCALE}）：原版字体缩到
     * {@code scale}、朝八个方向各描一道黑的<b>九遍</b>描边（{@link FastGlyphText#drawNine}），
     * 字底压在像素款同一条底线上。
     * <p>
     * 上一轮为了给头顶的箭头让路，把九遍砍成了去掉上缘的五遍黑（连本色共六遍）；
     * 玩家看图后反馈"6 下不清楚"，已经恢复成九遍。描边最高一行因此比字形顶行再高一行：
     * 0.7 倍时字形墨迹从 y + 10 起，黑边顶行落在 y + 9。
     * <p>
     * <b>为什么以前有时虚、有时不虚：</b>原版字体纹理是最近邻采样，缩放本身不会糊成灰边，
     * 只会让笔画少一两列。真正让边缘发灰的是<b>起点没落在整像素上</b>——右对齐时
     * {@code 右缘 - 宽度 × 倍率} 算出来往往是 {@code x + 8.2} 这种数，字从半像素起步，
     * 每一列笔画都跨两格，描边和字又互相错开半格，看上去就是"虚"。这一款先把起点和顶行
     * 都 {@code Math.round} 到整像素再缩放，字形网格和屏幕像素对齐，边缘是硬的；
     * 代价只是右缘最多差半个像素，肉眼看不出来。
     * <p>
     * 0.7 倍时三位数（「999」约 12.6 像素）稳稳放得下；只有数量过千显示的「999+」四位
     * 会宽到 16.8 像素、比字面（{@code CELL - 2}）宽一点，这里按可用宽度等比收窄再画，
     * 不越界压进邻格——正常三位数不走这条。
     */
    private void drawScaledFontDigits(GuiGraphics graphics, String text, int rightCol, int pixelTopRow,
                                      float scale, int color) {
        float scaledWidth = this.font.width(text) * scale;
        float maxWidth = CELL - 2;
        if (scaledWidth > maxWidth) {
            scale *= maxWidth / scaledWidth;
            scaledWidth = maxWidth;
        }
        float left = Math.round(rightCol + 1 - scaledWidth);
        // 原版字体墨迹高 8 行：按倍率往上顶，底边和像素款的底行对齐（像素款 5 行）
        float top = Math.round(pixelTopRow + DIGIT_BASELINE_GAP - 8.0F * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        FastGlyphText.drawNine(this.font, graphics, text, 0.0F, 0.0F, color);
        graphics.pose().popPose();
    }

    /**
     * 字体款的倍率：原版字号（数字约 4×8）压在 16×16 的图标上，1:1 太挤，
     * 0.65、0.6、0.8 都比过，玩家挑定 <b>0.7</b>。
     */
    private static final float BADGE_SCALE = 0.7F;

    /** 右下角的堆叠数量角标：两款画法见 {@link #drawCountDigits}，默认 0.7 倍字体，贴底从 y + 11 起画。
     * <p>
     * 早前这里走的是原版 {@link GuiGraphics#renderItemDecorations}，它按 16 格画满、挡住物品；
     * 后来换成缩放的原版字体（玩家截图里那款的位置，倍率这轮是 0.7）。0.7 倍字体九遍描边后
     * 最低到 y + 15、最高到 y + 9（顶行黑边），正好落在格子底边框上方；
     * 右侧这一列从上到下就它和顶上的链环两样东西——「可换配方」箭头在左侧（见 {@link #drawAltBadge}），
     * 它俩最宽也只是在 y + 9 那一行和箭头的下描边黑压黑，位数再多也压不到绿芯。
     * 白字、纯黑描边，右缘贴到链环角标同一条竖线。
     * 数量 1 不画（和原版一致），超过 999 显示成 999+。
     */
    private void drawSmallCount(GuiGraphics graphics, ItemStack stack, int x, int y) {
        int count = stack.getCount();
        if (count <= 1) {
            return;
        }
        String text = count > 999 ? "999+" : String.valueOf(count);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        // 右缘钉在 x + CELL - 3：和右上角链环角标（含描边）的最后一条竖线对齐。
        // 两款都压在同一条底线上：点阵从 y + 11 画到 y + 15 贴住图标底沿，0.7 倍字体按自己的倍率
        // 往上顶到同一条底线，同样贴住底沿。
        drawCountDigits(graphics, text, x + CELL - 3, y + 11, GROUP_COUNT_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 折叠头的额外装饰：青边 + 左上角 +/- 表示收起/展开 + 右下角这一类的成员数。
     * 图标沿用第一个成员，玩家一眼能看出这堆是什么东西。
     * <p>
     * 这里最容易踩的两个坑都在<b>深度</b>上，跟代码里谁先写没关系：
     * <ol>
     *   <li>抬到 z=0 会被盖住。原版物品图标带 translate(0,0,150) 画（见
     *       GuiGraphics#renderItemAndGlintIntoBatch），角标留在 z=0 时，图标不透明的角落
     *       （钻石装备、压力板）就连底带字把角标整块吃掉，只在下沿露一个钩子；
     *       角落本来就透明的图标（铁锭、命名牌）反而看得见——前几轮截图里正是这个现象。</li>
     *   <li>抬到 z=400 又会盖住提示框。GUI 的深度测试是 GL_LESS，两笔同高判不过，
     *       于是先画的角标把后画的提示框顶掉，数字浮到物品说明上面去了。
     *       {@link #GROUP_CHIP_Z} 取 200：比图标靠前、比提示框靠后，两头都正好。</li>
     * </ol>
     * 角标本身不再垫深色底，改成给字画阴影/描边，免得挡住折叠的是什么物品。
     */
    /** 折叠头的第一遍：青色的圈 + 右下角成员数（都是 fill，和链环/箭头/星星同批）。 */
    private void drawGroupHeaderRing(GuiGraphics graphics, CraftableListView.Cell cell, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        graphics.renderOutline(x - 1, y - 1, CELL, CELL, GROUP_COLOR);
        // 成员数和列表格子的数量数字同一套画法，位置、右对齐算法、贴底完全一致（三款都用 y + 11 起 5 行）。
        drawCountDigits(graphics, String.valueOf(cell.groupSize()), x + CELL - 3, y + 11,
                GROUP_COUNT_COLOR);
        graphics.pose().popPose();
    }

    /** 折叠头的第二遍：左上角「+/-」（原版字体，1.0 不缩放、不糊）。同抬 {@link #GROUP_CHIP_Z}。 */
    private void drawGroupHeaderMark(GuiGraphics graphics, CraftableListView.Cell cell, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        String mark = this.expandedGroups.contains(cell.groupKey()) ? "-" : "+";
        drawOutlinedChar(graphics, mark, x + 1, y + 1, GROUP_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 阴影字：同一个字画四遍——正下方、左、右各一道<b>纯黑</b>，正中再画本色。
     * <p>
     * 原来是朝 8 个方向各铺一遍黑再画本色（一个角标 9 次绘制），亮底上确实清楚，
     * 但玩家实测这套描边就是帧数的大头，先砍到只剩正下方一道。实测数字在亮底上
     * 左右边缘还是发虚，于是在下方之外补左右两道：4 遍仍是 9 遍的不到一半，
     * 唯独不描上缘——字的上半截几乎总是压在图标较暗的轮廓上，多那一遍看不见、白花钱。
     * 走原版字面阴影的话底色是「颜色压暗」而非纯黑，压在深灰铁锭上就没了硬底，不用。
     * <p>
     * 走 {@link FastGlyphText} 的快速通道：同一个字形图集、同样的四遍顺序，画出来
     * 和原来逐像素一致，但省掉了每遍的排版和对象分配；哪天反射挑不到唯一的字形集
     * 取器（或文字里出现数字以外的字），自动退回原来的四遍 {@code drawString}。
     * <p>
     * 调用约束两条：① 要在 {@code drawManaged} 批次里调——快速通道不逐遍自提交，
     * 见 {@link FastGlyphText#draw} 的提交契约；② 调用方要先把姿势栈抬到
     * {@link #GROUP_CHIP_Z}，否则图标会连阴影带字一起盖住。
     */
    private void drawOutlinedChar(GuiGraphics graphics, String text, int x, int y, int color) {
        FastGlyphText.draw(this.font, graphics, text, x, y, color);
    }

    /**
     * 列表格子上那一层角标：链式角标（紫色链环）、双向箭头（可换配方）、收藏星、折叠头「+/-」和数量数字。
     * <p>
     * <b>为什么要单独攒一层：</b>1.20.1 的 {@link GuiGraphics#drawString} 每画一小段字，
     * 末尾都会走 {@code flushIfUnmanaged()}——在普通界面里这等于「把攒着的绘制立刻提交掉」
     * （内部是 disableDepthTest → endBatch → enableDepthTest，一次真正的显卡提交）。
     * 一个阴影数字要画 4 遍，再叠上折叠头「+/-」等好几处字，25 格一起画
     * 逐笔提交就是几百次，帧数大头耗在这上面
     * （玩家实测：搜索框敲进 zzzz 让列表变空，帧数立刻回升，正是这个原因）。
     * 像素角标（链式/箭头/星）虽然只是一次次 fill，跟这些字同层攒批也顺路。
     * <p>
     * {@link GuiGraphics#drawManaged} 是官方留的出口：这段里的绘制先攒在缓冲区，
     * 出这段时统一提交一次。角标都落在物品图标之上、彼此不重叠，攒着画和一笔一笔画
     * 长得一模一样，所以这里放心合并。
     * <p>
     * 注意：<b>物品图标本身不能放进来</b>。{@code renderItem} 会在内部设置并还原 3D 物品光照
     * （{@code Lighting.setupFor3DItems()}），它的那次提交是不能省的，所以图标仍在前一遍单独画。
     */
    private void drawCellBadges(GuiGraphics graphics, int firstCell, int lastCell) {
        if (firstCell >= lastCell) {
            return;
        }
        graphics.drawManaged(() -> {
            // 分两趟：先画完所有「fill 类」（青色圈、链环/双向箭头/星星像素标、点阵数字），
            // 再画所有「文字类」（折叠头的 +/-）——两者走不同的显卡缓冲，
            // 同一格里交替画的话每换一次类型就把上一段冲掉一次，25 格就是几十次提交；
            // 同类攒齐再换，整批只提交两次（掉帧方案 A4）。
            // （数字自绘成点阵后也归 fill 类，两趟的活儿比原来更均匀。）
            for (int index = firstCell; index < lastCell; index++) {
                CraftableListView.Cell cell = this.display.get(index);
                int offset = index - firstCell;
                int x = this.leftPos + LIST_X + (offset % LIST_COLS) * CELL;
                int y = this.topPos + LIST_Y + (offset / LIST_COLS) * CELL;
                if (cell.header()) {
                    drawGroupHeaderRing(graphics, cell, x, y);
                    continue;
                }
                if (cell.entry().chain()) {
                    drawChainBadge(graphics, x, y);
                }
                if (cell.hasOtherOptions()) {
                    // 这件产物还有别的材料能做：左侧画一枚双向箭头（收藏星星正下方、数量数字左上，
                    // 见 drawAltBadge），折叠着时右键这一格换一份配方，展开时每种材料各自一格
                    drawAltBadge(graphics, x, y);
                }
                if (Favorites.isFavorite(cell.entry().result().getItem())) {
                    // 常用收藏：左上角一颗金色小星星（用户问题 8：「藏」字角标怪）。
                    drawFavBadge(graphics, x, y);
                }
                drawSmallCount(graphics, cell.entry().result(), x, y);
            }
            for (int index = firstCell; index < lastCell; index++) {
                CraftableListView.Cell cell = this.display.get(index);
                if (!cell.header()) {
                    continue;
                }
                int offset = index - firstCell;
                int x = this.leftPos + LIST_X + (offset % LIST_COLS) * CELL;
                int y = this.topPos + LIST_Y + (offset / LIST_COLS) * CELL;
                drawGroupHeaderMark(graphics, cell, x, y);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 问号按钮：点开展开面板、再点收回。排在队列面板之前——队列面板开着时点问号，
        // 一次点击就完成「收队列、开说明」，不用先关一层再点。
        if (button == 0 && helpBtnHit(mouseX, mouseY)) {
            toggleHelpPanel();
            return true;
        }
        // 队列面板开着：它吃下所有点击，窗外一律当「关面板」，绝不漏到下面的格子上去。
        if (this.queuePanelOpen) {
            return handleQueuePanelClick(mouseX, mouseY, button);
        }
        // 面板开着：按在滚动条上就起拖（滑块跟手、点轨道跳转）。
        // 浮层形态吃下所有点击——点 × 关、点窗外也关（和队列面板一个脾气）；
        // 滑出形态只吃面板内的点击，界外照常能用。
        if (this.helpPanelOpen) {
            int[] rect = helpPanelRect();
            boolean inside = pointIn(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
            if (button == 0 && inside && helpBarHit(mouseX, mouseY)) {
                this.helpScrollDragging = true;
                updateHelpScrollFromMouse(mouseY);
                return true;
            }
            if (!helpSlideFits()) {
                if (pointIn(mouseX, mouseY, rect[0] + rect[2] - 13, rect[1] + 4, 10, 10) || !inside) {
                    closeHelpPanel();
                    playClickSound();
                }
                return true;
            }
            // 滑出形态只吃落在面板上的点击；面板外直接放行往下走——右侧列表选物品、
            // 分类键切换统统照常（用户问题 2：以前这里的 return inside 把界外点击吞成「没点中」）。
            if (inside) {
                return true;
            }
        }
        if (handleTextBoxClick(mouseX, mouseY, button)) {
            return true;
        }
        ensureDisplay();
        // 点分类键排在一切手势之前：按在键上就只是切分类——哪怕同时按着 Alt/空格，
        // 也不该在"换了个分类"的同时顺手合成一份东西。
        // 右键同样算"点键"：轮换键沿反方向切回上一档（用户点名要的"右键往回切"），
        // 普通键右键和左键等效。
        if (button == 0 || button == 1) {
            CraftCategory.Key clickedTab = tabAt(mouseX, mouseY);
            if (clickedTab != null) {
                if (button == 0) {
                    selectTabKey(clickedTab);
                } else {
                    selectTabKeyBack(clickedTab);
                }
                return true;
            }
        }
        // 队列状态条：点一下开管理面板（它压不住合成、也不碰任何格子，放在分类键之后最省事）。
        if (button == 0 && this.queueStripHovered) {
            openQueuePanel();
            playClickSound();
            return true;
        }
        // 右键列表项：Shift+右键 = 收藏/取消「常用收藏」；普通右键 = 这件产物换一份配方。
        // 收藏切换排在换配方前面：钉住这件东西比轮换候选更重要，而且折叠头没有候选可换。
        if (button == 1) {
            int altIndex = cellIndexAt(mouseX, mouseY);
            if (altIndex >= 0) {
                CraftableListView.Cell cell = this.display.get(altIndex);
                if (!cell.header()) {
                    if (hasShiftDown()) {
                        toggleFavorite(cell.entry());
                        return true;
                    }
                    if (cell.entry().hasAlternatives()) {
                        cycleRecipe(cell.entry());
                        return true;
                    }
                }
            }
        }
        // 全部展开 / 全部折叠（默认 空格+左键，按键可改，和自动合成不再共用 Alt+左键）：
        // 点在右侧列表区域上生效——有收起的就全展开，全开着就全收起；列表里没有折叠项时不吃这一下点击。
        if (isHeldClickGesture(ModKeyMappings.EXPAND_ALL, button)
                && isInsidePanel(mouseX, mouseY) && toggleExpandAllGroups()) {
            return true;
        }
        // 批量合成（默认 左Ctrl+左键，按键可改）。两处入口：
        // 产物格 = 批量合成合成格里摆好的这份配方，不看材料是谁摆的，自己手动一格一格摆的也算；
        // 列表项 = 批量合成点中的那一项。两条都做到材料用完为止，走同一套服务端安全管道。
        if (isHeldClickGesture(ModKeyMappings.BATCH_CRAFT, button)) {
            if (isPointInResultSlot(mouseX, mouseY)) {
                if (!actionReady()) {
                    return true;
                }
                playClickSound();
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(),
                        null, false, true, parseBatchCount()));
                // 产物格批量：做的格子里摆好的这份配方，拿产物记一次
                noteCraftUse(this.menu.getSlot(SmartWorkbenchMenu.SLOT_RESULT).getItem());
                return true;
            }
            int batchIndex = cellIndexAt(mouseX, mouseY);
            if (batchIndex >= 0 && !this.display.get(batchIndex).header()) {
                if (!actionReady()) {
                    return true;
                }
                CraftableListView.Cell cell = this.display.get(batchIndex);
                this.selectedRecipe = cell.entry().recipeId();
                playClickSound();
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(),
                        cell.entry().recipeId(), true, true, parseBatchCount()));
                noteCraftUse(cell.entry().result());
                return true;
            }
        }
        // 自动合成键（默认 Alt+左键，也可以在按键绑定里改）：点在列表物品上就合成那一项，点别处就合成选中项。
        // 折叠头不再响应自动合成键——上一版「全部展开」借用了同一个手势，玩家反馈撞键；
        // 现在 Alt+左键点折叠头就落回普通左键，只展开/收起被点的那一类。
        if (isAutoCraftGesture(button)) {
            if (this.menu.getCarried().isEmpty() && isPointInResultSlot(mouseX, mouseY)) {
                if (actionReady()) {
                    autoCraftGrid();
                }
                return true;
            }
            int autoIndex = cellIndexAt(mouseX, mouseY);
            if (autoIndex >= 0) {
                CraftableListView.Cell cell = this.display.get(autoIndex);
                if (!cell.header()) {
                    if (actionReady()) {
                        selectAndAutoCraft(cell);
                    }
                    return true;
                }
            } else if (isInsidePanel(mouseX, mouseY)) {
                // 只吃面板里的点击：以前点背包格、点面板外空白也会触发自动合成，
                // 而且这一下点击被吞掉、格子点不到。
                if (actionReady()) {
                    autoCraftSelected();
                }
                return true;
            }
        }
        if (button == 0) {
            if (isPointInScrollBar(mouseX, mouseY) && maxScroll() > 0) {
                this.draggingScroll = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
            if (isPointInButton(mouseX, mouseY)) {
                this.refreshPressed = true;
                if (!actionReady()) {
                    return true;
                }
                playClickSound();
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_REFRESH);
                return true;
            }
            if (isPointInAutoButton(mouseX, mouseY)) {
                this.autoPressed = true;
                if (actionReady()) {
                    autoCraftSelected();
                }
                return true;
            }
            if (isPointInFoldButton(mouseX, mouseY)) {
                this.foldPressed = true;
                toggleFolding();
                return true;
            }
            if (isPointInSortButton(mouseX, mouseY)) {
                this.sortPressed = true;
                cycleSortMode();
                return true;
            }
            if (isPointInRefillButton(mouseX, mouseY)) {
                if (!actionReady()) {
                    return true;
                }
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_AUTO_REFILL);
                playClickSound();
                return true;
            }
            if (isPointInClearButton(mouseX, mouseY)) {
                this.clearPressed = true;
                playClickSound();
                // 格子空着就别发这个包了：服务端除了回一句"没材料"什么也不做，白跑一趟
                if (isCraftGridEmpty()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_nothing", "合成格里没有材料"),
                                true);
                    }
                } else {
                    if (!actionReady()) {
                        return true;
                    }
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                            SmartWorkbenchMenu.BUTTON_CLEAR_GRID);
                }
                return true;
            }
            int index = cellIndexAt(mouseX, mouseY);
            if (index >= 0) {
                CraftableListView.Cell cell = this.display.get(index);
                if (cell.header()) {
                    playClickSound();
                    toggleGroup(cell.groupKey());
                    return true;
                }
                // Shift 点击 = 直接用存储或背包里的材料合成一次；普通点击 = 把材料配到合成格。
                // 批量合成不在这里：它由上面的批量合成键独立处理，两个手势不再挤同一次点击。
                // 不管哪种，都顺手把它记成"选中项"，供自动合成按钮/快捷键使用
                if (!actionReady()) {
                    // 服务端 4 tick 内只处理一个包；轮不到就别改选中项，免得金边指向被丢掉的配方
                    return true;
                }
                this.selectedRecipe = cell.entry().recipeId();
                playClickSound();
                boolean directCraft = hasShiftDown();
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(), cell.entry().recipeId(),
                        directCraft, false, 0));
                if (directCraft) {
                    // 真正出手合成了一次才算「常用」；普通点击只是往格子里摆料，不记
                    noteCraftUse(cell.entry().result());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 客户端冷却：和服务端 acceptAction 的「每 4 tick 一个重操作」保持一致。 */
    private boolean actionReady() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return true;
        }
        long now = this.minecraft.level.getGameTime();
        if (now < this.clientNextActionTick) {
            return false;
        }
        this.clientNextActionTick = now + 4L;
        return true;
    }

    /**
     * 「按住某个键 + 鼠标点击」的手势判定。
     * <p>
     * 键取的是 {@link KeyMapping} 当前绑定的值——也就是玩家在游戏按键设置里自己配的那个键——
     * 每一下点击都实时查 GLFW 的按下状态，改绑之后立刻生效，不用重开游戏。
     * <ul>
     *   <li>绑成键盘键：按住该键 + 左键；</li>
     *   <li>绑成鼠标键：按那个键本身就是手势（比如绑右键 = 右键直接触发），不再要求左键；</li>
     *   <li>Forge 的修饰键（Ctrl/Shift/Alt）也要满足，否则玩家设的「Shift+E」会退化成裸 E。</li>
     * </ul>
     */
    private boolean isHeldClickGesture(KeyMapping mapping, int button) {
        if (this.minecraft == null || this.minecraft.getWindow() == null || !modifierActive(mapping)) {
            return false;
        }
        long window = this.minecraft.getWindow().getWindow();
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return key.getValue() >= 0 && button == key.getValue()
                    && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() >= 0
                && InputConstants.isKeyDown(window, key.getValue());
    }

    /** 玩家给按键绑的 Forge 修饰键此刻是否按着（NONE 表示没有额外要求）。 */
    private static boolean modifierActive(KeyMapping mapping) {
        KeyModifier modifier = mapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
    }

    /**
     * 手势键的显示名（如「左 Ctrl」「空格」）；未绑定返回 null，对应的提示行就整条不显示。
     * <p>
     * 返回字符串而不是组件：提示文案都带英文回退，%s 落进 {@code String.format} 时
     * 字符串在两条路径（有翻译 / 用回退）下长得一模一样，组件的 toString 会变成一坨 JSON。
     */
    private String gestureKeyName(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getValue() < 0) {
            return null;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return key.getDisplayName().getString();
        }
        // 绑成鼠标键也要给提示，否则玩家改完键位后界面上完全看不出该怎么用。
        return mapping.getTranslatedKeyMessage().getString();
    }

    /**
     * 完整键位描述（如「Alt+鼠标左键」）：给自动合成那种「修饰键+鼠标」的绑定用。
     * Forge 把 {@link KeyMapping#getTranslatedKeyMessage()} 打成「修饰键在前、键名在后」的组合显示
     * （内部走 KeyModifier.getCombinedName，NONE 时只剩键名），取字符串即可，不能再自己拼一遍修饰键。
     */
    private String fullKeybindText(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    /** 点搜索框或数量框就聚焦；点别处取消焦点，避免快捷键被输入框吃掉。 */
    private boolean handleTextBoxClick(double mouseX, double mouseY, int button) {
        boolean inSearch = this.searchBox != null && isPointInSearchBox(mouseX, mouseY);
        boolean inBatch = this.batchBox != null && isPointInBatchBox(mouseX, mouseY);
        if (inSearch || inBatch) {
            if (this.searchBox != null) {
                this.searchBox.setFocused(inSearch);
            }
            if (this.batchBox != null) {
                this.batchBox.setFocused(inBatch);
            }
            if (inSearch) {
                this.searchBox.mouseClicked(mouseX, mouseY, button);
            } else {
                this.batchBox.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        if (this.batchBox != null) {
            this.batchBox.setFocused(false);
        }
        return false;
    }

    /** 记下选中项并直接自动合成（Alt+左键点某个具体物品）。 */
    private void selectAndAutoCraft(CraftableListView.Cell cell) {
        this.selectedRecipe = cell.entry().recipeId();
        sendAutoCraft(cell.entry());
    }

    /**
     * 全部展开 / 全部折叠（默认 空格+左键，按键可改）：列表里还有收起的就全部展开，
     * 已经全展开就全部收起。列表压根没有折叠项（同类折叠关着）时什么都不做、返回 false，
     * 免得白吃一下普通左键。
     */
    private boolean toggleExpandAllGroups() {
        int headers = 0;
        int collapsed = 0;
        for (CraftableListView.Cell cell : this.display) {
            if (cell.header()) {
                headers++;
                if (!this.expandedGroups.contains(cell.groupKey())) {
                    collapsed++;
                }
            }
        }
        if (headers == 0) {
            return false;
        }
        if (collapsed > 0) {
            for (CraftableListView.Cell cell : this.display) {
                if (cell.header()) {
                    this.expandedGroups.add(cell.groupKey());
                }
            }
        } else {
            this.expandedGroups.clear();
        }
        this.displayDirty = true;
        playClickSound();
        return true;
    }

    /** 展开 / 收起某一类。 */
    private void toggleGroup(String groupKey) {
        if (!this.expandedGroups.remove(groupKey)) {
            this.expandedGroups.add(groupKey);
        }
        this.displayDirty = true;
    }

    /** 折叠总开关：关掉时把展开状态一并清掉，下次打开回到"全部收起"。 */
    private void toggleFolding() {
        this.folding = !this.folding;
        if (!this.folding) {
            this.expandedGroups.clear();
        }
        this.displayDirty = true;
        playClickSound();
    }

    /** 排序按钮：默认 → 名称 → 常用 → 默认，换档立刻记住，下次打开还是这档。 */
    private void cycleSortMode() {
        this.sortMode = this.sortMode.next();
        this.scroll = 0;
        this.displayDirty = true;
        ensureDisplay();
        playClickSound();
        ClientPrefs.saveSortMode(this.sortMode);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.sort_mode",
                            "排序方式：%s（点「排序」按钮换下一档）", this.sortMode.label().getString()), true);
        }
    }

    /** Shift+右键列表项：收藏/取消「常用收藏」。 */
    private void toggleFavorite(CraftableEntry entry) {
        boolean nowPinned = Favorites.toggle(entry.result());
        this.displayDirty = true;
        ensureDisplay();
        invalidateTooltip();
        playClickSound();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    nowPinned
                            ? labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.fav_pinned",
                                    "已收藏「%s」：一直排在列表最前面", entry.result().getHoverName().getString())
                            : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.fav_unpinned",
                                    "已取消收藏「%s」（再多做几次会自己收回来）", entry.result().getHoverName().getString()),
                    true);
        }
    }

    /**
     * 记一次「出手合成」：给常用收藏计数。刚跨过门槛自动转正时提醒一句。
     * <p>
     * 计数口径是<b>点了一次会真正合成的操作</b>（Shift 直接合成、批量、自动合成各算一次点），
     * 纯浏览、往格子里摆料不算；服务端偶尔拒单会多记一次，对「常用」判断无关紧要。
     */
    private void noteCraftUse(ItemStack result) {
        invalidateTooltip();
        if (Favorites.recordUse(result) && this.minecraft != null && this.minecraft.player != null) {
            this.displayDirty = true;
            ensureDisplay();
            this.minecraft.player.displayClientMessage(
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.fav_auto_pinned",
                            "「%s」做过 %s 次了，自动收藏到列表最前面（Shift+右键可取消）",
                            result.getHoverName().getString(), Favorites.AUTO_PIN_COUNT), true);
        }
    }

    /**
     * 就地合成快捷键（默认 C）：鼠标停在下方背包的某件物品上按一下，
     * 立刻在右侧列表里找到「能做出这件东西」的配方、选中并翻到它面前。
     * <p>
     * 找不到时按原因分两条提示：压根没有这份产物的配方 / 有配方但这次的材料凑不齐——
     * 前者列表里永远不会出现，后者刷新一下库存可能就出来了。
     */
    private void locateRecipeForHoveredItem() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()
                || slot.index < SmartWorkbenchMenu.INV_START || slot.index >= SmartWorkbenchMenu.INV_END) {
            this.minecraft.player.displayClientMessage(
                    label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.locate_no_item",
                            "鼠标先停在背包里的物品上，再按寻配方键"), true);
            return;
        }
        ItemStack stack = slot.getItem();
        ensureDisplay();
        CraftableEntry match = null;
        for (CraftableEntry entry : this.effectiveCraftables) {
            if (entry.result().is(stack.getItem())) {
                if (match == null) {
                    match = entry;
                }
                if (ItemStack.isSameItemSameTags(entry.result(), stack)) {
                    match = entry; // 连 NBT 都对得上的是最理想的那条，直接要它
                    break;
                }
            }
        }
        if (match == null) {
            this.minecraft.player.displayClientMessage(
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.locate_no_recipe",
                            "「%s」没有可合成的配方，或附近材料凑不齐", stack.getHoverName().getString()), true);
            return;
        }
        revealEntry(match);
        this.selectedRecipe = match.recipeId();
        playClickSound();
        this.minecraft.player.displayClientMessage(
                labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".msg.locate_done",
                        "已选中「%s」的配方：点它摆料，%s 直接合成",
                        match.result().getHoverName().getString(),
                        fullKeybindText(ModKeyMappings.AUTO_CRAFT)), true);
    }

    /**
     * 把一条配方露出来并滚到眼前：先按当前搜索/分类找，找不到就清掉搜索、
     * 切回「全部」再找（临时看货，不改玩家记住的分类偏好），还藏在折叠组里就展开那一组。
     */
    private void revealEntry(CraftableEntry entry) {
        int at = indexOfRecipe(entry.recipeId());
        if (at < 0 && this.searchBox != null && !this.searchBox.getValue().isEmpty()) {
            this.searchBox.setValue("");
            this.category = CraftCategory.ALL;
            this.displayDirty = true;
            ensureDisplay();
            at = indexOfRecipe(entry.recipeId());
        }
        if (at < 0) {
            String key = CraftableListView.groupKey(entry.result());
            if (key != null && this.expandedGroups.add(key)) {
                this.displayDirty = true;
                ensureDisplay();
                at = indexOfRecipe(entry.recipeId());
            }
        }
        if (at < 0) {
            return; // 列表刚被服务端换掉（库存变了）：选中的金边还在，只是这一屏露不出来
        }
        int row = at / LIST_COLS;
        if (row < this.scroll) {
            this.scroll = row;
        } else if (row >= this.scroll + LIST_ROWS) {
            this.scroll = row - LIST_ROWS + 1;
        }
    }

    /** 配方在当前的显示列表里排第几格；没露出来返回 -1。 */
    private int indexOfRecipe(ResourceLocation recipeId) {
        for (int i = 0; i < this.display.size(); i++) {
            CraftableListView.Cell cell = this.display.get(i);
            if (!cell.header() && cell.entry().recipeId().equals(recipeId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 悬停溯源：换了一条配方就问服务端一次「这份配方的每样材料都在哪儿」，
     * 答案（{@code menu.getClientTraceFor}）一到，提示框里多一段"材料在哪"，
     * 世界里的箱子同时描上金框。
     * <p>
     * 发包只看四种情形：悬停的配方<b>真变了</b>；存储快照刚更新过（{@link #traceRefreshPending}
     * 强制重问）；上一问隔了半秒多还没等到回包（多半是列表刷新把缓存作废了）；
     * 或者答案已经放了两秒（箱子里的材料可能变了）。
     * 服务端另有 3 tick 的节流，被节流挡下的最后一问会在冷却到期后补算，
     * 鼠标沿列表一路拖过去也不会烧穿扫描预算。
     */
    private void requestTrace(CraftableEntry entry) {
        ResourceLocation id = entry.recipeId();
        if (id == null || this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        // 轻量存储快照到了（包只敲菜单上的旗，见 S2CStorageSnapshotPacket）：
        // 这一帧必须重问一次服务端。缓存不清——旧答案照常显示，新答案到了整包换掉，中间不闪白。
        // 单把 lastTraceRequestTick 打回 0 不够：缓存还有旧答案时 unanswered 不成立，
        // 世界时间还没到 40 时 stale 也不成立，于是要拖到第 40 tick（约 2 秒）才发。
        // 用 pending 旗标把「快照到了」本身变成一次明确的强制重问，下一帧就发出去。
        if (this.menu.consumeClientStorageTraceDirty()) {
            this.lastTraceRequestTick = 0L;
            this.traceRefreshPending = true;
        }
        List<S2CTraceResponsePacket.MaterialHit> cached = this.menu.getClientTraceFor(id);
        long now = this.minecraft.level.getGameTime();
        boolean changed = !id.equals(this.traceRequestedFor);
        // 问出去 0.5 秒还没等到答案就补问一次（服务端自己按配方缓存，重复问不打架）：
        // 覆盖「列表刷新把缓存作废了但悬停没变」这种情形，不再等满 2 秒。
        boolean unanswered = cached.isEmpty() && now - this.lastTraceRequestTick > 10L;
        // 悬停没变但答案放了两秒：箱子里材料可能已经变了，重问一次（服务端自己还有 3 tick 节流）
        boolean stale = now - this.lastTraceRequestTick >= TRACE_REFRESH_TICKS;
        // 存储快照刚更新：不管缓存空不空、世界时间走到哪，这一帧都重问一次。
        boolean refresh = this.traceRefreshPending;
        if (changed || refresh || unanswered || stale) {
            this.traceRequestedFor = id;
            this.lastTraceRequestTick = now;
            this.traceRefreshPending = false;
            ModNetwork.sendToServer(new C2STraceRequestPacket(this.menu.getBenchPos(), id));
        }
        // 金框每帧照当前这一条的溯源结果对表：有结果就亮着（续命），没有就撤
        TraceHighlights.sync(traceGroups(cached));
    }

    /**
     * 溯源结果里的<b>整台容器格组</b>（按锚点去重、封顶跟高亮登记处同一上限）：
     * 一台大箱子一组两格，金框并成一个整框（r239，口径同 F12 总览的分组清单）；
     * 精妙背包/身上这类没有坐标，天然进不来。
     */
    private static List<List<BlockPos>> traceGroups(List<S2CTraceResponsePacket.MaterialHit> hits) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<List<BlockPos>> groups = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (S2CTraceResponsePacket.MaterialHit hit : hits) {
            for (S2CTraceResponsePacket.Spot spot : hit.spots()) {
                if (spot.pos() == null) {
                    continue;
                }
                // cells 解码端保证非空且含锚点；万一为空，退回锚点单格也比漏一个框好
                List<BlockPos> cells = spot.cells().isEmpty() ? List.of(spot.pos()) : spot.cells();
                if (seen.add(spot.pos())) {
                    groups.add(cells);
                    if (groups.size() >= TraceHighlights.MAX_BOXES) {
                        return groups;
                    }
                }
            }
        }
        return groups;
    }

    /** 这一下点击是不是自动合成键（默认 Alt+左键，也可以在按键设置里改）。 */
    private boolean isAutoCraftGesture(int button) {
        if (!ModKeyMappings.AUTO_CRAFT.matchesMouse(button)) {
            return false;
        }
        KeyModifier modifier = ModKeyMappings.AUTO_CRAFT.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
    }

    /** 自动合成当前选中的配方；没有选中时改用合成格当前配方。 */
    private void autoCraftSelected() {
        CraftableEntry entry = findSelected();
        if (entry == null) {
            autoCraftGrid();
            return;
        }
        sendAutoCraft(entry);
    }

    private void autoCraftGrid() {
        if (this.minecraft == null || this.minecraft.player == null
                || !this.menu.getSlot(SmartWorkbenchMenu.SLOT_RESULT).hasItem()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.grid_empty",
                                "合成格里还没有能合成的配方"), true);
            }
            return;
        }
        playClickSound();
        ModNetwork.sendToServer(new C2SAutoCraftGridPacket(this.menu.getBenchPos()));
    }

    /** 按配方 id 在最新的可合成列表里找回选中项：列表被搜索/折叠/刷新后也找得回来。 */
    private CraftableEntry findSelected() {
        if (this.selectedRecipe == null) {
            return null;
        }
        // 用换过配方之后的那份列表：玩家右键选中的那份配方，自动合成要合成的也是它。
        ensureDisplay();
        for (CraftableEntry entry : this.effectiveCraftables) {
            if (this.selectedRecipe.equals(entry.recipeId())) {
                return entry;
            }
        }
        return null;
    }

    private void sendAutoCraft(CraftableEntry entry) {
        playClickSound();
        ModNetwork.sendToServer(new C2SAutoCraftPacket(this.menu.getBenchPos(), entry.recipeId()));
        noteCraftUse(entry.result());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 数量数字样式切换（默认 F8，键位在「控制 → 按键绑定 → 智能工作台」里改）：
        // 在「原版字体」和「点阵数字」两款之间来回换，切换时在动作栏报一句当前是哪款。
        // 选中的那一款写进客户端配置（config/smart_workbench-client.toml），重开游戏仍记得。
        // 判断放在文本框之前（审查报告问题④）：搜索框 / 批量框 / 队列数量框聚焦时也能按 F8 换款，
        // 不用先点空白处。唯一的例外是「这个键在文本框里本来要打出字来」（字母、数字、空格）——
        // 那还是让给文本框，免得把样式键绑成字母后，搜索框里再也打不出那个字母。
        if (ModKeyMappings.TOGGLE_DIGIT_STYLE.matches(keyCode, scanCode)
                && modifierActive(ModKeyMappings.TOGGLE_DIGIT_STYLE)
                && !(textBoxFocused() && typesCharacter(keyCode))) {
            toggleDigitStyle();
            return true;
        }
        // 队列数量输入框（双击数量弹出的）优先：回车收下这个数、Esc 只把框收掉，
        // 其余按键一律喂给它，免得打字误触快捷键或被 E 关掉整个界面。
        if (this.queueQtyBox != null && this.queueQtyBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelQueueQtyEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitQueueQtyEdit();
                return true;
            }
            this.queueQtyBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // 搜索框聚焦时，键盘优先给搜索框：Esc/回车退出输入，其余按键一律吞掉，
        // 免得打字时误触刷新、自动合成快捷键，或者被 E 键把整个界面关掉。
        EditBox focusedBox = this.searchBox != null && this.searchBox.isFocused() ? this.searchBox
                : (this.batchBox != null && this.batchBox.isFocused() ? this.batchBox : null);
        if (focusedBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                focusedBox.setFocused(false);
                return true;
            }
            focusedBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // Ctrl+F 聚焦搜索框，像 REI 一样顺手
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && this.searchBox != null) {
            this.searchBox.setFocused(true);
            return true;
        }
        // 队列面板开着：Esc 只关面板，不把整个界面一起关掉。
        if (this.queuePanelOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeQueuePanel();
            return true;
        }
        // 操作说明面板同理：Esc 先收面板。
        if (this.helpPanelOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeHelpPanel();
            return true;
        }
        // 说明面板开着时，PageUp/PageDown/Home/End 也能翻正文：
        // 万一滚轮被别的模组接走了、或者触控板滚不动，键盘还有一条路。
        // 只认这四个键，V/G/B 这些快捷键照旧不受影响。
        if (this.helpPanelOpen) {
            int max = helpMaxScroll();
            int page = Math.max(1, helpViewportH() - helpLineHeight());
            if (max > 0) {
                if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                    this.helpScroll = Mth.clamp(this.helpScroll + page, 0, max);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                    this.helpScroll = Mth.clamp(this.helpScroll - page, 0, max);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_HOME) {
                    this.helpScroll = 0;
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_END) {
                    this.helpScroll = max;
                    return true;
                }
            }
        }
        // 就地合成快捷键（默认 C）：停在背包物品上按一下，去右侧列表里寻它的配方
        if (ModKeyMappings.CRAFT_HERE.matches(keyCode, scanCode)
                && ModKeyMappings.CRAFT_HERE.getKeyModifier().isActive(KeyConflictContext.GUI)) {
            locateRecipeForHoveredItem();
            return true;
        }
        // 收藏键（默认 B）：钉住 / 摘掉鼠标停着的那一项。Shift+右键那一下也照常能用，两个入口并存。
        if (ModKeyMappings.FAVORITE.matches(keyCode, scanCode) && modifierActive(ModKeyMappings.FAVORITE)) {
            favoriteFromHover();
            return true;
        }
        // 加入队列键（默认 V）：把停在右侧列表上的那一项按「批量数量」框的个数排进队列。
        if (ModKeyMappings.QUEUE_ADD.matches(keyCode, scanCode) && modifierActive(ModKeyMappings.QUEUE_ADD)) {
            queueAddFromHover();
            return true;
        }
        // 队列开关键（默认 G）：没跑就跑起来，正跑就停下；进度留着，再按接着做。
        if (ModKeyMappings.QUEUE_TOGGLE.matches(keyCode, scanCode) && modifierActive(ModKeyMappings.QUEUE_TOGGLE)) {
            queueToggleStart();
            return true;
        }
        if (ModKeyMappings.REFRESH.matches(keyCode, scanCode)) {
            playClickSound();
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    SmartWorkbenchMenu.BUTTON_REFRESH);
            return true;
        }
        if (ModKeyMappings.AUTO_CRAFT.matches(keyCode, scanCode)
                && ModKeyMappings.AUTO_CRAFT.getKeyModifier().isActive(KeyConflictContext.GUI)) {
            autoCraftSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.queueQtyBox != null && this.queueQtyBox.isFocused()) {
            this.queueQtyBox.charTyped(codePoint, modifiers);
            return true;
        }
        EditBox focusedBox = this.searchBox != null && this.searchBox.isFocused() ? this.searchBox
                : (this.batchBox != null && this.batchBox.isFocused() ? this.batchBox : null);
        if (focusedBox != null && focusedBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return focusedBox != null || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 文本框自己也要收到松开事件，否则它内部的拖动状态会一直挂着。
        EditBox box = focusedTextBox();
        if (box != null && box.mouseReleased(mouseX, mouseY, button)) {
            this.refreshPressed = false;
            this.autoPressed = false;
            this.foldPressed = false;
            this.sortPressed = false;
            this.clearPressed = false;
            this.draggingScroll = false;
            this.helpScrollDragging = false;
            return true;
        }
        // 松开鼠标就取消按钮和滚动条的按住状态。
        this.refreshPressed = false;
        this.autoPressed = false;
        this.foldPressed = false;
        this.sortPressed = false;
        this.clearPressed = false;
        this.helpScrollDragging = false;
        boolean wasDragging = this.draggingScroll;
        this.draggingScroll = false;
        return wasDragging || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 说明面板的滚动条滑块拖动中：正文跟着手走
        if (this.helpScrollDragging && button == 0) {
            updateHelpScrollFromMouse(mouseY);
            return true;
        }
        if (this.draggingScroll && button == 0) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        // 文本框的拖动/选区事件以前没转发：在搜索框里拖选文字、拖动光标都没反应。
        EditBox box = focusedTextBox();
        if (box != null && box.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 当前聚焦的文本框（队列数量框优先，它在时别的框都被迫让位）；都没聚焦返回 null。 */
    private EditBox focusedTextBox() {
        if (this.queueQtyBox != null && this.queueQtyBox.isFocused()) {
            return this.queueQtyBox;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox;
        }
        if (this.batchBox != null && this.batchBox.isFocused()) {
            return this.batchBox;
        }
        return null;
    }

    /** 原版按钮和配方书用的点击音效，给刷新按钮、列表项一个"点到了"的反馈。 */
    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /** 居中绘制文字；MC 自带的 drawCenteredString 没有阴影参数，只能自己算宽度。 */
    private void drawCentred(GuiGraphics graphics, Component text, int centerX, int y, int color, boolean shadow) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, shadow);
    }

    /**
     * 居中绘制，宽度超出上限时整体等比缩小（最低 0.5 倍）。
     * <p>
     * 按钮只有 30 像素宽，英文「Refresh」「Craftable」在默认字号下会顶出按钮/标题区；
     * 中文没问题不代表英文没问题，换任何语言都按同一套规则收进去。
     */
    private void drawCentredFitted(GuiGraphics graphics, Component text, int centerX, int y,
                                   int color, int maxWidth) {
        int width = this.font.width(text);
        if (maxWidth <= 0 || width <= maxWidth) {
            drawCentred(graphics, text, centerX, y, color, false);
            return;
        }
        if (width > maxWidth * 2) {
            // 连 0.5 倍都放不下（德语/法语的长按钮名）：截断加省略号，别糊出按钮外
            text = Component.literal(ellipsized(text.getString(), maxWidth));
            width = this.font.width(text);
        }
        float scale = Math.max(0.5F, (float) maxWidth / (float) width);
        float scaledWidth = width * scale;
        graphics.pose().pushPose();
        // 缩放后行高也变小，纵向补回一半，字仍然落在原来的行里
        graphics.pose().translate(centerX - scaledWidth / 2.0F,
                y + this.font.lineHeight * (1.0F - scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** 左对齐版本：用于固定坐标的标签，宽度超了就缩小，不往右边的输入框上压。 */
    private void drawLeftFitted(GuiGraphics graphics, Component text, int x, int y,
                                int color, int maxWidth) {
        int width = this.font.width(text);
        if (maxWidth <= 0 || width <= maxWidth) {
            graphics.drawString(this.font, text, x, y, color, false);
            return;
        }
        if (width > maxWidth * 2) {
            text = Component.literal(ellipsized(text.getString(), maxWidth));
            width = this.font.width(text);
        }
        float scale = Math.max(0.5F, (float) maxWidth / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + this.font.lineHeight * (1.0F - scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * 居中版本的缓存宽度重载（方案 A2）：文案早就量好宽度，这里连排版都不用碰，
     * 直接进画字那一步。放不下时的兜底逻辑与 Component 版一致。
     */
    private void drawCentredFitted(GuiGraphics graphics, Fitted text, int centerX, int y,
                                   int color, int maxWidth) {
        int width = text.width();
        if (maxWidth <= 0 || width <= maxWidth) {
            graphics.drawString(this.font, text.text(), centerX - width / 2, y, color, false);
            return;
        }
        drawCentredFitted(graphics, text.text(), centerX, y, color, maxWidth);
    }

    /** 左对齐版本的缓存宽度重载（方案 A2）。 */
    private void drawLeftFitted(GuiGraphics graphics, Fitted text, int x, int y,
                                int color, int maxWidth) {
        if (maxWidth <= 0 || text.width() <= maxWidth) {
            graphics.drawString(this.font, text.text(), x, y, color, false);
            return;
        }
        drawLeftFitted(graphics, text.text(), x, y, color, maxWidth);
    }

    /** 按「0.5 倍仍放得下」的物理宽度截断，末尾补省略号。 */
    private String ellipsized(String raw, int maxWidth) {
        String dots = "...";
        int budget = Math.max(1, maxWidth * 2 - this.font.width(dots));
        return this.font.plainSubstrByWidth(raw, budget) + dots;
    }

    private void drawCentred(GuiGraphics graphics, FormattedCharSequence text, int centerX, int y,
                             int color, boolean shadow) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, shadow);
    }

    /**
     * 事件总线抢先层（{@link HelpWheelGuard}）在滚轮事件还没传进界面之前调这里：
     * <b>鼠标压在说明面板上</b>、正文在这一记方向上还能滚，才把正文滚掉并标记已处理——
     * 就算整合包里哪个模组在半路把这记滚轮吞了（REI、小地图、Mouse Tweaks
     * 都是出了名的滚轮狂），面板也已经滚到位。事件后来正常传进
     * {@link #mouseScrolled} 时凭标记原样吞下，不会滚两次。
     * <p>
     * 上一版这里不看鼠标位置：面板一开就把全屏的滚轮都抢走，右边的可合成列表
     * 直接滚不动。现在跟点击一个脾气——指针在面板上才归面板，面板外原样放行。
     * <p>
     * <b>关于旧标记的清理（审查报告问题 1）</b>：上一记滚轮若在半路被别的模组取消，
     * {@link #mouseScrolled} 压根不会被调到，标记就没人消费；残留到下一记，
     * 会把一记本来该给列表的滚轮白白吞掉。事件总线的配对关系很死：
     * 每一记滚轮先过总线、后（可能）进界面——所以总线每见一记新的就先把旧标记
     * 清掉：旧事件要么已经配过对，要么永远配不了对，标记留着只有害。
     *
     * @param alreadyCanceled 这记事件到我们手上时是否已被别的模组取消（取消了就别抢，
     *                        对面已经处理了这记滚轮）
     * @return 面板是否真的滚了这一格（调用方据此取消事件，别人就不必在同一记上叠操作——问题 6）
     */
    boolean catchHelpWheel(double mouseX, double mouseY, double delta, boolean alreadyCanceled) {
        // 上一记的遗留标记到此为止（见方法注释）。
        this.helpWheelCaught = false;
        if (alreadyCanceled || !this.helpPanelOpen || delta == 0.0D) {
            return false;
        }
        int[] rect = helpPanelRect();
        if (!pointIn(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
            return false;
        }
        // 到顶/到底、或正文短到一屏放得下：松手放行，这一记滚轮还回去。
        if (!helpCanScrollIn(delta)) {
            return false;
        }
        if (!this.helpBusProbeLogged) {
            this.helpBusProbeLogged = true;
            LOGGER.debug("[smart_workbench] 滚轮(总线)：鼠标=({}, {}) 面板=({}, {}, {}, {}) 可滚={} 当前位置={}",
                    (int) mouseX, (int) mouseY, rect[0], rect[1], rect[2], rect[3], helpMaxScroll(), this.helpScroll);
        }
        int max = helpMaxScroll();
        this.helpScroll = Mth.clamp(this.helpScroll - (int) Math.signum(delta) * Math.max(22, helpLineHeight() * 2),
                0, max);
        this.helpWheelCaught = true;
        return true;
    }

    /** 说明面板在滚轮这个方向上还能不能滚：滚轮值为 0、已到顶/到底、或内容不满一屏，都算不能。 */
    private boolean helpCanScrollIn(double delta) {
        if (delta == 0.0D) {
            return false;
        }
        int max = helpMaxScroll();
        if (max <= 0) {
            return false;
        }
        return delta > 0.0D ? this.helpScroll > 0 : this.helpScroll < max;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 诊断行：面板开合后头一记走到界面层的滚轮，不管总线那层抢没抢过，都如实打一条。
        // 上一版这行排在「已被总线滚过」的 return 后面，永远出不来，日志只剩「总线」一条线索。
        if (!this.helpGuiProbeLogged && this.helpPanelOpen) {
            this.helpGuiProbeLogged = true;
            int[] rect = helpPanelRect();
            LOGGER.debug("[smart_workbench] 滚轮(界面)：鼠标=({}, {}) 面板=({}, {}, {}, {}) 可滚={} 当前位置={} 总线已滚={}",
                    (int) mouseX, (int) mouseY, rect[0], rect[1], rect[2], rect[3],
                    helpMaxScroll(), this.helpScroll, this.helpWheelCaught);
        }
        // 总线抢先层已经滚过这一记：吞掉，不重复滚。
        if (this.helpWheelCaught) {
            this.helpWheelCaught = false;
            return true;
        }
        // 说明面板：鼠标压在面板上、这个方向还能滚，才归面板滚正文——
        // 跟点击一个脾气。上一版「面板开=吃掉全屏滚轮」，面板一开右边列表就滚不动；
        // 反过来指针在面板外（含滚到顶/到底）一律放行，下面的队列行调整、
        // 可合成列表照常吃这记滚轮。
        if (this.helpPanelOpen && delta != 0.0D && helpCanScrollIn(delta)) {
            int[] rect = helpPanelRect();
            if (pointIn(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
                this.helpScroll = Mth.clamp(this.helpScroll - (int) Math.signum(delta) * Math.max(22, helpLineHeight() * 2),
                        0, helpMaxScroll());
                return true;
            }
        }
        // 队列面板开着时：停在某一行上滚轮就是调它的目标数量（Shift 一档十个）。
        if (this.queuePanelOpen) {
            // 正在打字时滚轮只负责"收下这个数"，不叠加 ±1，免得数字在指下自己跳。
            if (this.queueQtyBox != null) {
                commitQueueQtyEdit();
                return true;
            }
            int row = queuePanelRowAt(mouseX, mouseY);
            if (row >= 0) {
                CraftQueue.Entry entry = CraftQueue.entries().get(row);
                CraftQueue.adjust(entry.id, (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
                invalidateTooltip();
            }
            return true;
        }
        if (isInsidePanel(mouseX, mouseY) && maxScroll() > 0) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // 产物格：先摘掉原版悬停槽位，压住 super 里那份光秃秃的物品提示框，
        // 等下换成本界面自己拼的（原版内容 + 一行「Ctrl+点击可以批量合成」）。
        Slot hoveredSlot = this.hoveredSlot;
        boolean overResult = hoveredSlot != null && hoveredSlot.index == SmartWorkbenchMenu.SLOT_RESULT
                && hoveredSlot.hasItem() && this.menu.getCarried().isEmpty();
        if (overResult) {
            this.hoveredSlot = null;
        }
        // 原版这句只会处理"鼠标下面是真 Slot"的情况；右侧 5x5 列表里的图标是我们自己画的假图标，
        // 所以下面再补一份列表项的提示框。
        super.renderTooltip(graphics, mouseX, mouseY);
        ensureDisplay();
        // 分类键的提示框：档名 + 这一键眼下有多少项 + 点了会怎样（轮换键连下一站一起报）。
        CraftCategory.Key hoveredTab = tabAt(mouseX, mouseY);
        if (hoveredTab != null) {
            graphics.renderTooltip(this.font, tabTooltip(hoveredTab), Optional.empty(), mouseX, mouseY);
            this.listHintDrawn = true;
            // 停在分类键上不算停在配方上：金框同样撤掉
            this.traceRequestedFor = null;
            TraceHighlights.sync(List.of());
            return;
        }
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0 && index < this.display.size()) {
            CraftableListView.Cell cell = this.display.get(index);
            if (!cell.header()) {
                requestTrace(cell.entry());
            } else {
                // 折叠头是一份"堆"不是具体配方：溯源查询作废、金框跟着撤
                this.traceRequestedFor = null;
                TraceHighlights.sync(List.of());
            }
            graphics.renderTooltip(this.font,
                    cell.header() ? cachedGroupTooltip(cell) : cachedTooltip(cell),
                    Optional.empty(), mouseX, mouseY);
            // 记一笔：这一帧已经弹过列表提示框，render 末尾就别再补按钮说明了
            this.listHintDrawn = true;
        } else {
            // 指针不在列表格上：金框跟着悬停走，挪开就该撤（sync 空表 → 半秒内自然到期）
            this.traceRequestedFor = null;
            TraceHighlights.sync(List.of());
        }
        if (overResult) {
            ItemStack shown = hoveredSlot.getItem();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(shown));
            String batchKey = gestureKeyName(ModKeyMappings.BATCH_CRAFT);
            if (batchKey != null) {
                // 按键名跟着玩家的绑定走，别写死 Ctrl
                lines.add(resultTipLine1(batchKey));
            }
            lines.add(resultTipLine2());
            graphics.renderTooltip(this.font, lines, shown.getTooltipImage(), shown, mouseX, mouseY);
        }
        if (isPointInBatchBox(mouseX, mouseY)) {
            if (this.batchBoxTip == null) {
                this.batchBoxTip = List.of(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.batch_box",
                        "批量合成次数：批量点击时做这么多次；配置开启用完材料时会忽略这里"));
            }
            graphics.renderTooltip(this.font, this.batchBoxTip, Optional.empty(), mouseX, mouseY);
        }
        // 这个方法由上面 render 里的 this.renderTooltip(...) 调用（AbstractContainerScreen.render
        // 自己不调它，必须手动补一句）。两个按钮的说明仍然画在 render 末尾。
    }

    /**
     * 悬停列表项的提示框：带一份「按配方」缓存。
     * <p>
     * {@link #buildTooltip} 一次要查配方表、翻整段材料、拼十几个组件，
     * 原先<b>每帧</b>都重拼一遍——列表停在鼠标下时字根本没变，纯烧 CPU。
     * 只有悬停配方、溯源回包（列表换对象）、可合成列表刷新、收藏/队列计数
     * （{@code uiVersion}）任何一个真变了才重建。
     */
    private List<Component> cachedTooltip(CraftableListView.Cell cell) {
        CraftableEntry entry = cell.entry();
        ResourceLocation recipe = entry.recipeId();
        List<S2CTraceResponsePacket.MaterialHit> hits = this.menu.getClientTraceFor(recipe);
        List<CraftableEntry> source = this.menu.getCraftables();
        if (java.util.Objects.equals(recipe, this.tipCacheRecipe)
                && hits == this.tipCacheTrace && source == this.tipCacheSource
                && this.uiVersion == this.tipCacheUi) {
            return this.tipCacheLines;
        }
        this.tipCacheRecipe = recipe;
        this.tipCacheTrace = hits;
        this.tipCacheSource = source;
        this.tipCacheUi = this.uiVersion;
        this.tipCacheLines = buildTooltip(cell);
        return this.tipCacheLines;
    }

    /** 列表有变化（刷新、收藏、队列、配方轮换…）就叫一次：作废提示框缓存。 */
    private void invalidateTooltip() {
        this.uiVersion++;
    }

    /**
     * 分类键提示框的缓存壳（掉帧方案 A4）：原先<b>悬停期间每帧</b>都要重拼四五行组件、
     * 查一遍轮换路线。内容只跟语言、档位、计数、轮换指针有关——探针没变就直接沿用上次的。
     */
    private List<Component> tabTooltip(CraftCategory.Key key) {
        CraftCategory face = tabFace(key);
        int shown = face == this.category
                ? this.categoryCounts.getOrDefault(face, 0) : keyTotal(key);
        String probe = this.probeLang + '|' + key + '|' + face + '|' + (face == this.category)
                + '|' + shown + '|' + keyTotal(key) + '|' + this.buildingCycle + '|' + this.uiVersion;
        if (probe.equals(this.tabTipProbe)) {
            return this.tabTipLines;
        }
        this.tabTipProbe = probe;
        this.tabTipLines = buildTabTooltip(key);
        return this.tabTipLines;
    }

    /** 折叠头提示框的缓存壳（掉帧方案 A4）：同一格悬停期间内容根本不会变。 */
    private List<Component> cachedGroupTooltip(CraftableListView.Cell cell) {
        String probe = this.probeLang + '|' + cell.groupKey() + '|' + cell.groupSize()
                + '|' + cell.optionIndex() + '|' + cell.optionCount() + '|' + this.uiVersion;
        if (probe.equals(this.groupTipProbe)) {
            return this.groupTipLines;
        }
        this.groupTipProbe = probe;
        this.groupTipLines = buildGroupTooltip(cell);
        return this.groupTipLines;
    }

    /** 产物格提示框那两行附加说明：只跟语言和绑定键有关，悬停期间逐帧重拼纯属浪费（方案 A4）。 */
    private Component resultTipLine1(String batchKey) {
        String probe = this.probeLang + '|' + batchKey;
        if (!probe.equals(this.resultTip1Probe)) {
            this.resultTip1Probe = probe;
            this.resultTip1 = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.result_batch",
                            "%s+点击：批量合成合成格里这份配方，直到材料用完（自己摆的料也算）", batchKey)
                    .withStyle(ChatFormatting.GRAY);
        }
        return this.resultTip1;
    }

    /** 同上第二行：自动合成键的整串描述（修饰键跟着绑定变）。 */
    private Component resultTipLine2() {
        String key = fullKeybindText(ModKeyMappings.AUTO_CRAFT);
        if (!key.equals(this.resultTip2Probe)) {
            this.resultTip2Probe = key;
            this.resultTip2 = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.result_auto",
                            "%s：自动合成合成格里的这份配方（缺料自动补齐，产物进输出容器）", key)
                    .withStyle(ChatFormatting.GOLD);
        }
        return this.resultTip2;
    }

    /** 折叠头的提示框：这一类是什么、有多少项、怎么展开。 */
    private List<Component> buildGroupTooltip(CraftableListView.Cell cell) {
        List<Component> lines = new ArrayList<>();
        lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_title",
                        "%s 等 %s 项同类物品",
                        cell.entry().result().getHoverName().getString(), cell.groupSize())
                .withStyle(ChatFormatting.WHITE));
        lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_key",
                        "同类：%s", CraftableListView.groupLabel(cell.groupKey()).getString())
                .withStyle(ChatFormatting.GRAY));
        if (cell.pinnedExtra() > 0) {
            // 钉在最前的收藏不参与折叠，角标只数没收进去的那些；这里补一句，免得玩家以为这一类就这么多
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_pinned_elsewhere",
                            "另有 %s 项已收藏在列表最前（同属这一类）", cell.pinnedExtra())
                    .withStyle(ChatFormatting.AQUA));
        }
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_click",
                "左键：展开 / 收起这一类").withStyle(ChatFormatting.YELLOW));
        String expandKey = gestureKeyName(ModKeyMappings.EXPAND_ALL);
        if (expandKey != null) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_expand_all",
                    "%s+左键：全部展开 / 全部折叠", expandKey).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private List<Component> buildTooltip(CraftableListView.Cell cell) {
        CraftableEntry entry = cell.entry();
        List<Component> lines = new ArrayList<>();
        lines.add(entry.result().getHoverName().copy().withStyle(ChatFormatting.WHITE));
        // 溯源答案先取：每行「橡木木板 x2 ← 箱子(-6,-60,4) x64」本身就写全了
        // 材料名和需求量，这时顶上再刷一遍「橡木木板x2」就是纯重复（用户图2 两处划线）。
        // 只有服务端还没答上（第一次悬停的那几帧）才用材料清单顶着。
        List<Component> trace = traceLines(entry);
        Recipe<?> recipe = (this.minecraft == null || this.minecraft.level == null) ? null
                : this.minecraft.level.getRecipeManager().<Recipe<?>>byKey(entry.recipeId()).orElse(null);
        if (recipe != null && trace.isEmpty()) {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            // 同一种材料摆了几格就合并成「钻石x8」，不再一格一行刷一长串「钻石x1」。
            List<ItemStack> kinds = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                ItemStack[] previews = ingredient.getItems();
                if (previews.length == 0) {
                    continue;
                }
                ItemStack preview = previews[0];
                int at = -1;
                for (int i = 0; i < kinds.size(); i++) {
                    if (ItemStack.isSameItemSameTags(kinds.get(i), preview)) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) {
                    kinds.add(preview.copy());
                    counts.add(1);
                } else {
                    counts.set(at, counts.get(at) + 1);
                }
            }
            for (int i = 0; i < kinds.size(); i++) {
                lines.add(kinds.get(i).getHoverName().copy()
                        .append("x" + counts.get(i)).withStyle(ChatFormatting.GRAY));
            }
        }
        // 一件产物可能有好几份配方（不同材料都能做出来）。把来源和份数写出来，
        // 玩家才知道列表里两个长得一样的图标到底差在哪、该怎么选。
        if (entry.recipeId() != null) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.recipe_source",
                            "配方来源：%s", entry.recipeId().getNamespace())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        // 注意这里说的是「同一个产物、几套不同材料」，跟多态合成（同一把材料出不同产物）
        // 不是一回事，多态合成的按钮在这种配方上永远不会出现，所以切换由本模组自己做：
        // 折叠着时右键这一格换，展开着时每套材料各占一格（见 CraftableListView#emitOptions）。
        if (cell.hasOtherOptions()) {
            if (entry.hasAlternatives()) {
                lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.alternatives",
                                "这件产物有 %s 份材料不同的配方，列表只显示一份；右键这一格可换下一份"
                                        + "（关掉折叠或展开这一类，每份都会各占一格）", cell.optionCount())
                        .withStyle(ChatFormatting.AQUA));
            } else {
                lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.option_index",
                                "同一件产物的第 %1$s / %2$s 套材料（其它几套在列表里各占一格）",
                                cell.optionIndex(), cell.optionCount())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (entry.chain()) {
            lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.chain",
                    "链式合成：缺的中间材料会先自动补做（需开启自动补齐）")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        // 常用收藏：做过几次、现在收藏没、怎么手动加/摘。一行说全。
        lines.add(favoriteLine(entry));
        // 材料溯源：服务端答过了才有一行行「钻石x2 → 箱子(12,64,-3)×35」；
        // 世界的金框在 requestTrace 里跟着同一份结果对表，这里只负责把字写出来。
        // （以前末尾还挂着一整段固定的操作说明——悬停什么都要重读一遍，用户图7 要求搬走；
        // 现在那一段住在左上角「操作说明」小标签展开的面板里。）
        lines.addAll(trace);
        return lines;
    }

    /** 提示框里的收藏那一行：已收藏说怎么取消，快转正的还差几次，没动过就教一句怎么加。 */
    private Component favoriteLine(CraftableEntry entry) {
        net.minecraft.world.item.Item item = entry.result().getItem();
        int made = Favorites.count(item);
        String favKey = gestureKeyName(ModKeyMappings.FAVORITE);
        String how = favKey == null ? "Shift+右键" : favKey + " / Shift+右键";
        if (Favorites.isFavorite(item)) {
            return Favorites.isManual(item)
                    ? labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fav_pinned",
                            "已收藏（%s取消）", how).withStyle(ChatFormatting.GOLD)
                    : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fav_auto",
                            "常做了 %1$s 次，已收藏（%2$s可取消）", made, how).withStyle(ChatFormatting.GOLD);
        }
        if (made > 0) {
            return labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fav_progress",
                    "做过 %1$s 次，再做 %2$s 次自动收藏（%3$s：立刻收藏）",
                    made, Math.max(1, Favorites.AUTO_PIN_COUNT - made), how).withStyle(ChatFormatting.GRAY);
        }
        return labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fav_hint",
                "%s：添加到收藏", how).withStyle(ChatFormatting.GRAY);
    }

    /**
     * 提示框里的「材料在哪」那一截。服务端还没答（第一次悬停）就是空列表、什么都不加，
     * 答回来了每种材料一行：去处带坐标的顺便写明（那也是世界里描了金框的那几台）。
     */
    private List<Component> traceLines(CraftableEntry entry) {
        List<S2CTraceResponsePacket.MaterialHit> hits = this.menu.getClientTraceFor(entry.recipeId());
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.trace_header",
                "材料溯源（高亮材料存储处）").withStyle(ChatFormatting.AQUA));
        // 「关掉界面后金框还会亮几秒」这句整条删掉了（用户嫌解释太细）：
        // 续命功能照旧静默生效，不再在悬停框和操作说明里占字。
        for (S2CTraceResponsePacket.MaterialHit hit : hits) {
            MutableComponent line = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.trace_material",
                    "%1$s x%2$s ← ", hit.preview().getHoverName().getString(), hit.need())
                    .withStyle(ChatFormatting.WHITE);
            if (hit.spots().isEmpty()) {
                // 没扫全（接入容器太多、扫描预算不够）时别武断说「附近没有」——
                // 东西可能就在某个还没轮到的箱子里，报「没扫全」让玩家知道结果不保证
                if (hit.partial()) {
                    line.append(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.trace_partial",
                            "箱子太多没扫全，可能有")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    line.append(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.trace_missing", "附近没有，要先做")
                            .withStyle(ChatFormatting.RED));
                }
            } else {
                boolean first = true;
                for (S2CTraceResponsePacket.Spot spot : hit.spots()) {
                    if (!first) {
                        line.append("；");
                    }
                    first = false;
                    MutableComponent part = spot.name().copy().withStyle(ChatFormatting.GRAY);
                    if (spot.pos() != null) {
                        part.append(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.trace_pos",
                                "(%1$s,%2$s,%3$s) ", spot.pos().getX(), spot.pos().getY(), spot.pos().getZ()));
                    }
                    part.append(Component.literal("x" + spot.count()).withStyle(ChatFormatting.YELLOW));
                    line.append(part);
                }
            }
            lines.add(line);
        }
        return lines;
    }

    /** 文本组件；返回 MutableComponent 才能直接 withStyle（Component 接口上没有这个方法）。 */
    private MutableComponent label(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback);
    }

    /** 带参数的文本组件。 */
    private MutableComponent labelArgs(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    /**
     * 每帧开头查一眼语言（方案 A2 的总开关）：换了语言就把所有预制文字作废重建。
     * 语言切换是极低频事件，每帧一次字符串比较不要钱。
     */
    private void refreshStatics() {
        String lang = this.minecraft == null ? "en_us" : this.minecraft.getLanguageManager().getSelected();
        if (lang.equals(this.probeLang)) {
            return;
        }
        this.probeLang = lang;
        this.staticTexts.clear();
        this.hintCaches.clear();
        this.storageProbe = null;
        this.queueStripProbe = null;
        this.inventoryLabelCache = null;
        this.searchHintClip = null;
        this.emptyHintProbe = null;
        this.tabTipProbe = null;
        this.groupTipProbe = null;
        this.batchBoxTip = null;
        this.resultTip1Probe = null;
        this.resultTip1 = null;
        this.resultTip2Probe = null;
        this.resultTip2 = null;
    }

    /** 按键取预制文字，顺带量好宽度；没有就现做一份存进表里。 */
    private Fitted fitted(String key, String fallback) {
        Fitted f = this.staticTexts.get(key);
        if (f == null) {
            Component c = Component.translatableWithFallback(key, fallback);
            f = new Fitted(c, this.font.width(c));
            this.staticTexts.put(key, f);
        }
        return f;
    }

    /** 「接入存储: N  输出: N」整行：两个计数没变就整行沿用上一帧的组件和宽度（方案 A2）。 */
    private Fitted storageLine() {
        int in = this.menu.getConnectedStorageCount();
        int out = this.menu.getOutputCount();
        String probe = in + "/" + out;
        if (!probe.equals(this.storageProbe)) {
            this.storageProbe = probe;
            MutableComponent text = label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.storage", "接入存储: ")
                    .copy().append(String.valueOf(in))
                    .append(label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.output", "  输出: "))
                    .append(String.valueOf(out));
            this.storageLineText = new Fitted(text, this.font.width(text));
        }
        return this.storageLineText;
    }

    /**
     * 排版一次（掉帧方案 A5）：把原先 {@code drawButtonHint} 里"每帧都跑"的分行、量宽、
     * 定位整套算出来存进缓存。位置只跟语言和按钮状态有关，跟鼠标坐标无关。
     * 摆位规则沿用老版：优先按钮右侧，放不下挪到按钮正上方，宁可略超面板也不压住列表物品。
     */
    private HintLines layoutHint(Object probe, Component message,
                                 int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        final int gap = 4;
        final int minWidth = 96;

        // 位置一：按钮右侧（右侧要留得下 minWidth 才有意义）。"自动补齐"按钮右侧是空的，
        // 走的就是这一条；"自动"按钮右侧只剩 8 像素，会被下面的判据否掉。
        int rightSpace = TEXTURE_W - (buttonX + buttonWidth) - gap;
        // 位置二：按钮上方，横向可用到整张面板右侧边缘。
        int aboveSpace = TEXTURE_W - buttonX - gap;

        int x = buttonX + buttonWidth + gap;
        int maxWidth = rightSpace;
        int y = buttonY + buttonHeight + 2;
        if (rightSpace < minWidth) {
            // 改到按钮正上方：提示框底边贴着按钮顶边
            maxWidth = aboveSpace;
            x = buttonX;
        }

        maxWidth = Math.max(minWidth, maxWidth);
        List<FormattedCharSequence> lines = this.font.split(message, maxWidth);
        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, this.font.width(line));
        }
        int boxWidth = Math.min(maxWidth, Math.max(minWidth, textWidth)) + 6;
        int lineHeight = this.font.lineHeight + 1;
        int boxHeight = lines.size() * lineHeight + 6;

        if (rightSpace < minWidth) {
            // 上方：底边贴住按钮，往上展开；顶到界面顶就改画在按钮下方
            y = buttonY - boxHeight - 2;
            if (y < 0) {
                y = buttonY + buttonHeight + 2;
            }
        } else if (y + boxHeight > TEXTURE_H) {
            // 右侧放不下整框时，也转到上方
            x = buttonX;
            y = buttonY - boxHeight - 2;
            if (y < 0) {
                y = buttonY + buttonHeight + 2;
            }
        }

        // 兜底：整框不许越出贴图，免得有元素被裁掉半截
        x = Mth.clamp(x, 0, Math.max(0, TEXTURE_W - boxWidth));
        y = Mth.clamp(y, 0, Math.max(0, TEXTURE_H - boxHeight));
        return new HintLines(probe, lines, x, y, boxWidth, boxHeight);
    }

    /** 绘制缓存好的说明框：坐标存的是面板相对值，这里加上 leftPos/topPos 再画，一次提交。 */
    private void drawHint(GuiGraphics graphics, HintLines h) {
        int screenX = this.leftPos + h.boxX();
        int screenY = this.topPos + h.boxY();
        // 图层抬到最高：原版的物品图标是画在 z=150 那一层的，原版提示框靠 translate(0,0,400)
        // 压到图标之上（见 GuiGraphics.renderTooltipInternal）。我们照抄同一个高度，
        // 否则底框和文字虽然后画，仍会被图标和数量角标盖住（截图里就是文字被钻石装备压住）。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        // 底、描边、几行字攒一批（复核报告隐患 2）：这里是本模组自己画的文字，
        // 不在 drawManaged 里的话每行末尾都要提交一次显卡——悬停按钮时每帧好几次提交。
        // 注意只包<b>我们自己</b>这几笔；原版 renderTooltip 内部已经有 drawManaged，
        // 再在外面套一层会把里面那批提前冲掉，反而更慢。
        graphics.drawManaged(() -> {
            // 不透明的深紫底 + 白字，对比度拉满：这一层在图标之上，底下有什么都不影响阅读
            graphics.fill(screenX - 2, screenY - 2,
                    screenX + h.boxW() + 2, screenY + h.boxH() + 2, 0xFF2A2440);
            graphics.renderOutline(screenX - 2, screenY - 2, h.boxW() + 4, h.boxH() + 4, 0xFFE0A83C);
            int lineY = screenY + 2;
            int lineHeight = this.font.lineHeight + 1;
            for (FormattedCharSequence line : h.lines()) {
                graphics.drawString(this.font, line, screenX + 1, lineY, TEXT_ON_DARK, false);
                lineY += lineHeight;
            }
        });
        graphics.pose().popPose();
    }

    /** 鼠标落在哪个列表项上；没落在格子上返回 -1。 */
    private int cellIndexAt(double mouseX, double mouseY) {
        double localX = mouseX - (this.leftPos + LIST_X);
        double localY = mouseY - (this.topPos + LIST_Y);
        if (localX < 0 || localY < 0) {
            return -1;
        }
        int col = (int) (localX / CELL);
        int row = (int) (localY / CELL);
        if (col < 0 || col >= LIST_COLS || row < 0 || row >= LIST_ROWS) {
            return -1;
        }
        int index = (this.scroll + row) * LIST_COLS + col;
        return index < this.display.size() ? index : -1;
    }

    private boolean isPointInSearchBox(double mouseX, double mouseY) {
        int x = this.leftPos + SEARCH_X;
        int y = this.topPos + SEARCH_Y;
        return mouseX >= x && mouseX < x + SEARCH_W && mouseY >= y && mouseY < y + SEARCH_H;
    }

    private boolean isPointInBatchBox(double mouseX, double mouseY) {
        int x = this.leftPos + GuiLayout.BATCH_INPUT_X;
        int y = this.topPos + GuiLayout.BATCH_INPUT_Y;
        return mouseX >= x && mouseX < x + GuiLayout.BATCH_INPUT_WIDTH
                && mouseY >= y && mouseY < y + GuiLayout.BATCH_INPUT_HEIGHT;
    }

    private int parseBatchCount() {
        if (this.batchBox == null) {
            return BATCH_DEFAULT;
        }
        try {
            int value = Integer.parseInt(this.batchBox.getValue());
            return Mth.clamp(value, 1, BATCH_MAX);
        } catch (NumberFormatException ignored) {
            return BATCH_DEFAULT;
        }
    }

    private boolean isPointInFoldButton(double mouseX, double mouseY) {
        int x = this.leftPos + FOLD_BUTTON_X;
        int y = this.topPos + FOLD_BUTTON_Y;
        return mouseX >= x && mouseX < x + FOLD_BUTTON_W && mouseY >= y && mouseY < y + FOLD_BUTTON_H;
    }

    private boolean isPointInSortButton(double mouseX, double mouseY) {
        int x = this.leftPos + SORT_BUTTON_X;
        int y = this.topPos + SORT_BUTTON_Y;
        return mouseX >= x && mouseX < x + SORT_BUTTON_W && mouseY >= y && mouseY < y + SORT_BUTTON_H;
    }

    private boolean isPointInButton(double mouseX, double mouseY) {
        int x = this.leftPos + BUTTON_X;
        int y = this.topPos + BUTTON_Y;
        return mouseX >= x && mouseX < x + BUTTON_W && mouseY >= y && mouseY < y + BUTTON_H;
    }

    private boolean isPointInAutoButton(double mouseX, double mouseY) {
        int x = this.leftPos + AUTO_BUTTON_X;
        int y = this.topPos + AUTO_BUTTON_Y;
        return mouseX >= x && mouseX < x + AUTO_BUTTON_W && mouseY >= y && mouseY < y + AUTO_BUTTON_H;
    }

    private boolean isPointInRefillButton(double mouseX, double mouseY) {
        int x = this.leftPos + REFILL_BUTTON_X;
        int y = this.topPos + REFILL_BUTTON_Y;
        return mouseX >= x && mouseX < x + REFILL_BUTTON_W && mouseY >= y && mouseY < y + REFILL_BUTTON_H;
    }

    private boolean isPointInClearButton(double mouseX, double mouseY) {
        int x = this.leftPos + CLEAR_BUTTON_X;
        int y = this.topPos + CLEAR_BUTTON_Y;
        return mouseX >= x && mouseX < x + CLEAR_BUTTON_W && mouseY >= y && mouseY < y + CLEAR_BUTTON_H;
    }

    /**
     * 3x3 合成格是不是全空。只读客户端这一侧的槽位（这些槽位由服务端同步过来），
     * 用来决定「清空」按钮的字要不要压淡、以及这一下点击值不值得发包。
     */
    private boolean isCraftGridEmpty() {
        for (int i = SmartWorkbenchMenu.GRID_START; i < SmartWorkbenchMenu.GRID_END; i++) {
            if (this.menu.getSlot(i).hasItem()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这一点落在产物格（16x16 的物品区）上吗。坐标取自 {@link GuiLayout}，
     * 和菜单里 addSlot 用的是同一个来源，所以判定范围和槽位本体严丝合缝。
     */
    private boolean isPointInResultSlot(double mouseX, double mouseY) {
        int x = this.leftPos + GuiLayout.OUTPUT_ITEM_X;
        int y = this.topPos + GuiLayout.OUTPUT_ITEM_Y;
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    /** 画一个按钮的悬停/按下高亮和居中文字（底色已经在贴图里）。 */
    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            boolean hovered, boolean down, Component text) {
        drawButton(graphics, x, y, w, h, hovered, down, text, TEXT_MAIN);
    }

    /** 缓存文案版（方案 A2）：宽度是量好的，画的时候零排版。 */
    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            boolean hovered, boolean down, Fitted text) {
        drawButton(graphics, x, y, w, h, hovered, down, text, TEXT_MAIN);
    }

    /**
     * 同上，多一个「平时用什么颜色」的参数：格子空着的「清空」要淡一点，
     * 让玩家不点也知道这一按没东西可退。按下时仍然用更深的 TEXT_PRESSED，
     * 那一下的反馈比颜色更重要。
     */
    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            boolean hovered, boolean down, Fitted text, int baseColor) {
        int px = this.leftPos + x;
        int py = this.topPos + y;
        if (hovered) {
            graphics.fill(px + 1, py + 1, px + w - 1, py + h - 1, down ? BUTTON_PRESSED : BUTTON_HOVER);
        }
        int textY = py + (h - this.font.lineHeight) / 2 + (down ? 1 : 0);
        drawCentredFitted(graphics, text, px + w / 2, textY, down ? TEXT_PRESSED : baseColor, w - 3);
    }

    /**
     * 同上，多一个「平时用什么颜色」的参数：格子空着的「清空」要淡一点，
     * 让玩家不点也知道这一按没东西可退。按下时仍然用更深的 TEXT_PRESSED，
     * 那一下的反馈比颜色更重要。
     */
    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            boolean hovered, boolean down, Component text, int baseColor) {
        int px = this.leftPos + x;
        int py = this.topPos + y;
        if (hovered) {
            graphics.fill(px + 1, py + 1, px + w - 1, py + h - 1, down ? BUTTON_PRESSED : BUTTON_HOVER);
        }
        int textY = py + (h - this.font.lineHeight) / 2 + (down ? 1 : 0);
        drawCentredFitted(graphics, text, px + w / 2, textY, down ? TEXT_PRESSED : baseColor, w - 3);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + PANEL_X && mouseX < this.leftPos + PANEL_X + PANEL_W
                && mouseY >= this.topPos + PANEL_Y && mouseY < this.topPos + PANEL_Y + PANEL_H;
    }

    private boolean isPointInScrollBar(double mouseX, double mouseY) {
        int x = this.leftPos + SCROLL_X - 2;
        int y = this.topPos + LIST_Y - 1;
        int height = LIST_ROWS * CELL;
        return mouseX >= x && mouseX < x + SCROLL_W + 4 && mouseY >= y && mouseY < y + height;
    }

    /** 根据鼠标在滚动轨道上的位置换算列表行偏移。 */
    private void updateScrollFromMouse(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            this.scroll = 0;
            return;
        }
        int trackTop = this.topPos + LIST_Y - 1;
        int trackHeight = LIST_ROWS * CELL;
        int thumbHeight = Math.max(10, (trackHeight - 2) * LIST_ROWS / (max + LIST_ROWS));
        int movable = Math.max(1, trackHeight - 2 - thumbHeight);
        double thumbCenter = mouseY - trackTop - thumbHeight / 2.0D;
        this.scroll = Mth.clamp((int) Math.round((thumbCenter - 1) * max / movable), 0, max);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 这里只留标题。「物品栏」不在这画了：真机上那行字整行不见了，而同一次调用的标题好好的，
        // 排查下来是父类给的 playerInventoryTitle 在这个环境里画出来是空的（宽 0、字也是空串），
        // 所以那行字改成自己取词、自己画，而且放到整帧最上层，见 {@link #drawInventoryLabel}。
        // 包进 drawManaged：省掉那一下 disable/enable 深度测试的开销（掉帧方案 A4）。
        graphics.drawManaged(() ->
                graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_MAIN, false));
    }

    /**
     * 背包那一栏的「物品栏」三个字。
     * <p>
     * 画在 render 的末尾（所有物品图标、数量角标、提示框、别的模组往容器界面上叠的东西都画完之后），
     * 所以不管底下有什么都没法把它盖住。位置固定用 GuiLayout 的坐标：宽松版里它在背包正上方那一行
     * （y=88），上面是「清空/自动补齐」按钮（到 y=83），下面是背包格子（从 y=96 起），正好夹在中间。
     */
    private void drawInventoryLabel(GuiGraphics graphics) {
        drawLeftFitted(graphics, inventoryLabel(),
                this.leftPos + this.inventoryLabelX, this.topPos + this.inventoryLabelY,
                TEXT_MAIN, inventoryLabelRoom());
    }

    /**
     * 背包标签的文案。
     * <p>
     * 正常情况下用游戏给的「容器标题」——资源包或别的语言改了它，这里跟着变，最省事；
     * 但它可能是空的（就是之前那行字消失的原因），空的时候就退回我们自己的语言文件词条
     * （中文「物品栏」、英文 "Inventory"），保证永远有字可画。
     * 宽度放不下时再退一步用短标签（中文本来就一样是「物品栏」，英文给 "Inv."）。
     */
    private Fitted inventoryLabel() {
        if (this.inventoryLabelCache != null) {
            return this.inventoryLabelCache;
        }
        int room = inventoryLabelRoom();
        Component full = this.playerInventoryTitle;
        int fullW = full == null ? 0 : this.font.width(full);
        if (full == null || fullW <= 0) {
            full = label("gui." + SmartWorkbenchMod.MOD_ID + ".label.inventory", "物品栏");
            fullW = this.font.width(full);
        }
        if (fullW <= room) {
            this.inventoryLabelCache = new Fitted(full, fullW);
            return this.inventoryLabelCache;
        }
        Fitted compact = fitted("gui." + SmartWorkbenchMod.MOD_ID + ".label.inventory_compact", "背包");
        this.inventoryLabelCache = compact.width() <= room ? compact : new Fitted(full, fullW);
        return this.inventoryLabelCache;
    }

    /** 这行字横向能用到哪里：一直到背包最右格的右边缘，别越过面板边界。 */
    private int inventoryLabelRoom() {
        int invRight = GuiLayout.INV_ITEM_X + (GuiLayout.INV_COLS - 1) * GuiLayout.INV_PITCH + 18;
        return invRight - this.inventoryLabelX;
    }

    /* ==================== 合成队列：状态条 / 管理面板 / 按键动作 ==================== */

    /** 状态条画在背景层（那一带没有物品图标，压不着）；顺手算好悬停标记。 */
    private void drawQueueStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        this.queueStripHovered = false;
        if (CraftQueue.isEmpty()) {
            return;
        }
        int x = this.leftPos + QS_X;
        int y = this.topPos + QS_Y;
        this.queueStripHovered = pointIn(mouseX, mouseY, x, y, QS_W, QS_H);
        graphics.fill(x, y, x + QS_W, y + QS_H, this.queueStripHovered ? 0xC0453F5C : 0xA62B2838);
        graphics.renderOutline(x, y, QS_W, QS_H, 0xFF9A96B0);
        int state = this.menu.getClientQueueState();
        int color = switch (state) {
            case SmartWorkbenchMenu.QUEUE_STATE_STUCK -> 0xFFFFB3B3;
            case SmartWorkbenchMenu.QUEUE_STATE_FINISHED -> 0xFFA9E8B8;
            case SmartWorkbenchMenu.QUEUE_STATE_RUNNING -> 0xFFFFFFFF;
            default -> 0xFFD8D5E8;
        };
        // 整行字只跟队列状态有关：状态/进度没推回来，字和宽度沿用上一帧（方案 A2）。
        List<CraftQueue.Entry> entries = CraftQueue.entries();
        int idx = this.menu.getClientQueueIndex();
        CraftQueue.Entry cur = idx >= 0 && idx < entries.size() ? entries.get(idx) : null;
        int totalDone = 0;
        int totalTarget = 0;
        for (CraftQueue.Entry entry : entries) {
            totalDone += Math.min(entry.done, entry.target);
            totalTarget += entry.target;
        }
        // fill 类（底、描边、进度）画完再画字：批内同类攒齐，少两次缓冲切换（方案 A4）。
        // 底部一条细进度：整队列「已做 ÷ 总量」，一眼看出跑到哪儿了。
        if (totalTarget > 0 && state != SmartWorkbenchMenu.QUEUE_STATE_FINISHED) {
            int w = (int) ((QS_W - 6) * (float) totalDone / totalTarget);
            graphics.fill(x + 3, y + QS_H - 2, x + 3 + w, y + QS_H - 1, SCROLL_THUMB);
        }
        drawLeftFitted(graphics, queueStripLabel(state, idx, entries.size(), cur, totalDone, totalTarget),
                x + 3, y + 3, color, QS_W - 6);
    }

    /** 状态条整行字的缓存：探针一比对，队列没动静就零拼字、零量宽。 */
    private Fitted queueStripLabel(int state, int idx, int size, CraftQueue.Entry cur,
                                   int done, int target) {
        String probe = state + "|" + idx + "|" + size + "|" + done + "|" + target + "|"
                + (cur == null ? -1 : cur.id) + "|" + this.menu.getClientQueueReason();
        if (probe.equals(this.queueStripProbe)) {
            return this.queueStripLabel;
        }
        this.queueStripProbe = probe;
        Component text = switch (state) {
            case SmartWorkbenchMenu.QUEUE_STATE_RUNNING -> cur != null
                    ? labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.strip.running",
                            "队列 %1$s/%2$s：%3$s %4$s/%5$s",
                            idx + 1, size, cur.display.getHoverName().getString(),
                            Math.min(cur.done, cur.target), cur.target)
                    : label("gui." + SmartWorkbenchMod.MOD_ID + ".queue.strip.running_short", "队列进行中");
            case SmartWorkbenchMenu.QUEUE_STATE_STUCK -> labelArgs(
                    "gui." + SmartWorkbenchMod.MOD_ID + ".queue.strip.stuck",
                    "队列停下：%s", queueReasonText(this.menu.getClientQueueReason()).getString());
            case SmartWorkbenchMenu.QUEUE_STATE_FINISHED -> labelArgs(
                    "gui." + SmartWorkbenchMod.MOD_ID + ".queue.strip.finished",
                    "队列全部做完（%s 项）", size);
            default -> labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.strip.idle",
                    "队列 %s 项待做，点我管理", size);
        };
        this.queueStripLabel = new Fitted(text, this.font.width(text));
        return this.queueStripLabel;
    }

    /** 卡住原因的短文案（状态条宽度有限，比服务端那句行动条提示更精简）。 */
    private Component queueReasonText(int reason) {
        String prefix = "gui." + SmartWorkbenchMod.MOD_ID + ".queue.reason.";
        switch (reason) {
            case SmartWorkbenchMenu.QUEUE_REASON_OUTPUT_FULL:
                return label(prefix + "output_full", "输出容器满了");
            case SmartWorkbenchMenu.QUEUE_REASON_INVENTORY_FULL:
                return label(prefix + "inventory_full", "背包满了");
            case SmartWorkbenchMenu.QUEUE_REASON_RECIPE_GONE:
                return label(prefix + "recipe_gone", "配方没了");
            case SmartWorkbenchMenu.QUEUE_REASON_UNSUPPORTED:
                return label(prefix + "unsupported", "配方不支持一键");
            case SmartWorkbenchMenu.QUEUE_REASON_PLAN_ABORTED:
                return label(prefix + "plan_aborted", "链式配方太复杂");
            default:
                return label(prefix + "missing", "材料不够");
        }
    }

    private int queuePanelX() {
        return this.leftPos + 1;
    }

    private int queuePanelY() {
        return this.topPos + 5;
    }

    /** 队列管理面板：盖在左半边界面上，六行清单一排到底，页脚三个动作键。 */
    private void drawQueuePanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int px = queuePanelX();
        int py = queuePanelY();
        int state = this.menu.getClientQueueState();
        graphics.pose().pushPose();
        // 面板必须站在提示框（z=400）<b>之上</b>：同为 400 时 GL_LESS 判不过，
        // 先画的物品说明会在面板上透出一层字（用户截图里的图层问题）。
        graphics.pose().translate(0.0F, 0.0F, QUEUE_PANEL_Z);
        List<CraftQueue.Entry> entries = CraftQueue.entries();
        int current = this.menu.getClientQueueIndex();
        String queueKey = gestureKeyName(ModKeyMappings.QUEUE_ADD);
        // 第一遍：暗层、底板、每张卡片和行里的文字——全攒成一批。
        // 没攒批时一个 fill、一句 drawString 各是一次显卡提交，六行面板一帧上百次提交，
        // 这就是队列面板一开就掉帧的原因。文字和图标互不重叠（图标占行首 16 像素，
        // 文字从 24 起），所以文字先画、图标后画，跟交错画的像素结果一样。
        graphics.drawManaged(() -> {
            // 整屏压暗一层：谁都看得出「现在在管队列，底下先别乱点」。
            graphics.fill(0, 0, this.width, this.height, 0x80000000);
            graphics.fill(px, py, px + QP_W, py + QP_H, 0xF5322D43);
            graphics.renderOutline(px, py, QP_W, QP_H, 0xFF9A96B0);
            Component panelTitle = label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.queue", "合成队列");
            graphics.drawString(this.font, panelTitle, px + 6, py + 5, 0xFFFFFFFF, false);
            Component countText = labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".panel.queue.count",
                    "%s/%s 项", Math.min(CraftQueue.entries().size(), SmartWorkbenchMenu.QUEUE_MAX_ENTRIES),
                    SmartWorkbenchMenu.QUEUE_MAX_ENTRIES);
            graphics.drawString(this.font, countText, px + 6 + this.font.width(panelTitle) + 4, py + 5, TEXT_DIM, false);
            // 右上角关闭 ×
            boolean closeHover = pointIn(mouseX, mouseY, px + QP_W - 13, py + 4, 10, 10);
            if (closeHover) {
                graphics.fill(px + QP_W - 13, py + 4, px + QP_W - 3, py + 14, 0x33FFFFFF);
            }
            graphics.drawString(this.font, "×", px + QP_W - 11, py + 5, closeHover ? 0xFFFFFFFF : TEXT_DIM, false);

            if (entries.isEmpty()) {
                Component empty = queueKey == null
                        ? label("gui." + SmartWorkbenchMod.MOD_ID + ".queue.empty",
                                "队列是空的：鼠标停在右侧列表的物品上，按加入队列键排进来")
                        : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.empty_key",
                                "队列是空的：鼠标停在右侧列表的物品上按 %s 排进来", queueKey);
                drawCentredFitted(graphics, empty, px + QP_W / 2, py + 72, 0xFFB9B6C8, QP_W - 24);
            }
            // 每一行是一块<b>淡紫卡片</b>（用户问题 4）：卡片上字全换深紫灰，
            // 行与行之间留 1 像素面板底色当分隔。正在执行的行描金边也在这批里。
            for (int i = 0; i < entries.size(); i++) {
                CraftQueue.Entry entry = entries.get(i);
                int ry = py + QP_ROW_Y + i * QP_ROW_H;
                boolean complete = entry.done >= entry.target;
                boolean runningRow = (state == SmartWorkbenchMenu.QUEUE_STATE_RUNNING
                        || state == SmartWorkbenchMenu.QUEUE_STATE_STUCK) && i == current;
                boolean rowHover = i == queuePanelRowAt(mouseX, mouseY);
                boolean editing = this.queueQtyBox != null && this.queueQtyEntryId == entry.id;
                graphics.fill(px + 3, ry, px + QP_W - 3, ry + QP_ROW_H - 1, ROW_CARD);
                graphics.renderOutline(px + 3, ry, QP_W - 6, QP_ROW_H - 1, ROW_CARD_EDGE);
                if (rowHover && !editing) {
                    graphics.fill(px + 3, ry, px + QP_W - 3, ry + QP_ROW_H - 1, ROW_HOVER_TINT);
                }
                if (runningRow) {
                    graphics.renderOutline(px + 3, ry, QP_W - 6, QP_ROW_H - 1, SELECT_COLOR);
                }
                // 行里的字和按钮抬到图标层之上（+160 → z 760）：物品图标是第二批画的、
                // 站在 z 750，长柄 / 旗帜 / 大号模型会伸出 16 像素格子；
                // 文字留在 600 的话就会被图标压住（复核报告隐患 1）。760 仍低于完成罩 799。
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, QP_ROW_TEXT_Z);
                drawLeftFitted(graphics, entry.display.getHoverName(), px + 24, ry + 6,
                        complete ? ROW_TEXT_DONE : ROW_TEXT, 62);
                // 正在编辑数量的那一行不画进度数字：它右对齐到 px+124，和 px+88 起、
                // 宽 38 的输入框正好重叠；数字抬在 z760、输入框原本在 z600，
                // 深度测试下旧字会浮在输入框上面透出（审查报告问题 2）。
                if (!editing) {
                    String progress = complete ? String.valueOf(entry.target)
                            : entry.done + "/" + entry.target;
                    int progressColor = complete ? ROW_NUM_DONE : runningRow ? ROW_TEXT : ROW_NUM;
                    graphics.drawString(this.font, progress,
                            px + 124 - this.font.width(progress), ry + 6, progressColor, false);
                }
                // 行按钮：▲ 上移、▼ 下移、× 删除（卡片是浅底，符号用深紫灰）
                drawRowArrow(graphics, px + 128, ry + 3, true,
                        pointIn(mouseX, mouseY, px + 128, ry + 1, 11, 17));
                drawRowArrow(graphics, px + 141, ry + 3, false,
                        pointIn(mouseX, mouseY, px + 141, ry + 1, 11, 17));
                boolean delHover = pointIn(mouseX, mouseY, px + 154, ry + 1, 11, 17);
                if (delHover) {
                    graphics.fill(px + 154, ry + 1, px + 165, ry + 18, ROW_DEL_HOVER_BG);
                }
                graphics.drawString(this.font, "×", px + 157, ry + 6, delHover ? ROW_DEL_HOVER : ROW_ARROW, false);
                graphics.pose().popPose();
            }

            // 页脚：开始/暂停、跳过当前、清空队列（和图标不重叠，一并留在第一批里）
            boolean running = state == SmartWorkbenchMenu.QUEUE_STATE_RUNNING;
            drawPanelButton(graphics, px + 4, py + QP_H - 15, 50, 12,
                    label(running ? "gui." + SmartWorkbenchMod.MOD_ID + ".queue.button.pause"
                            : "gui." + SmartWorkbenchMod.MOD_ID + ".queue.button.start",
                            running ? "暂停" : "开始"),
                    pointIn(mouseX, mouseY, px + 4, py + QP_H - 15, 50, 12), 0xFFFFFFFF);
            drawPanelButton(graphics, px + 57, py + QP_H - 15, 44, 12,
                    label("gui." + SmartWorkbenchMod.MOD_ID + ".queue.button.skip", "跳过"),
                    pointIn(mouseX, mouseY, px + 57, py + QP_H - 15, 44, 12), 0xFFFFFFFF);
            drawPanelButton(graphics, px + 104, py + QP_H - 15, 52, 12,
                    label("gui." + SmartWorkbenchMod.MOD_ID + ".queue.button.clear", "清空队列"),
                    pointIn(mouseX, mouseY, px + 104, py + QP_H - 15, 52, 12), 0xFFFFFFFF);
        });
        // 第二遍：逐行画物品图标。renderItem 自己必设 3D 物品光照、必提交一次，
        // 混进别的批次会把攒好的绘制提前冲掉——图标批量的改造是另一回事（暂缓的第 4 步）。
        for (int i = 0; i < entries.size(); i++) {
            CraftQueue.Entry entry = entries.get(i);
            int ry = py + QP_ROW_Y + i * QP_ROW_H;
            graphics.renderItem(entry.display, px + 5, ry + 2);
        }
        // 第三遍：做完的项在图标上罩一层半透明深色 + 右下角一枚绿✓。
        // 必须在图标全部画完之后才能盖得住图标（抬到物品层之上），这几笔再攒成一批。
        graphics.drawManaged(() -> {
            for (int i = 0; i < entries.size(); i++) {
                CraftQueue.Entry entry = entries.get(i);
                if (entry.done < entry.target) {
                    continue;
                }
                int ry = py + QP_ROW_Y + i * QP_ROW_H;
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 199.0F);
                graphics.fill(px + 5, ry + 2, px + 21, ry + 18, 0x66101018);
                // 对勾手绘，不赌字体有没有 U+2713：缺字形就是一个豆腐块，
                // 而且按字宽居中也没意义（复核报告隐患 6）。位置固定在图标右下角。
                drawDoneTick(graphics, px + 12, ry + 11, ROW_NUM_DONE);
                graphics.pose().popPose();
            }
        });
        // 双击数量后的输入框：金边深底，盖在原数字位置上直接打字（问题 3）。
        // 原本会被抬到 z760 的进度数字穿透盖住（审查报告问题 2）——那数字现在在
        // 编辑期间干脆不画（见上面 drawQueuePanel 行循环里的 !editing），这里保持
        // 原图层：不额外抬升，免得和本批之后的行提示框抢深度。
        if (this.queueQtyBox != null) {
            int ex = this.queueQtyX;
            int ey = this.queueQtyY;
            graphics.fill(ex - 1, ey - 1, ex + QEB_W + 1, ey + QEB_H + 1, SELECT_COLOR);
            graphics.fill(ex, ey, ex + QEB_W, ey + QEB_H, SEARCH_BG);
            this.queueQtyBox.render(graphics, mouseX, mouseY, partialTick);
        }

        // 行提示框：停在某行上给一句教学；正在输入数量的那一行不弹（免得盖住输入框）。
        // 提示框自己会攒批自提交，画在最后保证压在一切之上。
        int hoverRow = queuePanelRowAt(mouseX, mouseY);
        if (hoverRow >= 0 && hoverRow < entries.size()) {
            CraftQueue.Entry entry = entries.get(hoverRow);
            boolean editing = this.queueQtyBox != null && this.queueQtyEntryId == entry.id;
            if (!editing) {
                List<Component> tip = new ArrayList<>();
                tip.add(entry.display.getHoverName().copy().withStyle(ChatFormatting.WHITE));
                tip.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.tip.progress",
                        "目标 %1$s 个，已做出 %2$s 个", entry.target, entry.done).withStyle(ChatFormatting.GRAY));
                tip.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".queue.tip.wheel",
                        "滚轮：数量 ±1（按住 Shift ±10）；双击数量可直接填；▲▼ 换序，× 删除")
                        .withStyle(ChatFormatting.YELLOW));
                graphics.renderTooltip(this.font, tip, Optional.empty(), mouseX, mouseY);
            }
        }
        graphics.pose().popPose();
    }

    /** 行里的上移/下移小三角：手绘三行，省得赌字体里有没有 U+25B2。画在浅紫卡片上，用深紫灰。 */
    private void drawRowArrow(GuiGraphics graphics, int x, int y, boolean up, boolean hovered) {
        int color = hovered ? ROW_ARROW_HOVER : ROW_ARROW;
        if (hovered) {
            graphics.fill(x, y - 2, x + 11, y + 15, ROW_ARROW_HOVER_BG);
        }
        for (int i = 0; i < 3; i++) {
            int half = up ? 1 + i : 3 - i;
            graphics.fill(x + 5 - half, y + 5 + i, x + 6 + half, y + 6 + i, color);
        }
    }

    /** 做完的绿对勾：两笔手绘（短一撇 + 长一提），7×6 像素，画在图标右下角。 */
    private void drawDoneTick(GuiGraphics graphics, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + i, y + 3 + i, x + i + 2, y + 4 + i, color);
        }
        for (int i = 0; i < 5; i++) {
            graphics.fill(x + 2 + i, y + 5 - i, x + 4 + i, y + 6 - i, color);
        }
    }

    private void drawPanelButton(GuiGraphics graphics, int x, int y, int w, int h,
                                 Component text, boolean hovered, int color) {
        if (hovered) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x33FFFFFF);
        }
        graphics.renderOutline(x, y, w, h, 0xFF6F9CBE);
        drawCentredFitted(graphics, text, x + w / 2, y + (h - this.font.lineHeight) / 2, color, w - 4);
    }

    /** 鼠标落在面板的第几行；不在行上返回 -1。 */
    private int queuePanelRowAt(double mouseX, double mouseY) {
        int px = queuePanelX();
        int py = queuePanelY();
        if (mouseX < px + 3 || mouseX >= px + QP_W - 3) {
            return -1;
        }
        int rel = (int) (mouseY - (py + QP_ROW_Y));
        if (rel < 0 || rel >= QP_ROW_H * SmartWorkbenchMenu.QUEUE_MAX_ENTRIES) {
            return -1;
        }
        int row = rel / QP_ROW_H;
        return row < CraftQueue.entries().size() ? row : -1;
    }

    /** 面板开着时的点击分发：先输入框，再按钮后行，窗外一律当「关面板」。所有点击都被吞掉。 */
    private boolean handleQueuePanelClick(double mouseX, double mouseY, int button) {
        // 正在输入数量：点框子里头交给输入框（放光标/选字）；点别处＝先收下这个数，
        // 这一下只用来收框，不再干别的（免得"点哪儿哪儿删/挪"吓到新手）。
        if (this.queueQtyBox != null) {
            if (pointIn(mouseX, mouseY, this.queueQtyX - 1, this.queueQtyY - 1, QEB_W + 2, QEB_H + 2)
                    && button == 0) {
                this.queueQtyBox.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            commitQueueQtyEdit();
            return true;
        }
        int px = queuePanelX();
        int py = queuePanelY();
        if (button != 0) {
            closeQueuePanel();
            return true;
        }
        // 关闭
        if (pointIn(mouseX, mouseY, px + QP_W - 13, py + 4, 10, 10)) {
            closeQueuePanel();
            playClickSound();
            return true;
        }
        // 行按钮
        int row = queuePanelRowAt(mouseX, mouseY);
        if (row >= 0) {
            CraftQueue.Entry entry = CraftQueue.entries().get(row);
            int ry = py + QP_ROW_Y + row * QP_ROW_H;
            if (pointIn(mouseX, mouseY, px + 128, ry + 1, 11, 17)) {
                CraftQueue.move(entry.id, -1);
                playClickSound();
                return true;
            }
            if (pointIn(mouseX, mouseY, px + 141, ry + 1, 11, 17)) {
                CraftQueue.move(entry.id, 1);
                playClickSound();
                return true;
            }
            if (pointIn(mouseX, mouseY, px + 154, ry + 1, 11, 17)) {
                CraftQueue.remove(entry.id);
                playClickSound();
                return true;
            }
            // 数量区双击＝直接打字填（问题 3）。1.20.1 的 Screen 没有 doubleMouseDown 回调，
            // 只能自己按「同一项 400 毫秒内两下左键」认。单击仍只是吞掉，不会有别的后果。
            if (pointIn(mouseX, mouseY, px + QEB_X_OFF - 4, ry, QEB_W + 7, QP_ROW_H)) {
                long now = System.currentTimeMillis();
                if (this.lastQtyClickId == entry.id && now - this.lastQtyClickMs <= DOUBLE_CLICK_MS) {
                    this.lastQtyClickId = -1;
                    openQueueQtyEdit(entry, px + QEB_X_OFF, ry + 3);
                } else {
                    this.lastQtyClickId = entry.id;
                    this.lastQtyClickMs = now;
                }
                return true;
            }
            return true; // 点在行的其它地方：吞掉，别当滑窗误触底下的格子
        }
        // 页脚
        if (pointIn(mouseX, mouseY, px + 4, py + QP_H - 15, 50, 12)) {
            queueToggleStart();
            return true;
        }
        if (pointIn(mouseX, mouseY, px + 57, py + QP_H - 15, 44, 12)) {
            queueSkip();
            return true;
        }
        if (pointIn(mouseX, mouseY, px + 104, py + QP_H - 15, 52, 12)) {
            if (!CraftQueue.isEmpty()) {
                CraftQueue.clear();
                playClickSound();
                toast("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.cleared", "队列已清空");
            }
            return true;
        }
        // 面板外：关窗（点击同样吞掉，这一下不该落到任何格子上）
        closeQueuePanel();
        return true;
    }

    /**
     * 双击数量后开框：光标放末尾、整串选上，第一下按键就覆盖旧值。
     * 输入框只留一份实例复用（{@code setX/setY} 挪到被双击那一行）。
     */
    private void openQueueQtyEdit(CraftQueue.Entry entry, int x, int y) {
        if (this.queueQtyBox == null) {
            this.queueQtyBox = new EditBox(this.font, x, y, QEB_W, QEB_H,
                    labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.tip.edit",
                            "直接打字填个数（1~%s），回车确认，Esc 取消", CraftQueue.maxTarget()));
            this.queueQtyBox.setBordered(false);
            this.queueQtyBox.setTextColor(0xFFFFFFFF);
            this.queueQtyBox.setHint(Component.empty());
            this.queueQtyBox.setFilter(text -> text.matches("[0-9]*"));
        }
        this.queueQtyBox.setX(x);
        this.queueQtyBox.setY(y);
        this.queueQtyBox.setMaxLength(String.valueOf(CraftQueue.maxTarget()).length());
        this.queueQtyBox.setValue(String.valueOf(entry.target));
        this.queueQtyEntryId = entry.id;
        this.queueQtyX = x;
        this.queueQtyY = y;
        // 搜索框/批量框先让位，焦点给数量框；整串选上，直接打字就是改。
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        if (this.batchBox != null) {
            this.batchBox.setFocused(false);
        }
        this.queueQtyBox.setFocused(true);
        this.queueQtyBox.moveCursorToEnd();
        this.queueQtyBox.setHighlightPos(0);
        playClickSound();
    }

    /** 收下输入框里的数：合法就写进队列（{@link CraftQueue#setTarget} 内部夹 1~上限），空/非法当取消。 */
    private void commitQueueQtyEdit() {
        if (this.queueQtyBox == null) {
            return;
        }
        String text = this.queueQtyBox.getValue().trim();
        int id = this.queueQtyEntryId;
        cancelQueueQtyEdit();
        if (text.isEmpty()) {
            return;
        }
        try {
            CraftQueue.setTarget(id, Integer.parseInt(text));
            CraftQueue.Entry e = CraftQueue.findById(id);
            if (e != null) {
                toastArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.qty",
                        "「%1$s」目标数量改为 %2$s 个", e.display.getHoverName().getString(), e.target);
                invalidateTooltip();
            }
        } catch (NumberFormatException ignored) {
            // 位数已限死且过滤了非数字，真解析不了就不改
        }
    }

    /** 收掉输入框，<b>不</b>动数值（Esc / 关界面用）。 */
    private void cancelQueueQtyEdit() {
        if (this.queueQtyBox != null) {
            this.queueQtyBox.setFocused(false);
        }
        this.queueQtyBox = null;
        this.queueQtyEntryId = -1;
    }

    /** 改窗口大小后把输入框挪回它那一行（按项 id 找，不按序号——中途可能有人被删）。 */
    private void repositionQueueQtyEdit() {
        if (this.queueQtyBox == null) {
            return;
        }
        List<CraftQueue.Entry> entries = CraftQueue.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == this.queueQtyEntryId) {
                int x = queuePanelX() + QEB_X_OFF;
                int y = queuePanelY() + QP_ROW_Y + i * QP_ROW_H + 3;
                this.queueQtyBox.setX(x);
                this.queueQtyBox.setY(y);
                this.queueQtyX = x;
                this.queueQtyY = y;
                return;
            }
        }
        // 那一行没了（队列被清/项被删）：直接收框。
        cancelQueueQtyEdit();
    }

    /** 关面板的统一出口：顺带收掉没收的数量框。 */
    private void closeQueuePanel() {
        cancelQueueQtyEdit();
        this.queuePanelOpen = false;
    }

    /** 开队列面板的统一入口：和说明面板互斥，两个"盖窗"不同时在场。 */
    private void openQueuePanel() {
        closeHelpPanel();
        this.queuePanelOpen = true;
    }

    /* ==================== 操作说明：问号按钮 + 双形态面板 ==================== */

    /** 问号按钮的左上角（屏幕绝对坐标；画在标题栏左上角的空位）。 */
    private int helpBtnX() {
        return this.leftPos + HELP_BTN_X;
    }

    private int helpBtnY() {
        return this.topPos + HELP_BTN_Y;
    }

    private boolean helpBtnHit(double mouseX, double mouseY) {
        return pointIn(mouseX, mouseY, helpBtnX(), helpBtnY(), HELP_BTN_SIZE, HELP_BTN_SIZE);
    }

    /** 左边空位放得下一整块面板吗；放不下就走浮层形态（盖在界面正中）。 */
    private boolean helpSlideFits() {
        return this.leftPos - HELP_PANEL_W - 4 >= 0;
    }

    /** 当前形态下面板的位置和大小：{x, y, w, h}。 */
    private int[] helpPanelRect() {
        if (helpSlideFits()) {
            // 排布：[面板][界面]——面板右缘紧贴工作台左边，只隔 4 像素
            int w = HELP_PANEL_W;
            int h = Math.min(TEXTURE_H, this.height - 8);
            return new int[]{this.leftPos - 4 - w, Math.max(4, this.topPos), w, h};
        }
        int w = Math.min(240, this.width - 12);
        int h = Math.min(this.height - 12, Math.max(120, TEXTURE_H));
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    /** 正文一行的高度（像素，含行距）：滚轮、键盘、排版三处共用同一个数。 */
    private int helpLineHeight() {
        return Math.round(this.font.lineHeight * HELP_TEXT_SCALE) + 1;
    }

    /**
     * 正文视口高。优先用<b>画正文时真实用到的</b>那一个（{@link #helpViewH}），
     * 这一帧还没画过才按面板几何现算——两边一致，滚轮就不会白算成「不用滚」。
     */
    private int helpViewportH() {
        if (this.helpViewH > 0) {
            return this.helpViewH;
        }
        int[] rect = helpPanelRect();
        return Math.max(helpLineHeight(), rect[3] - 21);
    }

    /** 正文最多能往下滚多少像素；0 表示一屏放得下、不用滚。 */
    private int helpMaxScroll() {
        return this.helpDispTotalH <= 0 ? 0 : Math.max(0, this.helpDispTotalH - helpViewportH());
    }

    /** 点问号按钮：展开就现拼一份内容（跟着当前按键绑定走），再点收回。 */
    private void toggleHelpPanel() {
        if (this.helpPanelOpen) {
            closeHelpPanel();
        } else {
            this.helpLines = buildHelpLines();
            this.helpScroll = 0;
            // 两种面板不叠着开（用户问题 1 的根子）：队列面板开着时说明面板整块不画，
            // 滚动几何不更新，滚轮全无反应。点开说明就先把队列面板收掉，回来点状态条即可。
            if (this.queuePanelOpen) {
                closeQueuePanel();
            }
            // 重开按新内容重排：让排版缓存作废，第一次画时重建
            this.helpLayoutWidth = -1;
            this.helpViewH = 0;
            this.helpBusProbeLogged = false;
            this.helpGuiProbeLogged = false;
            this.helpWheelCaught = false;
            this.helpPanelOpen = true;
        }
        playClickSound();
    }

    private void closeHelpPanel() {
        this.helpPanelOpen = false;
        this.helpScroll = 0;
        this.helpScrollDragging = false;
        // 滚动条几何作废：没画过正文之前，鼠标事件不拿旧坐标判命中
        this.helpBarTotalH = 0;
        this.helpBarVisibleH = 0;
        this.helpViewH = 0;
        this.helpBusProbeLogged = false;
        this.helpGuiProbeLogged = false;
        this.helpWheelCaught = false;
    }

    /** 配色：操作（冒号前）淡黄、解释近白、组标题金、分隔线暗紫。 */
    private static final int HELP_KEY_COLOR = 0xFFF4E2A2;
    private static final int HELP_DESC_COLOR = 0xFFEAE6F6;
    private static final int HELP_HEAD_COLOR = 0xFFFFD876;
    private static final int HELP_RULE_COLOR = 0xFF4E4770;

    /**
     * 面板内容。原先这坨固定说明挂在<b>每件物品</b>的悬停框末尾——
     * 悬停什么都要重读一遍同一段话，还得每帧重拼（用户图 7 的意见）；
     * 现在搬进这块点开才画的面板，按键名一律从按键绑定里现取。
     * 排版（用户意见）：不编序号（图 22：序号怪，删）、组与组之间有分隔横线、
     * 字缩一号、「操作」和「说明」两种颜色分开——按什么键一目了然，后面的解释不抢戏。
     */
    private List<HelpRow> buildHelpLines() {
        List<HelpRow> lines = new ArrayList<>();
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_list", "列表操作"));
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.click",
                "点击：自动配料到合成格");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.shift",
                "Shift+点击：直接用存储或背包里的材料合成");
        String batchKey = gestureKeyName(ModKeyMappings.BATCH_CRAFT);
        if (batchKey != null) {
            helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.batch",
                    "%s+点击：批量合成，次数取自产物格下方的批量数量框", batchKey);
        }
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.auto",
                "%s：自动合成（缺料自动补齐，产物进输出容器）",
                fullKeybindText(ModKeyMappings.AUTO_CRAFT));
        String queueKey = gestureKeyName(ModKeyMappings.QUEUE_ADD);
        if (queueKey != null) {
            helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.queue",
                    "%s：加进合成队列，排在选中项后面（数量与批量合成数量一致）", queueKey);
        }
        String favKey = gestureKeyName(ModKeyMappings.FAVORITE);
        String favHow = favKey == null ? "Shift+右键" : favKey + " / Shift+右键";
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.fav",
                "%s：添加到收藏 / 取消收藏", favHow);
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.cycle",
                "右键：同一产物有多份配方时换配方");
        String expandKey = gestureKeyName(ModKeyMappings.EXPAND_ALL);
        if (expandKey != null) {
            helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.expand",
                    "%s+左键（列表上）：全部展开 / 全部折叠", expandKey);
        }
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_tabs", "物品分类"));
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.tab_left",
                "左键：查看当前类别（无物品的分类无法点击）");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.tab_cycle",
                "[建筑]分类点击：左键切换下一项，建筑方块→染色方块→自然方块");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.tab_back",
                "右键切回上一项（空白分类自动跳过）");
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_bag", "背包配方查询"));
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.craft_here",
                "%s：鼠标悬停在背包物品上，按C键，工作台右侧会自动寻找到此物品的配方", fullKeybindText(ModKeyMappings.CRAFT_HERE));
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_trace", "材料溯源"));
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.trace",
                "鼠标悬停可合成物品：存有合成此物品的材料容器会自动高亮");
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_buttons", "按钮作用"));
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_refresh",
                "刷新：重扫容器和背包，重建列表");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_auto",
                "自动：自动合成选定物品（金框），可在配置中修改合成数量");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_fold",
                "折叠：同类物品折叠，可自由展开与合并：搜索配方时不进行折叠");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_sort",
                "排序：默认 / 名称 / 常用 循环切换");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_clear",
                "清空：清空合成栏物品，材料会原路返回");
        helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.btn_refill",
                "自动补齐：会自动对所选配方缺失的中间产物进行制作");
        // 显示设置：数量数字的样式切换键（键位可在按键绑定里改，所以这里现取）。
        lines.add(helpHeader("gui." + SmartWorkbenchMod.MOD_ID + ".help.group_temp", "显示设置"));
        String digitKey = gestureKeyName(ModKeyMappings.TOGGLE_DIGIT_STYLE);
        if (digitKey != null) {
            helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.digit_switch",
                    "%s：数量数字在「原版字体」和「点阵数字」之间换", digitKey);
        }
        // 接入总览键在<b>世界里</b>按才生效，但玩家得先知道它有这个键——帮助页里报一句。
        String overviewKey = gestureKeyName(ModKeyMappings.CONNECTION_OVERVIEW);
        if (overviewKey != null) {
            helpItem(lines, "gui." + SmartWorkbenchMod.MOD_ID + ".help.overview",
                    "%s（退出工作台界面按）：绿框表示输入容器，红框表示输出容器，会显示工作台坐标和已连接的容器数量", overviewKey);
        }
        return lines;
    }

    /** 分组标题行：金字；画正文时它的上面自动添一条分隔横线。 */
    private HelpRow helpHeader(String key, String cn) {
        return new HelpRow(label(key, cn).withStyle(st -> st.withColor(HELP_HEAD_COLOR)), true);
    }

    /**
     * 一条操作：按第一个冒号劈成两段上色（前面"怎么按"淡黄、后面"干什么"近白）。
     * 词条按当前语言取，英文词条同样用冒号分隔。不编序号——用户嫌数字排版怪。
     */
    private void helpItem(List<HelpRow> lines, String key, String cn, Object... args) {
        Component body = args.length == 0 ? label(key, cn) : labelArgs(key, cn, args);
        String text = body.getString();
        MutableComponent row = Component.empty();
        int cut = firstColon(text);
        if (cut < 0) {
            row.append(Component.literal(text).withStyle(st -> st.withColor(HELP_KEY_COLOR)));
        } else {
            row.append(Component.literal(text.substring(0, cut + 1))
                    .withStyle(st -> st.withColor(HELP_KEY_COLOR)));
            row.append(Component.literal(text.substring(cut + 1))
                    .withStyle(st -> st.withColor(HELP_DESC_COLOR)));
        }
        lines.add(new HelpRow(row, false));
    }

    /** 行内第一个全角/半角冒号的下标（含冒号本身）；没有返回 -1。 */
    private static int firstColon(String s) {
        int a = s.indexOf('：');
        int b = s.indexOf(':');
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * 问号按钮：工作台标题栏左上角的深紫小牌——淡紫描边、淡黄问号；
     * 悬停变金框，开着时金框常亮（一眼看出再点它就是收）。
     */
    private void drawHelpButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = helpBtnX();
        int y = helpBtnY();
        boolean hovered = helpBtnHit(mouseX, mouseY);
        this.helpBtnHovered = hovered;
        graphics.fill(x, y, x + HELP_BTN_SIZE, y + HELP_BTN_SIZE, HELP_BTN_BG);
        graphics.renderOutline(x, y, HELP_BTN_SIZE, HELP_BTN_SIZE,
                hovered || this.helpPanelOpen ? HELP_BTN_EDGE_LIT : HELP_BTN_EDGE);
        if (hovered && !this.helpPanelOpen) {
            graphics.fill(x + 1, y + 1, x + HELP_BTN_SIZE - 1, y + HELP_BTN_SIZE - 1, 0x22FFFFFF);
        }
        String glyph = "?";
        graphics.drawString(this.font, glyph,
                x + (HELP_BTN_SIZE - this.font.width(glyph)) / 2,
                y + (HELP_BTN_SIZE - this.font.lineHeight) / 2,
                HELP_BTN_GLYPH, false);
    }

    /** 面板标题行：题字 + 底下那条通栏分隔线，两种形态共用。 */
    private void drawHelpPanelHeader(GuiGraphics graphics, int px, int py, int pw) {
        graphics.drawString(this.font,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".help.title", "操作说明"),
                px + HELP_PAD, py + 5, 0xFFFFFFFF, false);
        graphics.fill(px + 3, py + 14, px + pw - 3, py + 15, HELP_RULE_COLOR);
    }

    /**
     * 正文：缩一号（0.8 倍）按宽折行、逐条留呼吸间距、组题上面添横线；
     * 滚动按像素走——滚轮、拖滑块、点轨道跳，三样都有。右缘一条 3 像素宽的
     * 淡紫滚动条，几何每帧记在字段里，鼠标事件直接拿来判命中和换算。
     * <p>
     * 折行结果缓存在 {@link #helpDispRows}：只有点开那一刻或折行宽度变了才重排，
     * 每帧只画视口里那十来行（掉帧问题 3 的修法）。
     */
    private void drawHelpPanelBody(GuiGraphics graphics, int px, int py, int pw, int ph) {
        int top = py + 16;
        int bottom = py + ph - 5;
        int barReserve = 7;                                   // 右缘留给滚动条
        int contentW = Math.max(24, pw - HELP_PAD * 2 - barReserve);
        int wrapW = (int) (contentW / HELP_TEXT_SCALE);       // 折行按未缩放像素算
        int lineH = Math.round(this.font.lineHeight * HELP_TEXT_SCALE) + 1;
        int itemGap = 3;

        if (this.helpLayoutWidth != wrapW) {
            rebuildHelpLayout(wrapW, lineH, itemGap);
        }

        int viewH = Math.max(lineH, bottom - top);
        // 记下这一帧真实的视口高：滚轮/键盘/滚动条三处的可滚范围都拿它算（helpMaxScroll）
        this.helpViewH = viewH;
        int maxScroll = Math.max(0, this.helpDispTotalH - viewH);
        this.helpScroll = Mth.clamp(this.helpScroll, 0, maxScroll);

        // 视口外用剪刀裁掉：滚出去的正文不会糊到标题和边框上。
        // 剪刀必须开在攒批之前、关在提交之后——批次的提交发生在 drawManaged 出口，
        // 裁剪状态要盖住那一刻，滚出去的正文才真被裁掉。
        // 用 try/finally 收尾：批里只要抛一次异常，剪刀不关就会一直裁着后面所有界面。
        graphics.enableScissor(px + 2, top, px + pw - 1, bottom);
        try {
            List<HelpDispRow> rows = this.helpDispRows;
            // 正文整段攒一批：不开 drawManaged 时每行 drawString 末尾都要"画字即提交"，
            // 一屏十几行就是一十几次显卡提交——"点开说明直接掉 50 帧"就是这么来的。
            // 这里没有物品图标，放心合并；行的缩放姿势在各自写入时烘进顶点，合批不影响。
            graphics.drawManaged(() -> {
                int y = top - this.helpScroll;
                for (int i = 0; i < rows.size(); i++) {
                    HelpDispRow r = rows.get(i);
                    if (r.first() && i > 0) {
                        y += itemGap;
                    }
                    if (r.header()) {
                        y += 4;
                    }
                    // 视口外的行整行跳过：滚出去多远都不占绘制，只剩一次比大小
                    if (y + lineH >= top && y <= bottom) {
                        if (r.header()) {
                            graphics.fill(px + HELP_PAD, y - 3, px + pw - barReserve, y - 2, HELP_RULE_COLOR);
                        }
                        drawHelpScaledText(graphics, r.seq(), px + HELP_PAD, y, 0xFFFFFFFF);
                    }
                    y += lineH;
                }
            });
        } finally {
            graphics.disableScissor();
        }

        // 滚动条：内容溢出不满一屏才现身（也攒一批，两个 fill 加滑块高亮一次提交）
        this.helpBarTrackX = px + pw - HELP_PAD - 2;
        this.helpBarTrackTop = top;
        this.helpBarTrackH = viewH;
        this.helpBarTotalH = this.helpDispTotalH;
        this.helpBarVisibleH = viewH;
        graphics.drawManaged(() -> {
            if (maxScroll > 0) {
                graphics.fill(this.helpBarTrackX, top, this.helpBarTrackX + 3, bottom, 0x2AFFFFFF);
                int thumbH = Math.max(14, (int) ((long) viewH * viewH / this.helpDispTotalH));
                this.helpBarThumbH = thumbH;
                int thumbY = top + (int) ((long) (viewH - thumbH) * this.helpScroll / maxScroll);
                graphics.fill(this.helpBarTrackX, thumbY, this.helpBarTrackX + 3, thumbY + thumbH,
                        this.helpScrollDragging ? 0xFFE9DDFB : HELP_BTN_EDGE);
            } else {
                this.helpBarThumbH = viewH;
            }
        });
    }

    /**
     * 排版一次：把每条逻辑行按折行宽度拆成显示行，顺手把总高算出来存着。
     * 只有点开面板（{@code helpLayoutWidth} 被置 -1）或窗口宽度变了才会走到这里。
     */
    private void rebuildHelpLayout(int wrapW, int lineH, int itemGap) {
        List<HelpDispRow> rows = new ArrayList<>();
        int totalH = 0;
        for (HelpRow row : this.helpLines) {
            List<FormattedCharSequence> wrapped = this.font.split(row.text(), wrapW);
            for (int i = 0; i < wrapped.size(); i++) {
                boolean first = i == 0;
                if (first && totalH > 0) {
                    totalH += itemGap;
                }
                if (row.header()) {
                    totalH += 4;
                }
                totalH += lineH;
                rows.add(new HelpDispRow(wrapped.get(i), row.header(), first));
            }
        }
        this.helpDispRows = rows;
        this.helpDispTotalH = totalH;
        this.helpLayoutWidth = wrapW;
    }

    /** 按 HELP_TEXT_SCALE 缩放画一行正文（原版只有一档字号，"小一号"就这么来）。 */
    private void drawHelpScaledText(GuiGraphics graphics, FormattedCharSequence line,
                                    int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(HELP_TEXT_SCALE, HELP_TEXT_SCALE, 1.0F);
        graphics.drawString(this.font, line, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** 鼠标压在滚动条轨道上吗（左右各宽待 1 像素，好按）。 */
    private boolean helpBarHit(double mouseX, double mouseY) {
        return this.helpPanelOpen && this.helpBarTotalH > this.helpBarVisibleH
                && pointIn(mouseX, mouseY, this.helpBarTrackX - 1, this.helpBarTrackTop, 5,
                        this.helpBarTrackH);
    }

    /** 把鼠标纵向位置换算成滚动偏移：按住滑块就滑块跟手，点轨道就整页跳过去。 */
    private void updateHelpScrollFromMouse(double mouseY) {
        int max = helpMaxScroll();
        int movable = Math.max(1, this.helpBarTrackH - this.helpBarThumbH);
        double center = mouseY - this.helpBarTrackTop - this.helpBarThumbH / 2.0D;
        this.helpScroll = Mth.clamp((int) Math.round(center * max / movable), 0, max);
    }

    /** 滑出形态：紧贴工作台左侧的深色说明板，不压暗全屏、不吃底下的点击。 */
    private void drawHelpPanelSlide(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] rect = helpPanelRect();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, QUEUE_PANEL_Z);
        // 底板、边框、标题攒成一批（一次 fill 在没攒批时也是一次提交）；正文另有两批，见 drawHelpPanelBody
        graphics.drawManaged(() -> {
            graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0xF5322D43);
            graphics.renderOutline(rect[0], rect[1], rect[2], rect[3], HELP_BTN_EDGE);
            drawHelpPanelHeader(graphics, rect[0], rect[1], rect[2]);
        });
        drawHelpPanelBody(graphics, rect[0], rect[1], rect[2], rect[3]);
        graphics.pose().popPose();
    }

    /** 浮层形态：全屏压暗 + 居中面板（和队列面板同款脾气），界内问号重画在暗层之上。 */
    private void drawHelpPanelOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        int[] rect = helpPanelRect();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, QUEUE_PANEL_Z);
        // 暗层、底板、边框、标题、关闭键一批画完：这一段没有物品图标，能合的全合了
        graphics.drawManaged(() -> {
            graphics.fill(0, 0, this.width, this.height, 0x80000000);
            graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], 0xF5322D43);
            graphics.renderOutline(rect[0], rect[1], rect[2], rect[3], HELP_BTN_EDGE);
            drawHelpPanelHeader(graphics, rect[0], rect[1], rect[2]);
            this.helpCloseHovered = pointIn(mouseX, mouseY, rect[0] + rect[2] - 13, rect[1] + 4, 10, 10);
            if (this.helpCloseHovered) {
                graphics.fill(rect[0] + rect[2] - 13, rect[1] + 4, rect[0] + rect[2] - 3, rect[1] + 14, 0x33FFFFFF);
            }
            graphics.drawString(this.font, "×", rect[0] + rect[2] - 11, rect[1] + 5,
                    this.helpCloseHovered ? 0xFFFFFFFF : TEXT_DIM, false);
        });
        drawHelpPanelBody(graphics, rect[0], rect[1], rect[2], rect[3]);
        // 问号画在暗层之上：开着时一眼看出"点它收回去"。也攒一批：底、边框、悬停亮底、问号字一次提交。
        graphics.drawManaged(() -> drawHelpButton(graphics, mouseX, mouseY));
        graphics.pose().popPose();
    }

    /**
     * 跳过：永远是「跳过当前这一项」（客户端按当前项算出 id，服务端认 id）。
     * 以前这里接一个「鼠标停在哪行就跳哪行」的参数，但跳过键在页脚、指针根本不在行上，
     * 实际等于恒为 -1，还跟第六行有半格重叠——参数干脆删掉，语义只留一种。
     */
    private void queueSkip() {
        List<CraftQueue.Entry> entries = CraftQueue.entries();
        int target = queueCurrentRow();
        if (target < 0 || target >= entries.size()) {
            return;
        }
        playClickSound();
        CraftQueue.sendOp(this.menu, SmartWorkbenchMenu.QUEUE_OP_SKIP, entries.get(target).id);
    }

    /** 服务端没指当前项时（暂停后刚重开界面），客户端按「第一个没做完的」顶上。 */
    private int queueCurrentRow() {
        int idx = this.menu.getClientQueueIndex();
        List<CraftQueue.Entry> entries = CraftQueue.entries();
        if (idx >= 0 && idx < entries.size()) {
            return idx;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).done < entries.get(i).target) {
                return i;
            }
        }
        return -1;
    }

    /** 开始 / 暂停（面板按钮和 G 键共用）。 */
    private void queueToggleStart() {
        if (CraftQueue.isEmpty()) {
            toast("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.empty", "队列还是空的，先按加入队列键排几项进来");
            return;
        }
        int state = this.menu.getClientQueueState();
        if (state != SmartWorkbenchMenu.QUEUE_STATE_RUNNING && !CraftQueue.hasPending()) {
            // 全做完再点＝再来一轮：先把本地账本清零同步过去，再发 START。
            // 不然新轮刚起步，本地还揣着上一轮的满 done，随后任何一次改动 SYNC 都会
            // 被服务端"取较大值"的进度保护顶成已完成，这一轮就白跳过了。
            CraftQueue.resetDoneAndSync(this.menu);
        }
        CraftQueue.sendOp(this.menu, state == SmartWorkbenchMenu.QUEUE_STATE_RUNNING
                ? SmartWorkbenchMenu.QUEUE_OP_PAUSE : SmartWorkbenchMenu.QUEUE_OP_START, -1);
        playClickSound();
    }

    /** 加入队列键（默认 V）：把停在右侧列表上的那一项排进队列。 */
    private void queueAddFromHover() {
        CraftableEntry entry = craftableAtMouse();
        if (entry == null) {
            toast("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.no_hover",
                    "先把鼠标停在右侧「可合成」列表的物品上");
            return;
        }
        int qty = parseBatchCount();
        int applied = CraftQueue.add(entry.recipeId(), entry.result(), qty);
        if (applied < 0) {
            toastArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.full",
                    "队列满了（最多 %s 项）：先做完或删几项", SmartWorkbenchMenu.QUEUE_MAX_ENTRIES);
            return;
        }
        this.selectedRecipe = entry.recipeId();
        playClickSound();
        if (applied == 0) {
            // 这一项的个数已经顶到上限，加不进去了
            toastArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.capped",
                    "「%s」已经排到单项上限（%s 个），加不进去了",
                    entry.result().getHoverName().getString(), SmartWorkbenchMenu.QUEUE_MAX_TARGET);
            return;
        }
        // 报「真排进去几个」而不是「想排几个」：输入 1024 时被上限裁到 640，提示要照实说
        toastArgs("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.added",
                "已排进队列：%s ×%s（共 %s 项）",
                entry.result().getHoverName().getString(), applied, CraftQueue.size());
        // 排完不再自动弹管理面板（用户问题 5）：面板一盖住界面，想接着排下一样就得
        // 先关面板、再挪鼠标、再按 V，连着排反而更麻烦。现在按 V 只安静地排，
        // 「物品栏」右侧的队列状态条会报数，想管理点它随时开。
    }

    /** 收藏键（默认 B）：钉住 / 摘掉停在列表上的那一项。 */
    private void favoriteFromHover() {
        CraftableEntry entry = craftableAtMouse();
        if (entry == null) {
            toast("gui." + SmartWorkbenchMod.MOD_ID + ".queue.toast.no_hover",
                    "先把鼠标停在右侧「可合成」列表的物品上");
            return;
        }
        toggleFavorite(entry);
    }

    /** 此刻鼠标停在右侧列表的哪个物品上（折叠头不算）；没有返回 null。 */
    private CraftableEntry craftableAtMouse() {
        ensureDisplay();
        int index = cellIndexAt(this.lastMouseX, this.lastMouseY);
        if (index >= 0 && index < this.display.size()) {
            CraftableListView.Cell cell = this.display.get(index);
            if (!cell.header()) {
                return cell.entry();
            }
        }
        return null;
    }

    private boolean pointIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * 数量数字在「原版字体」和「点阵数字」两款之间来回换（键位见 {@link ModKeyMappings#TOGGLE_DIGIT_STYLE}）。
     * 换完在动作栏报一句当前是哪款：中英文都走语言包（审查报告问题③），提示文字形如
     * 「数量数字：点阵数字」／「数量数字：原版字体」。
     */
    private void toggleDigitStyle() {
        boolean pixel = !DigitStylePref.pixelDigits();
        DigitStylePref.setPixelDigits(pixel);
        playClickSound();
        if (pixel) {
            toast("gui." + SmartWorkbenchMod.MOD_ID + ".msg.digit_pixel", "数量数字：点阵数字");
        } else {
            toast("gui." + SmartWorkbenchMod.MOD_ID + ".msg.digit_font", "数量数字：原版字体");
        }
    }

    /** 现在有没有哪个文本框正等着打字：搜索框、批量数量框、队列数量框。 */
    private boolean textBoxFocused() {
        return (this.searchBox != null && this.searchBox.isFocused())
                || (this.batchBox != null && this.batchBox.isFocused())
                || (this.queueQtyBox != null && this.queueQtyBox.isFocused());
    }

    /**
     * 这个键在文本框里是不是一个「能打出来的字符」（字母、主键盘数字、小键盘数字、空格、标点）。
     * 样式键判断要靠它避让：玩家若把样式键绑成字母，在搜索框里按那个键应该出字、不是换款。
     * 功能键（F1–F12、方向键等）在文本框里打不出东西，所以照样让给样式切换。
     */
    private static boolean typesCharacter(int keyCode) {
        return (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z)
                || (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9)
                || (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9)
                || keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode == GLFW.GLFW_KEY_MINUS
                || keyCode == GLFW.GLFW_KEY_EQUAL
                || keyCode == GLFW.GLFW_KEY_COMMA
                || keyCode == GLFW.GLFW_KEY_PERIOD
                || keyCode == GLFW.GLFW_KEY_SLASH
                || keyCode == GLFW.GLFW_KEY_SEMICOLON
                || keyCode == GLFW.GLFW_KEY_APOSTROPHE
                || keyCode == GLFW.GLFW_KEY_LEFT_BRACKET
                || keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET
                || keyCode == GLFW.GLFW_KEY_BACKSLASH
                || keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT;
    }

    /** 行动条上冒一句（自动消失、不占聊天栏）。 */
    private void toast(String key, String fallback) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(label(key, fallback), true);
        }
    }

    private void toastArgs(String key, String fallback, Object... args) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(labelArgs(key, fallback, args), true);
        }
    }
}
