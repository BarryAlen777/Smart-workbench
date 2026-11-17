package cn.blockforge.generated.mod2e8abd21.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;

/**
 * 穿墙可见的世界框线：把一批（AABB + 颜色）画成线框，任何方块都挡不住它们。
 * <p>
 * <b>为什么不能用原版 {@code RenderType#LINES}（r243 用户实测踩的坑）：</b>
 * 走 {@code MultiBufferSource} 时 {@code getBuffer} 只是把顶点攒进缓冲，
 * 真正交给显卡的是后来的 {@code endBatch}，而 LINES 的渲染状态自带
 * LEQUAL 深度档——它在<b>提交那一瞬间</b>无条件把深度测试重新打开
 * （已核实 1.20.1 字节码：深度档的 setup 就是 enableDepthTest + depthFunc）。
 * 所以"先关深度、攒顶点、恢复深度、再 endBatch"的老写法等于没关，
 * 墙后的箱子照样被墙挡住——这就是 r243 总览绿/红框穿墙失效的根因。
 * 现在的做法对齐原版 F3 调试线框：直接从 {@link Tesselator} 借公共缓冲，
 * {@code begin → 写顶点 → end} 整段都夹在 disableDepthTest 之内，
 * <b>提交发生在关深度之内</b>，不绕 RenderType 的状态机。
 * <p>
 * <b>r248 抗发虚三点（对应用户实拍"框线发虚"的三条成因）：</b>
 * <ol>
 *   <li><b>线改成"面片"，屏幕上定宽。</b>1.17 以后 OpenGL 核心模式里"把线画粗"
 *       （glLineWidth）基本被显卡驱动忽略，DEBUG_LINES 永远只有 1 个物理像素——
 *       静态看细、斜看有锯齿台阶、离远几乎断成虚线。现在每条边画成一根细长的
 *       三棱面小盒子（纯三角形，不碰线段宽度），厚度按 "屏幕约 1.5 个 GUI 像素"
 *       （r249 稳妥档：原 2.0 芯 + 2.6 倍描边实测偏粗，用户要求收一档）
 *       随观察距离线性放大（用窗口高度和视场角换算），近看远看屏幕上的粗细一致。</li>
 *   <li><b>暗描边 + 亮芯双层。</b>光影/渲染优化模组会把世界先画进中间图再做
 *       抗锯齿/上采样，1~2 像素的硬边必然被磨软。先铺一层 1.8 倍粗的同色暗描边
 *       （亮度 ×0.22），再压上亮芯——被磨后仍留得住一条深色轮廓，
 *       任何背景上都分得清，而不是和背景糊成一团。</li>
 *   <li><b>画线前复位图形状态，画完恢复。</b>全局染色（shaderColor）、混合、雾
 *       三样都是上一步渲染留下的：不关混合，线的像素会掺进背景色；不重置
 *       shaderColor，颜色可能被染淡；雾参数不推到无限远，远处箱子会被雾
 *       抹成灰的。r245 逐像素实拍的"线色随背景变"就是这几样没复位叠加后处理造成的。
 *       r252 复查后又补两样：shader 用完原样还回去（②-1）；全局 modelview 矩阵
 *       在 begin→end 提交全程钉成单位阵（①——顶点已经乘过本方法的 pose，
 *       提交瞬间盖进 shader 的却是全局栈缓存值，别的模组 push 不 pop 就会把框再变换一次）。</li>
 * </ol>
 * 相机偏移在 {@link #render} 里统一做（事件给的 PoseStack 还在世界坐标系，
 * 要自己平移到相机原点），调用方只管交出世界坐标的框。alpha 固定 1.0：
 * 不混色，压着什么都看得分明——这是"找箱子"的标记，不是装饰。
 */
public final class SeeThroughLineBoxes {

    /** 一个待画框线：世界坐标的 AABB + 线色（r/g/b 取 0~1，alpha 固定 1）。 */
    public record Box(AABB aabb, float r, float g, float b) {
    }

    /**
     * 亮芯在屏幕上的目标粗细（GUI 像素）。r249 稳妥档：2.0 → 1.5——
     * 仍比 1 物理像素的"钢笔画线"结实，但连描边整根约 2.7 像素，不糊脸。
     */
    private static final float CORE_GUI_PIXELS = 1.5F;
    /** 暗描边相对亮芯的倍数：1.8 倍粗、亮度 ×0.22——"深色轮廓托亮芯"（r249 从 2.6 收到 1.8）。 */
    private static final float OUTLINE_SCALE = 1.8F;
    private static final float OUTLINE_DARKEN = 0.22F;
    /** 单根芯线的世界厚度上限（格）/像素——离得再远线也不许粗到把相邻箱子糊在一起。 */
    private static final float MAX_WORLD_PER_PIXEL = 0.17F;
    /** 绝对上限：一条边再远也不超半格，防极端坐标下自己盖自己。 */
    private static final double MAX_WORLD_THICKNESS = 0.5D;
    /** 下限：贴脸看也不细于这个厚度，免得 0 距离算出 0 宽。 */
    private static final double MIN_WORLD_THICKNESS = 0.02D;

    private SeeThroughLineBoxes() {
    }

    /**
     * 画一批框线。{@code pose} 用事件给的即可（本方法自己 push/translate/pop，
     * 不污染调用方的姿势栈）；{@code camera} 是相机世界坐标。
     */
    public static void render(PoseStack pose, Vec3 camera, List<Box> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // —— 三点之一（r248 第③点）：把借来的图形状态先记下、复位，画完原样还回去 ——
        float[] savedShaderColor = Arrays.copyOf(RenderSystem.getShaderColor(), 4);
        ShaderInstance savedShader = RenderSystem.getShader();
        float savedFogStart = RenderSystem.getShaderFogStart();
        float savedFogEnd = RenderSystem.getShaderFogEnd();
        // —— 三点之一（r248 第①点）：屏幕定宽换算 ——
        // 世界厚度 / 距离 / 目标像素 = 2·tan(半视场角) / GUI 屏幕高：
        // 屏幕上占几个 GUI 像素，在什么距离看都占几个，透视缩不掉它。
        double guiHeight = Math.max(1.0D, minecraft.getWindow().getGuiScaledHeight());
        double tanHalfFov = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0D);
        double kPerPixel = 2.0D * tanHalfFov / guiHeight;

        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            // 雾推到"起点 0、终点无限远"：着色器对任何实际距离都算出无雾，颜色不被雾抹灰。
            RenderSystem.setShaderFogStart(0.0F);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            // r252 问题①：全局 modelview 栈在 begin→end 全程钉成单位阵。顶点已经乘过
            // 本方法的 pose，而 tesselator.end() 提交瞬间盖进 shader 的是全局栈的缓存值——
            // 同帧更早的任何渲染者 push 了不 pop，框就会被再变换一次"飞走"。原版云/雨雪/
            // 世界边界全走 push→改→apply / pop→apply 这一套，我们照做对齐惯例。
            // 注意范围必须含住 end()：缓存矩阵是提交那一刻才被读走的。
            PoseStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushPose();
            modelViewStack.setIdentity();
            RenderSystem.applyModelViewMatrix();
            try {
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder buffer = tesselator.getBuilder();
                buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                Matrix4f matrix = pose.last().pose();
                // 先整批铺暗描边、再整批压亮芯：关深度后提交顺序就是叠放顺序，
                // 若一框一框地"暗完压亮"，后一框的暗边会盖掉前一框的亮芯。
                for (Box box : boxes) {
                    drawBoxBeams(buffer, matrix, camera, kPerPixel, box.aabb(),
                            CORE_GUI_PIXELS * OUTLINE_SCALE,
                            box.r() * OUTLINE_DARKEN, box.g() * OUTLINE_DARKEN, box.b() * OUTLINE_DARKEN);
                }
                for (Box box : boxes) {
                    drawBoxBeams(buffer, matrix, camera, kPerPixel, box.aabb(),
                            CORE_GUI_PIXELS, box.r(), box.g(), box.b());
                }
                // 立刻提交：此刻深度测试还关着，画完才恢复——顺序就是本类存在的理由。
                tesselator.end();
            } finally {
                // 先还矩阵（顺序与借走时相反），再还 shader（r252 问题②-1）：
                // 原版 clearRenderState 自己就会 setShader(null)，还一个可能为 null 的旧值是合法姿势。
                modelViewStack.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShader(() -> savedShader);
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.setShaderFogStart(savedFogStart);
                RenderSystem.setShaderFogEnd(savedFogEnd);
                RenderSystem.enableCull();
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(savedShaderColor[0], savedShaderColor[1],
                        savedShaderColor[2], savedShaderColor[3]);
            }
        } finally {
            pose.popPose();
        }
    }

    /** 一个框的 12 条边，每条画成一根"板面"细长小盒子（r248 第①点：不再用 1 像素的 gl 线段）。 */
    private static void drawBoxBeams(VertexConsumer buffer, Matrix4f matrix, Vec3 camera, double kPerPixel,
                                     AABB a, double guiPixels, float r, float g, float b) {
        float x0 = (float) a.minX;
        float y0 = (float) a.minY;
        float z0 = (float) a.minZ;
        float x1 = (float) a.maxX;
        float y1 = (float) a.maxY;
        float z1 = (float) a.maxZ;
        // 底面四边
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y0, z0, x1, y0, z0, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y0, z0, x1, y0, z1, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y0, z1, x0, y0, z1, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y0, z1, x0, y0, z0, r, g, b);
        // 顶面四边
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y1, z0, x1, y1, z0, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y1, z0, x1, y1, z1, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y1, z1, x0, y1, z1, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y1, z1, x0, y1, z0, r, g, b);
        // 四条立柱
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y0, z0, x0, y1, z0, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y0, z0, x1, y1, z0, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x1, y0, z1, x1, y1, z1, r, g, b);
        beam(buffer, matrix, camera, kPerPixel, guiPixels, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    /**
     * 一条边 → 一根轴对齐的细长小盒子：边所在轴取精确端点，另外两轴各外扩半厚。
     * 半厚 = 屏幕目标像素折算到世界（随距离线性放大），并上下限夹住。
     */
    private static void beam(VertexConsumer buffer, Matrix4f matrix, Vec3 camera, double kPerPixel,
                             double guiPixels,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b) {
        double midX = (x1 + x2) * 0.5 - camera.x;
        double midY = (y1 + y2) * 0.5 - camera.y;
        double midZ = (z1 + z2) * 0.5 - camera.z;
        double dist = Math.sqrt(midX * midX + midY * midY + midZ * midZ);
        double thickness = Math.min(kPerPixel * guiPixels * dist, MAX_WORLD_PER_PIXEL * guiPixels);
        thickness = Math.max(Math.min(thickness, MAX_WORLD_THICKNESS), MIN_WORLD_THICKNESS);
        float h = (float) (thickness * 0.5);

        float minx = Math.min(x1, x2);
        float maxx = Math.max(x1, x2);
        float miny = Math.min(y1, y2);
        float maxy = Math.max(y1, y2);
        float minz = Math.min(z1, z2);
        float maxz = Math.max(z1, z2);
        if (minx != maxx) {            // 沿 X：厚度摊在 Y/Z
            miny -= h;
            maxy += h;
            minz -= h;
            maxz += h;
        } else if (miny != maxy) {     // 沿 Y：厚度摊在 X/Z
            minx -= h;
            maxx += h;
            minz -= h;
            maxz += h;
        } else {                       // 沿 Z：厚度摊在 X/Y
            minx -= h;
            maxx += h;
            miny -= h;
            maxy += h;
        }
        fillBox(buffer, matrix, minx, miny, minz, maxx, maxy, maxz, r, g, b);
    }

    /** 六面全画的实心小盒子（关剔除后正反都画，薄边条看不出错）。 */
    private static void fillBox(VertexConsumer buffer, Matrix4f matrix,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b) {
        quad(buffer, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b);
        quad(buffer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b);
        quad(buffer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b);
        quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b);
        quad(buffer, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b);
        quad(buffer, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b);
    }

    /** 一个面 = 两个三角形（a,b,c + a,c,d）。 */
    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b) {
        vertex(buffer, matrix, ax, ay, az, r, g, b);
        vertex(buffer, matrix, bx, by, bz, r, g, b);
        vertex(buffer, matrix, cx, cy, cz, r, g, b);
        vertex(buffer, matrix, ax, ay, az, r, g, b);
        vertex(buffer, matrix, cx, cy, cz, r, g, b);
        vertex(buffer, matrix, dx, dy, dz, r, g, b);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, float r, float g, float b) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, 1.0F).endVertex();
    }
}
