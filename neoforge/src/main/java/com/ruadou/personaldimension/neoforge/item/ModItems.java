package com.ruadou.personaldimension.neoforge.item;

import com.ruadou.personaldimension.item.PersonalDimensionControllerItem;
import com.ruadou.personaldimension.item.PersonalDimensionGeneratorItem;
import com.ruadou.personaldimension.item.ShelterPermitItem;

import com.ruadou.personaldimension.neoforge.PersonalDimension;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("null")
public class ModItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(PersonalDimension.MODID);

    // 个人维度生成器：右键进入专属维度，不可堆叠
    public static final DeferredItem<Item> PERSONAL_DIMENSION_GENERATOR =
        ITEMS.register("personal_dimension_generator",
            () -> new PersonalDimensionGeneratorItem(new Item.Properties().stacksTo(1)));

    // 个人维度控制器：右键打开控制器 GUI（信息/设置），不可堆叠
    public static final DeferredItem<Item> PERSONAL_DIMENSION_CONTROLLER =
        ITEMS.register("personal_dimension_controller",
            () -> new PersonalDimensionControllerItem(new Item.Properties().stacksTo(1)));

    // Personal Shelter: four non-craftable expansion permits. Server-side redemption is pending.
    public static final DeferredItem<Item> SHELTER_PERMIT_LV1 =
        ITEMS.register("shelter_permit_lv1", () -> new ShelterPermitItem(1, new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> SHELTER_PERMIT_LV2 =
        ITEMS.register("shelter_permit_lv2", () -> new ShelterPermitItem(2, new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> SHELTER_PERMIT_LV3 =
        ITEMS.register("shelter_permit_lv3", () -> new ShelterPermitItem(3, new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> SHELTER_PERMIT_LV4 =
        ITEMS.register("shelter_permit_lv4", () -> new ShelterPermitItem(4, new Item.Properties().stacksTo(64)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
