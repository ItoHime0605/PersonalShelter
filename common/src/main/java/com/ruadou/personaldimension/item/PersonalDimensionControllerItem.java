package com.ruadou.personaldimension.item;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 个人维度控制器物品。
 * 右键打开控制器 GUI（信息/设置）。GUI 仅展示客户端已同步的当前维度数据，
 * 因此打开动作只在客户端执行，服务端不做任何处理。
 */
public class PersonalDimensionControllerItem extends Item {

    public PersonalDimensionControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 仅客户端打开界面；Platform 实现委托给 ClientScreenOpener，客户端类只在方法调用时加载
        if (level.isClientSide()) {
            Platform.openControllerScreen();
        }
        return InteractionResultHolder.success(stack);
    }
}
