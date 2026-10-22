import com.ruadou.personaldimension.shelter.ShelterUpgradeRules;

/** Pure-Java geometry regression test: all four corners must be included. */
public final class ShelterSquareExpansionTest {
    public static void main(String[] args) {
        int[] sizes = {64, 96, 128, 192, 256};
        for (int level = 0; level < 4; level++) {
            int old = sizes[level], next = sizes[level + 1];
            int added = 0;
            for (int x = -next / 2; x < next / 2; x++) {
                for (int z = -next / 2; z < next / 2; z++) {
                    if (ShelterUpgradeRules.newRing(level, x, z)) added++;
                }
            }
            if (added != next * next - old * old)
                throw new AssertionError("Incorrect new square area at level " + level + ": " + added);
            int corner = next / 2 - 1;
            if (!ShelterUpgradeRules.newRing(level, corner, corner) ||
                !ShelterUpgradeRules.newRing(level, -corner - 1, -corner - 1) ||
                !ShelterUpgradeRules.newRing(level, corner, -corner - 1) ||
                !ShelterUpgradeRules.newRing(level, -corner - 1, corner))
                throw new AssertionError("Missing corner at level " + level);
            System.out.println("Lv." + level + " -> Lv." + (level+1) + ": " + added + " new blocks per layer");
        }
    }
}
