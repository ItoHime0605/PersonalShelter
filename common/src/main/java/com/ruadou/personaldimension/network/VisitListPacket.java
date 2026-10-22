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
 * 可访问维度列表包（服务端 -&gt; 客户端）。
 * 访问界面据此展示"我被授予进入权限、且维度已存在"的他人个人维度（主人 UUID + 显示名）。
 */
public record VisitListPacket(List<NamedPlayer> owners) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VisitListPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "visit_list"));

    public static final StreamCodec<ByteBuf, VisitListPacket> STREAM_CODEC =
        StreamCodec.composite(
            NamedPlayer.LIST_STREAM_CODEC, VisitListPacket::owners,
            VisitListPacket::new);

    public void handle() {
        ClientFriendData.updateVisitList(this.owners);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
