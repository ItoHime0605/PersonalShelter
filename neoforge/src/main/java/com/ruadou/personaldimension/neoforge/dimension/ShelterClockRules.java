package com.ruadou.personaldimension.neoforge.dimension;

import com.ruadou.personaldimension.dimension.PersonalDimensionLevelData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class ShelterClockRules {
    private ShelterClockRules() {}
    public static boolean daylight(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, ServerLevel level) {
        boolean vanilla = rules.getBoolean(key);
        if (key == GameRules.RULE_DAYLIGHT
                && level.getLevelData() instanceof PersonalDimensionLevelData pd
                && !pd.isFollowOverworldTime()) return vanilla && pd.isDaylightCycle();
        return vanilla;
    }
}
