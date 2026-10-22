package com.ruadou.personaldimension.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Four tiers of shelter land-expansion permits. */
public final class ShelterPermitItem extends Item {
    private final int level;
    public ShelterPermitItem(int level, Properties properties) {
        super(properties);
        if (level < 1 || level > 4) throw new IllegalArgumentException("level");
        this.level = level;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("寸土寸金，在這荒涼的災後世界，能有個窩就是不一樣。")
            .withStyle(ChatFormatting.WHITE));
        int size = 47 + level * 32;
        tooltip.add(Component.literal("可將私人土地擴充為LV." + level + " (" + size + "x" + size + ")")
            .withStyle(ChatFormatting.GRAY));
    }
}
