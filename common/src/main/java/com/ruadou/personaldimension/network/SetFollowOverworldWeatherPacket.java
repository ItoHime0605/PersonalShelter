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
 * 设置个人维度天气跟随主世界开关包（客户端 -> 服务端）。
 * 由控制器"跟随主世界"开关触发，开启后本维度天气直接读取主世界实时值，
 * 优先级高于天气循环与手动设置。仅能影响发送者本人的维度（按 UUID 定位）。
 *
 * <p>天气变化的客户端渲染由 {@code ServerLevel.advanceWeatherCycle()} 每刻依据
 * {@code isRaining()} 等自动插值并广播，故切换后无需额外广播即可生效。
 *
 * @param enabled true 天气跟随主世界，false 恢复独立天气
 */
public record SetFollowOverworldWeatherPacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFollowOverworldWeatherPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_follow_overworld_weather"));

    public static final StreamCodec<ByteBuf, SetFollowOverworldWeatherPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetFollowOverworldWeatherPacket::enabled,
            SetFollowOverworldWeatherPacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setFollowOverworldWeather(this.enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
