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
 * 设置个人维度天气包（客户端 -> 服务端）。
 * 由控制器"天气控制"按钮触发，强制设置玩家自己个人维度的天气（无视天气循环开关）。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param state 目标天气：{@link #CLEAR}=晴，{@link #RAIN}=雨，{@link #THUNDER}=雷雨
 */
public record SetDimensionWeatherPacket(int state) implements CustomPacketPayload {

    public static final int CLEAR = 0;
    public static final int RAIN = 1;
    public static final int THUNDER = 2;

    public static final CustomPacketPayload.Type<SetDimensionWeatherPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_weather"));

    public static final StreamCodec<ByteBuf, SetDimensionWeatherPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetDimensionWeatherPacket::state,
            SetDimensionWeatherPacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        // 编号到天气参数的映射集中在 PersonalDimensionLevelData，避免与维度接口方块重复
        data.setWeatherState(this.state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
