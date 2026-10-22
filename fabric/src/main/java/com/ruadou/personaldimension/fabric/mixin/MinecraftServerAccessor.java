package com.ruadou.personaldimension.fabric.mixin;

import java.util.Map;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * MinecraftServer 私有字段访问器。
 * 使用 Mixin @Accessor 替代反射，运行时自动处理 intermediary 映射重命名。
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    /** 获取服务器的维度 Map（替代 NeoForge 的 forgeGetWorldMap） */
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    /** 获取服务器的线程池 */
    @Accessor("executor")
    Executor getExecutor();

    /** 获取存档存储访问对象 */
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();
}
