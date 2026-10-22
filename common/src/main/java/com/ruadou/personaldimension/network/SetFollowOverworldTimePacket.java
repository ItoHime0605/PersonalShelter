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
 * 设置个人维度时间跟随主世界开关包（客户端 -> 服务端）。
 * 由控制器"跟随主世界"开关触发，开启后本维度时刻/昼夜直接读取主世界实时值，
 * 优先级高于昼夜循环与手动设置。仅能影响发送者本人的维度（按 UUID 定位）。
 *
 * @param enabled true 时间跟随主世界，false 恢复独立时间
 */
public record SetFollowOverworldTimePacket(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFollowOverworldTimePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "set_follow_overworld_time"));

    public static final StreamCodec<ByteBuf, SetFollowOverworldTimePacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SetFollowOverworldTimePacket::enabled,
            SetFollowOverworldTimePacket::new);

    public void handle(ServerPlayer player) {
        PersonalDimensionLevelData data = PersonalDimensionService.getPersonalLevelData(player);
        if (data == null) {
            return;
        }
        data.setFollowOverworldTime(this.enabled);
        // 立即向本维度玩家推送最新时刻，避免等待下一次周期性同步
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
