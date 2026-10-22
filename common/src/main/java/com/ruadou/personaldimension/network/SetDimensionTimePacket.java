package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


/**
 * 设置个人维度时刻包（客户端 -> 服务端）。
 * 由控制器"设置时刻"控件触发，强制设置玩家自己个人维度的当前时刻（无视昼夜循环开关）。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param dayTime 目标时刻（会归一化到 0..23999 一天之内）
 */
public record SetDimensionTimePacket(long dayTime) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDimensionTimePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_time"));

    public static final StreamCodec<ByteBuf, SetDimensionTimePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SetDimensionTimePacket::dayTime,
            SetDimensionTimePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        long normalized = ((this.dayTime % 24000L) + 24000L) % 24000L;
        data.forceSetDayTime(normalized);
        // 立即向维度内玩家广播新时刻（人不在维度内时，控制器仍会通过信息包刷新）
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.forceTimeSynchronization();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
