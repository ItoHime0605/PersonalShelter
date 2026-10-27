package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 玩家目前所在土地的電台廣播狀態，非全服廣播。 */
public record CurrentLandRadioPacket(boolean enabled, String stationId) implements CustomPacketPayload {
    public static final Type<CurrentLandRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "current_land_radio"));
    public static final StreamCodec<ByteBuf, CurrentLandRadioPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CurrentLandRadioPacket::enabled,
            ByteBufCodecs.STRING_UTF8, CurrentLandRadioPacket::stationId,
            CurrentLandRadioPacket::new);
    public void handle() { ClientCurrentLandRadio.update(enabled, stationId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
