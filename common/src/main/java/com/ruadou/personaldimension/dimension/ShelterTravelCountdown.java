package com.ruadou.personaldimension.dimension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.function.Consumer;

/** Five-second server-side travel countdown; all movement and damage checks are authoritative. */
public final class ShelterTravelCountdown {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private ShelterTravelCountdown() {}

    private record Pending(ResourceKey<Level> dimension, double x, double y, double z,
                           float health, int ticks, ServerBossEvent bar, Consumer<ServerPlayer> action) {}

    public static void begin(ServerPlayer player, Consumer<ServerPlayer> action) {
        if (PENDING.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("傳送倒數已經開始。"), true);
            return;
        }
        ServerBossEvent bar = new ServerBossEvent(Component.literal("傳送倒數：5 秒"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(1.0F);
        bar.addPlayer(player);
        PENDING.put(player.getUUID(), new Pending(player.level().dimension(), player.getX(), player.getY(),
            player.getZ(), player.getHealth(), 100, bar, action));
    }

    public static void tick(ServerPlayer player) {
        Pending p = PENDING.get(player.getUUID());
        if (p == null) return;
        if (!player.isAlive() || player.hurtTime > 0 || !player.level().dimension().equals(p.dimension)
            || player.distanceToSqr(p.x, p.y, p.z) > 0.015625D || player.getHealth() < p.health) {
            cancel(player);
            player.displayClientMessage(Component.literal("移動或受到傷害，傳送已取消。"), true);
            return;
        }
        int remaining = p.ticks - 1;
        if (remaining <= 0) {
            PENDING.remove(player.getUUID());
            p.bar.removePlayer(player);
            p.action.accept(player);
            return;
        }
        p.bar.setProgress(remaining / 100.0F);
        p.bar.setName(Component.literal("傳送倒數：" + ((remaining + 19) / 20) + " 秒"));
        PENDING.put(player.getUUID(), new Pending(p.dimension, p.x, p.y, p.z, p.health, remaining, p.bar, p.action));
    }

    public static void cancel(ServerPlayer player) {
        Pending p = PENDING.remove(player.getUUID());
        if (p != null) p.bar.removePlayer(player);
    }
}
