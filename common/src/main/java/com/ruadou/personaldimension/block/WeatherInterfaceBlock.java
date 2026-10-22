package com.ruadou.personaldimension.block;

import com.ruadou.personaldimension.block.entity.WeatherInterfaceBlockEntity;
import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度天气接口方块：右键在自己的个人维度选择目标天气（晴/雨/雷雨），外部红石上升沿激活时
 * 把该天气应用到方块所在个人维度。所选天气存于 {@link WeatherInterfaceBlockEntity}。
 */
public class WeatherInterfaceBlock extends AbstractDimensionInterfaceBlock {

    public WeatherInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherInterfaceBlockEntity(pos, state);
    }

    @Override
    protected void applyEffect(ServerLevel level, BlockPos pos) {
        DimensionInterfaceService.applyWeather(level, pos);
    }

    @Override
    protected void openScreen(BlockPos pos) {
        Platform.openWeatherInterfaceScreen(pos);
    }
}
