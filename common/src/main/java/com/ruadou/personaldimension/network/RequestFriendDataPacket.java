package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



/**
 * 好友配置请求包（客户端 -&gt; 服务端）。
 * 好友管理 / 权限组界面打开或改动后请求刷新，服务端以 {@link FriendDataPacket} 回传发送者本人的配置快照。
 */
public record RequestFriendDataPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFriendDataPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_friend_data"));

    public static final StreamCodec<ByteBuf, RequestFriendDataPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestFriendDataPacket());

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Platform.sendToPlayer(player, FriendDataPacket.of(server, player.getUUID()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
