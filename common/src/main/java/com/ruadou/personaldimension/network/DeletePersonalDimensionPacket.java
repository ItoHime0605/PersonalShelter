package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 请求删除自己的个人维度（客户端 -> 服务端）。
 *
 * <p>由控制器信息页"删除维度"按钮在 5 秒确认后触发。无参：服务端始终以发送者 UUID 定位
 * 其<b>自己</b>的个人维度，玩家无法删除他人维度，无越权风险。删除编排见
 * {@link PersonalDimensionService#deletePersonalDimension(ServerPlayer)}。
 */
public record DeletePersonalDimensionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeletePersonalDimensionPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "delete_personal_dimension"));

    public static final StreamCodec<ByteBuf, DeletePersonalDimensionPacket> STREAM_CODEC =
        StreamCodec.unit(new DeletePersonalDimensionPacket());

    public void handle(ServerPlayer player) {
        PersonalDimensionService.deletePersonalDimension(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
