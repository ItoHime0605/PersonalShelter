package com.ruadou.personaldimension.dimension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.template.DimensionTemplate;
import com.ruadou.personaldimension.dimension.template.TemplateChunkGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

public final class ModDimensions {

    private ModDimensions() {}

    /** 数据包 JSON 预定义的个人维度类型（data/personaldimension/dimension_type/personal.json）。 */
    public static final ResourceKey<DimensionType> PERSONAL_DIMENSION_TYPE =
        ResourceKey.create(Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "personal"));

    /** 数据包 JSON 预定义的个人维度生物群系（data/personaldimension/worldgen/biome/personal.json）。 */
    public static final ResourceKey<Biome> PERSONAL_BIOME =
        ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "personal"));

    /** 公共维度固定键：全局唯一，所有人共享，通过控制器创建。 */
    public static final ResourceKey<Level> PUBLIC_LEVEL_KEY =
        ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "public"));

    public static ResourceKey<Level> levelKeyFor(UUID playerId) {
        String path = "personal_" + playerId.toString().replace("-", "");
        return ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, path));
    }

    public static boolean isPersonalDimension(ResourceKey<Level> key) {
        if (key == null) {
            return false;
        }
        ResourceLocation id = key.location();
        return PersonalDimension.MODID.equals(id.getNamespace())
            && id.getPath().startsWith("personal_");
    }

    public static boolean isPublicDimension(ResourceKey<Level> key) {
        return PUBLIC_LEVEL_KEY.equals(key);
    }

    public static boolean isModDimension(ResourceKey<Level> key) {
        return isPersonalDimension(key) || isPublicDimension(key);
    }

    public static Optional<UUID> ownerOf(ResourceKey<Level> key) {
        if (!isPersonalDimension(key)) {
            return Optional.empty();
        }
        String hex = key.location().getPath().substring("personal_".length());
        if (hex.length() != 32) {
            return Optional.empty();
        }
        try {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return Optional.of(UUID.fromString(dashed));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static LevelStem createPersonalStem(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();
        Holder<DimensionType> dimensionType = registryAccess
            .lookupOrThrow(Registries.DIMENSION_TYPE)
            .getOrThrow(PERSONAL_DIMENSION_TYPE);
        Holder<Biome> personalBiome = registryAccess
            .lookupOrThrow(Registries.BIOME)
            .getOrThrow(PERSONAL_BIOME);

        FlatLevelGeneratorSettings settings =
            new FlatLevelGeneratorSettings(Optional.empty(), personalBiome, List.of());

        return new LevelStem(dimensionType, new FlatLevelSource(settings));
    }

    public static LevelStem createTemplateStem(MinecraftServer server, DimensionTemplate template) {
        RegistryAccess registryAccess = server.registryAccess();
        Holder<DimensionType> dimensionType = registryAccess
            .lookupOrThrow(Registries.DIMENSION_TYPE)
            .getOrThrow(PERSONAL_DIMENSION_TYPE);
        Holder<Biome> personalBiome = registryAccess
            .lookupOrThrow(Registries.BIOME)
            .getOrThrow(PERSONAL_BIOME);

        return new LevelStem(dimensionType,
            new TemplateChunkGenerator(new FixedBiomeSource(personalBiome), template));
    }

    public static LevelStem createStem(MinecraftServer server, DimensionPreset preset, DimensionTemplate template) {
        if (preset.requiresTemplate() && template != null) {
            return createTemplateStem(server, template);
        }
        return createPersonalStem(server);
    }
}
