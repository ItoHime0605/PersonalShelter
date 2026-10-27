package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.radio.DefaultRadioStations;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 土地擁有者更新自己土地的電台開關與頻道。
 * 不接受客戶端提供的土地 UUID，避免修改其他玩家的土地。
 */
public record SetLandRadioPacket(boolean enabled, String stationId) implements CustomPacketPayload {
    public static final Type<SetLandRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_land_radio"));
    public static final StreamCodec<ByteBuf, SetLandRadioPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetLandRadioPacket::enabled,
            ByteBufCodecs.STRING_UTF8, SetLandRadioPacket::stationId,
            SetLandRadioPacket::new);

    public void handle(ServerPlayer player) {
        if (stationId == null || !stationId.matches("[a-zA-Z0-9_-]{0,80}")) return;
        var server = player.getServer();
        if (server == null) return;
        // 永遠由伺服器端的玩家 UUID 決定可修改的土地。
        var level = server.getLevel(Platform.levelKeyFor(player.getUUID()));
        if (level == null || !Platform.ownerOf(level.dimension()).filter(player.getUUID()::equals).isPresent()) return;
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        boolean custom = data.getRadioCustomStations().stream().anyMatch(s -> s.id().equals(stationId));
        // 預設頻道清單的 ID 由後續整合時在伺服器端驗證。
        if (enabled && !custom && !DefaultRadioStations.contains(stationId)) return;
        data.updateRadio(enabled, stationId, data.getRadioCustomStations());
        Platform.sendToPlayer(player, new LandRadioStatePacket(data.isRadioEnabled(), data.getRadioSelectedStationId()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
