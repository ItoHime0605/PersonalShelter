package com.ruadou.personaldimension.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

/**
 * 本機玩家獨立音量，0～100%，每次可調整 1%。
 * 拖曳滑桿立即調整音量，但延後合併寫入設定檔，避免每個滑鼠事件阻塞遊戲畫面。
 */
public final class ClientRadioVolume {
    private static volatile int volume = 50;
    private static volatile boolean loaded;
    private static final Object SAVE_LOCK = new Object();
    private static final ScheduledThreadPoolExecutor WRITER =
            new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "PersonalShelter-Radio-Volume-Save");
                thread.setDaemon(true);
                return thread;
            });
    private static ScheduledFuture<?> pendingSave;
    private static long saveGeneration;

    static {
        // Cancelled drag updates should not accumulate in the executor queue.
        WRITER.setRemoveOnCancelPolicy(true);
        // Persist the final slider value even when the game exits within the debounce window.
        Runtime.getRuntime().addShutdownHook(
                new Thread(ClientRadioVolume::flush, "PersonalShelter-Radio-Volume-Exit"));
    }

    private ClientRadioVolume() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/personalshelter-radio-volume.txt");
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
        int normalized = Math.clamp(value, 0, 100);
        if (loaded && volume == normalized) return;
        volume = normalized;
        loaded = true;
        // Resolve the game directory on the client thread, not the I/O worker.
        Path target = file();
        synchronized (SAVE_LOCK) {
            if (pendingSave != null) pendingSave.cancel(false);
            long generation = ++saveGeneration;
            pendingSave = WRITER.schedule(() -> {
                synchronized (SAVE_LOCK) {
                    if (generation != saveGeneration) return;
                    write(target, normalized);
                    pendingSave = null;
                }
            }, 400, TimeUnit.MILLISECONDS);
        }
    }

    /** Flush a pending slider change when leaving the world or closing the game. */
    public static void flush() {
        synchronized (SAVE_LOCK) {
            if (pendingSave == null) return;
            pendingSave.cancel(false);
            pendingSave = null;
            ++saveGeneration;
            write(file(), volume);
        }
    }

    private static void write(Path target, int value) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, Integer.toString(value));
        } catch (Exception ignored) {
            // 設定檔唯讀時仍可在本次遊戲內調整音量。
        }
    }
}
