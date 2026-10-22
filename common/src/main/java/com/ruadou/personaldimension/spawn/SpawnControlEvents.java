package com.ruadou.personaldimension.spawn;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

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
