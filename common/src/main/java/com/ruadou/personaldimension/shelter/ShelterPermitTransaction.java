package com.ruadou.personaldimension.shelter;

import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import net.minecraft.server.level.ServerLevel;
import com.ruadou.personaldimension.dimension.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Server-only entry point for a future one-slot permit menu.
 * The menu must verify the owner and ensure that the supplied stack belongs to its
 * server-side slot; never trust an ItemStack or a level sent by the client.
 */
public final class ShelterPermitTransaction {
    private ShelterPermitTransaction() {}

    public enum Result {
        SUCCESS, NOT_OWNER, INVALID_PERMIT, WRONG_LEVEL, EMPTY_STACK,
        OCCUPIED, PLACEMENT_FAILED
    }

    /**
     * Server-authoritative entry point. The slot must belong to a server-side menu;
     * the caller must never construct it from client-provided item data.
     * Ownership is resolved from the dimension registry, not a client flag.
     */
    public static Result tryUpgrade(ServerPlayer actor, ServerLevel shelter,
                                    PersonalDimensionData data, ItemStack serverMenuSlot) {
        boolean owner = ModDimensions.ownerOf(shelter.dimension())
            .map(uuid -> uuid.equals(actor.getUUID())).orElse(false);
        return tryUpgrade(actor, shelter, data, serverMenuSlot, owner);
    }

    /** Must be invoked on the Minecraft server thread. */
    private static Result tryUpgrade(ServerPlayer actor, ServerLevel shelter,
                                    PersonalDimensionData data, ItemStack menuSlot,
                                    boolean verifiedOwner) {
        if (!actor.server.isSameThread())
            throw new IllegalStateException("Shelter upgrade must run on the server thread");
        if (!verifiedOwner) return Result.NOT_OWNER;
        if (menuSlot.isEmpty()) return Result.EMPTY_STACK;
        int permitLevel = permitLevel(menuSlot.getItem());
        if (permitLevel < 1) return Result.INVALID_PERMIT;
        if (!ShelterUpgradeRules.canUpgrade(data.getShelterLevel(), permitLevel))
            return Result.WRONG_LEVEL;
        // Do not mutate inventory before the terrain operation succeeds.
        ShelterExpansionService.Result expansion = ShelterExpansionService.expand(shelter, data, permitLevel);
        if (expansion == ShelterExpansionService.Result.OCCUPIED) return Result.OCCUPIED;
        if (expansion != ShelterExpansionService.Result.SUCCESS) return Result.PLACEMENT_FAILED;
        menuSlot.shrink(1);
        return Result.SUCCESS;
    }

    private static int permitLevel(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (!"personaldimension".equals(key.getNamespace())) return -1;
        return switch (key.getPath()) {
            case "shelter_permit_lv1" -> 1;
            case "shelter_permit_lv2" -> 2;
            case "shelter_permit_lv3" -> 3;
            case "shelter_permit_lv4" -> 4;
            default -> -1;
        };
    }
}
