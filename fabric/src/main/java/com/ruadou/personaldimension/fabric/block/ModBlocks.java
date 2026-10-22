package com.ruadou.personaldimension.fabric.block;

import com.ruadou.personaldimension.block.BiomeCollectorBlock;
import com.ruadou.personaldimension.block.BiomeModifierBlock;
import com.ruadou.personaldimension.block.FriendVisitorTeleporterBlock;
import com.ruadou.personaldimension.block.LightningInterfaceBlock;
import com.ruadou.personaldimension.block.MoonPhaseInterfaceBlock;
import com.ruadou.personaldimension.block.TeleporterBlock;
import com.ruadou.personaldimension.block.TimeInterfaceBlock;
import com.ruadou.personaldimension.block.WeatherInterfaceBlock;
import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.fabric.item.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.Registry;

/**
 * 群系功能方块的集中注册。
 * 方块与对应 BlockItem 均在此注册，使用 Fabric Registry.register 方式。
 */
public class ModBlocks {

    // 群系收录器：右键收录所在区块的群系到个人数据
    public static final Block BIOME_COLLECTOR = register("biome_collector",
        new BiomeCollectorBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 群系改造器：右键在自己的个人维度打开界面，用已收录群系覆盖当前区块
    public static final Block BIOME_MODIFIER = register("biome_modifier",
        new BiomeModifierBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 维度天气接口：右键在自己的个人维度选天气，外部红石上升沿激活时应用
    public static final Block WEATHER_INTERFACE = register("weather_interface",
        new WeatherInterfaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 维度时间接口：右键在自己的个人维度选时刻，外部红石上升沿激活时应用
    public static final Block TIME_INTERFACE = register("time_interface",
        new TimeInterfaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 维度闪电接口：纯红石触发器，上升沿在所在 chunk 内尝试生成一次"自然闪电"
    public static final Block LIGHTNING_INTERFACE = register("lightning_interface",
        new LightningInterfaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 维度月相接口：右键在自己的个人维度选月相，外部红石上升沿激活时锁定该月相
    public static final Block MOON_PHASE_INTERFACE = register("moon_phase_interface",
        new MoonPhaseInterfaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GRAY)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 维度传送器：放置后记录位置，供个人维度控制器传送标签页选择并传送
    public static final Block TELEPORTER = register("teleporter",
        new TeleporterBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // 好友访问传送器：允许特定权限组的好友传送到此方块上方出现
    public static final Block FRIEND_VISITOR_TELEPORTER = register("friend_visitor_teleporter",
        new FriendVisitorTeleporterBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PINK)
            .strength(3.0F)
            .sound(SoundType.METAL)));

    // BlockItem 同步注册到物品表
    public static final BlockItem BIOME_COLLECTOR_ITEM =
        ModItems.registerBlockItem("biome_collector", BIOME_COLLECTOR);
    public static final BlockItem BIOME_MODIFIER_ITEM =
        ModItems.registerBlockItem("biome_modifier", BIOME_MODIFIER);
    public static final BlockItem WEATHER_INTERFACE_ITEM =
        ModItems.registerBlockItem("weather_interface", WEATHER_INTERFACE);
    public static final BlockItem TIME_INTERFACE_ITEM =
        ModItems.registerBlockItem("time_interface", TIME_INTERFACE);
    public static final BlockItem LIGHTNING_INTERFACE_ITEM =
        ModItems.registerBlockItem("lightning_interface", LIGHTNING_INTERFACE);
    public static final BlockItem MOON_PHASE_INTERFACE_ITEM =
        ModItems.registerBlockItem("moon_phase_interface", MOON_PHASE_INTERFACE);
    public static final BlockItem TELEPORTER_ITEM =
        ModItems.registerBlockItem("teleporter", TELEPORTER);
    public static final BlockItem FRIEND_VISITOR_TELEPORTER_ITEM =
        ModItems.registerBlockItem("friend_visitor_teleporter", FRIEND_VISITOR_TELEPORTER);

    private static Block register(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, name), block);
    }

    public static void init() {
    }
}
