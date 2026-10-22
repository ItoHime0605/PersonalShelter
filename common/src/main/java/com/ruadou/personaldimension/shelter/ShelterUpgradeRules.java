package com.ruadou.personaldimension.shelter;

/** Server-authoritative level rules; levels must never be skipped. */
public final class ShelterUpgradeRules {
    private static final int[] SIZES = {47, 79, 111, 143, 175};
    private ShelterUpgradeRules() {}
    public static int size(int level) {
        if (level < 0 || level >= SIZES.length) throw new IllegalArgumentException("level");
        return SIZES[level];
    }
    public static boolean canUpgrade(int currentLevel, int permitLevel) {
        return currentLevel >= 0 && currentLevel < 4 && permitLevel == currentLevel + 1;
    }
    public static boolean inside(int level, int x, int z) {
        int size = size(level);
        return x >= -size / 2 && x <= size / 2 && z >= -size / 2 && z <= size / 2;
    }
    public static boolean newRing(int fromLevel, int x, int z) {
        if (fromLevel < 0 || fromLevel >= 4) return false;
        return inside(fromLevel + 1, x, z) && !inside(fromLevel, x, z);
    }
}
