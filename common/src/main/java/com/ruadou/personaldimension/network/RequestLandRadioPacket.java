package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 僅回傳玩家當前所在私人土地的電台狀態。 */
public record RequestLandRadioPacket() implements CustomPacketPayload {
    public static final Type<RequestLandRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_land_radio"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestLandRadioPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestLandRadioPacket());
    public void handle(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        ResourceKey<Level> current = player.level().dimension();
        // 不可僅依命名空間判斷：必須確認此維度確實對應玩家私人土地的 UUID。
        boolean privateLand = current.location().getNamespace().equals(PersonalDimension.MODID)
                && server.getPlayerList().getPlayers().stream().map(p -> p.getUUID())
                    .anyMatch(id -> current.equals(Platform.levelKeyFor(id)));
        var level = server.getLevel(current);
        if (level == null || !privateLand) {
            Platform.sendToPlayer(player, new LandRadioStatePacket(false, ""));
            return;
        }
        var data = level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        Platform.sendToPlayer(player, new LandRadioStatePacket(data.isRadioEnabled(), data.getRadioSelectedStationId()));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
