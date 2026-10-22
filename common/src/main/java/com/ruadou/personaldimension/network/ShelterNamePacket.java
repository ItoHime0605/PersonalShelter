package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientShelterName;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShelterNamePacket(String name) implements CustomPacketPayload {
    public static final Type<ShelterNamePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "shelter_name"));
    public static final StreamCodec<ByteBuf, ShelterNamePacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ShelterNamePacket::name, ShelterNamePacket::new);
    public void handle() { ClientShelterName.update(name); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
