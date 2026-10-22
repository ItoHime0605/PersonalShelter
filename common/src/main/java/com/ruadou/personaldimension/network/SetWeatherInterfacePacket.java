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
 * 配置维度天气接口方块（客户端 -&gt; 服务端）。
 * 由天气接口界面选择天气触发，携带方块坐标与目标天气编号。服务端
 * {@link DimensionInterfaceService#configureWeather} 复检维度归属、方块存在与交互距离后写入方块实体，
 * 权限完全由服务端裁决，无越权风险。
 *
 * @param pos   天气接口方块坐标
 * @param state 目标天气：0=晴、1=雨、2=雷雨
 */
public record SetWeatherInterfacePacket(BlockPos pos, int state) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetWeatherInterfacePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_weather_interface"));

    public static final StreamCodec<ByteBuf, SetWeatherInterfacePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetWeatherInterfacePacket::pos,
            ByteBufCodecs.VAR_INT, SetWeatherInterfacePacket::state,
            SetWeatherInterfacePacket::new);

    public void handle(ServerPlayer player) {
        DimensionInterfaceService.configureWeather(player, this.pos, this.state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
