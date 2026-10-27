package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientLandRadioData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 伺服器同步目前土地的電台開關與頻道 ID。 */
public record LandRadioStatePacket(boolean enabled, String stationId) implements CustomPacketPayload {
    public static final Type<LandRadioStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "land_radio_state"));
    public static final StreamCodec<ByteBuf, LandRadioStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LandRadioStatePacket::enabled,
            ByteBufCodecs.STRING_UTF8, LandRadioStatePacket::stationId,
            LandRadioStatePacket::new);
    public void handle() { ClientLandRadioData.update(enabled, stationId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
