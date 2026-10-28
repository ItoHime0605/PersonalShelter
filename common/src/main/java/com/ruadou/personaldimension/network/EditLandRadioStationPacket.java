package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.radio.LandRadioSettings;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.UUID;

/** 所有自訂電台變更都由伺服器查詢玩家自己的土地；封包不能指定其他玩家土地。 */
public record EditLandRadioStationPacket(String action, String stationId, String name, String streamUrl)
        implements CustomPacketPayload {
    public static final Type<EditLandRadioStationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "edit_land_radio_station"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, EditLandRadioStationPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, EditLandRadioStationPacket::action,
                    ByteBufCodecs.STRING_UTF8, EditLandRadioStationPacket::stationId,
                    ByteBufCodecs.STRING_UTF8, EditLandRadioStationPacket::name,
                    ByteBufCodecs.STRING_UTF8, EditLandRadioStationPacket::streamUrl,
                    EditLandRadioStationPacket::new);

    public void handle(ServerPlayer player) {
        if (!action.equals("add") && !action.equals("edit") && !action.equals("delete")) return;
        if (action.equals("add") && !stationId.isEmpty()) return;
        if (!action.equals("add") && !stationId.matches("[a-zA-Z0-9_-]{1,80}")) return;
        if (name.length() > LandRadioSettings.MAX_NAME_LENGTH
                || streamUrl.length() > LandRadioSettings.MAX_URL_LENGTH
                || stationId.length() > 80) return;
        var server = player.getServer();
        if (server == null) return;
        var key = Platform.levelKeyFor(player.getUUID());
        var level = server.getLevel(key);
        if (level == null || !Platform.ownerOf(key).filter(player.getUUID()::equals).isPresent()) return;
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        var stations = new ArrayList<>(data.getRadioCustomStations());
        String id = stationId;
        try {
            if (action.equals("add")) {
                if (stations.size() >= LandRadioSettings.MAX_STATIONS) return;
                id = "custom_" + UUID.randomUUID().toString().replace("-", "");
                stations.add(new LandRadioSettings.Station(id, name, streamUrl));
            } else {
                int index = -1;
                for (int i = 0; i < stations.size(); i++) {
                    if (stations.get(i).id().equals(id)) { index = i; break; }
                }
                if (index < 0) return;
                if (action.equals("edit")) {
                    stations.set(index, new LandRadioSettings.Station(id, name, streamUrl));
                } else {
                    stations.remove(index);
                }
            }
            String selected = data.getRadioSelectedStationId();
            boolean enabled = data.isRadioEnabled();
            if (action.equals("delete") && selected.equals(id)) {
                selected = "";
                enabled = false;
            }
            data.updateRadio(enabled, selected, stations);
            Platform.sendToPlayer(player, new LandRadioStatePacket(
                    data.isRadioEnabled(), data.getRadioSelectedStationId(), data.getRadioCustomStations()));
        } catch (IllegalArgumentException exception) {
            // 不接受格式無效的請求，舊存檔與目前電台設定保持不變。
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
