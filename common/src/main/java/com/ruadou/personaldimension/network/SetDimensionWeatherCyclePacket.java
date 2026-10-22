package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


/**
 * 设置个人维度天气循环开关包（客户端 -> 服务端）。
 * 由控制器"天气循环"开关触发，控制玩家自己个人维度的天气是否自动推进。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param enabled true 恢复天气循环，false 冻结当前天气
 */
public record SetDimensionWeatherCyclePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDimensionWeatherCyclePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_weather_cycle"));

    public static final StreamCodec<ByteBuf, SetDimensionWeatherCyclePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetDimensionWeatherCyclePacket::enabled,
            SetDimensionWeatherCyclePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setWeatherCycle(this.enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
