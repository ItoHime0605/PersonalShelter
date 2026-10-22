package com.ruadou.personaldimension.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * ServerLevel 私有字段与方法访问器。
 * 用于替换 serverLevelData 和 tickTime 字段（rebindLevelData 使用），
 * 以及暴露 protected 的 {@code findLightningTargetAround} 给闪电接口方块使用（替代反射）。
 *
 * <p>serverLevelData 是 final 字段，需配合 @Mutable 注解才能通过 @Accessor 修改。
 */
@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {

    @Mutable
    @Accessor("serverLevelData")
    void setServerLevelData(ServerLevelData data);

    @Mutable
    @Accessor("tickTime")
    void setTickTime(boolean tickTime);

    /** 暴露 ServerLevel.findLightningTargetAround(BlockPos)（原 protected）。 */
    @Invoker("findLightningTargetAround")
    BlockPos invokeFindLightningTargetAround(BlockPos pos);
}
