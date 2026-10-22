package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.client.ClientDimensionInfo;
import com.ruadou.personaldimension.client.DimensionInfoSnapshot;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


/**
 * 个人维度信息响应包（服务端 -> 客户端）。
 * 携带玩家<b>自己的个人维度</b>的权威快照，供控制器界面展示（无论玩家当前身处何处）。
 *
 * <p>字段超过 {@code StreamCodec.composite} 支持的 6 组上限，故手写编解码器。
 *
 * @param created                   个人维度是否已创建（未创建时其余字段无意义）
 * @param raining                   是否下雨
 * @param thundering                是否雷暴
 * @param rainTime                  距离下次雨状态切换的剩余 tick
 * @param thunderTime               距离下次雷暴状态切换的剩余 tick
 * @param clearWeatherTime          强制晴天的剩余 tick（{@code /weather clear} 设置，>0 时优先）
 * @param dayTime                   个人维度当前时刻（独立于主世界）
 * @param daylightCycle             个人维度昼夜循环是否开启
 * @param weatherCycle              个人维度天气循环是否开启（false 表示当前天气已冻结）
 * @param followOverworldTime       个人维度时间是否跟随主世界（true 时时刻/昼夜与主世界一致）
 * @param followOverworldWeather    个人维度天气是否跟随主世界（true 时天气与主世界一致）
 * @param moonPhaseOverride         个人维度月相锁定值（-1 表示未锁定 / 跟随 dayTime 循环）
 * @param moonPhaseCycle            个人维度月相循环是否开启（true 时按 dayTime 自然变化）
 * @param followOverworldMoonPhase  个人维度月相是否跟随主世界（true 时回退原版计算）
 * @param moonPhase                 当前月相值（0..7，跟随主世界时为主世界月相，供信息页直接展示）
 */
public record DimensionInfoPacket(boolean created,
                                  boolean raining, boolean thundering,
                                  int rainTime, int thunderTime, int clearWeatherTime,
                                  long dayTime, boolean daylightCycle, boolean weatherCycle,
                                  boolean followOverworldTime, boolean followOverworldWeather,
                                  int moonPhaseOverride, boolean moonPhaseCycle, boolean followOverworldMoonPhase,
                                  int moonPhase, int shelterLevel)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DimensionInfoPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "dimension_info"));

    public static final StreamCodec<ByteBuf, DimensionInfoPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public DimensionInfoPacket decode(ByteBuf buf) {
                boolean created = buf.readBoolean();
                boolean raining = buf.readBoolean();
                boolean thundering = buf.readBoolean();
                int rainTime = buf.readInt();
                int thunderTime = buf.readInt();
                int clearWeatherTime = buf.readInt();
                long dayTime = buf.readLong();
                boolean daylightCycle = buf.readBoolean();
                boolean weatherCycle = buf.readBoolean();
                boolean followOverworldTime = buf.readBoolean();
                boolean followOverworldWeather = buf.readBoolean();
                int moonPhaseOverride = buf.readInt();
                boolean moonPhaseCycle = buf.readBoolean();
                boolean followOverworldMoonPhase = buf.readBoolean();
                int moonPhase = buf.readInt();
                int shelterLevel = buf.readInt();
                return new DimensionInfoPacket(created, raining, thundering,
                    rainTime, thunderTime, clearWeatherTime, dayTime, daylightCycle, weatherCycle,
                    followOverworldTime, followOverworldWeather,
                    moonPhaseOverride, moonPhaseCycle, followOverworldMoonPhase, moonPhase, shelterLevel);
            }

            @Override
            public void encode(ByteBuf buf, DimensionInfoPacket packet) {
                buf.writeBoolean(packet.created);
                buf.writeBoolean(packet.raining);
                buf.writeBoolean(packet.thundering);
                buf.writeInt(packet.rainTime);
                buf.writeInt(packet.thunderTime);
                buf.writeInt(packet.clearWeatherTime);
                buf.writeLong(packet.dayTime);
                buf.writeBoolean(packet.daylightCycle);
                buf.writeBoolean(packet.weatherCycle);
                buf.writeBoolean(packet.followOverworldTime);
                buf.writeBoolean(packet.followOverworldWeather);
                buf.writeInt(packet.moonPhaseOverride);
                buf.writeBoolean(packet.moonPhaseCycle);
                buf.writeBoolean(packet.followOverworldMoonPhase);
                buf.writeInt(packet.moonPhase);
                buf.writeInt(packet.shelterLevel);
            }
        };

    /** 个人维度尚未创建时的占位快照（平台无关，供客户端缓存直接写入）。 */
    public static DimensionInfoSnapshot uncreatedSnapshot() {
        return DimensionInfoSnapshot.uncreated();
    }

    public void handle() {
        // 仅客户端接收；Packet 转 Snapshot 后写入 common 缓存，避免平台端持有客户端专用类
        ClientDimensionInfo.update(new DimensionInfoSnapshot(created, raining, thundering,
            rainTime, thunderTime, clearWeatherTime,
            dayTime, daylightCycle, weatherCycle,
            followOverworldTime, followOverworldWeather,
            moonPhaseOverride, moonPhaseCycle, followOverworldMoonPhase, moonPhase, shelterLevel));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
