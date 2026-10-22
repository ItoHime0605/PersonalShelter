package com.ruadou.personaldimension.fabric.block.entity;

import com.ruadou.personaldimension.block.entity.FriendVisitorTeleporterBlockEntity;
import com.ruadou.personaldimension.block.entity.LightningInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.MoonPhaseInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.TeleporterBlockEntity;
import com.ruadou.personaldimension.block.entity.TimeInterfaceBlockEntity;
import com.ruadou.personaldimension.block.entity.WeatherInterfaceBlockEntity;
import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.fabric.block.ModBlocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Registry;

/**
 * 方块实体类型集中注册。
 * 每个类型绑定其对应方块，供 newBlockEntity 构造与 vanilla 存取匹配。
 */
public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final BlockEntityType<WeatherInterfaceBlockEntity> WEATHER_INTERFACE =
        register("weather_interface",
            BlockEntityType.Builder.of(
                WeatherInterfaceBlockEntity::new, ModBlocks.WEATHER_INTERFACE).build(null));

    public static final BlockEntityType<TimeInterfaceBlockEntity> TIME_INTERFACE =
        register("time_interface",
            BlockEntityType.Builder.of(
                TimeInterfaceBlockEntity::new, ModBlocks.TIME_INTERFACE).build(null));

    public static final BlockEntityType<LightningInterfaceBlockEntity> LIGHTNING_INTERFACE =
        register("lightning_interface",
            BlockEntityType.Builder.of(
                LightningInterfaceBlockEntity::new, ModBlocks.LIGHTNING_INTERFACE).build(null));

    public static final BlockEntityType<MoonPhaseInterfaceBlockEntity> MOON_PHASE_INTERFACE =
        register("moon_phase_interface",
            BlockEntityType.Builder.of(
                MoonPhaseInterfaceBlockEntity::new, ModBlocks.MOON_PHASE_INTERFACE).build(null));

    public static final BlockEntityType<TeleporterBlockEntity> TELEPORTER =
        register("teleporter",
            BlockEntityType.Builder.of(
                TeleporterBlockEntity::new, ModBlocks.TELEPORTER).build(null));

    public static final BlockEntityType<FriendVisitorTeleporterBlockEntity> FRIEND_VISITOR_TELEPORTER =
        register("friend_visitor_teleporter",
            BlockEntityType.Builder.of(
                FriendVisitorTeleporterBlockEntity::new, ModBlocks.FRIEND_VISITOR_TELEPORTER).build(null));

    private static <T extends BlockEntityType<?>> T register(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, name), type);
    }

    public static void init() {
    }
}
