package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.compat.EmiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.JeiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.PolymorphCompat;
import cn.blockforge.generated.mod2e8abd21.compat.ReiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SCraftRequestPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private static final int SEARCH_X = GuiLayout.SEARCH_X;
    private static final int SEARCH_Y = GuiLayout.SEARCH_Y;
    private static final int SEARCH_W = GuiLayout.SEARCH_WIDTH;
    private static final int SEARCH_H = GuiLayout.SEARCH_HEIGHT;

    private static final int FOLD_BUTTON_X = GuiLayout.BTN_FOLD_X;
    private static final int FOLD_BUTTON_Y = GuiLayout.BTN_FOLD_Y;
    private static final int FOLD_BUTTON_W = GuiLayout.BTN_FOLD_WIDTH;
    private static final int FOLD_BUTTON_H = GuiLayout.BTN_FOLD_HEIGHT;

    // 新贴图是浅色水彩 + 深紫灰格子：浅底上的字用深色，深色格子上的字用白色，
    // 两边都补一层阴影，中文才不会发虚、也不会和贴图纹理糊在一起。
    private static final int TEXT_MAIN = GuiLayout.TEXT_COLOR;
    private static final int TEXT_DIM = GuiLayout.TEXT_DIM_COLOR;
    /** 深色格子上的文字（列表为空时的提示）。 */
    private static final int TEXT_ON_DARK = 0xFFFFFFFF;
    private static final int HOVER_BG = 0x408A86B8;
    /** 选中项的描边色（金）。 */
    private static final int SELECT_COLOR = 0xFFE0A83C;
    /** 同类折叠头的描边色（青），和选中的金色区分开。 */
    private static final int GROUP_COLOR = 0xFF4FC3F7;
    /** 折叠头角上的数量数字。 */
    private static final int GROUP_COUNT_COLOR = 0xFFFFFFFF;
    /**
     * 折叠头的「+/-」和数量数字的描边色。
     * <p>
     * 上一版给它们垫了一块<b>不透明</b>的深色方底，字是清楚了，可那块底把折叠项的图标盖掉
     * 将近一半（一个数字连底约 8x10 像素，格子图标总共才 16x16），玩家看得见数字、
     * 却认不出这一堆折叠的到底是什么东西。改成沿字的外圈描 1 像素黑边：
     * 笔画是实心白/青、外圈是黑，落在任何颜色的图标上都有足够对比度，
     * 而图标只在字本身那几个像素上被让开，整体轮廓照样看得清。
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
     * 说明文字的 z 层。原版物品图标画在 z=150，原版提示框靠 translate(0,0,400) 浮到图标之上
     * （见 GuiGraphics.renderTooltipInternal）；我们只有站到同一个高度，文字才不会被图标压住。
     */
    private static final float TOOLTIP_Z = 400.0F;

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
    /** 鼠标是否悬停在"自动"/"自动补齐"按钮上；说明文字要留到 render 最末尾那一层才画。 */
    private boolean autoHovered;
    private boolean refillHovered;
    /**
     * 这一帧是否已经把列表项的提示框画掉了。
     * 右侧列表里的图标不是真的 Slot，原版的槽位提示框管不到它们，所以由我们覆写的
     * {@link #renderTooltip} 补一份；画过就记一笔，按钮说明就不必再挤上来。
     */
    private boolean listHintDrawn;
    /** 是否正在按住右侧滚动条拖动列表。 */
    private boolean draggingScroll;
    /** 当前选中的配方（自动合成按钮 / 快捷键就作用于它）；null 表示还没选。按配方 id 记，列表过滤/折叠后也找得回来。 */
    private ResourceLocation selectedRecipe;
    /** 折叠按钮是否正被按住。 */
    private boolean foldPressed;
    /** 鼠标是否悬停在折叠按钮上。 */
    private boolean foldHovered;

    /** 搜索框：只负责过滤右侧列表，不参与网络协议。 */
    private EditBox searchBox;
    /** 同类折叠开关，默认开（列表里同一种尾段的物品收成一项）。 */
    private boolean folding = true;
    /** 已经点开、正在展开显示成员的分类。 */
    private final Set<String> expandedGroups = new HashSet<>();
    /** 过滤 + 折叠之后真正要画的一格一格。 */
    private List<CraftableListView.Cell> display = List.of();
    /** display 是否需要按当前搜索词 / 折叠状态重算。 */
    private boolean displayDirty = true;
    /** display 是照着哪一份 craftables 算的，换了对象就重算。 */
    private List<CraftableEntry> displaySource;

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
        this.displayDirty = true;
        this.displaySource = null;
    }

    /** 物品管理器可能比界面晚准备好，或中途重载；每 tick 补挂一次，已经在就只做一次字段比较。 */
    @Override
    protected void containerTick() {
        super.containerTick();
        ensureRecipeViewerBridges();
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        ensureDisplay();
    }

    /** 搜索词或折叠状态变了才重算列表，平时就用上一帧的结果。 */
    private void ensureDisplay() {
        List<CraftableEntry> source = this.menu.getCraftables();
        if (!this.displayDirty && source == this.displaySource) {
            return;
        }
        this.displaySource = source;
        this.displayDirty = false;
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        this.display = CraftableListView.build(source, query, this.folding, this.expandedGroups);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    @Override
    public void removed() {
        super.removed();
        EmiTransferBridge.clearActiveMenu();
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
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());

        // 上移 1 像素：贴图在标题区下沿（y=26）有一条装饰横线，字贴太近会像被线切掉。
        // 标题按用户要求左移，右边整段让给搜索框（坐标都在 GuiLayout 里）。
        drawCentred(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.craftable", "可合成"),
                this.leftPos + GuiLayout.LIST_TITLE_X, this.topPos + GuiLayout.LIST_TITLE_Y - 1, TEXT_MAIN, false);

        // 搜索框画在背景层：它只占标题行，不和任何物品格重叠，不会挡东西
        if (this.searchBox != null) {
            drawSearchBoxFrame(graphics);
            this.searchBox.render(graphics, mouseX, mouseY, partialTick);
        }

        // 5x5 的配方图标
        int hovered = cellIndexAt(mouseX, mouseY);
        for (int row = 0; row < LIST_ROWS; row++) {
            for (int col = 0; col < LIST_COLS; col++) {
                int index = (this.scroll + row) * LIST_COLS + col;
                if (index >= this.display.size()) {
                    break;
                }
                CraftableListView.Cell cell = this.display.get(index);
                int x = this.leftPos + LIST_X + col * CELL;
                int y = this.topPos + LIST_Y + row * CELL;
                if (hovered == index) {
                    // 贴图上的格子框是 18x18，左上角比物品坐标各小 1 像素，高亮正好盖住整格
                    graphics.fill(x - 1, y - 1, x + CELL - 1, y + CELL - 1, HOVER_BG);
                }
                if (!cell.header() && this.selectedRecipe != null
                        && this.selectedRecipe.equals(cell.entry().recipeId())) {
                    // 选中的那格描一圈金边：自动合成按钮和快捷键要作用在它身上
                    graphics.renderOutline(x - 1, y - 1, CELL, CELL, SELECT_COLOR);
                }
                ItemStack result = cell.entry().result();
                graphics.renderItem(result, x, y);
                if (cell.header()) {
                    drawGroupHeader(graphics, cell, x, y);
                } else {
                    graphics.renderItemDecorations(this.font, result, x, y);
                }
            }
        }

        if (this.display.isEmpty()) {
            String query = this.searchBox == null ? "" : this.searchBox.getValue();
            if (!query.isBlank()) {
                drawCentredHint(graphics, labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty_search",
                        "没有找到匹配「%s」的配方", query));
            } else {
                drawCentredHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty",
                        "接入存储和背包里暂时没有能直接合成的东西"));
            }
        }

        // 滚动条（贴图已画好轨道，这里只画滑块）
        int maxScroll = maxScroll();
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

        // 两个按钮：底色已经画进贴图，这里补悬停/按下的高亮和文字
        drawButton(graphics, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, isPointInButton(mouseX, mouseY),
                this.refreshPressed, label("gui." + SmartWorkbenchMod.MOD_ID + ".button.refresh", "刷新"));
        boolean autoHovered = isPointInAutoButton(mouseX, mouseY);
        boolean refillHovered = isPointInRefillButton(mouseX, mouseY);
        boolean foldHovered = isPointInFoldButton(mouseX, mouseY);
        drawButton(graphics, AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H,
                autoHovered, this.autoPressed,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".button.auto", "自动"));
        drawButton(graphics, FOLD_BUTTON_X, FOLD_BUTTON_Y, FOLD_BUTTON_W, FOLD_BUTTON_H,
                foldHovered, this.foldPressed,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".button.fold", "折叠"));
        if (this.folding) {
            // 按钮本身没有"开/关"字样，用金边表示折叠正开着
            graphics.renderOutline(this.leftPos + FOLD_BUTTON_X, this.topPos + FOLD_BUTTON_Y,
                    FOLD_BUTTON_W, FOLD_BUTTON_H, SELECT_COLOR);
        }
        drawButton(graphics, REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H,
                refillHovered, false,
                label(this.menu.isAutoRefillEnabled()
                                ? "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_on"
                                : "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_off",
                        this.menu.isAutoRefillEnabled() ? "自动补齐：开" : "自动补齐：关"));
        // 按钮的说明文字不在这里画：renderBg 是背景层，画在这里会被上面的物品图标
        // 和数量角标压住（之前"自动合成"的说明就是这么糊在图标上的）。真正的最上层是
        // render 的末尾（所有图标、提示框都画完、并且把 z 抬到 TOOLTIP_Z 之后），那里才画按钮说明。
        this.autoHovered = autoHovered;
        this.refillHovered = refillHovered;
        this.foldHovered = foldHovered;

        // 接入存储 / 输出容器数量只绘制一次且关闭阴影，避免浅色面板上出现重影。
        Component storageLine = Component.translatableWithFallback(
                        "gui." + SmartWorkbenchMod.MOD_ID + ".panel.storage", "接入存储: ")
                .copy().append(String.valueOf(this.menu.getConnectedStorageCount()))
                .append(Component.translatableWithFallback(
                        "gui." + SmartWorkbenchMod.MOD_ID + ".panel.output", "  输出: "))
                .append(String.valueOf(this.menu.getOutputCount()));
        graphics.drawString(this.font, storageLine,
                this.leftPos + GuiLayout.STORAGE_X, this.topPos + GuiLayout.STORAGE_Y, TEXT_DIM, false);

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        // 补上原版那一句：画本次悬停的物品提示框（背包/合成格是真的 Slot，右侧列表由覆写的
        // renderTooltip 补一份）。这一句同时会把 listHintDrawn 标记好，下面就不用再弹按钮说明。
        this.renderTooltip(graphics, mouseX, mouseY);
        // 真正的最上层：所有图标和提示框都画完之后，最后补按钮说明，绝不会被谁盖住。
        drawButtonHints(graphics, mouseX, mouseY);
    }

    /** 按钮说明：鼠标停在"自动"或"自动补齐"上时弹一段说明，画在整帧的最上层。 */
    private void drawButtonHints(GuiGraphics graphics, int mouseX, int mouseY) {
        // 鼠标停在列表项上时，列表本来就有自己的物品提示框，这时不弹按钮说明，免得两个框叠在一起
        if (this.listHintDrawn) {
            return;
        }
        if (this.autoHovered) {
            drawButtonHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.auto_button",
                            "自动合成：先点右侧要合成的物品选中它，再点这个按钮。缺少的中间材料会自动补齐，产物送进输出容器"),
                    AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H);
        } else if (this.refillHovered) {
            drawButtonHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.refill",
                            "缺少的中间材料可由其他配方合成"),
                    REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H);
        } else if (this.foldHovered) {
            drawButtonHint(graphics, label(this.folding
                            ? "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fold_on"
                            : "gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.fold_off",
                    this.folding
                            ? "同类折叠：开。把同一种东西（比如各种木头的楼梯）收成一项，点它展开；再点这个按钮可关掉"
                            : "同类折叠：关。列表里的每一项都单独显示"),
                    FOLD_BUTTON_X, FOLD_BUTTON_Y, FOLD_BUTTON_W, FOLD_BUTTON_H);
        }
    }

    /** 列表空的时候，把提示文字在 5x5 区域里居中排，避免压在格子上显得乱。 */
    private void drawCentredHint(GuiGraphics graphics, Component message) {
        int width = LIST_COLS * CELL - 8;
        List<FormattedCharSequence> lines = this.font.split(message, width);
        int areaTop = this.topPos + LIST_Y;
        int total = lines.size() * (this.font.lineHeight + 1);
        int y = areaTop + (LIST_ROWS * CELL - total) / 2 + 2;
        for (FormattedCharSequence line : lines) {
            // 提示文字压在下方的深紫灰格子上，必须用白色 + 阴影才看得清
            graphics.drawString(this.font, line,
                    this.leftPos + LIST_X + (width - this.font.width(line)) / 2, y, TEXT_ON_DARK, false);
            y += this.font.lineHeight + 1;
        }
    }

    /**
     * 自己画搜索框的底、边和占位提示。
     * EditBox 关掉边框后只剩光标和文字，底和边在这里补；不聚焦且没输入时，
     * 用浅灰把「搜索 / 拼音」写在框里，玩家才知道这里能打字。
     */
    private void drawSearchBoxFrame(GuiGraphics graphics) {
        int x = this.leftPos + SEARCH_X;
        int y = this.topPos + SEARCH_Y;
        int border = this.searchBox.isFocused() ? SELECT_COLOR : GuiLayout.LINE_SOFT_COLOR;
        graphics.fill(x - 1, y - 1, x + SEARCH_W + 1, y + SEARCH_H + 1, border);
        graphics.fill(x, y, x + SEARCH_W, y + SEARCH_H, SEARCH_BG);
        if (this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            // 提示要裁剪在框内：中文提示比 70 像素的框略宽，不裁会漏到框外（原版 EditBox 也裁）。
            graphics.enableScissor(x + 1, y + 1, x + SEARCH_W - 1, y + SEARCH_H - 1);
            graphics.drawString(this.font,
                    label("gui." + SmartWorkbenchMod.MOD_ID + ".search.hint", "搜索 / 拼音"),
                    x + 4, y + (SEARCH_H - 8) / 2, SEARCH_HINT_COLOR, false);
            graphics.disableScissor();
        }
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
     * 角标本身不再垫深色底，改成描边（见 {@link #drawOutlinedChar}），免得挡住折叠的是什么物品。
     */
    private void drawGroupHeader(GuiGraphics graphics, CraftableListView.Cell cell, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, GROUP_CHIP_Z);
        graphics.renderOutline(x - 1, y - 1, CELL, CELL, GROUP_COLOR);
        String mark = this.expandedGroups.contains(cell.groupKey()) ? "-" : "+";
        drawOutlinedChar(graphics, mark, x + 1, y + 1, GROUP_COLOR);
        String count = String.valueOf(cell.groupSize());
        drawOutlinedChar(graphics, count, x + CELL - 2 - this.font.width(count), y + 9, GROUP_COUNT_COLOR);
        graphics.pose().popPose();
    }

    /**
     * 描边字：先把同一个字在周围 8 个方向各画一遍纯黑，再在正中画本色。
     * <p>
     * 字的外圈是一整圈不透明黑，笔画是实心亮色——底下是亮青的钻石装备还是深灰的铁锭，
     * 对比度都一样；代价只有字自己占的那几个像素。相比上一版「垫一块 8x10 的深色底」，
     * 折叠项的图标从被盖掉近一半变成基本完整，认得出是什么东西了。
     * <p>
     * 调用方要先把姿势栈抬到 {@link #GROUP_CHIP_Z}，否则图标会连描边带字一起盖住。
     */
    private void drawOutlinedChar(GuiGraphics graphics, String text, int x, int y, int color) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    graphics.drawString(this.font, text, x + dx, y + dy, CHIP_OUTLINE_COLOR, false);
                }
            }
        }
        graphics.drawString(this.font, text, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleSearchBoxClick(mouseX, mouseY, button)) {
            return true;
        }
        ensureDisplay();
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
                playClickSound();
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_BATCH_GRID);
                return true;
            }
            int batchIndex = cellIndexAt(mouseX, mouseY);
            if (batchIndex >= 0 && !this.display.get(batchIndex).header()) {
                CraftableListView.Cell cell = this.display.get(batchIndex);
                this.selectedRecipe = cell.entry().recipeId();
                playClickSound();
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(),
                        cell.entry().recipeId(), true, true));
                return true;
            }
        }
        // 自动合成键（默认 Alt+左键，也可以在按键绑定里改）：点在列表物品上就合成那一项，点别处就合成选中项。
        // 折叠头不再响应自动合成键——上一版「全部展开」借用了同一个手势，玩家反馈撞键；
        // 现在 Alt+左键点折叠头就落回普通左键，只展开/收起被点的那一类。
        if (isAutoCraftGesture(button)) {
            int autoIndex = cellIndexAt(mouseX, mouseY);
            if (autoIndex >= 0) {
                CraftableListView.Cell cell = this.display.get(autoIndex);
                if (!cell.header()) {
                    selectAndAutoCraft(cell);
                    return true;
                }
            } else {
                autoCraftSelected();
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
                playClickSound();
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_REFRESH);
                return true;
            }
            if (isPointInAutoButton(mouseX, mouseY)) {
                this.autoPressed = true;
                autoCraftSelected();
                return true;
            }
            if (isPointInFoldButton(mouseX, mouseY)) {
                this.foldPressed = true;
                toggleFolding();
                return true;
            }
            if (isPointInRefillButton(mouseX, mouseY)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_AUTO_REFILL);
                playClickSound();
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
                this.selectedRecipe = cell.entry().recipeId();
                playClickSound();
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(), cell.entry().recipeId(),
                        hasShiftDown(), false));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 「按住某个键盘键 + 鼠标左键」的手势判定。
     * <p>
     * 键取的是 {@link KeyMapping} 当前绑定的值——也就是玩家在游戏按键设置里自己配的那个键——
     * 每一下点击都实时查 GLFW 的按下状态，改绑之后立刻生效，不用重开游戏。
     * 绑成键盘键才认；玩家把它清成"未绑定"时手势自然失效。
     */
    private boolean isHeldClickGesture(KeyMapping mapping, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || this.minecraft == null || this.minecraft.getWindow() == null) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(this.minecraft.getWindow().getWindow(), key.getValue());
    }

    /**
     * 手势键的显示名（如「左 Ctrl」「空格」）；未绑定返回 null，对应的提示行就整条不显示。
     * <p>
     * 返回字符串而不是组件：提示文案都带英文回退，%s 落进 {@code String.format} 时
     * 字符串在两条路径（有翻译 / 用回退）下长得一模一样，组件的 toString 会变成一坨 JSON。
     */
    private String gestureKeyName(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) {
            return null;
        }
        return key.getDisplayName().getString();
    }

    /**
     * 完整键位描述（如「Alt+鼠标左键」）：给自动合成那种「修饰键+鼠标」的绑定用。
     * Forge 把 {@link KeyMapping#getTranslatedKeyMessage()} 打成「修饰键在前、键名在后」的组合显示
     * （内部走 KeyModifier.getCombinedName，NONE 时只剩键名），取字符串即可，不能再自己拼一遍修饰键。
     */
    private String fullKeybindText(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    /** 点搜索框就聚焦并把光标放好；点别处就取消聚焦，免得打字一直被搜索框吃掉。 */
    private boolean handleSearchBoxClick(double mouseX, double mouseY, int button) {
        if (this.searchBox == null) {
            return false;
        }
        if (isPointInSearchBox(mouseX, mouseY)) {
            if (!this.searchBox.isFocused()) {
                this.searchBox.setFocused(true);
            }
            this.searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
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

    /** 这一下点击是不是自动合成键（默认 Alt+左键，也可以在按键设置里改）。 */
    private boolean isAutoCraftGesture(int button) {
        if (!ModKeyMappings.AUTO_CRAFT.matchesMouse(button)) {
            return false;
        }
        KeyModifier modifier = ModKeyMappings.AUTO_CRAFT.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
    }

    /** 自动合成当前选中的配方；没选中就提示一句。 */
    private void autoCraftSelected() {
        CraftableEntry entry = findSelected();
        if (entry == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.select_first",
                                "先在右侧列表里点一下要自动合成的物品"), true);
            }
            return;
        }
        sendAutoCraft(entry);
    }

    /** 按配方 id 在最新的可合成列表里找回选中项：列表被搜索/折叠/刷新后也找得回来。 */
    private CraftableEntry findSelected() {
        if (this.selectedRecipe == null) {
            return null;
        }
        for (CraftableEntry entry : this.menu.getCraftables()) {
            if (this.selectedRecipe.equals(entry.recipeId())) {
                return entry;
            }
        }
        return null;
    }

    private void sendAutoCraft(CraftableEntry entry) {
        playClickSound();
        ModNetwork.sendToServer(new C2SAutoCraftPacket(this.menu.getBenchPos(), entry.recipeId()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 搜索框聚焦时，键盘优先给搜索框：Esc/回车退出输入，其余按键一律吞掉，
        // 免得打字时误触刷新、自动合成快捷键，或者被 E 键把整个界面关掉。
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        // Ctrl+F 聚焦搜索框，像 REI 一样顺手
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && this.searchBox != null) {
            this.searchBox.setFocused(true);
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
        if (this.searchBox != null && this.searchBox.isFocused()
                && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 松开鼠标就取消按钮和滚动条的按住状态。
        this.refreshPressed = false;
        this.autoPressed = false;
        this.foldPressed = false;
        boolean wasDragging = this.draggingScroll;
        this.draggingScroll = false;
        return wasDragging || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroll && button == 0) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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

    private void drawCentred(GuiGraphics graphics, FormattedCharSequence text, int centerX, int y,
                             int color, boolean shadow) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, shadow);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
        int index = cellIndexAt(mouseX, mouseY);
        if (index >= 0 && index < this.display.size()) {
            CraftableListView.Cell cell = this.display.get(index);
            graphics.renderTooltip(this.font,
                    cell.header() ? buildGroupTooltip(cell) : buildTooltip(cell.entry()),
                    Optional.empty(), mouseX, mouseY);
            // 记一笔：这一帧已经弹过列表提示框，render 末尾就别再补按钮说明了
            this.listHintDrawn = true;
        }
        if (overResult) {
            ItemStack shown = hoveredSlot.getItem();
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(shown));
            String batchKey = gestureKeyName(ModKeyMappings.BATCH_CRAFT);
            if (batchKey != null) {
                // 按键名跟着玩家的绑定走，别写死 Ctrl
                lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.result_batch",
                                "%s+点击：批量合成合成格里这份配方，直到材料用完（自己摆的料也算）", batchKey)
                        .withStyle(ChatFormatting.GRAY));
            }
            graphics.renderTooltip(this.font, lines, shown.getTooltipImage(), shown, mouseX, mouseY);
        }
        // 这个方法由上面 render 里的 this.renderTooltip(...) 调用（AbstractContainerScreen.render
        // 自己不调它，必须手动补一句）。两个按钮的说明仍然画在 render 末尾。
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
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_click",
                "左键：展开 / 收起这一类").withStyle(ChatFormatting.YELLOW));
        String expandKey = gestureKeyName(ModKeyMappings.EXPAND_ALL);
        if (expandKey != null) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.group_expand_all",
                    "%s+左键：全部展开 / 全部折叠", expandKey).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private List<Component> buildTooltip(CraftableEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(entry.result().getHoverName().copy().withStyle(ChatFormatting.WHITE));
        Recipe<?> recipe = (this.minecraft == null || this.minecraft.level == null) ? null
                : this.minecraft.level.getRecipeManager().<Recipe<?>>byKey(entry.recipeId()).orElse(null);
        if (recipe != null) {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            Map<ItemStack, Integer> merged = new LinkedHashMap<>();
            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                ItemStack[] previews = ingredient.getItems();
                if (previews.length == 0) {
                    continue;
                }
                merged.merge(previews[0].copy(), 1, Integer::sum);
            }
            for (Map.Entry<ItemStack, Integer> stackEntry : merged.entrySet()) {
                lines.add(stackEntry.getKey().getHoverName().copy()
                        .append("x" + stackEntry.getValue()).withStyle(ChatFormatting.GRAY));
            }
        }
        // 一件产物可能有好几份配方（不同模组各加一份）。把来源和份数写出来，
        // 玩家才知道列表里两个长得一样的图标到底差在哪、该怎么选。
        if (entry.recipeId() != null) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.recipe_source",
                            "配方来源：%s", entry.recipeId().getNamespace())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int sameOutput = countSameOutput(entry);
        if (sameOutput > 1) {
            lines.add((PolymorphCompat.isLoaded()
                    ? labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.polymorph",
                            "这件产物有 %s 份配方，取料后可用产物格旁的多态合成按钮切换", sameOutput)
                    : labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.duplicate",
                            "这件产物有 %s 份配方，点哪一项就用哪一份", sameOutput))
                    .withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.empty());
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.click", "点击：自动配料到合成格")
                .withStyle(ChatFormatting.YELLOW));
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.shift", "Shift+点击：直接用存储或背包里的材料合成")
                .withStyle(ChatFormatting.YELLOW));
        String batchKey = gestureKeyName(ModKeyMappings.BATCH_CRAFT);
        if (batchKey != null) {
            lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.ctrl",
                    "%s+点击：批量合成，直到材料用完", batchKey)
                    .withStyle(ChatFormatting.YELLOW));
        }
        lines.add(labelArgs("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.auto",
                        "%s：自动合成（缺料自动补齐，产物进输出容器）",
                        fullKeybindText(ModKeyMappings.AUTO_CRAFT))
                .withStyle(ChatFormatting.GOLD));
        return lines;
    }

    /** 列表里还有几项是同一件产物。 */
    private int countSameOutput(CraftableEntry entry) {
        int count = 0;
        for (CraftableEntry other : this.menu.getCraftables()) {
            if (ItemStack.isSameItemSameTags(other.result(), entry.result())) {
                count++;
            }
        }
        return count;
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
     * 在按钮旁边固定绘制说明。之前默认画在按钮右下方，而"自动"按钮的右下正好是 5x5
     * 可合成列表，长文本横跨四列图标、又只有半透明底，字全糊在图标和数量角标上。
     * 现在改成：优先用按钮右侧的空位，放不下就挪到按钮正上方；不管落到哪都先测一遍，
     * 宁可略微超出面板，也不回头压住列表里的物品。
     */
    private void drawButtonHint(GuiGraphics graphics, Component message,
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

        int screenX = this.leftPos + x;
        int screenY = this.topPos + y;
        // 图层抬到最高：原版的物品图标是画在 z=150 那一层的，原版提示框靠 translate(0,0,400)
        // 压到图标之上（见 GuiGraphics.renderTooltipInternal）。我们照抄同一个高度，
        // 否则底框和文字虽然后画，仍会被图标和数量角标盖住（截图里就是文字被钻石装备压住）。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        // 不透明的深紫底 + 白字，对比度拉满：这一层在图标之上，底下有什么都不影响阅读
        graphics.fill(screenX - 2, screenY - 2, screenX + boxWidth + 2, screenY + boxHeight + 2, 0xFF2A2440);
        graphics.renderOutline(screenX - 2, screenY - 2, boxWidth + 4, boxHeight + 4, 0xFFE0A83C);
        int lineY = screenY + 2;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, screenX + 1, lineY, TEXT_ON_DARK, false);
            lineY += lineHeight;
        }
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

    private boolean isPointInFoldButton(double mouseX, double mouseY) {
        int x = this.leftPos + FOLD_BUTTON_X;
        int y = this.topPos + FOLD_BUTTON_Y;
        return mouseX >= x && mouseX < x + FOLD_BUTTON_W && mouseY >= y && mouseY < y + FOLD_BUTTON_H;
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
        int px = this.leftPos + x;
        int py = this.topPos + y;
        if (hovered) {
            graphics.fill(px + 1, py + 1, px + w - 1, py + h - 1, down ? BUTTON_PRESSED : BUTTON_HOVER);
        }
        int textY = py + (h - this.font.lineHeight) / 2 + (down ? 1 : 0);
        drawCentred(graphics, text, px + w / 2, textY, down ? TEXT_PRESSED : TEXT_MAIN, false);
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
        // 统一带阴影：贴图是带水彩纹理的浅色底，纯平的文字会发虚、和纹理糊在一起
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_MAIN, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, TEXT_DIM, false);
    }
}
