package com.ruadou.personaldimension.fabric.platform;

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
import com.ruadou.personaldimension.fabric.Config;
import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.fabric.attachment.ModAttachments;
import com.ruadou.personaldimension.fabric.block.ModBlocks;
import com.ruadou.personaldimension.fabric.block.entity.ModBlockEntities;
import com.ruadou.personaldimension.client.ClientScreenOpener;
import com.ruadou.personaldimension.fabric.dimension.DynamicDimensionManager;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.fabric.item.ModItems;
import com.ruadou.personaldimension.fabric.menu.ModMenus;
import com.ruadou.personaldimension.fabric.mixin.ServerLevelAccessor;
import com.ruadou.personaldimension.network.TeleporterListPacket;
import com.ruadou.personaldimension.platform.PlatformProvider;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

/**
 * Fabric 平台实现。
 * 封装 Fabric API 的网络发送与维度键操作。
 */
public final class FabricPlatformProvider implements PlatformProvider {

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
        return ModBlocks.BIOME_COLLECTOR;
    }

    @Override
    public Block biomeModifierBlock() {
        return ModBlocks.BIOME_MODIFIER;
    }

    @Override
    public Block timeInterfaceBlock() {
        return ModBlocks.TIME_INTERFACE;
    }

    @Override
    public Block weatherInterfaceBlock() {
        return ModBlocks.WEATHER_INTERFACE;
    }

    @Override
    public Block lightningInterfaceBlock() {
        return ModBlocks.LIGHTNING_INTERFACE;
    }

    @Override
    public Block moonPhaseInterfaceBlock() {
        return ModBlocks.MOON_PHASE_INTERFACE;
    }

    @Override
    public BlockPos findLightningTargetAround(ServerLevel level, BlockPos target) {
        // Fabric 端用 Mixin @Invoker 暴露 protected 方法，避免反射
        return ((ServerLevelAccessor) level).invokeFindLightningTargetAround(target);
    }

    @Override
    public void markNaturalLightning(LightningBolt bolt) {
        // AE2LT 无 Fabric 端，无需实现
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
        return ModItems.PERSONAL_DIMENSION_GENERATOR;
    }

    @Override
    public MenuType<?> createMenuType() {
        return ModMenus.CREATE_MENU;
    }

    @Override
    public BlockEntityType<?> timeInterfaceBlockEntityType() {
        return ModBlockEntities.TIME_INTERFACE;
    }

    @Override
    public BlockEntityType<?> weatherInterfaceBlockEntityType() {
        return ModBlockEntities.WEATHER_INTERFACE;
    }

    @Override
    public BlockEntityType<?> lightningInterfaceBlockEntityType() {
        return ModBlockEntities.LIGHTNING_INTERFACE;
    }

    @Override
    public BlockEntityType<?> moonPhaseInterfaceBlockEntityType() {
        return ModBlockEntities.MOON_PHASE_INTERFACE;
    }

    @Override
    public BlockEntityType<?> teleporterBlockEntityType() {
        return ModBlockEntities.TELEPORTER;
    }

    @Override
    public BlockEntityType<?> friendVisitorTeleporterBlockEntityType() {
        return ModBlockEntities.FRIEND_VISITOR_TELEPORTER;
    }

    @Override
    public <T> void sendToPlayer(ServerPlayer player, T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public <T> void sendToAll(MinecraftServer server, T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public <T> void sendToServer(T packet) {
        CustomPacketPayload payload = (CustomPacketPayload) packet;
        ClientPlayNetworking.send(payload);
    }

    @Override
    public TravelMemory getTravelMemory(ServerPlayer player) {
        TravelMemory current = player.getAttached(ModAttachments.TRAVEL_MEMORY);
        return current != null ? current : TravelMemory.EMPTY;
    }

    @Override
    public void updateTravelMemory(ServerPlayer player, UnaryOperator<TravelMemory> op) {
        TravelMemory current = player.getAttached(ModAttachments.TRAVEL_MEMORY);
        if (current == null) current = TravelMemory.EMPTY;
        player.setAttached(ModAttachments.TRAVEL_MEMORY, op.apply(current));
    }

    @Override
    public void sendTeleporterList(ServerPlayer player, List<TeleporterEntry> entries) {
        ServerPlayNetworking.send(player, new TeleporterListPacket(entries));
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
