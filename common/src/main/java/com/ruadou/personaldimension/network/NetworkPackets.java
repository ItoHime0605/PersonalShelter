package com.ruadou.personaldimension.network;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络包注册表（平台无关）。
 * 集中管理所有网络包，供两端的 ModNetworking 读取并注册。
 */
public final class NetworkPackets {

    private NetworkPackets() {}

    public static final List<PacketEntry> S2C_PACKETS = List.of(
        new PacketEntry(UpdateDimensionsPacket.TYPE, UpdateDimensionsPacket.STREAM_CODEC),
        new PacketEntry(DimensionInfoPacket.TYPE, DimensionInfoPacket.STREAM_CODEC),
        new PacketEntry(ShelterNamePacket.TYPE, ShelterNamePacket.STREAM_CODEC),
        new PacketEntry(FriendDataPacket.TYPE, FriendDataPacket.STREAM_CODEC),
        new PacketEntry(OnlinePlayersPacket.TYPE, OnlinePlayersPacket.STREAM_CODEC),
        new PacketEntry(VisitListPacket.TYPE, VisitListPacket.STREAM_CODEC),
        new PacketEntry(CollectedBiomesPacket.TYPE, CollectedBiomesPacket.STREAM_CODEC),
        new PacketEntry(ChunkBiomesPacket.TYPE, ChunkBiomesPacket.STREAM_CODEC),
        new PacketEntry(SpawnRulesPacket.TYPE, SpawnRulesPacket.STREAM_CODEC),
        new PacketEntry(TeleporterListPacket.TYPE, TeleporterListPacket.STREAM_CODEC),
        new PacketEntry(FriendVisitorTeleporterStatusPacket.TYPE, FriendVisitorTeleporterStatusPacket.STREAM_CODEC)
    );

    public static final List<PacketEntry> C2S_PACKETS = List.of(
        new PacketEntry(RequestDimensionInfoPacket.TYPE, RequestDimensionInfoPacket.STREAM_CODEC),
        new PacketEntry(RequestShelterNamePacket.TYPE, RequestShelterNamePacket.STREAM_CODEC),
        new PacketEntry(SetShelterNamePacket.TYPE, SetShelterNamePacket.STREAM_CODEC),
        new PacketEntry(SetShelterEntryLockPacket.TYPE, SetShelterEntryLockPacket.STREAM_CODEC),
        new PacketEntry(SetDimensionTimePacket.TYPE, SetDimensionTimePacket.STREAM_CODEC),
        new PacketEntry(SetDimensionDaylightPacket.TYPE, SetDimensionDaylightPacket.STREAM_CODEC),
        new PacketEntry(SetDimensionWeatherPacket.TYPE, SetDimensionWeatherPacket.STREAM_CODEC),
        new PacketEntry(SetDimensionWeatherCyclePacket.TYPE, SetDimensionWeatherCyclePacket.STREAM_CODEC),
        new PacketEntry(SetFollowOverworldTimePacket.TYPE, SetFollowOverworldTimePacket.STREAM_CODEC),
        new PacketEntry(SetFollowOverworldWeatherPacket.TYPE, SetFollowOverworldWeatherPacket.STREAM_CODEC),
        new PacketEntry(SetDimensionMoonPhasePacket.TYPE, SetDimensionMoonPhasePacket.STREAM_CODEC),
        new PacketEntry(SetDimensionMoonPhaseCyclePacket.TYPE, SetDimensionMoonPhaseCyclePacket.STREAM_CODEC),
        new PacketEntry(SetFollowOverworldMoonPhasePacket.TYPE, SetFollowOverworldMoonPhasePacket.STREAM_CODEC),
        new PacketEntry(PersonalDimensionTravelPacket.TYPE, PersonalDimensionTravelPacket.STREAM_CODEC),
        new PacketEntry(OpenCreateMenuPacket.TYPE, OpenCreateMenuPacket.STREAM_CODEC),
        new PacketEntry(OpenShelterUpgradePacket.TYPE, OpenShelterUpgradePacket.STREAM_CODEC),
        new PacketEntry(CreateDimensionPacket.TYPE, CreateDimensionPacket.STREAM_CODEC),
        new PacketEntry(DeletePersonalDimensionPacket.TYPE, DeletePersonalDimensionPacket.STREAM_CODEC),
        new PacketEntry(RequestFriendDataPacket.TYPE, RequestFriendDataPacket.STREAM_CODEC),
        new PacketEntry(RequestOnlinePlayersPacket.TYPE, RequestOnlinePlayersPacket.STREAM_CODEC),
        new PacketEntry(RequestVisitListPacket.TYPE, RequestVisitListPacket.STREAM_CODEC),
        new PacketEntry(RequestVisitPacket.TYPE, RequestVisitPacket.STREAM_CODEC),
        new PacketEntry(FriendConfigActionPacket.TYPE, FriendConfigActionPacket.STREAM_CODEC),
        new PacketEntry(RequestCollectedBiomesPacket.TYPE, RequestCollectedBiomesPacket.STREAM_CODEC),
        new PacketEntry(ApplyBiomePacket.TYPE, ApplyBiomePacket.STREAM_CODEC),
        new PacketEntry(RequestChunkBiomesPacket.TYPE, RequestChunkBiomesPacket.STREAM_CODEC),
        new PacketEntry(CollectChunkBiomesPacket.TYPE, CollectChunkBiomesPacket.STREAM_CODEC),
        new PacketEntry(RequestSpawnRulesPacket.TYPE, RequestSpawnRulesPacket.STREAM_CODEC),
        new PacketEntry(SetSpawnFilterModePacket.TYPE, SetSpawnFilterModePacket.STREAM_CODEC),
        new PacketEntry(SetSpawnSourceScopePacket.TYPE, SetSpawnSourceScopePacket.STREAM_CODEC),
        new PacketEntry(ModifySpawnEntityPacket.TYPE, ModifySpawnEntityPacket.STREAM_CODEC),
        new PacketEntry(SetWeatherInterfacePacket.TYPE, SetWeatherInterfacePacket.STREAM_CODEC),
        new PacketEntry(SetTimeInterfacePacket.TYPE, SetTimeInterfacePacket.STREAM_CODEC),
        new PacketEntry(SetLightningInterfacePacket.TYPE, SetLightningInterfacePacket.STREAM_CODEC),
        new PacketEntry(SetMoonPhaseInterfacePacket.TYPE, SetMoonPhaseInterfacePacket.STREAM_CODEC),
        new PacketEntry(RequestTeleporterListPacket.TYPE, RequestTeleporterListPacket.STREAM_CODEC),
        new PacketEntry(TeleportToTargetPacket.TYPE, TeleportToTargetPacket.STREAM_CODEC),
        new PacketEntry(RenameTeleporterPacket.TYPE, RenameTeleporterPacket.STREAM_CODEC),
        new PacketEntry(SetFriendVisitorTeleporterPacket.TYPE, SetFriendVisitorTeleporterPacket.STREAM_CODEC),
        new PacketEntry(RequestFriendVisitorTeleporterStatusPacket.TYPE, RequestFriendVisitorTeleporterStatusPacket.STREAM_CODEC),
        new PacketEntry(UnbindOtherTeleporterPacket.TYPE, UnbindOtherTeleporterPacket.STREAM_CODEC),
        new PacketEntry(PublicDimensionPacket.TYPE, PublicDimensionPacket.STREAM_CODEC)
    );

    @SuppressWarnings("rawtypes")
    public record PacketEntry(CustomPacketPayload.Type type, StreamCodec codec) {}

    public static void handleS2CPacket(CustomPacketPayload payload) {
        try {
            Method method = payload.getClass().getMethod("handle");
            method.invoke(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle S2C packet: " + payload.type(), e);
        }
    }

    public static void handleC2SPacket(CustomPacketPayload payload, ServerPlayer player) {
        try {
            Method method = payload.getClass().getMethod("handle", ServerPlayer.class);
            method.invoke(payload, player);
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle C2S packet: " + payload.type(), e);
        }
    }
}
