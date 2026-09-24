package cn.blockforge.generated.mod2e8abd21.item;

import cn.blockforge.generated.mod2e8abd21.block.SmartWorkbenchBlock;
import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 绑定扳手。它有两种绑定模式：
 * <ul>
 *   <li><b>取料容器</b>（默认）：工作台从这里抽材料；</li>
 *   <li><b>输出容器</b>：自动合成出来的产物优先送进这里。</li>
 * </ul>
 * 用法：手里拿着扳手，<b>潜行 + 右键空中</b>切换模式 → 右键一个容器选中它 →
 * 右键智能工作台完成绑定。潜行右键工作台仍然是清空该工作台的全部绑定。
 */
public class WrenchItem extends Item {

    private static final String TAG_TARGET = "BoundTarget";
    private static final String TAG_OUTPUT_TARGET = "BoundOutputTarget";
    private static final String TAG_DIMENSION = "BoundDimension";
    private static final String TAG_BENCH_TARGET = "SelectedWorkbench";
    private static final String TAG_BENCH_DIMENSION = "SelectedWorkbenchDimension";
    private static final String TAG_MODE = "BindMode";

    private static final String MODE_OUTPUT = "output";

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /**
     * Forge 的“先问物品”钩子：不潜行时原版是方块先处理（箱子、工作台的界面会把右键吃掉），
     * 走这个钩子扳手才能真正拿到右键。
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleUse(context);
    }

    /** 兜底：某些情况下方块自己的 use 返回 PASS 之后也会走到这里。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleUse(context);
    }

    /** 右键“空气”时走这里：潜行右键切换绑定模式。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                boolean toOutput = toggleMode(stack);
                feedback(player, toOutput
                        ? "绑定模式：输出容器（产物会送进去）"
                        : "绑定模式：取料容器（工作台从它抽材料）");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private InteractionResult handleUse(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(pos);

        // 右击智能工作台：绑定 / 清空
        if (state.getBlock() instanceof SmartWorkbenchBlock) {
            if (!(level.getBlockEntity(pos) instanceof SmartWorkbenchBlockEntity bench)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player.isSecondaryUseActive()) {
                int cleared = bench.clearBindings();
                clearSelectedTarget(stack, false);
                clearSelectedTarget(stack, true);
                clearSelectedWorkbench(stack);
                feedback(player, cleared > 0
                        ? "已清空这个工作台的 " + cleared + " 个绑定"
                        : "这个工作台还没有绑定任何容器");
                return InteractionResult.CONSUME;
            }
            boolean output = isOutputMode(stack);
            BlockPos target = output ? readOutputTarget(stack) : readTarget(stack);
            if (target == null) {
                clearSelectedTarget(stack, false);
                clearSelectedTarget(stack, true);
                writeSelectedWorkbench(stack, pos, level.dimension().location().toString());
                feedback(player, "已选中这个工作台，再右键容器即可完成绑定或取消绑定");
                return InteractionResult.CONSUME;
            }
            String dimension = readDimension(stack);
            if (dimension != null && !dimension.equals(level.dimension().location().toString())) {
                feedback(player, "选中的容器在别的维度，绑定不了");
                return InteractionResult.CONSUME;
            }
            // 再次点已经绑定的同一个容器就是取消绑定，避免必须潜行清空全部绑定。
            boolean removedOutput = false;
            boolean removed = output ? bench.removeOutputBinding(target) : bench.removeBinding(target);
            if (!removed) {
                // 取消绑定不受当前扳手模式限制，避免切换模式后无法解除已有绑定。
                removedOutput = output ? bench.removeBinding(target) : bench.removeOutputBinding(target);
                removed = removedOutput;
            } else {
                removedOutput = output;
            }
            if (removed) {
                clearSelectedTarget(stack, output);
                clearSelectedWorkbench(stack);
                feedback(player, removedOutput
                        ? "已取消输出容器绑定：" + target.toShortString()
                        : "已取消取料容器绑定：" + target.toShortString());
                return InteractionResult.CONSUME;
            }
            if (!bench.canBind(target)) {
                feedback(player, "选中的容器已经不在了，或它本身是工作台");
                return InteractionResult.CONSUME;
            }
            boolean added = output ? bench.addOutputBinding(target) : bench.addBinding(target);
            if (added) {
                clearSelectedTarget(stack, output);
                clearSelectedWorkbench(stack);
                feedback(player, output
                        ? "已把 " + target.toShortString() + " 绑成输出容器，当前输出容器 " + bench.getOutputBindingCount() + " 个"
                        : "已绑定 " + target.toShortString() + "，当前取料容器 " + bench.getBindingCount() + " 个");
            } else {
                feedback(player, output
                        ? "绑不了：重复了、上限到了，或这个容器已经当成取料容器"
                        : "绑不了：重复了、上限到了，或这个容器已经当成输出容器");
            }
            return InteractionResult.CONSUME;
        }

        // 右击普通方块：把有物品栏的容器记到扳手上（潜行时让给其它模组处理）
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return SmartWorkbenchBlockEntity.findItemHandler(level, pos) != null
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (SmartWorkbenchBlockEntity.findItemHandler(level, pos) == null) {
            return InteractionResult.PASS;
        }

        // 反向操作：先点工作台，再点容器时，第二次点击就完成绑定或取消绑定。
        // 以前这里在“尚未绑定”的情况下只清掉工作台选择，导致玩家还要再点一次容器。
        BlockPos selectedWorkbench = readSelectedWorkbench(stack);
        if (selectedWorkbench != null) {
            String benchDimension = readSelectedWorkbenchDimension(stack);
            if (benchDimension != null && !benchDimension.equals(level.dimension().location().toString())) {
                clearSelectedWorkbench(stack);
                feedback(player, "选中的工作台在别的维度，绑定不了");
                return InteractionResult.CONSUME;
            }
            if (level.getBlockEntity(selectedWorkbench) instanceof SmartWorkbenchBlockEntity bench) {
                boolean removedInput = bench.removeBinding(pos);
                boolean removedOutput = !removedInput && bench.removeOutputBinding(pos);
                if (removedInput || removedOutput) {
                    clearSelectedTarget(stack, false);
                    clearSelectedTarget(stack, true);
                    clearSelectedWorkbench(stack);
                    feedback(player, removedOutput
                            ? "已取消输出容器绑定：" + pos.toShortString()
                            : "已取消取料容器绑定：" + pos.toShortString());
                    return InteractionResult.CONSUME;
                }

                boolean output = isOutputMode(stack);
                if (!bench.canBind(pos)) {
                    clearSelectedWorkbench(stack);
                    feedback(player, "选中的容器已经不在了，或它本身是工作台");
                    return InteractionResult.CONSUME;
                }
                boolean added = output ? bench.addOutputBinding(pos) : bench.addBinding(pos);
                clearSelectedTarget(stack, false);
                clearSelectedTarget(stack, true);
                clearSelectedWorkbench(stack);
                feedback(player, added
                        ? (output
                                ? "已把 " + pos.toShortString() + " 绑成输出容器，当前输出容器 " + bench.getOutputBindingCount() + " 个"
                                : "已绑定 " + pos.toShortString() + "，当前取料容器 " + bench.getBindingCount() + " 个")
                        : (output
                                ? "绑不了：重复了、上限到了，或这个容器已经当成取料容器"
                                : "绑不了：重复了、上限到了，或这个容器已经当成输出容器"));
                return InteractionResult.CONSUME;
            }
            clearSelectedWorkbench(stack);
            feedback(player, "选中的工作台已经不存在了");
            return InteractionResult.CONSUME;
        }

        boolean output = isOutputMode(stack);
        writeTarget(stack, pos, level.dimension().location().toString(), output);
        feedback(player, output
                ? "已选中容器 " + pos.toShortString() + "，右键智能工作台绑成输出容器"
                : "已选中容器 " + pos.toShortString() + "，右键智能工作台完成绑定");
        return InteractionResult.CONSUME;
    }

    /** 当前是不是“输出容器”模式。 */
    public static boolean isOutputMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && MODE_OUTPUT.equals(tag.getString(TAG_MODE));
    }

    /** 切换绑定模式，返回切换后是不是“输出容器”模式。 */
    private static boolean toggleMode(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean output = !MODE_OUTPUT.equals(tag.getString(TAG_MODE));
        tag.putString(TAG_MODE, output ? MODE_OUTPUT : "input");
        return output;
    }

    /** 扳手上当前选中的“取料容器”坐标，没选过就是 null。 */
    @Nullable
    public static BlockPos readTarget(ItemStack stack) {
        return readPos(stack, TAG_TARGET);
    }

    /** 扳手上当前选中的“输出容器”坐标，没选过就是 null。 */
    @Nullable
    public static BlockPos readOutputTarget(ItemStack stack) {
        return readPos(stack, TAG_OUTPUT_TARGET);
    }

    @Nullable
    private static BlockPos readPos(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        return BlockPos.of(tag.getLong(key));
    }

    @Nullable
    private static String readDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_DIMENSION) ? tag.getString(TAG_DIMENSION) : null;
    }

    private static void writeTarget(ItemStack stack, BlockPos pos, String dimension, boolean output) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(output ? TAG_OUTPUT_TARGET : TAG_TARGET, pos.asLong());
        tag.putString(TAG_DIMENSION, dimension);
    }

    private static void writeSelectedWorkbench(ItemStack stack, BlockPos pos, String dimension) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_BENCH_TARGET, pos.asLong());
        tag.putString(TAG_BENCH_DIMENSION, dimension);
    }

    @Nullable
    private static BlockPos readSelectedWorkbench(ItemStack stack) {
        return readPos(stack, TAG_BENCH_TARGET);
    }

    @Nullable
    private static String readSelectedWorkbenchDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_BENCH_DIMENSION)
                ? tag.getString(TAG_BENCH_DIMENSION) : null;
    }

    private static void clearSelectedWorkbench(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_BENCH_TARGET);
        tag.remove(TAG_BENCH_DIMENSION);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private static void clearSelectedTarget(ItemStack stack, boolean output) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(output ? TAG_OUTPUT_TARGET : TAG_TARGET);
        if (!tag.contains(TAG_TARGET) && !tag.contains(TAG_OUTPUT_TARGET)) {
            tag.remove(TAG_DIMENSION);
        }
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean output = isOutputMode(stack);
        tooltip.add(Component.translatableWithFallback(
                        "tooltip.smart_workbench.wrench.mode", "当前模式：%s",
                        Component.translatableWithFallback(output
                                ? "tooltip.smart_workbench.wrench.mode.output" : "tooltip.smart_workbench.wrench.mode.input",
                                output ? "输出容器" : "取料容器"))
                .withStyle(output ? ChatFormatting.GOLD : ChatFormatting.AQUA));

        BlockPos input = readTarget(stack);
        BlockPos out = readOutputTarget(stack);
        tooltip.add(Component.translatableWithFallback(
                        "tooltip.smart_workbench.wrench.selected", "已选中的取料容器：")
                .append(input == null ? Component.translatableWithFallback(
                        "tooltip.smart_workbench.wrench.none", "无") : Component.literal(input.toShortString()))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatableWithFallback(
                        "tooltip.smart_workbench.wrench.selected_output", "已选中的输出容器：")
                .append(out == null ? Component.translatableWithFallback(
                        "tooltip.smart_workbench.wrench.none", "无") : Component.literal(out.toShortString()))
                .withStyle(ChatFormatting.GOLD));

        tooltip.add(Component.translatableWithFallback(
                "tooltip.smart_workbench.wrench.switch", "潜行 + 右键空中：切换绑定模式")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatableWithFallback(
                "tooltip.smart_workbench.wrench.clear", "潜行右键工作台：清空绑定")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatableWithFallback(
                "tooltip.smart_workbench.wrench.gui", "想打开工作台界面：手里换成别的物品再右键")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void feedback(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
