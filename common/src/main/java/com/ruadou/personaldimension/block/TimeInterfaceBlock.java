package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.block.entity.TimeInterfaceBlockEntity;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度时间接口方块：右键在自己的个人维度选择目标时刻（预设或精确输入），外部红石上升沿激活时
 * 把该时刻应用到方块所在个人维度。所选时刻存于 {@link TimeInterfaceBlockEntity}。
 */
public class TimeInterfaceBlock extends AbstractDimensionInterfaceBlock {

    public TimeInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimeInterfaceBlockEntity(pos, state);
    }

    @Override
    protected void applyEffect(ServerLevel level, BlockPos pos) {
        DimensionInterfaceService.applyTime(level, pos);
    }

    @Override
    protected void openScreen(BlockPos pos) {
        Platform.openTimeInterfaceScreen(pos);
    }
}
