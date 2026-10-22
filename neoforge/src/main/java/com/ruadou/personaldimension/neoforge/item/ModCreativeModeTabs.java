package com.ruadou.personaldimension.neoforge.item;

import java.util.function.Supplier;

import com.ruadou.personaldimension.neoforge.PersonalDimension;
import com.ruadou.personaldimension.neoforge.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PersonalDimension.MODID);

    public static final Supplier<CreativeModeTab> PERSONAL_DIMENSION_TAB =
        CREATIVE_MODE_TABS.register("personal_dimension_tab", () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(ModItems.PERSONAL_DIMENSION_GENERATOR.get()))
            .title(Component.translatable("itemGroup.personal_dimension_tab"))
            .displayItems((parameters, output)->{
                output.accept(ModItems.PERSONAL_DIMENSION_GENERATOR);
                output.accept(ModItems.PERSONAL_DIMENSION_CONTROLLER);
                output.accept(ModItems.SHELTER_PERMIT_LV1);
                output.accept(ModItems.SHELTER_PERMIT_LV2);
                output.accept(ModItems.SHELTER_PERMIT_LV3);
                output.accept(ModItems.SHELTER_PERMIT_LV4);
                output.accept(ModBlocks.BIOME_COLLECTOR_ITEM);
                output.accept(ModBlocks.BIOME_MODIFIER_ITEM);
                output.accept(ModBlocks.WEATHER_INTERFACE_ITEM);
                output.accept(ModBlocks.TIME_INTERFACE_ITEM);
                output.accept(ModBlocks.LIGHTNING_INTERFACE_ITEM);
                output.accept(ModBlocks.MOON_PHASE_INTERFACE_ITEM);
                output.accept(ModBlocks.TELEPORTER_ITEM);
                output.accept(ModBlocks.FRIEND_VISITOR_TELEPORTER_ITEM);
            }).build());

    public static void registerTabs(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
