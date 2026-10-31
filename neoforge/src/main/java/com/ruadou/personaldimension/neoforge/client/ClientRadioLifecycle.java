package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.client.ClientLandRadioData;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 登出世界後清除土地電台狀態，避免重連另一伺服器沿用舊頻道。 */
public final class ClientRadioLifecycle {
    private static boolean wasInWorld;
    private static boolean wasRadioPlaying;

    private ClientRadioLifecycle() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.player != null && mc.level != null;
        if (wasInWorld && !inWorld) {
            ClientCurrentLandRadio.clear();
            ClientLandRadioData.clear();
            StandaloneRadioPlayer.stop();
            // Flush the last slider position once on disconnect, not on every drag.
            ClientRadioVolume.flush();
        }
        wasInWorld = inWorld;
        StandaloneRadioPlayer.tick();
        // Stop the current vanilla track only once when the radio starts.
        // RadioMusicManagerMixin prevents new background tracks from being
        // scheduled during playback, without muting other sound categories.
        boolean radioPlaying = inWorld && ClientRadioVolume.get() > 0
                && StandaloneRadioPlayer.isActive();
        if (radioPlaying && !wasRadioPlaying) {
            mc.getMusicManager().stopPlaying();
        }
        wasRadioPlaying = radioPlaying;
    }
}
