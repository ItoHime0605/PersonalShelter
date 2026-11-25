package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.neoforge.network.ShelterMoonSyncPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ShelterMoonLifecycle {
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ShelterMoonSyncPacket.clear();
    }
}
