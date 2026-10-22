package com.ruadou.personaldimension.neoforge.dimension.template;

import com.mojang.serialization.MapCodec;
import com.ruadou.personaldimension.PersonalDimension;
import com.ruadou.personaldimension.dimension.template.TemplateChunkGenerator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 区块生成器 Codec 的集中注册。
 *
 * <p>{@link TemplateChunkGenerator} 需把 {@link TemplateChunkGenerator#CODEC} 注册进
 * {@code Registries.CHUNK_GENERATOR}，否则携带它的 LevelStem 无法写入/读取 level.dat（存档往返失败）。
 */
@SuppressWarnings("null")
public final class ModChunkGenerators {

    private ModChunkGenerators() {}

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, PersonalDimension.MODID);

    /** 自定义模板生成器的 Codec。 */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<TemplateChunkGenerator>> TEMPLATE =
            GENERATORS.register("template", () -> TemplateChunkGenerator.CODEC);

    public static void register(IEventBus modEventBus) {
        GENERATORS.register(modEventBus);
    }
}
