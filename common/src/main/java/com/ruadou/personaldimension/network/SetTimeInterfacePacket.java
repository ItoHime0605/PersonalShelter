package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.block.DimensionInterfaceService;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 配置维度时间接口方块（客户端 -&gt; 服务端）。
 * 由时间接口界面选择时刻触发，携带方块坐标与目标时刻。服务端
 * {@link DimensionInterfaceService#configureTime} 复检维度归属、方块存在与交互距离后写入方块实体，
 * 权限完全由服务端裁决，无越权风险。
 *
 * @param pos     时间接口方块坐标
 * @param dayTime 目标时刻（会归一化到 0..23999 一天之内）
 */
public record SetTimeInterfacePacket(BlockPos pos, long dayTime) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetTimeInterfacePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_time_interface"));

    public static final StreamCodec<ByteBuf, SetTimeInterfacePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTimeInterfacePacket::pos,
            ByteBufCodecs.VAR_LONG, SetTimeInterfacePacket::dayTime,
            SetTimeInterfacePacket::new);

    public void handle(ServerPlayer player) {
        DimensionInterfaceService.configureTime(player, this.pos, this.dayTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
