package com.ruadou.personaldimension.block.entity;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 维度闪电接口方块实体：存储红石上升沿生成闪电时是否标记为"自然闪电"。
 *
 * <p>isNatural=true 时，生成的 {@link net.minecraft.world.entity.LightningBolt} 会带上
 * {@code ae2lt.natural_weather_lightning} NBT 标签，使 AE2LT 等模组按自然闪电处理
 * （收集器产出 / 自然专属配方生效）；false 时为普通人工闪电。
 * 默认 true，与"模拟自然闪电"的设计初衷一致。
 */
public class LightningInterfaceBlockEntity extends SyncedBlockEntity {

    private static final String KEY_NATURAL = "Natural";

    private boolean natural = true;

    public LightningInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Platform.lightningInterfaceBlockEntityType(), pos, state);
    }

    public boolean isNatural() {
        return this.natural;
    }

    public void setNatural(boolean natural) {
        this.natural = natural;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(KEY_NATURAL, this.natural);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 旧方块 NBT 里无此键，按默认"自然"处理，避免升级后行为突然变"人造"
        this.natural = tag.contains(KEY_NATURAL) ? tag.getBoolean(KEY_NATURAL) : true;
    }
}
