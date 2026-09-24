package cn.blockforge.generated.mod2e8abd21.block;

import cn.blockforge.generated.mod2e8abd21.blockentity.SmartWorkbenchBlockEntity;
import cn.blockforge.generated.mod2e8abd21.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * 智能工作台方块：右键打开界面，破坏时把合成格里的东西掉出来。
 */
public class SmartWorkbenchBlock extends BaseEntityBlock {

    public SmartWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartWorkbenchBlockEntity(pos, state);
    }

    @Deprecated
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                InteractionHand hand, BlockHitResult hit) {
        // 手里拿着扳手时，工作台交给扳手处理（选中容器 / 清空绑定），不弹界面
        if (player.getItemInHand(hand).getItem() instanceof WrenchItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            // 潜行右键不打开界面，方便往工作台上放方块
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof SmartWorkbenchBlockEntity blockEntity
                && player instanceof ServerPlayer serverPlayer && blockEntity.stillValid(player)) {
            NetworkHooks.openScreen(serverPlayer, blockEntity, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Deprecated
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SmartWorkbenchBlockEntity blockEntity) {
            Containers.dropContents(level, pos, blockEntity.getItems());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
