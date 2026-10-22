package com.ruadou.personaldimension.block.entity;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度天气接口方块实体：存储该方块被红石激活时要应用到所在个人维度的目标天气。
 *
 * <p>weatherState 语义与 {@code SetDimensionWeatherPacket} 一致：0=晴、1=雨、2=雷雨。
 * 仅存一个小整数，随方块持久化并同步到客户端供界面回显当前选择。
 */
public class WeatherInterfaceBlockEntity extends SyncedBlockEntity {

    private static final String KEY_WEATHER = "WeatherState";

    /** 目标天气：0=晴、1=雨、2=雷雨，默认晴。 */
    private int weatherState = 0;

    public WeatherInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.weatherInterfaceBlockEntityType(), pos, state);
    }

    public int getWeatherState() {
        return this.weatherState;
    }

    public void setWeatherState(int weatherState) {
        this.weatherState = weatherState;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_WEATHER, this.weatherState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.weatherState = tag.getInt(KEY_WEATHER);
    }
}
