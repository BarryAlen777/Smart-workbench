package cn.blockforge.generated.mod2e8abd21.gui;

/**
 * GUI 坐标常量。<b>由 tools/gen_gui_texture.py 从 tools/gui_layout.json 自动生成，不要手改。</b>
 * 贴图上的每一个格子框都是按这里的坐标画的，两边同源，所以不会再出现「贴图和槽位错位」。
 * 想调布局：改 gui_layout.json -&gt; 跑一遍脚本 -&gt; 重新编译。
 */
public final class GuiLayout {

    private GuiLayout() {}

    /** 贴图像素尺寸。blit 必须把这两个数传进去：7 参数的 blit 会按 256x256 解释贴图，
     *  那样纵向会被拉伸 256/166 倍、右边被裁掉，界面看起来就是「格子全错位」。 */
    public static final int TEXTURE_WIDTH = 292;
    public static final int TEXTURE_HEIGHT = 166;

    /* 左侧主面板 */
    public static final int MAIN_X = 0;
    public static final int MAIN_Y = 0;
    public static final int MAIN_WIDTH = 176;
    public static final int MAIN_HEIGHT = 166;

    /* 3x3 合成格（GRID_ITEM_X/Y 就是 Slot 坐标） */
    public static final int GRID_ITEM_X = 28;
    public static final int GRID_ITEM_Y = 17;
    public static final int GRID_PITCH = 18;
    public static final int GRID_COLS = 3;
    public static final int GRID_ROWS = 3;

    /* 产物格 */
    public static final int OUTPUT_ITEM_X = 122;
    public static final int OUTPUT_ITEM_Y = 35;
    public static final int OUTPUT_BOX = 26;

    /* 合成箭头 */
    public static final int ARROW_X = 88;
    public static final int ARROW_Y = 36;
    public static final int ARROW_WIDTH = 22;
    public static final int ARROW_HEIGHT = 14;

    /* 玩家背包 3x9 */
    public static final int INV_ITEM_X = 6;
    public static final int INV_ITEM_Y = 84;
    public static final int INV_PITCH = 18;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 3;

    /* 快捷栏 */
    public static final int HOTBAR_ITEM_X = 6;
    public static final int HOTBAR_ITEM_Y = 141;
    public static final int HOTBAR_PITCH = 18;
    public static final int HOTBAR_COLS = 9;

    /* 文字标签 */
    public static final int LABEL_TITLE_X = 30;
    public static final int LABEL_TITLE_Y = 5;
    public static final int LABEL_INVENTORY_X = 6;
    public static final int LABEL_INVENTORY_Y = 72;

    /* 右侧面板 */
    public static final int PANEL_X = 178;
    public static final int PANEL_Y = 12;
    public static final int PANEL_WIDTH = 108;
    public static final int PANEL_HEIGHT = 146;

    /* 可合成列表 5x5 */
    public static final int LIST_ITEM_X = 182;
    public static final int LIST_ITEM_Y = 32;
    public static final int LIST_PITCH = 18;
    public static final int LIST_COLS = 5;
    public static final int LIST_ROWS = 5;

    /* 列表标题 */
    public static final int LIST_TITLE_X = 232;
    public static final int LIST_TITLE_Y = 16;

    /* 滚动条 */
    public static final int SCROLL_X = 276;
    public static final int SCROLL_WIDTH = 4;

    /* 刷新按钮 */
    public static final int BTN_X = 182;
    public static final int BTN_Y = 126;
    public static final int BTN_WIDTH = 44;
    public static final int BTN_HEIGHT = 13;

    /* 自动合成按钮 */
    public static final int BTN_AUTO_X = 230;
    public static final int BTN_AUTO_Y = 126;
    public static final int BTN_AUTO_WIDTH = 44;
    public static final int BTN_AUTO_HEIGHT = 13;

    /* 自动补齐开关 */
    public static final int BTN_REFILL_X = 92;
    public static final int BTN_REFILL_Y = 68;
    public static final int BTN_REFILL_WIDTH = 78;
    public static final int BTN_REFILL_HEIGHT = 13;

    /* 接入存储计数 */
    public static final int STORAGE_X = 182;
    public static final int STORAGE_Y = 143;

    /** 配色 0xAARRGGBB，和贴图同一套。 */
    public static final int TEXT_COLOR = 0xFF4C4B63;
    public static final int TEXT_DIM_COLOR = 0xFF7C7A92;
    public static final int LINE_COLOR = 0xFF63637A;
    public static final int LINE_SOFT_COLOR = 0xFF9291A8;
    public static final int ACCENT_COLOR = 0xFF6F9CBE;
}
