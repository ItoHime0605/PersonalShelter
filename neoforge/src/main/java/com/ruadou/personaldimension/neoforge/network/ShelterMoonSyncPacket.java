package com.ruadou.personaldimension.neoforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelterMoonSyncPacket(ResourceLocation dimension, int phase) implements CustomPacketPayload {
    public static final Type<ShelterMoonSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("personaldimension", "shelter_moon_sync"));
    public static final StreamCodec<ByteBuf, ShelterMoonSyncPacket> CODEC =
        StreamCodec.composite(ResourceLocation.STREAM_CODEC, ShelterMoonSyncPacket::dimension,
            ByteBufCodecs.VAR_INT, ShelterMoonSyncPacket::phase, ShelterMoonSyncPacket::new);
    private static volatile ShelterMoonSyncPacket current;
    public static ShelterMoonSyncPacket current() { return current; }
    public static void clear() { current = null; }
    public void handle() { current = this; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
