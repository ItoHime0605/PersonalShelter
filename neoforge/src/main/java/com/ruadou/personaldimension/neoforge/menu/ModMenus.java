package com.ruadou.personaldimension.neoforge.menu;

import com.ruadou.personaldimension.menu.PersonalDimensionCreateMenu;
import com.ruadou.personaldimension.neoforge.PersonalDimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 容器菜单类型集中注册。
 * 目前仅"创建维度"容器需要真正的服务端菜单（含生成器槽）；其余交互为纯页面数据，不入容器。
 */
@SuppressWarnings("null")
public final class ModMenus {

    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, PersonalDimension.MODID);

    /** 创建维度容器：无需附加同步数据，用原版 {@link MenuType} 即可。 */
    public static final DeferredHolder<MenuType<?>, MenuType<PersonalDimensionCreateMenu>> CREATE_MENU =
        MENUS.register("create_dimension",
            () -> new MenuType<>(PersonalDimensionCreateMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ShelterUpgradeMenu>> SHELTER_UPGRADE_MENU =
        MENUS.register("shelter_upgrade",
            () -> new MenuType<>(ShelterUpgradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
