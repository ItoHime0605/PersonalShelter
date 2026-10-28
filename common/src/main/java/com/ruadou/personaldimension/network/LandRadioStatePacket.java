package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientLandRadioData;
import com.ruadou.personaldimension.radio.LandRadioSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** 僅提供給地主的完整電台設定；其他土地的訪客不會收到自訂網址清單。 */
public record LandRadioStatePacket(boolean enabled, String stationId,
        List<LandRadioSettings.Station> customStations) implements CustomPacketPayload {
    public static final Type<LandRadioStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "land_radio_state"));
    public static final StreamCodec<ByteBuf, LandRadioStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public LandRadioStatePacket decode(ByteBuf buffer) {
            boolean enabled = ByteBufCodecs.BOOL.decode(buffer);
            String id = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int count = buffer.readUnsignedByte();
            if (count > LandRadioSettings.MAX_STATIONS) throw new IllegalArgumentException("自訂電台數量超過限制");
            List<LandRadioSettings.Station> stations = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                stations.add(new LandRadioSettings.Station(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)));
            }
            return new LandRadioStatePacket(enabled, id, List.copyOf(stations));
        }
        @Override public void encode(ByteBuf buffer, LandRadioStatePacket packet) {
            ByteBufCodecs.BOOL.encode(buffer, packet.enabled());
            ByteBufCodecs.STRING_UTF8.encode(buffer, packet.stationId());
            buffer.writeByte(packet.customStations().size());
            for (var station : packet.customStations()) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, station.id());
                ByteBufCodecs.STRING_UTF8.encode(buffer, station.name());
                ByteBufCodecs.STRING_UTF8.encode(buffer, station.streamUrl());
            }
        }
    };
    public void handle() { ClientLandRadioData.update(enabled, stationId, customStations); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
