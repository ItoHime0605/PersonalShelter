package com.ruadou.personaldimension.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterData;
import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;


public record SetFriendVisitorTeleporterPacket(BlockPos pos, List<String> groups, boolean controllerOnly)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFriendVisitorTeleporterPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_friend_visitor_teleporter"));

    public static final StreamCodec<ByteBuf, SetFriendVisitorTeleporterPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetFriendVisitorTeleporterPacket::pos,
            // 限制：最多 20 个权限组名，每个 50 字符，防恶意客户端 OOM
            ByteBufCodecs.stringUtf8(50).apply(ByteBufCodecs.list(20)), SetFriendVisitorTeleporterPacket::groups,
            ByteBufCodecs.BOOL, SetFriendVisitorTeleporterPacket::controllerOnly,
            SetFriendVisitorTeleporterPacket::new);

    public void handle(ServerPlayer player) {
        // 身份校验：玩家必须在自己拥有的个人维度内操作（防访客越权修改主人配置）
        Optional<UUID> dimOwner = Platform.ownerOf(player.level().dimension());
        if (dimOwner.isEmpty() || !dimOwner.get().equals(player.getUUID())) {
            return;
        }
        if (!player.canInteractWithBlock(this.pos, 64.0)) {
            return;
        }
        if (player.level().getBlockEntity(this.pos) instanceof FriendVisitorTeleporterBlockEntity be) {
            // 二次校验：方块 owner 必须与当前玩家一致（防方块被替换/伪造）
            UUID beOwner = be.getOwner();
            if (beOwner == null || !beOwner.equals(player.getUUID())) {
                return;
            }
            be.setBoundGroups(this.groups);
            be.setControllerOnly(this.controllerOnly);
            FriendVisitorTeleporterData.get(player.server)
                .update(be.getOwner(), player.level().dimension(), this.pos, this.groups, this.controllerOnly);
            BlockState state = player.level().getBlockState(this.pos);
            ((ServerLevel) player.level()).sendBlockUpdated(this.pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}