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
 * 设置个人维度月相循环开关包（客户端 -> 服务端）。
 * 由控制器"月相循环"开关触发，控制玩家自己个人维度的月相是否随 dayTime 自然变化。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param enabled true 恢复月相循环（按 dayTime 自然变化），false 锁定到当前月相
 */
public record SetDimensionMoonPhaseCyclePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDimensionMoonPhaseCyclePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_moon_phase_cycle"));

    public static final StreamCodec<ByteBuf, SetDimensionMoonPhaseCyclePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetDimensionMoonPhaseCyclePacket::enabled,
            SetDimensionMoonPhaseCyclePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setMoonPhaseCycle(this.enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
