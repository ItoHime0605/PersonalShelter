package com.ruadou.personaldimension.fabric.item;

import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.item.PersonalDimensionControllerItem;
import com.ruadou.personaldimension.item.PersonalDimensionGeneratorItem;
import com.ruadou.personaldimension.item.ShelterPermitItem;

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

    public static final Item SHELTER_PERMIT_LV1 = register("shelter_permit_lv1", new ShelterPermitItem(1, new Item.Properties().stacksTo(64)));
    public static final Item SHELTER_PERMIT_LV2 = register("shelter_permit_lv2", new ShelterPermitItem(2, new Item.Properties().stacksTo(64)));
    public static final Item SHELTER_PERMIT_LV3 = register("shelter_permit_lv3", new ShelterPermitItem(3, new Item.Properties().stacksTo(64)));
    public static final Item SHELTER_PERMIT_LV4 = register("shelter_permit_lv4", new ShelterPermitItem(4, new Item.Properties().stacksTo(64)));

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
