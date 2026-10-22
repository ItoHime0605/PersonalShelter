package com.ruadou.personaldimension.network;

import java.util.UUID;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 访问他人个人维度请求包（客户端 -&gt; 服务端）。
 * 由访问界面点击某条目触发，服务端 {@link PersonalDimensionService#handleVisit} 复检 ENTER 权限后放行，
 * 主人无需在线。仅携带目标主人 UUID，权限判定完全由服务端裁决，无越权风险。
 *
 * @param targetOwner 目标维度主人的 UUID
 */
public record RequestVisitPacket(UUID targetOwner) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestVisitPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_visit"));

    public static final StreamCodec<ByteBuf, RequestVisitPacket> STREAM_CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RequestVisitPacket::targetOwner,
            RequestVisitPacket::new);

    public void handle(ServerPlayer player) {
        com.ruadou.personaldimension.dimension.ShelterTravelCountdown.begin(player,
            actor -> PersonalDimensionService.handleVisit(actor, this.targetOwner));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
