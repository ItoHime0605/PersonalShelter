package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.client.ClientCurrentLandRadio;
import com.ruadou.personaldimension.client.ClientRadioListening;
import com.ruadou.personaldimension.client.ClientRadioVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * OpenFM 0.0.1 可選播放橋接。使用已核對的 RadioStreamPlayer 公開方法，
 * 不將 OpenFM 設為強制前置；未安裝時安全略過。
 */
public final class OpenFmRadioBridge {
    private static Object player;
    private static Method start, stop, tick, setVolume;
    private static String playingUrl = "";
    private static boolean initialized;
    private static Constructor<?> constructor;

    private OpenFmRadioBridge() {}

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("openfm")) return;
        try {
            Class<?> type = Class.forName("me.michiyo.openfm.client.audio.RadioStreamPlayer");
            constructor = type.getConstructor(String.class, Vec3.class, int.class);
            start = type.getMethod("start");
            stop = type.getMethod("stop");
            tick = type.getMethod("tick", Vec3.class);
            setVolume = type.getMethod("setVolume", int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            constructor = null;
        }
    }

    public static void clientTick() {
        initialize();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientRadioListening.shouldPlay()
                || constructor == null) {
            stop();
            return;
        }
        String url = ClientCurrentLandRadio.streamUrl();
        try {
            if (player == null || !url.equals(playingUrl)) {
                stop();
                player = constructor.newInstance(url, mc.player.position(), ClientRadioVolume.get());
                playingUrl = url;
                start.invoke(player);
            }
            setVolume.invoke(player, ClientRadioVolume.get());
            tick.invoke(player, mc.player.position());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            stop();
        }
    }

    public static void stop() {
        if (player != null) {
            try { stop.invoke(player); } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        player = null;
        playingUrl = "";
    }
}
