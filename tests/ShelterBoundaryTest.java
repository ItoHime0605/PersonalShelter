import com.ruadou.personaldimension.shelter.ShelterBoundary;
import com.ruadou.personaldimension.shelter.ShelterUpgradeRules;

public final class ShelterBoundaryTest {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("check " + assertions);
    }
    public static void main(String[] args) {
        int[] sizes = {64, 96, 128, 192, 256};
        for (int level = 0; level <= 4; level++) {
            int half = sizes[level] / 2;
            check(ShelterUpgradeRules.size(level) == sizes[level]);
            check(ShelterBoundary.containsBlock(level, -half, -half));
            check(ShelterBoundary.containsBlock(level, half - 1, half - 1));
            check(!ShelterBoundary.containsBlock(level, half, 0));
            check(!ShelterBoundary.containsBlock(level, 0, half));
            check(!ShelterBoundary.containsBlock(level, -half - 1, 0));
            check(ShelterBoundary.containsPosition(level, -half, -half));
            check(ShelterBoundary.containsPosition(level, half - 0.00001, half - 0.00001));
            check(!ShelterBoundary.containsPosition(level, half, 0));
            check(!ShelterBoundary.containsPosition(level, Double.NaN, 0));
            check(!ShelterBoundary.containsPosition(level, Double.POSITIVE_INFINITY, 0));
            check(ShelterBoundary.containsPosition(level, ShelterBoundary.clampCoordinate(level, 99999), 0));
            check(ShelterBoundary.containsPosition(level, ShelterBoundary.clampCoordinate(level, -99999), 0));
            check(ShelterBoundary.canModifyBlock(level, half - 1, -half));
            check(!ShelterBoundary.canTeleportTo(level, half, 0));
        }
        for (int level = 0; level < 4; level++) {
            int oldHalf = sizes[level] / 2;
            int newHalf = sizes[level + 1] / 2;
            check(ShelterUpgradeRules.newRing(level, newHalf - 1, newHalf - 1));
            check(!ShelterUpgradeRules.newRing(level, oldHalf - 1, oldHalf - 1));
            check(ShelterUpgradeRules.canUpgrade(level, level + 1));
            check(!ShelterUpgradeRules.canUpgrade(level, level + 2));
        }
        System.out.println("PASS " + assertions + " boundary and upgrade assertions");
    }
}
