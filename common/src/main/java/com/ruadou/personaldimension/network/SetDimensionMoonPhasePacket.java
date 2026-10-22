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
 * 设置个人维度月相包（客户端 -> 服务端）。
 * 由控制器"月相控制"按钮触发，强制锁定玩家自己个人维度的月相到指定值（关闭月相循环）。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param moonPhase 目标月相：0..7
 */
public record SetDimensionMoonPhasePacket(int moonPhase) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDimensionMoonPhasePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_moon_phase"));

    public static final StreamCodec<ByteBuf, SetDimensionMoonPhasePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetDimensionMoonPhasePacket::moonPhase,
            SetDimensionMoonPhasePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setMoonPhase(this.moonPhase);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
