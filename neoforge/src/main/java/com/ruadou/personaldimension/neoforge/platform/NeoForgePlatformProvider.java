package com.ruadou.personaldimension.neoforge.platform;

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
import com.ruadou.personaldimension.neoforge.Config;
import com.ruadou.personaldimension.neoforge.PersonalDimension;
import com.ruadou.personaldimension.neoforge.attachment.ModAttachments;
import com.ruadou.personaldimension.neoforge.block.ModBlocks;
import com.ruadou.personaldimension.neoforge.block.entity.ModBlockEntities;
import com.ruadou.personaldimension.client.ClientScreenOpener;
import com.ruadou.personaldimension.neoforge.dimension.DynamicDimensionManager;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.neoforge.item.ModItems;
import com.ruadou.personaldimension.neoforge.menu.ModMenus;
import com.ruadou.personaldimension.network.TeleporterListPacket;
import com.ruadou.personaldimension.platform.PlatformProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 平台实现。
 * 封装 NeoForge 的网络发送与维度键操作。
 */
@SuppressWarnings("null")
public final class NeoForgePlatformProvider implements PlatformProvider {

    @Override
    public void openShelterUpgrade(ServerPlayer player) {
        if (!ModDimensions.ownerOf(player.level().dimension())
                .map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("請先進入自己的個人空間，才能擴張土地。"));
            return;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, ignored) -> new com.ruadou.personaldimension.neoforge.menu.ShelterUpgradeMenu(id, inventory),
                net.minecraft.network.chat.Component.literal("土地擴張")));
    }

    @Override
    public String modId() {
        return PersonalDimension.MODID;
    }

    @Override
    public ResourceKey<Level> levelKeyFor(UUID playerId) {
        return ModDimensions.levelKeyFor(playerId);
    }

    @Override
    public boolean isPersonalDimension(ResourceKey<Level> key) {
        return ModDimensions.isPersonalDimension(key);
    }

    @Override
    public boolean isPublicDimension(ResourceKey<Level> key) {
        return ModDimensions.isPublicDimension(key);
    }

    @Override
    public boolean isModDimension(ResourceKey<Level> key) {
        return ModDimensions.isModDimension(key);
    }

    @Override
    public Optional<UUID> ownerOf(ResourceKey<Level> key) {
        return ModDimensions.ownerOf(key);
    }

    @Override
    public ResourceKey<Level> publicLevelKey() {
        return ModDimensions.PUBLIC_LEVEL_KEY;
    }

    @Override
    public Block biomeCollectorBlock() {
        return ModBlocks.BIOME_COLLECTOR.get();
    }

    @Override
    public Block biomeModifierBlock() {
        return ModBlocks.BIOME_MODIFIER.get();
    }

    @Override
    public Block timeInterfaceBlock() {
        return ModBlocks.TIME_INTERFACE.get();
    }

    @Override
    public Block weatherInterfaceBlock() {
        return ModBlocks.WEATHER_INTERFACE.get();
    }

    @Override
    public Block lightningInterfaceBlock() {
        return ModBlocks.LIGHTNING_INTERFACE.get();
    }

    @Override
    public Block moonPhaseInterfaceBlock() {
        return ModBlocks.MOON_PHASE_INTERFACE.get();
    }

    @Override
    public BlockPos findLightningTargetAround(ServerLevel level, BlockPos target) {
        // NeoForge 端通过 AT 暴露为 public，直接调用
        return level.findLightningTargetAround(target);
    }

    @Override
    public void markNaturalLightning(LightningBolt bolt) {
        // NeoForge 用 PersistentData API 写 NBT，兼容 AE2LT 等闪电科技模组
        bolt.getPersistentData().putBoolean("ae2lt.natural_weather_lightning", true);
    }

    @Override
    public void openWeatherInterfaceScreen(BlockPos pos) {
        ClientScreenOpener.openWeatherInterfaceScreen(pos);
    }

    @Override
    public void openTimeInterfaceScreen(BlockPos pos) {
        ClientScreenOpener.openTimeInterfaceScreen(pos);
    }

    @Override
    public void openLightningInterfaceScreen(BlockPos pos) {
        ClientScreenOpener.openLightningInterfaceScreen(pos);
    }

    @Override
    public void openMoonPhaseInterfaceScreen(BlockPos pos) {
        ClientScreenOpener.openMoonPhaseInterfaceScreen(pos);
    }

    @Override
    public void openBiomeCollectorScreen(BlockPos pos) {
        ClientScreenOpener.openBiomeCollectorScreen(pos);
    }

    @Override
    public void openBiomeModifierScreen(BlockPos pos) {
        ClientScreenOpener.openBiomeModifierScreen(pos);
    }

    @Override
    public void openFriendVisitorTeleporterScreen(BlockPos pos) {
        ClientScreenOpener.openFriendVisitorTeleporterScreen(pos);
    }

    @Override
    public void openControllerScreen() {
        ClientScreenOpener.openControllerScreen();
    }

    @Override
    public Item personalDimensionGeneratorItem() {
        return ModItems.PERSONAL_DIMENSION_GENERATOR.get();
    }

    @Override
    public MenuType<?> createMenuType() {
        return ModMenus.CREATE_MENU.get();
    }

    @Override
    public BlockEntityType<?> timeInterfaceBlockEntityType() {
        return ModBlockEntities.TIME_INTERFACE.get();
    }

    @Override
    public BlockEntityType<?> weatherInterfaceBlockEntityType() {
        return ModBlockEntities.WEATHER_INTERFACE.get();
    }

    @Override
    public BlockEntityType<?> lightningInterfaceBlockEntityType() {
        return ModBlockEntities.LIGHTNING_INTERFACE.get();
    }

    @Override
    public BlockEntityType<?> moonPhaseInterfaceBlockEntityType() {
        return ModBlockEntities.MOON_PHASE_INTERFACE.get();
    }

    @Override
    public BlockEntityType<?> teleporterBlockEntityType() {
        return ModBlockEntities.TELEPORTER.get();
    }

    @Override
    public BlockEntityType<?> friendVisitorTeleporterBlockEntityType() {
        return ModBlockEntities.FRIEND_VISITOR_TELEPORTER.get();
    }

    @Override
    public <T> void sendToPlayer(ServerPlayer player, T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public <T> void sendToAll(MinecraftServer server, T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public <T> void sendToServer(T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public TravelMemory getTravelMemory(ServerPlayer player) {
        return player.getData(ModAttachments.TRAVEL_MEMORY.get());
    }

    @Override
    public void updateTravelMemory(ServerPlayer player, UnaryOperator<TravelMemory> op) {
        TravelMemory current = player.getData(ModAttachments.TRAVEL_MEMORY.get());
        player.setData(ModAttachments.TRAVEL_MEMORY.get(), op.apply(current));
    }

    @Override
    public void sendTeleporterList(ServerPlayer player, List<TeleporterEntry> entries) {
        PacketDistributor.sendToPlayer(player, new TeleporterListPacket(entries));
    }

    // -----------------------------------------------------------------
    // 维度管理操作（委托给 DynamicDimensionManager / ModDimensions）
    // -----------------------------------------------------------------

    @Override
    public LevelStem createPersonalStem(MinecraftServer server) {
        return ModDimensions.createPersonalStem(server);
    }

    @Override
    public LevelStem createStem(MinecraftServer server, DimensionPreset preset, DimensionTemplate template) {
        return ModDimensions.createStem(server, preset, template);
    }

    @Override
    public ServerLevel getOrCreateLevel(MinecraftServer server, ResourceKey<Level> levelKey,
                                        Supplier<LevelStem> stemFactory,
                                        Function<WorldData, ServerLevelData> dataFactory,
                                        boolean tickTime) {
        return DynamicDimensionManager.getOrCreateLevel(server, levelKey, stemFactory, dataFactory, tickTime);
    }

    @Override
    public boolean removeLevel(MinecraftServer server, ResourceKey<Level> levelKey) {
        return DynamicDimensionManager.removeLevel(server, levelKey);
    }

    @Override
    public void rebindLevelData(ServerLevel level, PersonalDimensionLevelData data) {
        DynamicDimensionManager.rebindLevelData(level, data);
    }

    // -----------------------------------------------------------------
    // 配置抽象
    // -----------------------------------------------------------------

    @Override
    public boolean isBlockAllowed(Block block) {
        return Config.isBlockAllowed(block);
    }

    @Override
    public int minBuildY() {
        return PersonalDimension.MIN_BUILD_Y;
    }

    @Override
    public int maxBuildY() {
        return PersonalDimension.MAX_BUILD_Y;
    }
}
