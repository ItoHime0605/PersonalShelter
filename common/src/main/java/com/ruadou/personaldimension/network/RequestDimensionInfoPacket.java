package com.ruadou.personaldimension.network;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.platform.Platform;
import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;



/**
 * 个人维度信息请求包（客户端 -> 服务端）。
 * 控制器界面定期请求玩家<b>自己的个人维度</b>的权威快照，
 * 服务端读取后通过 {@link DimensionInfoPacket} 回传（这些计时/时刻字段不随普通客户端同步）。
 */
public record RequestDimensionInfoPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDimensionInfoPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "request_dimension_info"));

    // 无字段，单例编解码即可
    public static final StreamCodec<ByteBuf, RequestDimensionInfoPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestDimensionInfoPacket());

    public void handle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> key = Platform.levelKeyFor(player.getUUID());
        ServerLevel personal = server.getLevel(key);
        if (personal == null || !(personal.getLevelData() instanceof ServerLevelData data)) {
            Platform.sendToPlayer(player, new DimensionInfoPacket(
                false, false, false, 0, 0, 0, 0L, true, true, false, false, -1, true, false, 0, 0, false));
            return;
        }
        boolean daylight = true;
        boolean weatherCycle = true;
        boolean followTime = false;
        boolean followWeather = false;
        int moonPhaseOverride = -1;
        boolean moonPhaseCycle = true;
        boolean followMoonPhase = false;
        int moonPhase = personal.getMoonPhase();
        if (personal.getLevelData() instanceof PersonalDimensionLevelData pd) {
            daylight = pd.isDaylightCycle();
            weatherCycle = pd.isWeatherCycle();
            followTime = pd.isFollowOverworldTime();
            followWeather = pd.isFollowOverworldWeather();
            moonPhaseOverride = pd.getMoonPhaseOverride();
            moonPhaseCycle = pd.isMoonPhaseCycle();
            followMoonPhase = pd.isFollowOverworldMoonPhase();
            // 跟随主世界时读主世界（Mixin 不注入主世界，走原版计算），否则取 effective
            moonPhase = followMoonPhase
                ? server.overworld().getMoonPhase()
                : Math.max(0, pd.getEffectiveMoonPhase());
        }
        DimensionInfoPacket response = new DimensionInfoPacket(
            true,
            data.isRaining(), data.isThundering(),
            data.getRainTime(), data.getThunderTime(), data.getClearWeatherTime(),
            personal.getDayTime(), daylight, weatherCycle,
            followTime, followWeather,
            moonPhaseOverride, moonPhaseCycle, followMoonPhase, moonPhase,
            java.util.Optional.ofNullable(personal.getDataStorage().get(com.ruadou.personaldimension.dimension.PersonalDimensionData.FACTORY,
                com.ruadou.personaldimension.dimension.PersonalDimensionData.ID))
                .map(com.ruadou.personaldimension.dimension.PersonalDimensionData::getShelterLevel).orElse(0),
            java.util.Optional.ofNullable(personal.getDataStorage().get(com.ruadou.personaldimension.dimension.PersonalDimensionData.FACTORY,
                com.ruadou.personaldimension.dimension.PersonalDimensionData.ID))
                .map(com.ruadou.personaldimension.dimension.PersonalDimensionData::isShelterEntryLocked).orElse(false));
        Platform.sendToPlayer(player, response);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
