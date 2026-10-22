package com.ruadou.personaldimension.fabric.dimension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Lifecycle;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.fabric.mixin.LevelAccessor;
import com.ruadou.personaldimension.fabric.mixin.MappedRegistryAccessor;
import com.ruadou.personaldimension.fabric.mixin.MinecraftServerAccessor;
import com.ruadou.personaldimension.fabric.mixin.ServerLevelAccessor;
import com.ruadou.personaldimension.fabric.network.ModNetworking;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * 运行时动态维度管理器（Fabric 实现）。
 * 由于原版维度注册表在进入游戏前冻结，此类绕过冻结实现运行时建维。
 *
 * <p>使用 Mixin Accessor 访问 MinecraftServer 和 MappedRegistry 的私有字段，
 * 替代反射方案（反射在 Fabric 运行时因 intermediary 映射重命名而失效）。
 */
public final class DynamicDimensionManager {

    private DynamicDimensionManager() {}

    private static final RegistrationInfo REGISTRATION_INFO =
        new RegistrationInfo(Optional.empty(), Lifecycle.stable());

    /** 空的区块进度监听器，用于动态创建的维度（不需要显示加载进度）。 */
    private static final ChunkProgressListener EMPTY_PROGRESS_LISTENER = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(net.minecraft.world.level.ChunkPos chunkPos) {}

        @Override
        public void onStatusChange(net.minecraft.world.level.ChunkPos chunkPos,
                                   @Nullable net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    };

    /** 获取服务器的 levels Map（通过 Mixin Accessor，替代 NeoForge 的 forgeGetWorldMap）。 */
    private static Map<ResourceKey<Level>, ServerLevel> getLevelsMap(MinecraftServer server) {
        return ((MinecraftServerAccessor) server).getLevels();
    }

    /**
     * 服务器启动后扫描存档目录，重新加载所有已存在的个人维度。
     *
     * <p>Fabric 没有类似 NeoForge 的 markWorldsDirty 机制来自动持久化和恢复动态维度，
     * 需要在服务器启动时手动扫描存档目录，为每个存在存档的个人维度重新注册 LevelStem
     * 并创建 ServerLevel，使维度在重启后仍然可用。
     */
    public static void reloadExistingDimensions(MinecraftServer server,
                                                 Function<ResourceKey<Level>, LevelStem> stemFactory) {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        LevelStorageSource.LevelStorageAccess storage = serverAccessor.getStorageSource();

        ServerLevel overworld = server.overworld();
        WorldData worldData = server.getWorldData();
        Map<ResourceKey<Level>, ServerLevel> levels = getLevelsMap(server);

        // 扫描存档根目录下所有以 personaldimension_ 为前缀的维度目录
        Path worldDir = storage.getDimensionPath(Level.OVERWORLD).getParent();
        if (worldDir == null || !Files.exists(worldDir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(worldDir)) {
            entries.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                if (!name.startsWith("personaldimension_")) {
                    return;
                }
                String dimPath = name.substring("personaldimension_".length());
                ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath(
                    PersonalDimension.MODID, dimPath);
                ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimId);

                // 跳过已加载的维度
                if (levels.containsKey(levelKey)) {
                    return;
                }

                try {
                    LevelStem stem = stemFactory.apply(levelKey);
                    ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dimId);

                    // 注册 LevelStem 到注册表
                    Registry<LevelStem> registry = server.registryAccess()
                        .registryOrThrow(Registries.LEVEL_STEM);
                    if (registry instanceof MappedRegistry<LevelStem> mapped) {
                        registerStem(mapped, stemKey, stem);
                    }

                    // 创建 ServerLevel
                    ServerLevelData levelData = new PersonalDimensionLevelData(
                        worldData, worldData.overworldData());
                    Executor executor = serverAccessor.getExecutor();

                    ServerLevel newLevel = new ServerLevel(
                        server, executor, storage, levelData, levelKey, stem,
                        EMPTY_PROGRESS_LISTENER, worldData.isDebugWorld(),
                        overworld.getSeed(), List.of(), false, (RandomSequences) null);

                    overworld.getWorldBorder().addListener(
                        new BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));
                    levels.put(levelKey, newLevel);

                    PersonalDimension.LOGGER.info("已重载维度: {}", dimId);
                } catch (Exception e) {
                    PersonalDimension.LOGGER.error("重载维度失败: {}", dimId, e);
                }
            });
        } catch (IOException e) {
            PersonalDimension.LOGGER.error("扫描维度存档目录失败", e);
        }
    }

    /**
     * 获取指定维度，不存在则动态创建并注册。
     */
    public static ServerLevel getOrCreateLevel(MinecraftServer server,
                                               ResourceKey<Level> levelKey,
                                               Supplier<LevelStem> stemFactory,
                                               Function<WorldData, ServerLevelData> levelDataFactory,
                                               boolean tickTime) {
        Map<ResourceKey<Level>, ServerLevel> levels = getLevelsMap(server);
        ServerLevel existing = levels.get(levelKey);
        if (existing != null) {
            return existing;
        }
        return createAndRegisterLevel(server, levels, levelKey, stemFactory, levelDataFactory, tickTime);
    }

    private static ServerLevel createAndRegisterLevel(MinecraftServer server,
                                                      Map<ResourceKey<Level>, ServerLevel> levels,
                                                      ResourceKey<Level> levelKey,
                                                      Supplier<LevelStem> stemFactory,
                                                      Function<WorldData, ServerLevelData> levelDataFactory,
                                                      boolean tickTime) {
        ServerLevel overworld = server.overworld();
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;

        // 维度键与 LevelStem 键共用同一 ResourceLocation
        ResourceKey<LevelStem> stemKey =
            ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        LevelStem stem = stemFactory.get();

        // 通过 Mixin Accessor 解冻注册表并注册 LevelStem
        Registry<LevelStem> stemRegistry =
            server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (stemRegistry instanceof MappedRegistry<LevelStem> mappedRegistry) {
            registerStem(mappedRegistry, stemKey, stem);
        } else {
            throw new IllegalStateException(
                "无法注册维度 " + stemKey.location() + "：LEVEL_STEM 注册表不可写");
        }

        // 通过 Mixin Accessor 获取私有字段
        Executor executor = serverAccessor.getExecutor();
        LevelStorageSource.LevelStorageAccess storageAccess = serverAccessor.getStorageSource();
        WorldData worldData = server.getWorldData();
        ServerLevelData levelData = levelDataFactory.apply(worldData);

        ServerLevel newLevel = new ServerLevel(
            server,
            executor,
            storageAccess,
            levelData,
            levelKey,
            stem,
            EMPTY_PROGRESS_LISTENER,
            worldData.isDebugWorld(),
            overworld.getSeed(),
            List.of(),
            tickTime,
            (RandomSequences) null
        );

        // 世界边界在所有维度间同步
        overworld.getWorldBorder().addListener(
            new BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));

        levels.put(levelKey, newLevel);

        // 同步新维度到所有客户端的维度列表
        ModNetworking.sendDimensionUpdateToAll(server, levelKey);

        PersonalDimension.LOGGER.info("已动态创建维度: {}", levelKey.location());
        return newLevel;
    }

    /**
     * 将已构造维度的时间数据替换为个人维度数据，并开启独立走时。
     * 通过 Mixin Accessor 替换字段，替代 NeoForge 的 AccessTransformer 方案。
     */
    public static void rebindLevelData(ServerLevel level, PersonalDimensionLevelData data) {
        ((LevelAccessor) level).setLevelData(data);
        ((ServerLevelAccessor) level).setServerLevelData(data);
        ((ServerLevelAccessor) level).setTickTime(true);
    }

    /**
     * 运行时彻底移除一个动态维度。
     *
     * <p>与创建对称的销毁流程，供删除个人维度使用。
     * <b>调用前必须先把该维度内所有玩家传出</b>。
     *
     * @return 目标维度存在并已移除返回 true；不存在返回 false
     */
    public static boolean removeLevel(MinecraftServer server, ResourceKey<Level> levelKey) {
        Map<ResourceKey<Level>, ServerLevel> levels = getLevelsMap(server);
        ServerLevel level = levels.get(levelKey);
        if (level == null) {
            return false;
        }

        levels.remove(levelKey);

        try {
            level.close();
        } catch (IOException e) {
            PersonalDimension.LOGGER.error("关闭维度 {} 失败", levelKey.location(), e);
        }

        unregisterStem(server, levelKey);
        deleteDimensionFiles(server, levelKey);
        ModNetworking.sendDimensionRemoveToAll(server, levelKey);

        PersonalDimension.LOGGER.info("已删除维度: {}", levelKey.location());
        return true;
    }

    /**
     * 通过 Mixin Accessor 解冻 MappedRegistry 并注册 LevelStem。
     */
    @SuppressWarnings("unchecked")
    private static void registerStem(MappedRegistry<LevelStem> registry,
                                     ResourceKey<LevelStem> key,
                                     LevelStem value) {
        MappedRegistryAccessor<LevelStem> accessor = (MappedRegistryAccessor<LevelStem>) registry;
        boolean wasFrozen = accessor.isFrozen();
        // 临时解冻
        accessor.setFrozen(false);
        try {
            registry.register(key, value, REGISTRATION_INFO);
        } finally {
            // 恢复冻结状态
            accessor.setFrozen(wasFrozen);
        }
        PersonalDimension.LOGGER.debug("已通过解冻注册 LevelStem: {}", key.location());
    }

    /**
     * 从 LEVEL_STEM 注册表移除指定维度的 LevelStem（通过 Mixin Accessor 操作内部字段）。
     */
    @SuppressWarnings("unchecked")
    private static void unregisterStem(MinecraftServer server, ResourceKey<Level> levelKey) {
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        Registry<LevelStem> registry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!(registry instanceof MappedRegistry<LevelStem> mapped)) {
            PersonalDimension.LOGGER.warn("LEVEL_STEM 注册表不可写，无法移除 stem: {}", stemKey.location());
            return;
        }

        try {
            MappedRegistryAccessor<LevelStem> accessor = (MappedRegistryAccessor<LevelStem>) mapped;
            Map<ResourceKey<LevelStem>, Holder.Reference<LevelStem>> byKey = accessor.getByKey();

            Holder.Reference<LevelStem> ref = byKey.remove(stemKey);
            if (ref == null) {
                return;
            }

            LevelStem stem = ref.value();
            accessor.getByLocation().remove(stemKey.location());
            accessor.getByValue().remove(stem);
            accessor.getRegistrationInfos().remove(stemKey);

            List<Holder.Reference<LevelStem>> byId = accessor.getById();
            byId.remove(ref);

            // 重建 toId 映射
            Map<LevelStem, Integer> toId = accessor.getToId();
            toId.clear();
            for (int i = 0; i < byId.size(); i++) {
                toId.put(byId.get(i).value(), i);
            }
        } catch (Exception e) {
            PersonalDimension.LOGGER.error("无法从 MappedRegistry 移除 stem: {}", stemKey.location(), e);
        }
    }

    /** 递归删除维度存档目录（自底向上），确保重建时不残留旧区块/数据。 */
    private static void deleteDimensionFiles(MinecraftServer server, ResourceKey<Level> levelKey) {
        LevelStorageSource.LevelStorageAccess storage =
            ((MinecraftServerAccessor) server).getStorageSource();
        Path dimensionPath = storage.getDimensionPath(levelKey);
        if (!Files.exists(dimensionPath)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dimensionPath)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    PersonalDimension.LOGGER.warn("删除维度文件失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            PersonalDimension.LOGGER.error("删除维度目录失败: {}", dimensionPath, e);
        }
    }
}
