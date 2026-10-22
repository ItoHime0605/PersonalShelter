package com.ruadou.personaldimension.fabric.menu;

import com.ruadou.personaldimension.fabric.PersonalDimension;
import com.ruadou.personaldimension.menu.PersonalDimensionCreateMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.Registry;

/**
 * 容器菜单类型集中注册。
 * 目前仅"创建维度"容器需要真正的服务端菜单（含生成器槽）；其余交互为纯页面数据，不入容器。
 */
public final class ModMenus {

    private ModMenus() {}

    /** 创建维度容器：无需附加同步数据，用原版 MenuType 即可。 */
    public static final MenuType<PersonalDimensionCreateMenu> CREATE_MENU =
        Registry.register(BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(PersonalDimension.MODID, "create_dimension"),
            new MenuType<>(PersonalDimensionCreateMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void init() {
    }
}
