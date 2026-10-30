package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/** Desk-mounted, rotatable shelter management laptop. */
public final class ShelterManagementTerminalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // The laptop model has a thin 2.7px keyboard base and a nearly 14px tall
    // upright display. A single 10px cuboid left the upper screen untargetable
    // and blocked empty space in front of the keyboard. Match both parts and
    // rotate their hitboxes with the blockstate, just like the JSON model.
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0.65, 0, 2, 15.35, 2.7, 13),
            Block.box(1, 2.2, 10.3, 15, 14.1, 12.4)).optimize();
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(3, 0, 0.65, 14, 2.7, 15.35),
            Block.box(3.6, 2.2, 1, 5.7, 14.1, 15)).optimize();
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0.65, 0, 3, 15.35, 2.7, 14),
            Block.box(1, 2.2, 3.6, 15, 14.1, 5.7)).optimize();
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(2, 0, 0.65, 13, 2.7, 15.35),
            Block.box(10.3, 2.2, 1, 12.4, 14.1, 15)).optimize();

    public ShelterManagementTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) Platform.openControllerScreen();
        return ItemInteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) Platform.openControllerScreen();
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

}
