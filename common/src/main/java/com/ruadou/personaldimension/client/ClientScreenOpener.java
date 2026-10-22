package com.ruadou.personaldimension.client;

import com.ruadou.personaldimension.client.gui.BiomeCollectorScreen;
import com.ruadou.personaldimension.client.gui.BiomeModifierScreen;
import com.ruadou.personaldimension.client.gui.FriendVisitorTeleporterScreen;
import com.ruadou.personaldimension.client.gui.LightningInterfaceScreen;
import com.ruadou.personaldimension.client.gui.MoonPhaseInterfaceScreen;
import com.ruadou.personaldimension.client.gui.PersonalDimensionControllerScreen;
import com.ruadou.personaldimension.client.gui.TimeInterfaceScreen;
import com.ruadou.personaldimension.client.gui.WeatherInterfaceScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 客户端界面打开入口。
 * 单独成类以隔离客户端专用类的加载：仅当运行在客户端且被显式调用时才会被类加载器加载，
 * 从而保证专用服务器不会触碰到 Minecraft / Screen 等客户端类。
 */
public final class ClientScreenOpener {

    private ClientScreenOpener() {}

    /** 打开个人维度控制器界面。 */
    public static void openControllerScreen() {
        Minecraft.getInstance().setScreen(new PersonalDimensionControllerScreen());
    }

    /** 打开群系改造器界面（携带改造器方块坐标，用于定位目标区块）。 */
    public static void openBiomeModifierScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new BiomeModifierScreen(pos));
    }

    /** 打开群系收录器界面（携带收录器方块坐标，用于定位并读取目标区块群系）。 */
    public static void openBiomeCollectorScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new BiomeCollectorScreen(pos));
    }

    /** 打开维度天气接口界面（携带方块坐标，用于回显与配置目标天气）。 */
    public static void openWeatherInterfaceScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new WeatherInterfaceScreen(pos));
    }

    /** 打开维度时间接口界面（携带方块坐标，用于回显与配置目标时刻）。 */
    public static void openTimeInterfaceScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TimeInterfaceScreen(pos));
    }

    /** 打开维度闪电接口界面（携带方块坐标，用于回显与配置自然 / 人造闪电）。 */
    public static void openLightningInterfaceScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new LightningInterfaceScreen(pos));
    }

    /** 打开维度月相接口界面（携带方块坐标，用于回显与配置目标月相）。 */
    public static void openMoonPhaseInterfaceScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new MoonPhaseInterfaceScreen(pos));
    }

    /** 打开好友访问传送器界面（携带方块坐标，用于配置权限组绑定）。 */
    public static void openFriendVisitorTeleporterScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new FriendVisitorTeleporterScreen(pos));
    }
}
