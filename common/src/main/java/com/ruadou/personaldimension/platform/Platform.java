package com.ruadou.personaldimension.platform;

import java.util.List;
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
 * 平台抽象入口（Facade）。
 *
 * <p>common 模块的业务代码只依赖本类，不直接引用 Fabric 或 NeoForge 的 API。
 * 平台端在初始化时调用 {@link #init(PlatformProvider)} 注入具体实现。
 *
 * <p>遵循依赖倒置原则（DIP）：高层业务逻辑依赖抽象，不依赖具体实现。
 */
public final class Platform {

    private Platform() {}

    private static PlatformProvider provider;

    /**
     * 由平台端主类在初始化最早阶段调用，注入平台具体实现。
     * 只允许调用一次，重复调用会抛出异常。
     */
    public static void init(PlatformProvider provider) {
        if (Platform.provider != null) {
            throw new IllegalStateException("Platform 已初始化，不可重复设置");
        }
        Platform.provider = provider;
    }

    private static PlatformProvider p() {
        if (provider == null) {
            throw new IllegalStateException("Platform 尚未初始化，请检查平台端主类是否调用了 Platform.init()");
        }
        return provider;
    }

    /** Server-authoritative upgrade menu entry point. */
    public static void openShelterUpgrade(ServerPlayer player) {
        p().openShelterUpgrade(player);
    }

    /** 模组 ID。 */
    public static String modId() {
        return p().modId();
    }

    /** 根据玩家 UUID 生成个人维度键。 */
    public static ResourceKey<Level> levelKeyFor(UUID playerId) {
        return p().levelKeyFor(playerId);
    }

    /** 判断是否为本模组的个人维度。 */
    public static boolean isPersonalDimension(ResourceKey<Level> key) {
        return p().isPersonalDimension(key);
    }

    /** 判断是否为公共维度。 */
    public static boolean isPublicDimension(ResourceKey<Level> key) {
        return p().isPublicDimension(key);
    }

    /** 判断是否为本模组管理的维度（个人或公共）。 */
    public static boolean isModDimension(ResourceKey<Level> key) {
        return p().isModDimension(key);
    }

    /** 由个人维度键反解其主人 UUID，非个人维度返回空。 */
    public static java.util.Optional<java.util.UUID> ownerOf(ResourceKey<Level> key) {
        return p().ownerOf(key);
    }

    /** 向指定玩家发送网络包（S2C）。 */
    public static <T> void sendToPlayer(ServerPlayer player, T packet) {
        p().sendToPlayer(player, packet);
    }

    /** 向所有在线玩家发送网络包（S2C）。 */
    public static <T> void sendToAll(MinecraftServer server, T packet) {
        p().sendToAll(server, packet);
    }

    /** 向服务器发送网络包（C2S，仅客户端可调用）。 */
    public static <T> void sendToServer(T packet) {
        p().sendToServer(packet);
    }

    /** 公共维度的资源键。 */
    public static ResourceKey<Level> publicLevelKey() {
        return p().publicLevelKey();
    }

    /** 群系收录器方块。 */
    public static Block biomeCollectorBlock() {
        return p().biomeCollectorBlock();
    }

    /** 群系改造器方块。 */
    public static Block biomeModifierBlock() {
        return p().biomeModifierBlock();
    }

    /** 时间接口方块。 */
    public static Block timeInterfaceBlock() {
        return p().timeInterfaceBlock();
    }

    /** 天气接口方块。 */
    public static Block weatherInterfaceBlock() {
        return p().weatherInterfaceBlock();
    }

    /** 闪电接口方块。 */
    public static Block lightningInterfaceBlock() {
        return p().lightningInterfaceBlock();
    }

    /** 月相接口方块。 */
    public static Block moonPhaseInterfaceBlock() {
        return p().moonPhaseInterfaceBlock();
    }

    /** 调用 ServerLevel.findLightningTargetAround（原 protected，平台用 AT 或 Mixin @Invoker 暴露）。 */
    public static BlockPos findLightningTargetAround(ServerLevel level, BlockPos target) {
        return p().findLightningTargetAround(level, target);
    }

    /** 给闪电打上"自然闪电"标记（兼容 AE2LT 等）。 */
    public static void markNaturalLightning(LightningBolt bolt) {
        p().markNaturalLightning(bolt);
    }

    /** 打开维度天气接口配置界面（仅客户端调用）。 */
    public static void openWeatherInterfaceScreen(BlockPos pos) {
        p().openWeatherInterfaceScreen(pos);
    }

    /** 打开维度时间接口配置界面（仅客户端调用）。 */
    public static void openTimeInterfaceScreen(BlockPos pos) {
        p().openTimeInterfaceScreen(pos);
    }

    /** 打开维度闪电接口配置界面（仅客户端调用）。 */
    public static void openLightningInterfaceScreen(BlockPos pos) {
        p().openLightningInterfaceScreen(pos);
    }

    /** 打开维度月相接口配置界面（仅客户端调用）。 */
    public static void openMoonPhaseInterfaceScreen(BlockPos pos) {
        p().openMoonPhaseInterfaceScreen(pos);
    }

    /** 打开群系收录器界面（仅客户端调用）。 */
    public static void openBiomeCollectorScreen(BlockPos pos) {
        p().openBiomeCollectorScreen(pos);
    }

    /** 打开群系改造器界面（仅客户端调用）。 */
    public static void openBiomeModifierScreen(BlockPos pos) {
        p().openBiomeModifierScreen(pos);
    }

    /** 打开好友访问传送器界面（仅客户端调用）。 */
    public static void openFriendVisitorTeleporterScreen(BlockPos pos) {
        p().openFriendVisitorTeleporterScreen(pos);
    }

    /** 打开个人维度控制器界面（仅客户端调用）。 */
    public static void openControllerScreen() {
        p().openControllerScreen();
    }

    /** 个人维度生成器物品。 */
    public static Item personalDimensionGeneratorItem() {
        return p().personalDimensionGeneratorItem();
    }

    /** 创建维度菜单类型。 */
    public static MenuType<?> createMenuType() {
        return p().createMenuType();
    }

    /** 时间接口方块实体类型。 */
    public static BlockEntityType<?> timeInterfaceBlockEntityType() {
        return p().timeInterfaceBlockEntityType();
    }

    /** 天气接口方块实体类型。 */
    public static BlockEntityType<?> weatherInterfaceBlockEntityType() {
        return p().weatherInterfaceBlockEntityType();
    }

    /** 闪电接口方块实体类型。 */
    public static BlockEntityType<?> lightningInterfaceBlockEntityType() {
        return p().lightningInterfaceBlockEntityType();
    }

    /** 月相接口方块实体类型。 */
    public static BlockEntityType<?> moonPhaseInterfaceBlockEntityType() {
        return p().moonPhaseInterfaceBlockEntityType();
    }

    /** 传送器方块实体类型。 */
    public static BlockEntityType<?> teleporterBlockEntityType() {
        return p().teleporterBlockEntityType();
    }

    /** 好友来访传送器方块实体类型。 */
    public static BlockEntityType<?> friendVisitorTeleporterBlockEntityType() {
        return p().friendVisitorTeleporterBlockEntityType();
    }

    /** 获取玩家的旅行记忆（传送返回点等）。 */
    public static TravelMemory getTravelMemory(ServerPlayer player) {
        return p().getTravelMemory(player);
    }

    /** 更新玩家的旅行记忆（原子操作：读取-修改-写回）。 */
    public static void updateTravelMemory(ServerPlayer player, UnaryOperator<TravelMemory> op) {
        p().updateTravelMemory(player, op);
    }

    /** 向玩家发送传送器列表包（S2C）。 */
    public static void sendTeleporterList(ServerPlayer player, List<TeleporterEntry> entries) {
        p().sendTeleporterList(player, entries);
    }

    // -----------------------------------------------------------------
    // 维度管理操作（DynamicDimensionManager / ModDimensions 的平台抽象）
    // -----------------------------------------------------------------

    /** 创建个人维度的 LevelStem（虚空 stem）。 */
    public static LevelStem createPersonalStem(MinecraftServer server) {
        return p().createPersonalStem(server);
    }

    /** 按预设与可选模板创建 LevelStem。 */
    public static LevelStem createStem(MinecraftServer server, DimensionPreset preset, DimensionTemplate template) {
        return p().createStem(server, preset, template);
    }

    /** 获取或创建动态维度并完成持久化绑定。 */
    public static ServerLevel getOrCreateLevel(MinecraftServer server, ResourceKey<Level> levelKey,
                                               Supplier<LevelStem> stemFactory,
                                               Function<WorldData, ServerLevelData> dataFactory,
                                               boolean tickTime) {
        return p().getOrCreateLevel(server, levelKey, stemFactory, dataFactory, tickTime);
    }

    /** 卸载并移除动态维度。 */
    public static boolean removeLevel(MinecraftServer server, ResourceKey<Level> levelKey) {
        return p().removeLevel(server, levelKey);
    }

    /** 重绑维度的 LevelData（用于重载后接管个人维度）。 */
    public static void rebindLevelData(ServerLevel level, PersonalDimensionLevelData data) {
        p().rebindLevelData(level, data);
    }

    // -----------------------------------------------------------------
    // 配置抽象（Config 的平台抽象）
    // -----------------------------------------------------------------

    /** 判断方块是否被配置名单允许。 */
    public static boolean isBlockAllowed(Block block) {
        return p().isBlockAllowed(block);
    }

    /** 个人维度最小可建造 Y。 */
    public static int minBuildY() {
        return p().minBuildY();
    }

    /** 个人维度最大可建造 Y（exclusive）。 */
    public static int maxBuildY() {
        return p().maxBuildY();
    }
}
