package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SetShelterNamePacket(String name) implements CustomPacketPayload {
    public static final Type<SetShelterNamePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_shelter_name"));
    public static final StreamCodec<ByteBuf, SetShelterNamePacket> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SetShelterNamePacket::name, SetShelterNamePacket::new);
    public void handle(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || name == null || name.replaceAll("(?i)&[0-9a-fk-or]", "").codePointCount(0, name.replaceAll("(?i)&[0-9a-fk-or]", "").length()) > 16 || name.length() > 64) return;
        var level = server.getLevel(Platform.levelKeyFor(player.getUUID()));
        if (level == null) return;
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        data.setShelterName(name);
        Platform.sendToPlayer(player, new ShelterNamePacket(data.getShelterName()));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
