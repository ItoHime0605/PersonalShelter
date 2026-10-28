package com.ruadou.personaldimension.neoforge.dimension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.network.CurrentLandRadioPacket;
import com.ruadou.personaldimension.platform.Platform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 將玩家當前所在私人土地的電台狀態同步至客戶端。
 * 與控制器的「自己的土地設定」查詢分開處理，訪客也會收到地主的電台狀態。
 */
public final class LandRadioSync {
    private static final Map<UUID, Snapshot> LAST_SENT = new ConcurrentHashMap<>();

    private LandRadioSync() {}

    public static void tick(ServerPlayer player) {
        // 維度切換時立即同步；同維度每秒檢查一次設定變更。
        Snapshot previous = LAST_SENT.get(player.getUUID());
        String dimension = player.level().dimension().location().toString();
        if (previous != null && previous.dimension.equals(dimension) && player.tickCount % 20 != 0) return;
        Snapshot next = current(player);
        previous = LAST_SENT.put(player.getUUID(), next);
        if (!next.equals(previous)) {
            Platform.sendToPlayer(player, new CurrentLandRadioPacket(next.enabled, next.stationId));
        }
    }

    private static Snapshot current(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || Platform.ownerOf(level.dimension()).isEmpty()) {
            return new Snapshot("", false, "");
        }
        var data = level.getDataStorage().computeIfAbsent(
                PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        return new Snapshot(level.dimension().location().toString(),
                data.isRadioEnabled(), data.getRadioSelectedStationId());
    }

    public static void logout(ServerPlayer player) {
        LAST_SENT.remove(player.getUUID());
    }

    private record Snapshot(String dimension, boolean enabled, String stationId) {}
}
