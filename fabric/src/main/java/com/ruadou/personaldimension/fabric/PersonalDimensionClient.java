package com.ruadou.personaldimension.fabric;

import com.ruadou.personaldimension.client.gui.PersonalDimensionCreateScreen;
import com.ruadou.personaldimension.fabric.menu.ModMenus;
import com.ruadou.personaldimension.fabric.network.ModNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric 端客户端入口。
 * 注册 Screen、客户端网络包接收器等。
 */
public class PersonalDimensionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 注册容器界面
        MenuScreens.register(ModMenus.CREATE_MENU, PersonalDimensionCreateScreen::new);

        // 注册客户端网络包接收器
        ModNetworking.registerClientReceivers();
    }
}
