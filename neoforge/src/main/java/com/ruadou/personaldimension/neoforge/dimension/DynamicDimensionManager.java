package com.ruadou.personaldimension.neoforge.dimension;

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

import com.mojang.serialization.Lifecycle;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.neoforge.PersonalDimension;
import com.ruadou.personaldimension.neoforge.network.ModNetworking;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 运行时动态维度管理器。
 * 由于原版维度注册表在进入游戏前冻结，此类绕过冻结实现运行时建维。
 */
@SuppressWarnings("null")
public final class DynamicDimensionManager {

    private DynamicDimensionManager() {}

    private static final RegistrationInfo REGISTRATION_INFO =
        new RegistrationInfo(Optional.empty(), Lifecycle.stable());

    /**
     * 获取指定维度，不存在则动态创建并注册。
     *
     * @param server           服务器实例
     * @param levelKey         目标维度键
     * @param stemFactory      维度不存在时用于生成 LevelStem 的工厂
     * @param levelDataFactory 由全局 WorldData 生成该维度的 ServerLevelData 的工厂
     *                         （传 PersonalDimensionLevelData 以隔离时间，或 DerivedLevelData 共享主世界）
     * @param tickTime         是否让该维度独立推进时间（仅隔离时间的维度需 true）
     * @return 已存在或新建的 ServerLevel
     */
    @SuppressWarnings("deprecation") // forgeGetWorldMap 为 NeoForge 提供的可变 levels map 入口
    public static ServerLevel getOrCreateLevel(MinecraftServer server,
                                               ResourceKey<Level> levelKey,
                                               Supplier<LevelStem> stemFactory,
                                               Function<WorldData, ServerLevelData> levelDataFactory,
                                               boolean tickTime) {
        Map<ResourceKey<Level>, ServerLevel> levels = server.forgeGetWorldMap();
        ServerLevel existing = levels.get(levelKey);
        if (existing != null) {
            return existing;
        }
        return createAndRegisterLevel(server, levels, levelKey, stemFactory, levelDataFactory, tickTime);
    }

    @SuppressWarnings("deprecation") // markWorldsDirty 用于刷新 NeoForge 的世界缓存
    private static ServerLevel createAndRegisterLevel(MinecraftServer server,
                                                      Map<ResourceKey<Level>, ServerLevel> levels,
                                                      ResourceKey<Level> levelKey,
                                                      Supplier<LevelStem> stemFactory,
                                                      Function<WorldData, ServerLevelData> levelDataFactory,
                                                      boolean tickTime) {
        ServerLevel overworld = server.overworld();

        // 维度键与 LevelStem 键共用同一 ResourceLocation
        ResourceKey<LevelStem> stemKey =
            ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        LevelStem stem = stemFactory.get();

        // 解冻 LEVEL_STEM 注册表，注册新维度后无需重新冻结（原版后续不再校验）
        Registry<LevelStem> stemRegistry =
            server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (stemRegistry instanceof MappedRegistry<LevelStem> mappedRegistry) {
            mappedRegistry.unfreeze();
            mappedRegistry.register(stemKey, stem, REGISTRATION_INFO);
        } else {
            throw new IllegalStateException(
                "无法注册维度 " + stemKey.location() + "：LEVEL_STEM 注册表不可写");
        }

        // 通过 AccessTransformer 暴露的字段构造 ServerLevel
        ChunkProgressListener listener = server.progressListenerFactory.create(11);
        Executor executor = server.executor;
        LevelStorageSource.LevelStorageAccess storageAccess = server.storageSource;
        WorldData worldData = server.getWorldData();
        ServerLevelData levelData = levelDataFactory.apply(worldData);

        ServerLevel newLevel = new ServerLevel(
            server,
            executor,
            storageAccess,
            levelData,
            levelKey,
            stem,
            listener,
            worldData.isDebugWorld(),
            overworld.getSeed(),
            List.of(),      // 无特殊生成器（幻翼/流浪商人等仅主世界需要）
            tickTime,       // 是否独立推进时间
            (RandomSequences) null
        );

        // 与 JSON 维度保持一致：世界边界在所有维度间同步
        overworld.getWorldBorder().addListener(
            new BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));

        levels.put(levelKey, newLevel);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));

        // 同步新维度到所有客户端的维度列表
        ModNetworking.sendDimensionUpdateToAll(server, levelKey);

        PersonalDimension.LOGGER.info("已动态创建维度: {}", levelKey.location());
        return newLevel;
    }

    /**
     * 将已构造维度的时间数据替换为个人维度数据，并开启独立走时。
     *
     * <p>用于世界重载后的接管：vanilla {@code MinecraftServer.createLevels()} 会遍历
     * LEVEL_STEM 注册表，对所有非主世界维度硬编码使用 {@code DerivedLevelData} 且 {@code tickTime=false}
     * 重建 {@code ServerLevel}，绕过我们的工厂——导致个人维度重载后时间跟随主世界、控制失效。
     * 这里通过 AT 暴露的字段，把该 {@code ServerLevel} 的时间数据换成 {@link PersonalDimensionLevelData}
     * 并把 {@code tickTime} 置为 true，恢复时间隔离。
     *
     * <p>{@code ServerLevel.tickTime()} 同时读取继承自 {@code Level} 的 {@code levelData}
     * 与 {@code ServerLevel} 自身的 {@code serverLevelData}（二者本应指向同一对象），
     * 且 {@code getLevelData()} 返回前者，故两处必须一并替换。
     */
    public static void rebindLevelData(ServerLevel level, PersonalDimensionLevelData data) {
        level.levelData = data;        // 继承自 Level：getLevelData()/tickTime() 读它
        level.serverLevelData = data;  // ServerLevel 自身：setDayTime/setGameTime 写它
        level.tickTime = true;         // 开启本维度独立推进时间
    }

    /**
     * 运行时彻底移除一个动态维度。
     *
     * <p>与创建对称的销毁流程，供删除个人维度使用。<b>调用前必须先把该维度内所有玩家传出</b>，
     * 否则残留的玩家引用会指向已卸载的维度。完整顺序：
     * <ol>
     *   <li>从运行时 levels map 移除并 {@code markWorldsDirty}，使主循环下一 tick 不再触及；</li>
     *   <li>广播 {@link LevelEvent.Unload} 供其它监听清理；</li>
     *   <li>{@code level.close()} 关闭区块源/光照/实体管理器，flush 并释放 region 文件句柄
     *       （Windows 删目录前必须先释放句柄）；</li>
     *   <li>从 {@code LEVEL_STEM} 注册表移除 stem——否则存档时 {@code WorldGenSettings.encode}
     *       会把它重新写入 level.dat，导致重启后空维度复现；</li>
     *   <li>删除该维度磁盘目录，确保之后可用新预设干净重建；</li>
     *   <li>通知所有客户端从其已知维度集合移除该键。</li>
     * </ol>
     *
     * @return 目标维度存在并已移除返回 true；不存在返回 false
     */
    @SuppressWarnings("deprecation") // forgeGetWorldMap / markWorldsDirty 为 NeoForge 世界缓存入口
    public static boolean removeLevel(MinecraftServer server, ResourceKey<Level> levelKey) {
        Map<ResourceKey<Level>, ServerLevel> levels = server.forgeGetWorldMap();
        ServerLevel level = levels.get(levelKey);
        if (level == null) {
            return false;
        }

        levels.remove(levelKey);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));

        try {
            level.close(); // 关闭并 flush 区块源，释放文件句柄
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
     * 从 {@code LEVEL_STEM} 注册表移除指定维度的 {@link LevelStem}。
     *
     * <p>{@link MappedRegistry} 无公开的移除 API，故经 AT 暴露的内部索引手动删除：
     * 先从 {@code byKey/byLocation/byValue/registrationInfos} 四个查找表移除，再从
     * {@code byId} 顺序表移除并<b>整体重建 {@code toId}</b>——因为 {@code byId} 的下标即数值 id，
     * 删除一条会使其后所有条目的 id 前移，若不重建 {@code toId}（value→id）会与之错位。
     * 个人维度的数值 id 在存档/网络中不被引用（维度按 ResourceKey 定位），重建仅为保持自洽。
     */
    private static void unregisterStem(MinecraftServer server, ResourceKey<Level> levelKey) {
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        Registry<LevelStem> registry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!(registry instanceof MappedRegistry<LevelStem> mapped)) {
            PersonalDimension.LOGGER.warn("LEVEL_STEM 注册表不可写，无法移除 stem: {}", stemKey.location());
            return;
        }

        Holder.Reference<LevelStem> ref = mapped.byKey.remove(stemKey);
        if (ref == null) {
            return; // 未注册，无需移除
        }
        LevelStem stem = ref.value();
        mapped.byLocation.remove(stemKey.location());
        mapped.byValue.remove(stem);
        mapped.registrationInfos.remove(stemKey);

        // 从顺序表移除该条目，并按新顺序重建 value→id 映射
        mapped.byId.remove(ref);
        mapped.toId.clear();
        for (int i = 0; i < mapped.byId.size(); i++) {
            mapped.toId.put(mapped.byId.get(i).value(), i);
        }
    }

    /** 递归删除维度存档目录（自底向上），确保重建时不残留旧区块/数据。 */
    private static void deleteDimensionFiles(MinecraftServer server, ResourceKey<Level> levelKey) {
        LevelStorageSource.LevelStorageAccess storage = server.storageSource;
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
