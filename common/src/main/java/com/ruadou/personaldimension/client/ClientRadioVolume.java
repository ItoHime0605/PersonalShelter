package com.ruadou.personaldimension.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/** 本機玩家獨立音量，0～100%，每次可調整 1%。不會更動地主的電台設定。 */
public final class ClientRadioVolume {
    private static volatile int volume = 50;
    private static volatile boolean loaded;

    private ClientRadioVolume() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/personalshelter-radio-volume.txt");
    }

    public static int get() {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.isRegularFile(file())) {
                    volume = Math.clamp(Integer.parseInt(Files.readString(file()).trim()), 0, 100);
                }
            } catch (Exception ignored) {
                volume = 50;
            }
        }
        return volume;
    }

    public static void set(int value) {
        volume = Math.clamp(value, 0, 100);
        loaded = true;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Integer.toString(volume));
        } catch (Exception ignored) {
            // 設定檔唯讀時仍可在本次遊戲內調整音量。
        }
    }
}
