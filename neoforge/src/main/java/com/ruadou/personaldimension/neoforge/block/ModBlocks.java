package com.ruadou.personaldimension.neoforge.block;

import com.ruadou.personaldimension.block.BiomeCollectorBlock;
import com.ruadou.personaldimension.block.BiomeModifierBlock;
import com.ruadou.personaldimension.block.FriendVisitorTeleporterBlock;
import com.ruadou.personaldimension.block.LightningInterfaceBlock;
import com.ruadou.personaldimension.block.MoonPhaseInterfaceBlock;
import com.ruadou.personaldimension.block.TeleporterBlock;
import com.ruadou.personaldimension.block.ShelterManagementTerminalBlock;
import com.ruadou.personaldimension.block.ShelterRadioBlock;
import net.minecraft.world.item.DyeColor;
import java.util.EnumMap;
import java.util.Map;
import com.ruadou.personaldimension.block.TimeInterfaceBlock;
import com.ruadou.personaldimension.block.WeatherInterfaceBlock;
import com.ruadou.personaldimension.neoforge.PersonalDimension;
import com.ruadou.personaldimension.neoforge.item.ModItems;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 群系功能方块的集中注册。
 * 方块本身注册进 {@link #BLOCKS}，对应 {@link BlockItem} 注册进 {@link ModItems#ITEMS}，
 * 二者分别由各自的 DeferredRegister 在物品/方块注册事件时统一生效。
 */
@SuppressWarnings("null")
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(PersonalDimension.MODID);

    // 群系收录器：右键收录所在区块的群系到个人数据
    public static final DeferredBlock<Block> BIOME_COLLECTOR =
        BLOCKS.register("biome_collector", () -> new BiomeCollectorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 群系改造器：右键在自己的个人维度打开界面，用已收录群系覆盖当前区块
    public static final DeferredBlock<Block> BIOME_MODIFIER =
        BLOCKS.register("biome_modifier", () -> new BiomeModifierBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 维度天气接口：右键在自己的个人维度选天气，外部红石上升沿激活时应用
    public static final DeferredBlock<Block> WEATHER_INTERFACE =
        BLOCKS.register("weather_interface", () -> new WeatherInterfaceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 维度时间接口：右键在自己的个人维度选时刻，外部红石上升沿激活时应用
    public static final DeferredBlock<Block> TIME_INTERFACE =
        BLOCKS.register("time_interface", () -> new TimeInterfaceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 维度闪电接口：纯红石触发器，上升沿在所在 chunk 内尝试生成一次"自然闪电"（保留原版校验）
    public static final DeferredBlock<Block> LIGHTNING_INTERFACE =
        BLOCKS.register("lightning_interface", () -> new LightningInterfaceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_YELLOW)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 维度月相接口：右键在自己的个人维度选月相，外部红石上升沿激活时锁定该月相
    public static final DeferredBlock<Block> MOON_PHASE_INTERFACE =
        BLOCKS.register("moon_phase_interface", () -> new MoonPhaseInterfaceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 维度传送器：放置后记录位置，供个人维度控制器传送标签页选择并传送
    public static final DeferredBlock<Block> TELEPORTER =
        BLOCKS.register("teleporter", () -> new TeleporterBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    // 好友访问传送器：允许特定权限组的好友传送到此方块上方出现
    public static final DeferredBlock<Block> FRIEND_VISITOR_TELEPORTER =
        BLOCKS.register("friend_visitor_teleporter", () -> new FriendVisitorTeleporterBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PINK)
                .strength(3.0F)
                .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> SHELTER_MANAGEMENT_TERMINAL =
        BLOCKS.register("shelter_management_terminal", () -> new ShelterManagementTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredItem<BlockItem> SHELTER_MANAGEMENT_TERMINAL_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("shelter_management_terminal", SHELTER_MANAGEMENT_TERMINAL);

    // BlockItem 挂到物品注册器：随 ModItems 一并注册，无需单独 register 调用
    public static final DeferredItem<BlockItem> BIOME_COLLECTOR_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("biome_collector", BIOME_COLLECTOR);
    public static final DeferredItem<BlockItem> BIOME_MODIFIER_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("biome_modifier", BIOME_MODIFIER);
    public static final DeferredItem<BlockItem> WEATHER_INTERFACE_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("weather_interface", WEATHER_INTERFACE);
    public static final DeferredItem<BlockItem> TIME_INTERFACE_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("time_interface", TIME_INTERFACE);
    public static final DeferredItem<BlockItem> LIGHTNING_INTERFACE_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("lightning_interface", LIGHTNING_INTERFACE);
    public static final DeferredItem<BlockItem> MOON_PHASE_INTERFACE_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("moon_phase_interface", MOON_PHASE_INTERFACE);
    public static final DeferredItem<BlockItem> TELEPORTER_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("teleporter", TELEPORTER);
    public static final DeferredItem<BlockItem> FRIEND_VISITOR_TELEPORTER_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("friend_visitor_teleporter", FRIEND_VISITOR_TELEPORTER);

    public static final DeferredBlock<Block> SHELTER_RADIO = BLOCKS.register("shelter_radio",
        () -> new ShelterRadioBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
            .strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> SHELTER_RADIO_ITEM =
        ModItems.ITEMS.registerSimpleBlockItem("shelter_radio", SHELTER_RADIO);

    public static final Map<DyeColor, DeferredBlock<Block>> COLORED_RADIOS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredItem<BlockItem>> COLORED_RADIO_ITEMS = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor dye : DyeColor.values()) {
            String id = dye.getName() + "_shelter_radio";
            DeferredBlock<Block> block = BLOCKS.register(id,
                () -> new ShelterRadioBlock(BlockBehaviour.Properties.of().mapColor(dye.getMapColor())
                    .strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
            COLORED_RADIOS.put(dye, block);
            COLORED_RADIO_ITEMS.put(dye, ModItems.ITEMS.registerSimpleBlockItem(id, block));
        }
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
