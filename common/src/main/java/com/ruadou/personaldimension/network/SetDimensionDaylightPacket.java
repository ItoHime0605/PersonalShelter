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
 * 设置个人维度昼夜循环开关包（客户端 -> 服务端）。
 * 由控制器"昼夜循环"开关触发，控制玩家自己个人维度的时间是否自动推进。
 * 仅能影响发送者本人的维度（按 UUID 定位），不存在越权风险。
 *
 * @param enabled true 恢复昼夜循环，false 冻结当前时刻
 */
public record SetDimensionDaylightPacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDimensionDaylightPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_dimension_daylight"));

    public static final StreamCodec<ByteBuf, SetDimensionDaylightPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetDimensionDaylightPacket::enabled,
            SetDimensionDaylightPacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setDaylightCycle(this.enabled);
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
