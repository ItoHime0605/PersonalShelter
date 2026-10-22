package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 进出个人维度请求包（客户端 -> 服务端）。
 * 无需携带参数：服务端根据玩家当前所处维度类别（个人维度外 / 自己的 / 他人的）自行决定动作。
 * 由控制器底部的"进入/退出"按钮触发，仅作用于发送者本人，无越权风险。
 */
public record PersonalDimensionTravelPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PersonalDimensionTravelPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "travel"));

    public static final StreamCodec<ByteBuf, PersonalDimensionTravelPacket> STREAM_CODEC =
        StreamCodec.unit(new PersonalDimensionTravelPacket());

    public void handle(ServerPlayer player) {
        PersonalDimensionService.handleTravel(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
