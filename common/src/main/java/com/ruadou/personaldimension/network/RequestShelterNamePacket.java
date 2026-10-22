package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record RequestShelterNamePacket() implements CustomPacketPayload {
    public static final Type<RequestShelterNamePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_shelter_name"));
    public static final StreamCodec<ByteBuf, RequestShelterNamePacket> STREAM_CODEC =
        StreamCodec.unit(new RequestShelterNamePacket());
    public void handle(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        var level = server.getLevel(Platform.levelKeyFor(player.getUUID()));
        String name = "";
        if (level != null) {
            var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
            name = data.getShelterName();
        }
        Platform.sendToPlayer(player, new ShelterNamePacket(name));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
