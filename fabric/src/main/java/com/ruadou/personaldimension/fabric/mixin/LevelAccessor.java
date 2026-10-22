package com.ruadou.personaldimension.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;

/**
 * Level 类私有字段访问器。
 * 用于替换 Level.levelData 字段（rebindLevelData 使用）。
 *
 * <p>注意：字段类型为 {@link WritableLevelData}（非 LevelData），
 * Mixin @Accessor 要求类型精确匹配。
 * <p>levelData 是 final 字段，需配合 @Mutable 注解才能通过 @Accessor 修改。
 */
@Mixin(Level.class)
public interface LevelAccessor {

    @Mutable
    @Accessor("levelData")
    void setLevelData(WritableLevelData data);
}
