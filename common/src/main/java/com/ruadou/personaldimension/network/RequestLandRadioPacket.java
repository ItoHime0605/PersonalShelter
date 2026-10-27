package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 僅回傳玩家當前所在私人土地的電台狀態。 */
public record RequestLandRadioPacket() implements CustomPacketPayload {
    public static final Type<RequestLandRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_land_radio"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestLandRadioPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestLandRadioPacket());
    public void handle(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        var level = server.getLevel(player.level().dimension());
        if (level == null || !player.level().dimension().location().getNamespace().equals(PersonalDimension.MODID)) {
            Platform.sendToPlayer(player, new LandRadioStatePacket(false, ""));
            return;
        }
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        Platform.sendToPlayer(player, new LandRadioStatePacket(data.isRadioEnabled(), data.getRadioSelectedStationId()));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
