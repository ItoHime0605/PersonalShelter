package com.ruadou.personaldimension.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.ruadou.personaldimension.attachment.TravelMemory;
import com.ruadou.personaldimension.dimension.DimensionPreset;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.dimension.TeleporterEntry;
import com.ruadou.personaldimension.dimension.template.DimensionTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * 平台 SPI 接口（Service Provider Interface）。
 *
 * <p>Fabric 和 NeoForge 各自实现此接口，提供平台特定的功能。
 * common 模块通过 {@link Platform} 静态入口调用这些方法，
 * 完全不感知具体平台实现。
 *
 * <p>设计原则：接口隔离（ISP），每个方法都是最小粒度的抽象。
 */
public interface PlatformProvider {

    /** Open the NeoForge shelter upgrade container. Other loaders may override later. */
    default void openShelterUpgrade(ServerPlayer player) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("此平台尚未支援土地擴張介面。"));
    }

    /** 模组 ID。 */
    String modId();

    /** 根据玩家 UUID 生成个人维度键。 */
    ResourceKey<Level> levelKeyFor(UUID playerId);

    /** 判断是否为本模组的个人维度。 */
    boolean isPersonalDimension(ResourceKey<Level> key);

    /** 判断是否为公共维度。 */
    boolean isPublicDimension(ResourceKey<Level> key);

    /** 判断是否为本模组管理的维度。 */
    boolean isModDimension(ResourceKey<Level> key);

    /** 由个人维度键反解其主人 UUID。 */
    Optional<UUID> ownerOf(ResourceKey<Level> key);

    /** 公共维度的资源键。 */
    ResourceKey<Level> publicLevelKey();

    /** 群系收录器方块（平台抽象：Fabric 直接返回 Block，NeoForge 从 DeferredBlock 解包）。 */
    Block biomeCollectorBlock();

    /** 群系改造器方块。 */
    Block biomeModifierBlock();

    /** 时间接口方块。 */
    Block timeInterfaceBlock();

    /** 天气接口方块。 */
    Block weatherInterfaceBlock();

    /** 闪电接口方块。 */
    Block lightningInterfaceBlock();

    /** 月相接口方块。 */
    Block moonPhaseInterfaceBlock();

    /**
     * 调用 {@code ServerLevel.findLightningTargetAround(BlockPos)}（原 protected）。
     * 平台抽象：NeoForge 用 AT 直接调用，Fabric 用 Mixin @Invoker 调用，避免反射。
     */
    BlockPos findLightningTargetAround(ServerLevel level, BlockPos target);

    /**
     * 给闪电实体打上"自然闪电"标记（兼容 AE2LT 等模组）。
     * 平台抽象：NeoForge 写入 {@code getPersistentData()}，Fabric 端暂未实现。
     */
    void markNaturalLightning(LightningBolt bolt);

    /**
     * 打开维度天气接口配置界面（仅客户端调用）。
     * 平台抽象：实现委托给各端的 ClientScreenOpener，方法体中的客户端类只在方法被调用时加载，
     * 服务端不会触发客户端类加载。
     */
    void openWeatherInterfaceScreen(BlockPos pos);

    /** 打开维度时间接口配置界面（仅客户端调用）。 */
    void openTimeInterfaceScreen(BlockPos pos);

    /** 打开维度闪电接口配置界面（仅客户端调用）。 */
    void openLightningInterfaceScreen(BlockPos pos);

    /** 打开维度月相接口配置界面（仅客户端调用）。 */
    void openMoonPhaseInterfaceScreen(BlockPos pos);

    /** 打开群系收录器界面（仅客户端调用）。 */
    void openBiomeCollectorScreen(BlockPos pos);

    /** 打开群系改造器界面（仅客户端调用）。 */
    void openBiomeModifierScreen(BlockPos pos);

    /** 打开好友访问传送器界面（仅客户端调用）。 */
    void openFriendVisitorTeleporterScreen(BlockPos pos);

    /** 打开个人维度控制器界面（仅客户端调用，无坐标参数）。 */
    void openControllerScreen();

    /** 个人维度生成器物品（平台抽象：Fabric 直接字段，NeoForge 从 DeferredItem 解包）。 */
    Item personalDimensionGeneratorItem();

    /** 创建维度菜单类型（平台抽象：Fabric 直接字段，NeoForge 从 DeferredHolder 解包）。 */
    MenuType<?> createMenuType();

    /** 时间接口方块实体类型（平台抽象：Fabric 直接字段，NeoForge 从 Supplier 解包）。 */
    BlockEntityType<?> timeInterfaceBlockEntityType();

    /** 天气接口方块实体类型。 */
    BlockEntityType<?> weatherInterfaceBlockEntityType();

    /** 闪电接口方块实体类型。 */
    BlockEntityType<?> lightningInterfaceBlockEntityType();

    /** 月相接口方块实体类型。 */
    BlockEntityType<?> moonPhaseInterfaceBlockEntityType();

    /** 传送器方块实体类型。 */
    BlockEntityType<?> teleporterBlockEntityType();

    /** 好友来访传送器方块实体类型。 */
    BlockEntityType<?> friendVisitorTeleporterBlockEntityType();

    /** 向指定玩家发送网络包（S2C）。 */
    <T> void sendToPlayer(ServerPlayer player, T packet);

    /** 向所有在线玩家发送网络包（S2C）。 */
    <T> void sendToAll(MinecraftServer server, T packet);

    /** 向服务器发送网络包（C2S，仅客户端可调用）。 */
    <T> void sendToServer(T packet);

    /** 获取玩家的旅行记忆（传送返回点等）。 */
    TravelMemory getTravelMemory(ServerPlayer player);

    /** 更新玩家的旅行记忆（原子操作：读取-修改-写回）。 */
    void updateTravelMemory(ServerPlayer player, UnaryOperator<TravelMemory> op);

    /** 向玩家发送传送器列表包（S2C）。 */
    void sendTeleporterList(ServerPlayer player, List<TeleporterEntry> entries);

    // -----------------------------------------------------------------
    // 维度管理操作（DynamicDimensionManager / ModDimensions 的平台抽象）
    // -----------------------------------------------------------------

    /** 创建个人维度的 LevelStem（虚空 stem）。 */
    LevelStem createPersonalStem(MinecraftServer server);

    /** 按预设与可选模板创建 LevelStem。 */
    LevelStem createStem(MinecraftServer server, DimensionPreset preset, DimensionTemplate template);

    /**
     * 获取或创建动态维度并完成持久化绑定。
     * 平台抽象：Fabric 用 DynamicDimensionManager + Mixin 访问 levels map，
     * NeoForge 用 forgeGetWorldMap 直接访问。
     */
    ServerLevel getOrCreateLevel(MinecraftServer server, ResourceKey<Level> levelKey,
                                 Supplier<LevelStem> stemFactory,
                                 Function<WorldData, ServerLevelData> levelDataFactory,
                                 boolean tickTime);

    /** 卸载并移除动态维度（运行时维度、stem、磁盘目录）。 */
    boolean removeLevel(MinecraftServer server, ResourceKey<Level> levelKey);

    /** 重绑维度的 LevelData（用于重载后接管个人维度）。 */
    void rebindLevelData(ServerLevel level, PersonalDimensionLevelData data);

    // -----------------------------------------------------------------
    // 配置抽象（Config 的平台抽象）
    // -----------------------------------------------------------------

    /** 判断方块是否被配置名单允许（用于自定义模板校验）。 */
    boolean isBlockAllowed(Block block);

    /** 个人维度最小可建造 Y。 */
    int minBuildY();

    /** 个人维度最大可建造 Y（ exclusive）。 */
    int maxBuildY();
}
