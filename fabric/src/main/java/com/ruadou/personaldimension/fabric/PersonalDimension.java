package com.ruadou.personaldimension.fabric;



import com.ruadou.personaldimension.fabric.attachment.ModAttachments;
import com.ruadou.personaldimension.fabric.block.ModBlocks;
import com.ruadou.personaldimension.fabric.block.entity.ModBlockEntities;
import com.ruadou.personaldimension.fabric.dimension.DynamicDimensionManager;
import com.ruadou.personaldimension.fabric.platform.FabricPlatformProvider;
import com.ruadou.personaldimension.dimension.GuardEvents;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.dimension.PersonalDimensionService;
import com.ruadou.personaldimension.fabric.dimension.template.ModChunkGenerators;
import com.ruadou.personaldimension.fabric.item.ModCreativeModeTabs;
import com.ruadou.personaldimension.fabric.item.ModItems;
import com.ruadou.personaldimension.fabric.menu.ModMenus;
import com.ruadou.personaldimension.fabric.network.ModNetworking;
import com.ruadou.personaldimension.spawn.SpawnControlEvents;

import com.ruadou.personaldimension.platform.Platform;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric 端模组主类：装配各注册器、网络、数据附着与配置。
 *
 * <p>注：事件监听、网络包、数据附着、配置等后续逐步迁移，
 * 当前先完成注册系统，确保基础编译通过。
 */
public class PersonalDimension extends com.ruadou.personaldimension.PersonalDimension implements ModInitializer {

    @Override
    public void onInitialize() {
        // 必须最先初始化平台抽象层，后续所有代码都可能依赖 Platform
        Platform.init(new FabricPlatformProvider());

        // 初始化配置系统（缓存逻辑委托给 common 的 ConfigManager）
        Config.init();

        // 注册网络包 Payload 类型（必须在接收器之前）
        ModNetworking.registerPayloads();

        // 注册数据附着（必须在使用前）
        ModAttachments.init();

        // 注册顺序：物品 → 方块 → 方块实体 → 菜单 → 生成器 → 创造栏
        // （方块依赖物品的 BlockItem 注册，创造栏依赖物品和方块）
        ModItems.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModChunkGenerators.init();
        ModCreativeModeTabs.init();

        // 注册网络包接收器（服务端）
        ModNetworking.registerServerReceivers();

        // 注册事件监听器
        SpawnControlEvents.register();
        GuardEvents.register();

        // 服务器启动后重新加载已存在的个人维度并接管
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // 先扫描存档目录，重新创建所有已存在的个人维度
            DynamicDimensionManager.reloadExistingDimensions(server,
                levelKey -> ModDimensions.createPersonalStem(server));
            // 然后接管重建的维度，恢复时间/天气/月相设置
            PersonalDimensionService.adoptReloadedLevels(server);
        });

        LOGGER.info("个人维度模组 (Fabric) 初始化完成");
    }
}
