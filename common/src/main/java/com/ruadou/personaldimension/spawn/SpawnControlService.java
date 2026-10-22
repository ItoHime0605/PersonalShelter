package com.ruadou.personaldimension.spawn;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

public final class SpawnControlService {

    private SpawnControlService() {}

    public static SpawnRuleData ruleFor(ServerLevel level) {
        if (Platform.ownerOf(level.dimension()).isEmpty()) {
            return null;
        }
        return SpawnRuleData.get(level);
    }

    public static boolean isNatural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
    }

    public static ResourceLocation idOf(Mob mob) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
    }

    public static boolean shouldBlock(SpawnRuleData rule, Mob mob) {
        return rule != null && rule.isBlocked(idOf(mob));
    }
}
