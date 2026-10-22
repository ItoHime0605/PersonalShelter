package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.friend.FriendService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



/**
 * 在线玩家列表请求包（客户端 -&gt; 服务端）。
 * 好友管理界面"从在线玩家添加"时请求，服务端以 {@link OnlinePlayersPacket} 回传（已排除请求者自己）。
 */
public record RequestOnlinePlayersPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestOnlinePlayersPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_online_players"));

    public static final StreamCodec<ByteBuf, RequestOnlinePlayersPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestOnlinePlayersPacket());

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Platform.sendToPlayer(player, new OnlinePlayersPacket(FriendService.onlinePlayers(server, player.getUUID())));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
