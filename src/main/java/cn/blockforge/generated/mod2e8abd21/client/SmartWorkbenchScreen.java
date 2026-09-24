package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.ModNetwork;
import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import cn.blockforge.generated.mod2e8abd21.compat.EmiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.JeiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.compat.ReiTransferBridge;
import cn.blockforge.generated.mod2e8abd21.gui.GuiLayout;
import cn.blockforge.generated.mod2e8abd21.menu.CraftableEntry;
import cn.blockforge.generated.mod2e8abd21.menu.SmartWorkbenchMenu;
import cn.blockforge.generated.mod2e8abd21.network.C2SAutoCraftPacket;
import cn.blockforge.generated.mod2e8abd21.network.C2SCraftRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.NonNullList;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    // 新贴图是浅色水彩 + 深紫灰格子：浅底上的字用深色，深色格子上的字用白色，
    // 两边都补一层阴影，中文才不会发虚、也不会和贴图纹理糊在一起。
    private static final int TEXT_MAIN = GuiLayout.TEXT_COLOR;
    private static final int TEXT_DIM = GuiLayout.TEXT_DIM_COLOR;
    /** 深色格子上的文字（列表为空时的提示）。 */
    private static final int TEXT_ON_DARK = 0xFFFFFFFF;
    private static final int HOVER_BG = 0x408A86B8;
    /** 选中项的描边色（金）。 */
    private static final int SELECT_COLOR = 0xFFE0A83C;
    /** 按钮悬停/按下：贴图上的按钮底色是浅灰白，高亮必须用深色半透明才看得见。 */
    private static final int BUTTON_HOVER = 0x264C4B63;
    private static final int BUTTON_PRESSED = 0x4D4C4B63;
    private static final int TEXT_PRESSED = 0xFF33324A;
    private static final int SCROLL_TRACK = GuiLayout.LINE_SOFT_COLOR;
    private static final int SCROLL_THUMB = GuiLayout.ACCENT_COLOR;

    private int scroll;
    /** 刷新按钮是否正被按住，用来做原版那种"按下去"的视觉反馈。 */
    private boolean refreshPressed;
    /** 自动合成按钮是否正被按住。 */
    private boolean autoPressed;
    /** 是否正在按住右侧滚动条拖动列表。 */
    private boolean draggingScroll;
    /** 当前选中的列表项（自动合成按钮 / 快捷键就作用于它）；-1 表示还没选。 */
    private int selectedIndex = -1;
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

    /** 物品管理器可能比界面晚准备好，或中途重载；每 tick 补挂一次，已经在就只做一次字段比较。 */
    @Override
    protected void containerTick() {
        super.containerTick();
        ensureRecipeViewerBridges();
    }

    @Override
    public void removed() {
        super.removed();
        EmiTransferBridge.clearActiveMenu();
    }

    /** 列表按行滚动：最多能往下滚几行。 */
    private int maxScroll() {
        int rows = (this.menu.getCraftables().size() + LIST_COLS - 1) / LIST_COLS;
        return Math.max(0, rows - LIST_ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 关键：必须把贴图真实尺寸传给 blit。7 参数的 blit 内部按 256x256 换算 UV，
        // 我们这张是 292x166，于是整张贴图会被纵向拉伸 256/166≈1.54 倍、右边裁掉 12%，
        // 每一个格子都会跟着偏——这就是之前"重画贴图还是错位"的真正原因。
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);

        List<CraftableEntry> list = this.menu.getCraftables();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());

        // 上移 1 像素：贴图在标题区下沿（y=24）有一条装饰横线，字贴太近会像被线切掉
        drawCentred(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.craftable", "可合成"),
                this.leftPos + GuiLayout.LIST_TITLE_X, this.topPos + GuiLayout.LIST_TITLE_Y - 1, TEXT_MAIN, false);

        // 5x5 的配方图标
        int hovered = cellIndexAt(mouseX, mouseY);
        for (int row = 0; row < LIST_ROWS; row++) {
            for (int col = 0; col < LIST_COLS; col++) {
                int index = (this.scroll + row) * LIST_COLS + col;
                if (index >= list.size()) {
                    break;
                }
                int x = this.leftPos + LIST_X + col * CELL;
                int y = this.topPos + LIST_Y + row * CELL;
                if (hovered == index) {
                    // 贴图上的格子框是 18x18，左上角比物品坐标各小 1 像素，高亮正好盖住整格
                    graphics.fill(x - 1, y - 1, x + CELL - 1, y + CELL - 1, HOVER_BG);
                }
                if (index == this.selectedIndex) {
                    // 选中的那格描一圈金边：自动合成按钮和快捷键要作用在它身上
                    graphics.renderOutline(x - 1, y - 1, CELL, CELL, SELECT_COLOR);
                }
                ItemStack result = list.get(index).result();
                graphics.renderItem(result, x, y);
                graphics.renderItemDecorations(this.font, result, x, y);
            }
        }

        if (list.isEmpty()) {
            drawCentredHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".panel.empty",
                    "接入存储里暂时没有能直接合成的东西"));
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
        drawButton(graphics, AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H,
                autoHovered, this.autoPressed,
                label("gui." + SmartWorkbenchMod.MOD_ID + ".button.auto", "自动"));
        drawButton(graphics, REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H,
                refillHovered, false,
                label(this.menu.isAutoRefillEnabled()
                                ? "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_on"
                                : "gui." + SmartWorkbenchMod.MOD_ID + ".button.refill_off",
                        this.menu.isAutoRefillEnabled() ? "自动补齐：开" : "自动补齐：关"));
        if (autoHovered) {
            drawButtonHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.auto_button",
                    "自动合成，请绑定输出容器使用。点击自动合成，再点击想合成的物品，再点击一次自动合成。批量合成后的物品会送到输出容器"),
                    AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H);
        } else if (refillHovered) {
            drawButtonHint(graphics, label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.refill",
                    "缺少的中间材料可由其他配方合成"),
                    REFILL_BUTTON_X, REFILL_BUTTON_Y, REFILL_BUTTON_W, REFILL_BUTTON_H);
        }

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
        super.render(graphics, mouseX, mouseY, partialTick);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 自动合成键（默认 Alt+左键）：点在列表项上就合成那一项，点别处就合成选中项
        if (isAutoCraftGesture(button)) {
            int autoIndex = cellIndexAt(mouseX, mouseY);
            if (autoIndex >= 0) {
                this.selectedIndex = autoIndex;
                sendAutoCraft(this.menu.getCraftables().get(autoIndex));
            } else {
                autoCraftSelected();
            }
            return true;
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
            if (isPointInRefillButton(mouseX, mouseY)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        SmartWorkbenchMenu.BUTTON_AUTO_REFILL);
                playClickSound();
                return true;
            }
            int index = cellIndexAt(mouseX, mouseY);
            if (index >= 0) {
                // Ctrl 点击 = 反复取材批量合成；Shift 点击 = 合成一次；普通点击 = 把材料配到合成格
                // 不管哪种，都顺手把它记成"选中项"，供自动合成按钮/快捷键使用
                this.selectedIndex = index;
                playClickSound();
                CraftableEntry entry = this.menu.getCraftables().get(index);
                ModNetwork.sendToServer(new C2SCraftRequestPacket(this.menu.getBenchPos(), entry.recipeId(),
                        hasShiftDown() || hasControlDown(), hasControlDown()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 这一下点击是不是自动合成键（默认 Alt+左键，也可以在按键设置里改）。 */
    private boolean isAutoCraftGesture(int button) {
        if (!ModKeyMappings.AUTO_CRAFT.matchesMouse(button)) {
            return false;
        }
        KeyModifier modifier = ModKeyMappings.AUTO_CRAFT.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
    }

    /** 自动合成当前选中的列表项；没选中就提示一句。 */
    private void autoCraftSelected() {
        List<CraftableEntry> list = this.menu.getCraftables();
        if (this.selectedIndex < 0 || this.selectedIndex >= list.size()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        label("gui." + SmartWorkbenchMod.MOD_ID + ".msg.select_first",
                                "先在右侧列表里点一下要自动合成的物品"), true);
            }
            return;
        }
        sendAutoCraft(list.get(this.selectedIndex));
    }

    private void sendAutoCraft(CraftableEntry entry) {
        playClickSound();
        ModNetwork.sendToServer(new C2SAutoCraftPacket(this.menu.getBenchPos(), entry.recipeId()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 松开鼠标就取消按钮和滚动条的按住状态。
        this.refreshPressed = false;
        this.autoPressed = false;
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
        super.renderTooltip(graphics, mouseX, mouseY);
        int index = cellIndexAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        List<CraftableEntry> list = this.menu.getCraftables();
        if (index >= list.size()) {
            return;
        }
        graphics.renderTooltip(this.font, buildTooltip(list.get(index)), Optional.empty(), mouseX, mouseY);
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
        lines.add(Component.empty());
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.click", "点击：自动配料到合成格")
                .withStyle(ChatFormatting.YELLOW));
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.shift", "Shift+点击：直接用存储里的材料合成")
                .withStyle(ChatFormatting.YELLOW));
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.ctrl", "Ctrl+点击：批量合成，直到材料用完")
                .withStyle(ChatFormatting.YELLOW));
        lines.add(label("gui." + SmartWorkbenchMod.MOD_ID + ".tooltip.auto",
                        "Alt+点击：自动合成（缺料自动补齐，产物进输出容器）")
                .withStyle(ChatFormatting.GOLD));
        return lines;
    }

    /** 文本组件；返回 MutableComponent 才能直接 withStyle（Component 接口上没有这个方法）。 */
    private MutableComponent label(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback);
    }

    /** 在按钮旁固定绘制说明，避免长文本跟随鼠标跑到列表或屏幕外。 */
    private void drawButtonHint(GuiGraphics graphics, Component message,
                                int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        int maxWidth = Math.max(86, this.leftPos + TEXTURE_W - (this.leftPos + buttonX + buttonWidth) - 4);
        List<FormattedCharSequence> lines = this.font.split(message, maxWidth);
        int lineHeight = this.font.lineHeight + 1;
        int boxHeight = lines.size() * lineHeight + 6;
        int x = this.leftPos + buttonX + buttonWidth + 3;
        int y = this.topPos + buttonY + buttonHeight + 2;
        if (x + maxWidth > this.leftPos + TEXTURE_W) {
            x = this.leftPos + buttonX - maxWidth - 3;
        }
        if (y + boxHeight > this.topPos + TEXTURE_H) {
            y = this.topPos + buttonY - boxHeight - 2;
        }
        graphics.fill(x - 2, y - 2, x + maxWidth + 2, y + boxHeight, 0xE8F6F6FA);
        graphics.renderOutline(x - 2, y - 2, maxWidth + 4, boxHeight + 2, 0xFF7C8190);
        int lineY = y + 2;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, lineY, TEXT_MAIN, false);
            lineY += lineHeight;
        }
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
        return index < this.menu.getCraftables().size() ? index : -1;
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
