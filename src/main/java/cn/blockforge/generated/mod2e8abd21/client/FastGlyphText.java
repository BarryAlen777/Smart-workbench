package cn.blockforge.generated.mod2e8abd21.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数字角标的快速绘制通道（掉帧分析方案 A1）。
 * <p>
 * 原来画一串角标数字要走四遍 {@code drawString}（下、左、右各一道黑描边，再一遍本色）。
 * 每遍都要把字符串按样式走一遍访问器、分配一个排版对象、逐字查样式表——字只有三四个，
 * 这些固定开销反而比画字本身贵。这里改成：开局把「0-9 + -」十二个字形连同各自的
 * 前进宽度一次性缓存下来，画的时候直接往<b>原版文字用的同一块显卡缓冲</b>里补顶点。
 * 同一个字体图集、同一份 RenderType、同样的描边遍数与偏移，所以画出来和原来
 * 逐像素一模一样；省掉的只是每帧的排版与对象分配。
 * <p>
 * <b>怎么拿字形：</b>字形集要从 {@code Font#getFontSet} 拿，原版把它是包私有的，
 * 只能反射取一次。正式映射会把这个方法名换成别的编号，<b>按名字找必然落空</b>，
 * 所以按<b>签名特征</b>找：返回 {@code FontSet}、只收一个 {@code ResourceLocation}
 * 参数——开发映射和正式映射下都能命中（审查报告附录）。命中不止一个时按
 * {@link #pickPreferred} 的名称优先规则挑：先认原版方法在各映射下的本名（正式环境
 * 优先认 SRG 编号名），再排除第三方模组注入的「Default」重载；仍分不出就停用，绝不瞎猜。
 * 探测失败、不让访问都<b>永久退回</b>原来的四遍 {@code drawString}（这类失败等不来
 * 好转）；建字形缓存抛错（可能是字体图集尚未就绪的瞬态问题）则冷却重试、封顶
 * {@value #BUILD_FAILURE_CAP} 次——失败期间照常走回退，绝不留下
 * 「每帧重试、每帧抛一次异常再吞掉」的残留（异常构造才是真掉帧）。
 * 失败只作废「刚试着建缓存的那一份字体」：手里已有的另一份 Font 的完好缓存
 * 原样保留，该字体照常走快速通道，连重建都不必（r187 审查补充点）。
 * <p>
 * <b>实测背景（r185 诊断日志，forge 47.4.23 / 1.20.1 正式环境）：</b>整合包里
 * 确有第三方模组往 {@code Font} 注入了 {@code getDefaultFontSet(ResourceLocation)}
 * 这个同签名的公开重载（原版并没有这个方法），导致「唯一命中」规则在玩家机器上
 * 停用了通道；原版方法本身在正式映射下的名字是 {@code m_92863_}。
 * <p>
 * 说明：Forge 本有访问变换器（accesstransformer.cfg）能免反射地开放这个方法，
 * 但本工程的独立类型检查管线不应用 AT，直调包私有方法会挡死构建，故维持反射方案。
 */
final class FastGlyphText {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** 与原版界面文字同一个全亮度值。 */
    private static final int FULL_LIGHT = 15728880;
    /** 快速通道支持的字形：数字、加号、减号（角标数字只用得上这些）。 */
    private static final char[] SUPPORTED = "0123456789+-".toCharArray();

    /** 反射句柄（探测一次，见 {@link #findFontSetGetter}）；null 表示探测过且失败。 */
    private static Method fontSetGetter;
    /** 反射句柄只探测一次。 */
    private static boolean probed;
    /** 快速通道已永久停用（取不到取器、访问被拒，或建缓存失败到了封顶）；不再重试，免得逐帧抛异常。 */
    private static boolean disabled;
    /** 建字形缓存累计抛错次数；成功一次即清零，到 {@link #BUILD_FAILURE_CAP} 转永久停用。 */
    private static int buildFailures;
    /** 上次建缓存抛错后允许再试的最早时刻（{@code System.nanoTime()}）；冷却防逐帧重试。 */
    private static long buildRetryNotBefore;
    /** 建缓存失败最多重试几次（30 秒一次）：字体图集未就绪这类瞬态问题会自行恢复。 */
    private static final int BUILD_FAILURE_CAP = 3;
    /** 建缓存失败后的重试间隔：最坏情况整局只构造 3 个异常，然后彻底安静。 */
    private static final long BUILD_RETRY_COOLDOWN_NANOS = 30L * 1000_000_000L;
    /** 快速通道当前是否可用（探测成功且字形缓存建好了）。 */
    private static boolean usable;

    /** 诊断：「启用」的 info 只报一次，资源重载重建缓存不重复刷日志。 */
    private static boolean enabledLogged;
    /** 诊断：快速通道可用、但某串文字含不支持字符时，每种文字只报一次（封顶防刷屏）。 */
    private static final Set<String> loggedUnsupported = new HashSet<>();
    /** {@link #loggedUnsupported} 的封顶条数：再多也只说明调用方在喂意料之外的字。 */
    private static final int UNSUPPORTED_LOG_CAP = 8;

    /** 缓存只认一个 Font 实例；实例换了（资源重载重建了字体）就重取一次字形。 */
    private static Font source;
    private static BakedGlyph[] glyphs;
    private static float[] advances;
    private static RenderType type;

    private FastGlyphText() {
    }

    /** 这串文字能不能走快速通道（只含支持的字符）。 */
    static boolean covers(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 128 || glyphs == null || glyphs[c] == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 四遍描边画一串数字：正下方、左、右各一道纯黑，再画本色。
     * 偏移、颜色拆分、绘制顺序都与 {@code drawOutlinedChar} 完全一致。
     * <p>
     * <b>提交契约（审查报告问题⑤，调用前必读）：</b>快速通道的顶点直接写进
     * {@code graphics.bufferSource()} 取到的 {@code type} 缓冲，不走
     * {@code GuiGraphics#drawString}——因此<b>不会像原版 drawString 那样每遍自动提交</b>
     * （原版每遍末尾 flushIfUnmanaged，等于「画字即提交」）。由此约定：
     * <ul>
     *   <li>提交时机完全托管给调用方：必须在 {@code GuiGraphics#drawManaged} 批次内调用，
     *       出批统一提交。界面上现有四个调用点（页签数量、分组成员数、格子堆叠数、
     *       折叠头「+/-」）都满足；新增调用点若不满足批次条件，请直接用 {@code drawString}；</li>
     *   <li>姿势矩阵在入口取一次（{@code pose().last().pose()}），四遍共用——
     *       四遍之间不得改动姿势栈，row 的先后顺序也不得调整（那是阴影叠字的一部分）；</li>
     *   <li>批内同类型绘制要攒齐再换（先 fill 后文字），来回切渲染类型等于每次切换
     *       都替上一段把缓冲冲掉；</li>
     *   <li>回退路径（四遍 {@code drawString}）自带逐遍提交，画面结果与批内外一致——
     *       契约约束的是「快」，不是「对」：违约的代价是丢批次合并，不是画错。</li>
     * </ul>
     */
    static void draw(Font font, GuiGraphics graphics, String text, float x, float y, int color) {
        drawRings(font, graphics, text, x, y, color, RING4);
    }

    /**
     * 九遍描边：朝八个方向各铺一道纯黑，正中再画本色——性能缩减前老版本的做法，
     * 和旧版 {@code drawOutlinedChar} 的 {@code dy/dx = -1..1} 双重循环逐像素一致。
     * <p>
     * 数量数字的「0.7 倍字体」那一款（默认款，可按键切到点阵）用这一档：
     * 斜角四道会让描边在字的四角连成一圈厚边，字显得更"实"。玩家实测把八道减到五道
     * （去掉上缘）后数字又"不清楚"了，所以这里不再省——九遍就是玩家要的那一档。
     * 提交契约与 {@link #draw} 完全相同：必须在 {@code drawManaged} 批次内调用。
     */
    static void drawNine(Font font, GuiGraphics graphics, String text, float x, float y, int color) {
        drawRings(font, graphics, text, x, y, color, RING9);
    }

    /** 四遍描边的偏移（正下方、左、右）：顺序即绘制顺序，改它会改变像素结果。 */
    private static final int[][] RING4 = {{0, 1}, {-1, 0}, {1, 0}};
    /** 九遍描边的偏移：八个方向各一道（四角 + 上下左右）。八道黑遍先后顺序无所谓（同为纯黑），本色最后画。 */
    private static final int[][] RING9 = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},          {1, 0},
            {-1, 1},  {0, 1}, {1, 1},
    };

    private static void drawRings(Font font, GuiGraphics graphics, String text, float x, float y,
                                  int color, int[][] offsets) {
        if (prepare(font) && covers(text)) {
            // prepare 为 true 时缓存必然对应这个 font（见 prepare 的首查）
            VertexConsumer vc = graphics.bufferSource().getBuffer(type);
            Matrix4f m = graphics.pose().last().pose();
            for (int[] o : offsets) {
                row(vc, m, text, x + o[0], y + o[1], 0xFF000000);
            }
            row(vc, m, text, x, y, color);
            return;
        }
        if (usable && font == source && glyphs != null) {
            // 缓存正是这个字体的、却没走进快速通道，只可能是 covers 落空——
            // 报一次是哪串字、哪个字符。别的字体留着好缓存时不算（那是 prepare 拒的，不是字符的锅）。
            logUnsupportedOnce(text);
        }
        fallback(graphics, font, text, x, y, color, offsets);
    }

    /** 诊断：文字含不支持字符时每种文字只报一次，点出第一个不支持的字符。 */
    private static void logUnsupportedOnce(String text) {
        synchronized (FastGlyphText.class) {
            if (loggedUnsupported.size() >= UNSUPPORTED_LOG_CAP || !loggedUnsupported.add(text)) {
                return;
            }
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 128 || glyphs[c] == null) {
                LOGGER.warn("[smart_workbench] 快速通道可用，但角标文字\"{}\"含不支持字符'{}'(U+{})，这一串走回退",
                        text, c, String.format("%04X", (int) c));
                return;
            }
        }
        LOGGER.warn("[smart_workbench] 快速通道可用，但角标文字\"{}\"被 covers 拒了且找不到不支持的字符（不该发生）", text);
    }

    /** 一遍：沿字符串逐字写四个顶点，光标按缓存的前进宽度走。 */
    private static void row(VertexConsumer vc, Matrix4f m, String text, float x, float y, int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            glyphs[c].render(false, x, y, m, vc, r, g, b, a, FULL_LIGHT);
            x += advances[c];
        }
    }

    /** 回退路径：和改造前一模一样的逐遍 {@code drawString}，遍数、偏移顺序与快路径一致。 */
    private static void fallback(GuiGraphics graphics, Font font, String text,
                                 float x, float y, int color, int[][] offsets) {
        int xi = Math.round(x);
        int yi = Math.round(y);
        for (int[] o : offsets) {
            graphics.drawString(font, text, xi + o[0], yi + o[1], 0xFF000000, false);
        }
        graphics.drawString(font, text, xi, yi, color, false);
    }

    /**
     * 按<b>签名特征</b>找 {@code Font#getFontSet}：返回 {@code FontSet}、只收一个
     * {@code ResourceLocation} 参数。正式映射会把方法名换成别的编号，按名字找必然
     * 落空，所以按类型找。命中多个时交给 {@link #pickPreferred} 按名称规则挑——
     * r185 实测有第三方模组往 Font 注入了同签名重载（名字里带 Default），
     * 光靠「唯一命中」会在玩家机器上白白停用通道。挑不出就整体停用，绝不猜。
     */
    private static Method findFontSetGetter() {
        List<Method> matches = new ArrayList<>();
        List<String> nearMisses = new ArrayList<>();
        for (Method m : Font.class.getDeclaredMethods()) {
            boolean returnsFontSet = m.getReturnType() == FontSet.class;
            boolean takesResourceLocation = m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == ResourceLocation.class;
            if (returnsFontSet && takesResourceLocation) {
                matches.add(m);
            } else if (returnsFontSet || takesResourceLocation) {
                // 差一个条件的"近似候选"也记下来：正式映射下方法名是编号，
                // 光看命中数不够，下次报告得能说清 Font 上到底有些什么样的方法。
                nearMisses.add(describe(m));
            }
        }
        if (matches.isEmpty()) {
            LOGGER.warn("[smart_workbench] 反射找不到字形集取器：签名为 (ResourceLocation)->FontSet 的方法 0 个，"
                    + "Font 的全部方法数={}，近似候选={}", Font.class.getDeclaredMethods().length, nearMisses);
            return null;
        }
        Method found = pickPreferred(matches);
        if (found == null) {
            List<String> names = new ArrayList<>(matches.size());
            for (Method m : matches) {
                names.add(describe(m));
            }
            LOGGER.warn("[smart_workbench] 反射命中 {} 个同特征方法且按名称规则也分不出，停用快速通道：{}",
                    matches.size(), names);
            return null;
        }
        try {
            found.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.warn("[smart_workbench] 反射拿到 {} 但不允许 setAccessible，停用快速通道",
                    describe(found), t);
            return null;
        }
        if (matches.size() > 1) {
            LOGGER.info("[smart_workbench] 反射命中 {} 个同特征方法，按名称优先规则取 {}（当前映射环境下的实际签名）",
                    matches.size(), describe(found));
        } else {
            LOGGER.info("[smart_workbench] 反射命中字形集取器 {}（当前映射环境下的实际签名）", describe(found));
        }
        return found;
    }

    /**
     * 原版取字形集方法在各套映射下的本名。<b>SRG 编号名必须排在 Mojang 名前</b>：
     * 正式环境里原版方法就是 {@code m_92863_}，而第三方注入的方法（mixin 新增方法
     * 不会被重映射）反而保留可读名——先匹配 Mojang 名会撞上第三方注入的
     * {@code getFontSet(ResourceLocation)}（审查报告 r186 问题 1）。开发环境不存在
     * 编号名，自然落到 {@code getFontSet}，两边都稳。
     */
    private static final String[] VANILLA_FONT_SET_GETTER_NAMES = {"m_92863_", "getFontSet"};

    /**
     * 从同签名候选里挑出原版那一个：
     * <ol>
     *   <li>名字正好是原版 {@code getFontSet} 在某个映射层下的本名——直接认定，
     *       顺序上先认正式环境的 SRG 编号名（见 {@link #VANILLA_FONT_SET_GETTER_NAMES}）；</li>
     *   <li>否则排除名字里含 "default" 的候选（第三方注入的重载都带着可读的
     *       Mojang 风格名字，如 {@code getDefaultFontSet}，原版正式映射下是编号名）；</li>
     *   <li>只剩一个才敢用，仍然分不清就返回 null 停用。</li>
     * </ol>
     */
    private static Method pickPreferred(List<Method> matches) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        for (String wanted : VANILLA_FONT_SET_GETTER_NAMES) {
            for (Method m : matches) {
                if (m.getName().equals(wanted)) {
                    return m;
                }
            }
        }
        Method survivor = null;
        int survivors = 0;
        for (Method m : matches) {
            if (!m.getName().toLowerCase().contains("default")) {
                survivor = m;
                survivors++;
            }
        }
        return survivors == 1 ? survivor : null;
    }

    /** 方法的可读描述：名字(参数类型)->返回类型。正式映射下名字是混淆编号，恰好能区分环境。 */
    private static String describe(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ps[i].getSimpleName());
        }
        return sb.append(")->").append(m.getReturnType().getSimpleName()).toString();
    }

    /**
     * 确保字形缓存对应当前的 Font 实例；返回快速通道是否可用。
     * 取不到取器、访问被拒——这类失败等多久都一样，<b>永久停用</b>；
     * 建字形缓存抛错（字形还没预备好之类的瞬态问题）——冷却 30 秒重试，
     * 封顶 {@value #BUILD_FAILURE_CAP} 次后同永久停用。任何失败期间调用方
     * 都安静地走四遍 drawString，绝不留「每帧重试、每帧抛一次异常再吞掉」
     * 的残留（异常构造才是真掉帧）。
     * <p>
     * <b>连坐的排除：</b>停用与冷却只管「重建」，不动「已有」——另一份 Font
     * 的完好缓存不清空也不拦截，那个字体照常走快速通道（先查好缓存，后查停用）。
     */
    private static synchronized boolean prepare(Font font) {
        // 先认已有的好缓存，再谈停用与冷却：失败作废的只是「刚试着建的那一份字体」，
        // 另一份 Font 的完好缓存不清空，也不被全局冷却连坐（r187 审查补充点）。
        if (font == source && glyphs != null) {
            return usable;
        }
        if (disabled) {
            return false;
        }
        if (buildFailures > 0 && System.nanoTime() < buildRetryNotBefore) {
            return false; // 刚抛过错，冷却期内不重试（回退路径照画，零开销）
        }
        if (!probed) {
            probed = true;
            fontSetGetter = findFontSetGetter();
        }
        if (fontSetGetter == null) {
            usable = false;
            disabled = true;
            LOGGER.warn("[smart_workbench] 快速通道【停用】：取不到字形集取器（原因见上一条日志），"
                    + "角标永久走四遍 drawString");
            return false;
        }
        try {
            FontSet set = (FontSet) fontSetGetter.invoke(font, Style.DEFAULT_FONT);
            if (set == null) {
                throw new IllegalStateException("font set is null for " + Style.DEFAULT_FONT);
            }
            BakedGlyph[] gs = new BakedGlyph[128];
            float[] adv = new float[128];
            RenderType rt = null;
            for (char c : SUPPORTED) {
                BakedGlyph glyph = set.getGlyph(c);
                if (glyph == null) {
                    throw new IllegalStateException("missing glyph for " + c);
                }
                gs[c] = glyph;
                adv[c] = set.getGlyphInfo(c, false).getAdvance(false);
                if (rt == null) {
                    rt = glyph.renderType(Font.DisplayMode.NORMAL);
                }
            }
            if (rt == null) {
                throw new IllegalStateException("no render type");
            }
            glyphs = gs;
            advances = adv;
            type = rt;
            source = font;
            usable = true;
            buildFailures = 0; // 成功即清零：之后的瞬态失败又有了完整的重试额度
            if (!enabledLogged) {
                enabledLogged = true;
                LOGGER.info("[smart_workbench] 快速通道【启用】：{} 个字形已缓存，渲染类型 {}",
                        SUPPORTED.length, rt);
            }
        } catch (Throwable t) {
            // 失败只作废「这一份字体」的半成品：手里若还握着另一份 Font 的完好缓存，
            // 原样留给它继续快用——旧字体不但这次不被连坐，连重建都不必。
            if (glyphs == null || !usable) {
                usable = false;
                source = null;
                glyphs = null;
                advances = null;
                type = null;
            }
            buildFailures++;
            buildRetryNotBefore = System.nanoTime() + BUILD_RETRY_COOLDOWN_NANOS;
            if (buildFailures >= BUILD_FAILURE_CAP) {
                disabled = true;
                LOGGER.warn("[smart_workbench] 快速通道【停用】：建字形缓存累计失败 {} 次，新字体不再尝试建缓存"
                        + "（已有的完好缓存照常可用），拿不到好缓存的字体本局角标走四遍 drawString", buildFailures, t);
            } else {
                LOGGER.warn("[smart_workbench] 快速通道第 {} 次建字形缓存抛错（多为字体图集尚未就绪），"
                        + "{} 秒后自动重试；期间角标走四遍 drawString",
                        buildFailures, BUILD_RETRY_COOLDOWN_NANOS / 1000_000_000L, t);
            }
        }
        // 只有「好缓存正对应这个 font」才算这次可用：缓存是别的字体留下的，
        // 对当前 font 而言仍处于失败冷却，必须继续走回退，绝不拿错图集画字。
        return usable && font == source && glyphs != null;
    }
}
