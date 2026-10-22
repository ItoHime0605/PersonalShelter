package com.ruadou.personaldimension.neoforge.block.entity;

import java.util.function.Supplier;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.block.entity.LightningInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.MoonPhaseInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.TeleporterBlockEntity;
import com.ruadou.personaldimension.block.entity.TimeInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.WeatherInterfaceBlockEntity;
import com.ruadou.personaldimension.neoforge.PersonalDimension;
import com.ruadou.personaldimension.neoforge.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型集中注册。
 * 每个类型绑定其对应方块，供 {@code newBlockEntity} 构造与 vanilla 存取匹配。
 */
@SuppressWarnings("null")
public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PersonalDimension.MODID);

    public static final Supplier<BlockEntityType<WeatherInterfaceBlockEntity>> WEATHER_INTERFACE =
        BLOCK_ENTITIES.register("weather_interface",
            () -> BlockEntityType.Builder.of(
                WeatherInterfaceBlockEntity::new, ModBlocks.WEATHER_INTERFACE.get()).build(null));

    public static final Supplier<BlockEntityType<TimeInterfaceBlockEntity>> TIME_INTERFACE =
        BLOCK_ENTITIES.register("time_interface",
            () -> BlockEntityType.Builder.of(
                TimeInterfaceBlockEntity::new, ModBlocks.TIME_INTERFACE.get()).build(null));

    public static final Supplier<BlockEntityType<LightningInterfaceBlockEntity>> LIGHTNING_INTERFACE =
        BLOCK_ENTITIES.register("lightning_interface",
            () -> BlockEntityType.Builder.of(
                LightningInterfaceBlockEntity::new, ModBlocks.LIGHTNING_INTERFACE.get()).build(null));

    public static final Supplier<BlockEntityType<MoonPhaseInterfaceBlockEntity>> MOON_PHASE_INTERFACE =
        BLOCK_ENTITIES.register("moon_phase_interface",
            () -> BlockEntityType.Builder.of(
                MoonPhaseInterfaceBlockEntity::new, ModBlocks.MOON_PHASE_INTERFACE.get()).build(null));

    public static final Supplier<BlockEntityType<TeleporterBlockEntity>> TELEPORTER =
        BLOCK_ENTITIES.register("teleporter",
            () -> BlockEntityType.Builder.of(
                TeleporterBlockEntity::new, ModBlocks.TELEPORTER.get()).build(null));

    public static final Supplier<BlockEntityType<FriendVisitorTeleporterBlockEntity>> FRIEND_VISITOR_TELEPORTER =
        BLOCK_ENTITIES.register("friend_visitor_teleporter",
            () -> BlockEntityType.Builder.of(
                FriendVisitorTeleporterBlockEntity::new, ModBlocks.FRIEND_VISITOR_TELEPORTER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
