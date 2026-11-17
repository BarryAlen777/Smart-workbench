package cn.blockforge.generated.mod2e8abd21.client;

import cn.blockforge.generated.mod2e8abd21.SmartWorkbenchMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料溯源的高亮渲染：把 {@link TraceHighlights} 登记的容器在世界里描一圈金框。
 * <p>
 * 一组 = 一整台容器（大箱子两格拼一台）：组里所有格的 AABB 并起来描<b>一个</b>框，
 * 不是每格各描一个——和 F12 总览的绿/红框同一口径（r239，接 r233）。
 * <p>
 * 画在 AFTER_TRANSLUCENT_BLOCKS 这一段（半透明块之后、粒子之前），并<b>关掉深度测试</b>——
 * 关掉以后隔着墙也能看见框，这才是"高亮存在材料的箱子"要的效果：
 * 材料埋在仓库堆底下，看得见才找得着。界面上还悬停着那条配方时框一直亮着，
 * 指针挪开半秒内自动消失（续命机制见 {@link TraceHighlights}）。
 * <p>
 * 穿墙描框统一走 {@link SeeThroughLineBoxes}（r243 之后）：直接 LINES 攒批的老写法
 * 深度测试会在提交瞬间被渲染类型重新打开，看着像关了其实没关，那边的注释记了全过程。
 */
@Mod.EventBusSubscriber(modid = SmartWorkbenchMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TraceHighlightRenderer {

    /** 金色，和界面里"选中项描边"同一个族，玩家一眼知道这是工作台让我们看的东西。 */
    private static final float R = 0.88F;
    private static final float G = 0.66F;
    private static final float B = 0.24F;

    /** 描边外扩：贴着方块面留一丝缝，免得和方块的边线糊在一起。 */
    private static final double INFLATE = 0.004;

    private TraceHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<List<BlockPos>> groups = TraceHighlights.liveGroups();
        if (groups.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        // 距离剔除：超出渲染距离的箱子，chunk 本身根本没画，金框又是关深度穿墙可见的——
        // 不剔除就会在几百米外浮出一圈"悬在半空的框"，既没有参照物也纯属白画。
        // 按渲染距离加 2  chunks 余量裁，中心刚好在边缘外的框也能露出来。
        double cull = (minecraft.options.getEffectiveRenderDistance() + 2) * 16.0D;
        double cullSq = cull * cull;
        List<SeeThroughLineBoxes.Box> boxes = new ArrayList<>(groups.size());
        for (List<BlockPos> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            // 剔除按锚点（组第一格）算：大箱子两格紧挨着，锚点在圈里整台就在圈里，
            // 口径同 F12 总览的 drawBoxes。
            BlockPos anchor = group.get(0);
            double dx = anchor.getX() + 0.5 - camera.x;
            double dy = anchor.getY() + 0.5 - camera.y;
            double dz = anchor.getZ() + 0.5 - camera.z;
            if (dx * dx + dy * dy + dz * dz > cullSq) {
                continue;
            }
            AABB box = unionBox(group).inflate(INFLATE);
            boxes.add(new SeeThroughLineBoxes.Box(box, R, G, B));
        }
        if (!boxes.isEmpty()) {
            // 深度怎么关、何时提交，全在 {@link SeeThroughLineBoxes} 统一管——
            // r243 实测"disableDepthTest 包着 LINES 攒批、endBatch 在恢复之后"等于没关，
            // 这里别再往回加一层状态开关。
            SeeThroughLineBoxes.render(pose, camera, boxes);
        }
    }

    /** 组里所有方块的并集 AABB：大箱子两格并成 2x1x1 的一整台（同 {@code ConnectionOverviewRenderer}）。 */
    private static AABB unionBox(List<BlockPos> group) {
        AABB union = new AABB(group.get(0));
        for (int i = 1; i < group.size(); i++) {
            union = union.minmax(new AABB(group.get(i)));
        }
        return union;
    }
}
