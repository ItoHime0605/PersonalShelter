package com.ruadou.personaldimension.block.entity;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度月相接口方块实体：存储该方块被红石激活时要应用到所在个人维度的目标月相。
 *
 * <p>moonPhase 范围 0..7，默认 0（满月）。仅存一个小整数，随方块持久化并同步到客户端供界面回显。
 */
public class MoonPhaseInterfaceBlockEntity extends SyncedBlockEntity {

    private static final String KEY_MOON_PHASE = "MoonPhase";

    /** 目标月相：0..7，默认 0。 */
    private int moonPhase = 0;

    public MoonPhaseInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.moonPhaseInterfaceBlockEntityType(), pos, state);
    }

    public int getMoonPhase() {
        return this.moonPhase;
    }

    public void setMoonPhase(int moonPhase) {
        this.moonPhase = moonPhase;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_MOON_PHASE, this.moonPhase);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.moonPhase = tag.getInt(KEY_MOON_PHASE);
    }
}
