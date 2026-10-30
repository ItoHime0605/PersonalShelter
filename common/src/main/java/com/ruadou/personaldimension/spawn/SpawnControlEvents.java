package com.ruadou.personaldimension.spawn;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import com.ruadou.personaldimension.platform.Platform;

public final class SpawnControlEvents {

    private SpawnControlEvents() {}

    public static void register() {
        EntityEvent.LIVING_CHECK_SPAWN.register((entity, level, x, y, z, type, spawner) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return EventResult.pass();
            }
            if (!(entity instanceof Mob mob)) {
                return EventResult.pass();
            }
            // Personal Shelter: never allow natural hostile mob spawns in private dimensions.
            // Spawn eggs, commands and other deliberate sources are unaffected by this rule.
            // Modded zombies may not extend vanilla Monster; their registered
            // entity category is the reliable cross-mod hostile classification.
            if (mob.getType().getCategory() == MobCategory.MONSTER
                    && Platform.ownerOf(serverLevel.dimension()).isPresent()
                    && SpawnControlService.isNatural(type)) {
                return EventResult.interruptFalse();
            }
            if (!SpawnControlService.isNatural(type)) {
                return EventResult.pass();
            }
            SpawnRuleData rule = SpawnControlService.ruleFor(serverLevel);
            if (rule == null) {
                return EventResult.pass();
            }
            if (rule.isBlocked(SpawnControlService.idOf(mob))) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });

        EntityEvent.ADD.register((entity, level) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return EventResult.pass();
            }
            if (!(entity instanceof Mob mob)) {
                return EventResult.pass();
            }
            SpawnRuleData rule = SpawnControlService.ruleFor(serverLevel);
            if (rule == null || rule.getScope() != SpawnSourceScope.ALL_SOURCES) {
                return EventResult.pass();
            }
            if (SpawnControlService.shouldBlock(rule, mob)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }
}
