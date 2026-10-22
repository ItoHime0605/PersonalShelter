package com.ruadou.personaldimension.block;

import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 维度接口方块的抽象基类：右键在<b>玩家自己的个人维度</b>内打开配置界面，外部红石<b>上升沿</b>激活时
 * 把已配置的设置应用到方块所在维度。天气接口与时间接口共享这套"配置 + 红石触发"骨架（DRY），
 * 子类只需实现"应用什么效果"与"打开哪个界面"，遵循开闭原则。
 *
 * <p><b>红石语义</b>：{@link #POWERED} 记录当前是否收到邻近红石信号；仅在 false→true 的上升沿
 * 触发一次 {@link #applyEffect}，避免持续通电时每刻反复写入而与昼夜/天气循环打架。
 *
 * <p><b>权威约束</b>：右键打开界面是客户端动作，仅用于避免打开无用界面；实际配置写入由服务端在收到
 * 网络包后复检维度归属与方块存在再执行（见 {@code DimensionInterfaceService}），客户端校验不构成权威。
 */
public abstract class AbstractDimensionInterfaceBlock extends Block implements EntityBlock {

    /** 是否正被邻近红石信号供能，用于上升沿检测。 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected AbstractDimensionInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(POWERED, powered);
    }

    /**
     * 邻近方块变化：更新 POWERED 状态，并只在<b>上升沿</b>（新供能且此前未供能）触发一次效果应用。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level instanceof ServerLevel serverLevel) {
            applyEffect(serverLevel, pos);
        }
    }

    /**
     * 右键交互：仅允许在玩家自己的个人维度内打开界面；其它维度提示不可配置。
     * 打开界面为纯客户端动作，服务端不加载客户端类。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        Optional<UUID> owner = Platform.ownerOf(level.dimension());
        boolean inOwnDimension = owner.isPresent() && owner.get().equals(player.getUUID());
        if (!inOwnDimension) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("personaldimension.message.interface_wrong_dim"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            openScreen(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** 红石上升沿时把已配置设置应用到方块所在维度（服务端主线程）。 */
    protected abstract void applyEffect(ServerLevel level, BlockPos pos);

    /** 打开该接口的配置界面（仅客户端调用，方法体内引用的客户端类不会在服务端加载）。 */
    protected abstract void openScreen(BlockPos pos);
}
