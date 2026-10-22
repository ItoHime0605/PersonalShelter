package com.ruadou.personaldimension.dimension.template;

import com.ruadou.personaldimension.PersonalDimension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public class TemplateChunkGenerator extends ChunkGenerator {

    public static final MapCodec<TemplateChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            DimensionTemplate.CODEC.fieldOf("template").forGetter(TemplateChunkGenerator::template)
    ).apply(instance, TemplateChunkGenerator::new));

    private final DimensionTemplate template;

    public TemplateChunkGenerator(BiomeSource biomeSource, DimensionTemplate template) {
        super(biomeSource);
        this.template = template;
    }

    public DimensionTemplate template() {
        return this.template;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        int minBuild = chunk.getMinBuildHeight();
        int maxBuild = chunk.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int layer = 0; layer < this.template.layerCount(); layer++) {
            int worldY = this.template.baseY() + layer;
            if (worldY < minBuild || worldY >= maxBuild) {
                continue;
            }
            for (int x = 0; x < DimensionTemplate.SIZE; x++) {
                for (int z = 0; z < DimensionTemplate.SIZE; z++) {
                    BlockState state = this.template.stateAt(x, layer, z);
                    if (state.isAir()) {
                        continue;
                    }
                    chunk.setBlockState(cursor.set(x, worldY, z), state, false);
                    oceanFloor.update(x, worldY, z, state);
                    worldSurface.update(x, worldY, z, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int lx = Math.floorMod(x, DimensionTemplate.SIZE);
        int lz = Math.floorMod(z, DimensionTemplate.SIZE);
        for (int layer = this.template.layerCount() - 1; layer >= 0; layer--) {
            BlockState state = this.template.stateAt(lx, layer, lz);
            if (!state.isAir() && type.isOpaque().test(state)) {
                return this.template.baseY() + layer + 1;
            }
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        int lx = Math.floorMod(x, DimensionTemplate.SIZE);
        int lz = Math.floorMod(z, DimensionTemplate.SIZE);
        BlockState[] column = new BlockState[height.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        for (int layer = 0; layer < this.template.layerCount(); layer++) {
            int index = this.template.baseY() + layer - height.getMinBuildHeight();
            if (index >= 0 && index < column.length) {
                column[index] = this.template.stateAt(lx, layer, lz);
            }
        }
        return new NoiseColumn(height.getMinBuildHeight(), column);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return this.template.spawnY();
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }

    @Override
    public int getMinY() {
        return PersonalDimension.MIN_BUILD_Y;
    }

    @Override
    public int getGenDepth() {
        return PersonalDimension.MAX_BUILD_Y - PersonalDimension.MIN_BUILD_Y;
    }

    @Override
    public int getSeaLevel() {
        return getMinY();
    }
}
