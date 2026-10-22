package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;



/**
 * 请求好友访问传送器状态（客户端 -> 服务端）。
 * 服务端回传当前维度下所有传送器的权限组绑定情况。
 */
public record RequestFriendVisitorTeleporterStatusPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFriendVisitorTeleporterStatusPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_friend_visitor_teleporter_status"));

    public static final StreamCodec<ByteBuf, RequestFriendVisitorTeleporterStatusPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestFriendVisitorTeleporterStatusPacket::pos,
            RequestFriendVisitorTeleporterStatusPacket::new);

    public void handle(ServerPlayer player) {
        if (!player.canInteractWithBlock(this.pos, 64.0)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(this.pos) instanceof FriendVisitorTeleporterBlockEntity be)) {
            return;
        }
        UUID owner = be.getOwner();
        if (owner == null) {
            return;
        }

        FriendVisitorTeleporterData data = FriendVisitorTeleporterData.get(player.server);
        List<String> boundToCurrent = new ArrayList<>(be.getBoundGroups());
        Set<String> boundToOthers = new HashSet<>();

        for (FriendVisitorTeleporterData.TeleporterEntry entry : data.get(owner)) {
            if (!entry.dimension().equals(level.dimension()) || !entry.pos().equals(this.pos)) {
                boundToOthers.addAll(entry.groups());
            }
        }
        boundToOthers.removeAll(boundToCurrent);

        FriendVisitorTeleporterStatusPacket resp = new FriendVisitorTeleporterStatusPacket(
            boundToCurrent, new ArrayList<>(boundToOthers), be.isControllerOnly());
        Platform.sendToPlayer(player, resp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
