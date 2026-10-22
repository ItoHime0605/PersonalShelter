package com.ruadou.personaldimension.network;

import java.util.List;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.FriendVisitorTeleporterClientData;
import com.ruadou.personaldimension.client.FriendVisitorTeleporterStatusSnapshot;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 好友访问传送器状态回传（服务端 -> 客户端）。
 * 包含当前维度下所有传送器的权限组绑定情况，供界面区分三种颜色状态。
 *
 * @param boundToCurrent  当前传送器绑定的权限组列表
 * @param boundToOthers   被其他传送器绑定的权限组列表
 * @param controllerOnly  当前传送器是否仅控制器传送
 */
public record FriendVisitorTeleporterStatusPacket(
    List<String> boundToCurrent,
    List<String> boundToOthers,
    boolean controllerOnly
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FriendVisitorTeleporterStatusPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "friend_visitor_teleporter_status"));

    public static final StreamCodec<ByteBuf, FriendVisitorTeleporterStatusPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FriendVisitorTeleporterStatusPacket::boundToCurrent,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FriendVisitorTeleporterStatusPacket::boundToOthers,
            ByteBufCodecs.BOOL, FriendVisitorTeleporterStatusPacket::controllerOnly,
            FriendVisitorTeleporterStatusPacket::new);

    public void handle() {
        // 仅客户端接收；Packet 转 Snapshot 后写入 common 缓存，避免平台端持有客户端专用类
        FriendVisitorTeleporterClientData.update(
            new FriendVisitorTeleporterStatusSnapshot(boundToCurrent, boundToOthers, controllerOnly));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
