package com.ruadou.personaldimension.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * 每位玩家自行決定是否收聽所在土地的電台。
 * 此設定僅存在本機，不會關閉地主的電台，也不會影響其他訪客。
 */
public final class ClientRadioListening {
    private static boolean loaded;
    private static boolean listening = true;

    private ClientRadioListening() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/personalshelter-radio-listening.txt");
    }

    public static boolean isListening() {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.isRegularFile(file())) {
                    listening = Boolean.parseBoolean(Files.readString(file()).trim());
                }
            } catch (Exception ignored) {
                listening = true;
            }
        }
        return listening;
    }

    public static void setListening(boolean value) {
        listening = value;
        loaded = true;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Boolean.toString(value));
        } catch (Exception ignored) {
            // 唯讀設定檔不妨礙當次遊戲切換。
        }
    }

    /** 播放器必須依此條件判斷，不得直接以地主的 enabled 旗標開始播放。 */
    public static boolean shouldPlay() {
        return isListening() && ClientCurrentLandRadio.enabled()
                && !ClientCurrentLandRadio.streamUrl().isBlank()
                && ClientRadioVolume.get() > 0;
    }
}
