package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.client.ClientLandRadioData;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 登出世界後清除土地電台狀態，避免重連另一伺服器沿用舊頻道。 */
public final class ClientRadioLifecycle {
    private static boolean wasInWorld;

    private ClientRadioLifecycle() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.player != null && mc.level != null;
        if (wasInWorld && !inWorld) {
            ClientCurrentLandRadio.clear();
            ClientLandRadioData.clear();
        }
        wasInWorld = inWorld;
    }
}
