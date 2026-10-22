package com.ruadou.personaldimension.fabric.item;

import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.item.PersonalDimensionControllerItem;
import com.ruadou.personaldimension.item.PersonalDimensionGeneratorItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Registry;

public class ModItems {

    public static final Item PERSONAL_DIMENSION_GENERATOR = register("personal_dimension_generator",
        new PersonalDimensionGeneratorItem(new Item.Properties().stacksTo(1)));

    public static final Item PERSONAL_DIMENSION_CONTROLLER = register("personal_dimension_controller",
        new PersonalDimensionControllerItem(new Item.Properties().stacksTo(1)));

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, name), item);
    }

    /** 注册 BlockItem（由 ModBlocks 在方块注册后调用）。 */
    public static BlockItem registerBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, name),
            new BlockItem(block, new Item.Properties()));
    }

    public static void init() {
    }
}
