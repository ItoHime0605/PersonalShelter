package com.ruadou.personaldimension.fabric.network;

import java.util.Set;

import com.ruadou.personaldimension.network.NetworkPackets;
import com.ruadou.personaldimension.network.UpdateDimensionsPacket;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ModNetworking {

    private ModNetworking() {}

    @SuppressWarnings("unchecked")
    public static void registerPayloads() {
        for (NetworkPackets.PacketEntry entry : NetworkPackets.S2C_PACKETS) {
            PayloadTypeRegistry.playS2C().register(entry.type(), entry.codec());
        }
        for (NetworkPackets.PacketEntry entry : NetworkPackets.C2S_PACKETS) {
            PayloadTypeRegistry.playC2S().register(entry.type(), entry.codec());
        }
    }

    @SuppressWarnings("unchecked")
    public static void registerServerReceivers() {
        for (NetworkPackets.PacketEntry entry : NetworkPackets.C2S_PACKETS) {
            ServerPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> {
                    context.player().server.execute(() -> 
                        NetworkPackets.handleC2SPacket(payload, context.player()));
                });
        }
    }

    @SuppressWarnings("unchecked")
    public static void registerClientReceivers() {
        for (NetworkPackets.PacketEntry entry : NetworkPackets.S2C_PACKETS) {
            ClientPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> {
                    context.client().execute(() -> NetworkPackets.handleS2CPacket(payload));
                });
        }
    }

    public static void sendDimensionUpdateToAll(MinecraftServer server, ResourceKey<Level> levelKey) {
        UpdateDimensionsPacket packet = new UpdateDimensionsPacket(Set.of(levelKey), true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, UpdateDimensionsPacket.TYPE)) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public static void sendDimensionRemoveToAll(MinecraftServer server, ResourceKey<Level> levelKey) {
        UpdateDimensionsPacket packet = new UpdateDimensionsPacket(Set.of(levelKey), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, UpdateDimensionsPacket.TYPE)) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }
}
