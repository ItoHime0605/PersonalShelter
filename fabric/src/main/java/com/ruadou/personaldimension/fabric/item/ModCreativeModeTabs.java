package com.ruadou.personaldimension.fabric.item;

import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.fabric.block.ModBlocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;

public class ModCreativeModeTabs {

    public static final CreativeModeTab PERSONAL_DIMENSION_TAB =
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "personal_dimension_tab"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .icon(() -> new ItemStack(ModItems.PERSONAL_DIMENSION_GENERATOR))
                .title(Component.translatable("itemGroup.personal_dimension_tab"))
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.PERSONAL_DIMENSION_GENERATOR);
                    output.accept(ModItems.PERSONAL_DIMENSION_CONTROLLER);
                    output.accept(ModBlocks.BIOME_COLLECTOR_ITEM);
                    output.accept(ModBlocks.BIOME_MODIFIER_ITEM);
                    output.accept(ModBlocks.WEATHER_INTERFACE_ITEM);
                    output.accept(ModBlocks.TIME_INTERFACE_ITEM);
                    output.accept(ModBlocks.LIGHTNING_INTERFACE_ITEM);
                    output.accept(ModBlocks.MOON_PHASE_INTERFACE_ITEM);
                    output.accept(ModBlocks.TELEPORTER_ITEM);
                    output.accept(ModBlocks.FRIEND_VISITOR_TELEPORTER_ITEM);
                }).build());

    public static void init() {
    }
}
