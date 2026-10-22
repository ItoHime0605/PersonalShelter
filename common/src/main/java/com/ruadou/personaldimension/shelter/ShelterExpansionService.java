package com.ruadou.personaldimension.shelter;

import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side terrain expansion. This is a core service, not yet wired to the GUI.
 * Caller must authenticate the owner and check/consume a matching permit only after SUCCESS.
 * Run on the server thread only.
 */
public final class ShelterExpansionService {
    private ShelterExpansionService() {}
    public enum Result { SUCCESS, WRONG_PERMIT, OCCUPIED, PLACEMENT_FAILED }

    /** Checks the entire new ring before placing any blocks. Existing structures are never overwritten. */
    public static Result expand(ServerLevel world, PersonalDimensionData data, int permitLevel) {
        int oldLevel = data.getShelterLevel();
        if (!ShelterUpgradeRules.canUpgrade(oldLevel, permitLevel)) return Result.WRONG_PERMIT;
        if (world.getMinBuildHeight() > -6 || world.getMaxBuildHeight() <= -1)
            return Result.PLACEMENT_FAILED;
        int newSize = ShelterUpgradeRules.size(permitLevel);
        int half = newSize / 2;
        // Force-load all chunks that cover the new ring before inspecting or placing blocks.
        for (int cx = -half >> 4; cx <= half >> 4; cx++) {
            for (int cz = -half >> 4; cz <= half >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                if (ShelterUpgradeRules.inside(oldLevel, x, z)) continue;
                // Scan the entire buildable column. Checking only the six terrain layers
                // would silently create land under an existing floating structure.
                for (int y = world.getMinBuildHeight(); y < world.getMaxBuildHeight(); y++) {
                    if (!world.getBlockState(pos.set(x, y, z)).isAir() || world.getBlockEntity(pos) != null) {
                        return Result.OCCUPIED;
                    }
                }
            }
        }
        // Track successful placements so an ordinary placement failure can be rolled back.
        // This does not yet provide crash-safe journaling or a persistent upgrade lock.
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    if (ShelterUpgradeRules.inside(oldLevel, x, z)) continue;
                    for (int y = -6; y <= -1; y++) {
                        BlockPos target = new BlockPos(x, y, z);
                        if (!world.setBlock(target,
                                y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 3)) {
                            rollback(world, placed);
                            return Result.PLACEMENT_FAILED;
                        }
                        placed.add(target);
                    }
                }
            }
        } catch (RuntimeException error) {
            rollback(world, placed);
            return Result.PLACEMENT_FAILED;
        }
        // The new boundary is committed only after every block was placed.
        // The caller must still verify ownership, take the permit transactionally,
        // and save the dimension data on the server thread.
        data.setShelterLevel(permitLevel);
        return Result.SUCCESS;
    }

    private static void rollback(ServerLevel world, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            world.setBlock(placed.get(i), Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
