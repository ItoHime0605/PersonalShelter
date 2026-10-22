package com.ruadou.personaldimension.block.entity;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度时间接口方块实体：存储该方块被红石激活时要应用到所在个人维度的目标时刻。
 *
 * <p>dayTime 归一化到 0..23999 一天之内，随方块持久化并同步到客户端供界面回显当前选择。
 */
public class TimeInterfaceBlockEntity extends SyncedBlockEntity {

    private static final String KEY_DAY_TIME = "DayTime";

    /** 目标时刻（0..23999），默认白天 1000。 */
    private long dayTime = 1000L;

    public TimeInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.timeInterfaceBlockEntityType(), pos, state);
    }

    public long getDayTime() {
        return this.dayTime;
    }

    public void setDayTime(long dayTime) {
        this.dayTime = dayTime;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(KEY_DAY_TIME, this.dayTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.dayTime = tag.getLong(KEY_DAY_TIME);
    }
}
