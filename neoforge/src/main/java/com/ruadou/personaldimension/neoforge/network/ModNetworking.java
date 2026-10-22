package com.ruadou.personaldimension.neoforge.network;

import java.util.Set;

import com.ruadou.personaldimension.network.NetworkPackets;
import com.ruadou.personaldimension.network.UpdateDimensionsPacket;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@SuppressWarnings("null")
public final class ModNetworking {

    private ModNetworking() {}

    @SuppressWarnings("unchecked")
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        for (NetworkPackets.PacketEntry entry : NetworkPackets.S2C_PACKETS) {
            registrar.playToClient(entry.type(), entry.codec(),
                (payload, context) -> context.enqueueWork(() -> NetworkPackets.handleS2CPacket(payload)));
        }

        for (NetworkPackets.PacketEntry entry : NetworkPackets.C2S_PACKETS) {
            registrar.playToServer(entry.type(), entry.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        NetworkPackets.handleC2SPacket(payload, sp);
                    }
                }));
        }
    }

    public static void sendDimensionUpdateToAll(MinecraftServer server, ResourceKey<Level> levelKey) {
        UpdateDimensionsPacket packet = new UpdateDimensionsPacket(Set.of(levelKey), true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(packet)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void sendDimensionRemoveToAll(MinecraftServer server, ResourceKey<Level> levelKey) {
        UpdateDimensionsPacket packet = new UpdateDimensionsPacket(Set.of(levelKey), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(packet)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
