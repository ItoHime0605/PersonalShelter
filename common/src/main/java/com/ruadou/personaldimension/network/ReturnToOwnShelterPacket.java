package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.dimension.ShelterTravelCountdown;
import com.ruadou.personaldimension.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Return directly from a friend's land to the requesting player's own land. */
public record ReturnToOwnShelterPacket() implements CustomPacketPayload {
    public static final Type<ReturnToOwnShelterPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "return_to_own_shelter"));
    public static final StreamCodec<ByteBuf, ReturnToOwnShelterPacket> STREAM_CODEC =
        StreamCodec.unit(new ReturnToOwnShelterPacket());

    public void handle(ServerPlayer player) {
        if (!Platform.isModDimension(player.level().dimension())
            || Platform.isPublicDimension(player.level().dimension())
            || player.level().dimension().equals(Platform.levelKeyFor(player.getUUID()))) return;
        ShelterTravelCountdown.begin(player, PersonalDimensionService::handleReturnToOwnFromVisit);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
