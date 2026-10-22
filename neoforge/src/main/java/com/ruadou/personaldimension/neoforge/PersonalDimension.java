package com.ruadou.personaldimension.neoforge;


import com.ruadou.personaldimension.neoforge.attachment.ModAttachments;
import com.ruadou.personaldimension.neoforge.block.ModBlocks;
import com.ruadou.personaldimension.neoforge.block.entity.ModBlockEntities;
import com.ruadou.personaldimension.neoforge.dimension.PersonalDimensionGuardEvents;
import com.ruadou.personaldimension.dimension.GuardEvents;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.neoforge.dimension.template.ModChunkGenerators;
import com.ruadou.personaldimension.neoforge.item.ModCreativeModeTabs;
import com.ruadou.personaldimension.neoforge.item.ModItems;
import com.ruadou.personaldimension.neoforge.menu.ModMenus;
import com.ruadou.personaldimension.neoforge.menu.ShelterUpgradeCommands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.ruadou.personaldimension.neoforge.network.ModNetworking;
import com.ruadou.personaldimension.neoforge.platform.NeoForgePlatformProvider;
import com.ruadou.personaldimension.spawn.SpawnControlEvents;
import com.ruadou.personaldimension.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 模组主类：装配各注册器、网络、数据附着与配置，并在服务器启动后接管重载的个人维度。
 */
@Mod(PersonalDimension.MODID)
public class PersonalDimension extends com.ruadou.personaldimension.PersonalDimension {

    public PersonalDimension(IEventBus modEventBus, ModContainer modContainer) {
        // 必须最先初始化平台抽象层，后续所有代码都可能依赖 Platform
        Platform.init(new NeoForgePlatformProvider());

        modEventBus.addListener(this::commonSetup);

        // 集中注册物品（个人维度生成器、控制器）
        ModItems.register(modEventBus);
        // 注册功能方块（群系收录器、改造器、维度时间/天气接口）；其 BlockItem 已挂在 ModItems 上随之注册
        ModBlocks.register(modEventBus);
        // 注册方块实体类型（维度时间/天气接口存储所选设置并同步客户端）
        ModBlockEntities.register(modEventBus);
        // 注册容器菜单类型（创建维度界面的生成器容器）
        ModMenus.register(modEventBus);
        // 注册自定义模板区块生成器 Codec（供自定义地形维度存档往返）
        ModChunkGenerators.register(modEventBus);
        // 注册数据附着（玩家进出个人维度的记忆点，随存档持久化）
        ModAttachments.register(modEventBus);
        // 注册网络包处理器（维度同步、时间/天气控制、创建流程）
        modEventBus.addListener(ModNetworking::register);
        // 注册创造模式物品栏分组
        ModCreativeModeTabs.registerTabs(modEventBus);

        // 订阅游戏事件（本类的 @SubscribeEvent 方法，如 onServerStarted）
        NeoForge.EVENT_BUS.register(this);
        // 订阅访客行为守卫（破坏/放置/交互/攻击/拾取等按权限组拦截）
        NeoForge.EVENT_BUS.register(PersonalDimensionGuardEvents.class);
        // 生物生成控制（通过 Architectury 跨平台事件注册）
        SpawnControlEvents.register();

        // 注册配置规范，由 FML 生成并加载配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        Config.init();
        GuardEvents.register();
        LOGGER.info("个人维度模组通用初始化完成");
    }

    /**
     * 服务器启动完成、玩家登录前接管重载的个人维度。
     *
     * <p>动态注册的 LevelStem 会被写入 level.dat 并在重载时复原，
     * vanilla 随即以共享时间的 DerivedLevelData 重建这些维度；此时将它们换回
     * 隔离时间的 PersonalDimensionLevelData，恢复昼夜开关与时刻设置。
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ShelterUpgradeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        PersonalDimensionService.adoptReloadedLevels(event.getServer());
    }
}
