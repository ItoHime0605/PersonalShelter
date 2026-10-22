package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.friend.NamedPlayer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 在线玩家列表包（服务端 -&gt; 客户端）。
 * 好友管理界面"从在线玩家添加"时展示可选玩家（已排除请求者自己），显示名由服务端解析。
 */
public record OnlinePlayersPacket(List<NamedPlayer> players) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OnlinePlayersPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "online_players"));

    public static final StreamCodec<ByteBuf, OnlinePlayersPacket> STREAM_CODEC =
        StreamCodec.composite(
            NamedPlayer.LIST_STREAM_CODEC, OnlinePlayersPacket::players,
            OnlinePlayersPacket::new);

    public void handle() {
        ClientFriendData.updateOnline(this.players);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
