package com.ruadou.personaldimension.fabric.dimension.template;

import com.mojang.serialization.MapCodec;
import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.template.TemplateChunkGenerator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

/**
 * 区块生成器 Codec 的集中注册。
 *
 * <p>TemplateChunkGenerator 需把 CODEC 注册进 Registries.CHUNK_GENERATOR，
 * 否则携带它的 LevelStem 无法写入/读取 level.dat（存档往返失败）。
 */
public final class ModChunkGenerators {

    private ModChunkGenerators() {}

    /** 自定义模板生成器的 Codec。 */
    public static final MapCodec<TemplateChunkGenerator> TEMPLATE =
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "template"),
            TemplateChunkGenerator.CODEC);

    public static void init() {
    }
}
