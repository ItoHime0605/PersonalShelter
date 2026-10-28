package com.ruadou.personaldimension.neoforge;

import com.ruadou.personaldimension.client.gui.PersonalDimensionCreateScreen;
import com.ruadou.personaldimension.neoforge.menu.ModMenus;
import com.ruadou.personaldimension.neoforge.client.ShelterUpgradeScreen;
import com.ruadou.personaldimension.neoforge.client.ClientRadioLifecycle;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@SuppressWarnings("null")
@Mod(value = PersonalDimension.MODID, dist = Dist.CLIENT)
public class PersonalDimensionClient {
    public PersonalDimensionClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // 将本类注册到 MOD 总线，@SubscribeEvent 方法将按事件类型自动订阅
        container.getEventBus().register(this);
        NeoForge.EVENT_BUS.register(ClientRadioLifecycle.class);
    }

    /**
     * 绑定创建维度容器与其界面（MOD 总线事件）。
     */
    @SubscribeEvent
    public void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CREATE_MENU.get(), PersonalDimensionCreateScreen::new);
        event.register(ModMenus.SHELTER_UPGRADE_MENU.get(), ShelterUpgradeScreen::new);
    }
}
