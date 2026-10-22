package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.FriendVisitorTeleporterData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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


/**
 * 取消其他传送器的权限组绑定（客户端 -> 服务端）。
 * 用于将已被其他传送器绑定的权限组解绑，然后可以绑定到当前传送器。
 */
public record UnbindOtherTeleporterPacket(BlockPos pos, String groupName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnbindOtherTeleporterPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "unbind_other_teleporter"));

    public static final StreamCodec<ByteBuf, UnbindOtherTeleporterPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, UnbindOtherTeleporterPacket::pos,
            ByteBufCodecs.STRING_UTF8, UnbindOtherTeleporterPacket::groupName,
            UnbindOtherTeleporterPacket::new);

    public void handle(ServerPlayer player) {
        // 身份校验：玩家必须在自己拥有的个人维度内操作（防访客越权解绑他人配置）
        Optional<UUID> dimOwner = Platform.ownerOf(player.level().dimension());
        if (dimOwner.isEmpty() || !dimOwner.get().equals(player.getUUID())) {
            return;
        }
        if (!player.canInteractWithBlock(this.pos, 64.0)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(this.pos) instanceof FriendVisitorTeleporterBlockEntity be)) {
            return;
        }
        UUID owner = be.getOwner();
        // 二次校验：方块 owner 必须与当前玩家一致
        if (owner == null || !owner.equals(player.getUUID())) {
            return;
        }

        FriendVisitorTeleporterData data = FriendVisitorTeleporterData.get(player.server);
        FriendVisitorTeleporterData.TeleporterEntry target = null;
        for (FriendVisitorTeleporterData.TeleporterEntry entry : data.get(owner)) {
            if (entry.groups().contains(groupName)) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            return;
        }
        List<String> newGroups = new ArrayList<>(target.groups());
        newGroups.remove(groupName);
        data.update(owner, target.dimension(), target.pos(), newGroups, target.controllerOnly());

        if (level.getBlockEntity(target.pos()) instanceof FriendVisitorTeleporterBlockEntity otherBe) {
            otherBe.setBoundGroups(newGroups);
            BlockState state = level.getBlockState(target.pos());
            level.sendBlockUpdated(target.pos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
