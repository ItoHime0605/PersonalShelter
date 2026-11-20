package com.ruadou.personaldimension.dimension;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/** Per-land rule; never changes the shared vanilla gamerules. */
public final class ShelterFireRules {
    private ShelterFireRules() {}
    public static boolean isDisabled(LevelReader reader) {
        if (!(reader instanceof ServerLevel level) || !ModDimensions.isPersonalDimension(level.dimension())) return false;
        return level.getDataStorage().computeIfAbsent(PersonalDimensionData.FACTORY, PersonalDimensionData.ID).isFireSpreadDisabled();
    }
}
