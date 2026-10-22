package com.ruadou.personaldimension.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 个人维度生成器物品（创建材料）。
 *
 * <p>本身不再具备右键功能：维度创建改由个人维度控制器的"创建维度"界面完成——
 * 生成器需放入该界面的槽位，创建成功后被消耗。右键仅在动作栏提示引导玩家使用控制器。
 */
public class PersonalDimensionGeneratorItem extends Item {

    public PersonalDimensionGeneratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                Component.translatable("personaldimension.message.use_controller"), true);
        }
        return InteractionResultHolder.success(stack);
    }
}
