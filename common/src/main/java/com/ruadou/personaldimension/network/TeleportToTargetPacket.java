package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.TeleporterService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 传送到目标传送器请求包（客户端 -> 服务端）。
 * 携带传送器在玩家列表中的索引，服务端校验后执行传送。
 */
public record TeleportToTargetPacket(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeleportToTargetPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "teleport_to_target"));

    public static final StreamCodec<ByteBuf, TeleportToTargetPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, TeleportToTargetPacket::index,
            TeleportToTargetPacket::new);

    public void handle(ServerPlayer player) {
        TeleporterService.teleportTo(player, this.index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
