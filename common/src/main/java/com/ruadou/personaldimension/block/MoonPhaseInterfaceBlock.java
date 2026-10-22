package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.block.entity.MoonPhaseInterfaceBlockEntity;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度月相接口方块：右键在自己的个人维度选择目标月相（0..7），外部红石上升沿激活时
 * 把该月相锁定到方块所在个人维度。所选月相存于 {@link MoonPhaseInterfaceBlockEntity}。
 */
public class MoonPhaseInterfaceBlock extends AbstractDimensionInterfaceBlock {

    public MoonPhaseInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoonPhaseInterfaceBlockEntity(pos, state);
    }

    @Override
    protected void applyEffect(ServerLevel level, BlockPos pos) {
        DimensionInterfaceService.applyMoonPhase(level, pos);
    }

    @Override
    protected void openScreen(BlockPos pos) {
        Platform.openMoonPhaseInterfaceScreen(pos);
    }
}
