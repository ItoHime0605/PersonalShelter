package com.ruadou.personaldimension.shelter;

import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/** Server-side terrain expansion. Run on the server thread only. */
public final class ShelterExpansionService {
    private ShelterExpansionService() {}
    // Keep OCCUPIED for binary compatibility with existing callers.
    public enum Result { SUCCESS, WRONG_PERMIT, OCCUPIED, PLACEMENT_FAILED }

    private record PreviousBlock(BlockPos pos, BlockState state, CompoundTag blockEntityData) {}

    public static Result expand(ServerLevel world, PersonalDimensionData data, int permitLevel) {
        int oldLevel = data.getShelterLevel();
        if (!ShelterUpgradeRules.canUpgrade(oldLevel, permitLevel)) return Result.WRONG_PERMIT;
        if (world.getMinBuildHeight() > -6 || world.getMaxBuildHeight() <= -1)
            return Result.PLACEMENT_FAILED;
        int newSize = ShelterUpgradeRules.size(permitLevel);
        int half = newSize / 2;
        List<PreviousBlock> changed = new ArrayList<>();
        try {
            for (int cx = -half >> 4; cx <= half >> 4; cx++) {
                for (int cz = -half >> 4; cz <= half >> 4; cz++) {
                    world.getChunk(cx, cz);
                }
            }
            // No occupied-column check: trees and floating structures do not block upgrades.
            // Only the six original terrain layers in the newly added ring are overwritten.
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    if (ShelterUpgradeRules.inside(oldLevel, x, z)) continue;
                    for (int y = -6; y <= -1; y++) {
                        BlockPos target = new BlockPos(x, y, z);
                        BlockState desired = y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                        BlockState previous = world.getBlockState(target);
                        // setBlock can return false when the desired state is already present.
                        if (previous.equals(desired)) continue;
                        BlockEntity entity = world.getBlockEntity(target);
                        CompoundTag tag = entity == null ? null : entity.saveWithFullMetadata(world.registryAccess());
                        changed.add(new PreviousBlock(target, previous, tag));
                        if (!world.setBlock(target, desired, 3)) {
                            rollback(world, changed);
                            return Result.PLACEMENT_FAILED;
                        }
                    }
                }
            }
        } catch (RuntimeException error) {
            rollback(world, changed);
            return Result.PLACEMENT_FAILED;
        }
        data.setShelterLevel(permitLevel);
        return Result.SUCCESS;
    }

    // Best-effort restoration on placement failure. Not a crash-safe transaction:
    // neighbor updates and drops caused by block replacement cannot be undone here.
    private static void rollback(ServerLevel world, List<PreviousBlock> changed) {
        for (int i = changed.size() - 1; i >= 0; i--) {
            PreviousBlock previous = changed.get(i);
            world.setBlock(previous.pos(), previous.state(), 3);
            if (previous.blockEntityData() != null) {
                BlockEntity restored = world.getBlockEntity(previous.pos());
                if (restored != null) {
                    restored.loadWithComponents(previous.blockEntityData(), world.registryAccess());
                    restored.setChanged();
                }
            }
        }
    }
}
