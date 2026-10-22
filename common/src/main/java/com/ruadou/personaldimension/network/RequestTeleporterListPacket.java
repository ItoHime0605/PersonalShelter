package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.TeleporterService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 传送器列表请求包（客户端 -> 服务端）。
 * 无需携带参数：服务端以发送者 UUID 查询其传送器列表。
 */
public record RequestTeleporterListPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestTeleporterListPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_teleporter_list"));

    public static final StreamCodec<ByteBuf, RequestTeleporterListPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestTeleporterListPacket());

    public void handle(ServerPlayer player) {
        var list = TeleporterService.getList(player.getServer(), player.getUUID());
        Platform.sendToPlayer(player, new TeleporterListPacket(list));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
