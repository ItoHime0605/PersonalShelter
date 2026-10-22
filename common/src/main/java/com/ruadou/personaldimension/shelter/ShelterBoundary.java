package com.ruadou.personaldimension.shelter;

/**
 * Exact square shelter boundary, centred at block (0,0).
 * All methods are dependency-free so they can be tested without starting Minecraft.
 * Server event handlers must use these checks, never client-side checks alone.
 */
public final class ShelterBoundary {
    private ShelterBoundary() {}

    /** Block coordinates, inclusive at -half and +half. */
    public static boolean containsBlock(int level, int blockX, int blockZ) {
        return ShelterUpgradeRules.inside(level, blockX, blockZ);
    }

    /** Exact entity position, so flight and movement cannot cross the edge by fractions. */
    public static boolean containsPosition(int level, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
        int half = ShelterUpgradeRules.size(level) / 2;
        return x >= -half && x < half + 1.0 && z >= -half && z < half + 1.0;
    }

    /** Return a safe in-bounds X/Z coordinate, inset half a block from the edge. */
    public static double clampCoordinate(int level, double coordinate) {
        int half = ShelterUpgradeRules.size(level) / 2;
        if (!Double.isFinite(coordinate)) return 0.5;
        return Math.max(-half + 0.5, Math.min(half + 0.5, coordinate));
    }

    /** Destination check for pearls, commands, teleporters, and saved entry points. */
    public static boolean canTeleportTo(int level, double x, double z) {
        return containsPosition(level, x, z);
    }

    /** Used for block placement, breaking, fluid flow, piston target, etc. */
    public static boolean canModifyBlock(int level, int x, int z) {
        return containsBlock(level, x, z);
    }
}
