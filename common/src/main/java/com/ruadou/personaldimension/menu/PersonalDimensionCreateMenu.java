package com.ruadou.personaldimension.menu;

import com.ruadou.personaldimension.platform.Platform;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 个人维度"创建维度"容器菜单。
 *
 * <p>唯一的自定义槽用于放入个人维度生成器（创建成功时消耗）。玩家背包与快捷栏为标准布局，
 * 便于 shift 快速取放生成器。预设选择、地形画笔等其余状态<b>不走容器</b>（为页面数据），
 * 遵循"只有生成器用容器、其余用页面数据"的约束，降低客户端-服务端同步复杂度。
 */
public class PersonalDimensionCreateMenu extends AbstractContainerMenu {

    /** 生成器槽（16x16 物品区）相对面板左上角的坐标，与界面右列绘制的凹槽对齐。 */
    private static final int GENERATOR_SLOT_X = 150;
    private static final int GENERATOR_SLOT_Y = 26;

    private final Container generator = new SimpleContainer(1);

    /** 是否为公共维度创建（服务端专用，客户端始终为 false）。 */
    private final boolean isPublic;

    public PersonalDimensionCreateMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, false);
    }

    public PersonalDimensionCreateMenu(int containerId, Inventory playerInventory, boolean isPublic) {
        super(Platform.createMenuType(), containerId);
        this.isPublic = isPublic;

        // 生成器槽：仅允许放入个人维度生成器，最多一个
        this.addSlot(new Slot(this.generator, 0, GENERATOR_SLOT_X, GENERATOR_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Platform.personalDimensionGeneratorItem());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // 玩家背包主格 3x9（与界面尺寸 222x244 对齐：inventoryLabelY=150，主格起始 y=162）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 162 + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 220));
        }
    }

    /** 生成器槽当前物品（可能为空）。 */
    public ItemStack getGenerator() {
        return this.generator.getItem(0);
    }

    /** 消耗生成器槽中的一个生成器（创建成功后、传送关闭容器前调用）。 */
    public void consumeGenerator() {
        this.generator.removeItem(0, 1);
    }

    /** 是否为公共维度创建（服务端专用）。 */
    public boolean isPublic() {
        return this.isPublic;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int inventoryStart = 1;               // 背包首槽（生成器槽 index 0 之后）
            int inventoryEnd = this.slots.size();  // 背包末槽（含快捷栏）
            if (index == 0) {
                // 生成器槽 -> 玩家背包
                if (!this.moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Platform.personalDimensionGeneratorItem())) {
                // 背包中的生成器 -> 生成器槽
                if (!this.moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY; // 非生成器物品不允许进入生成器槽
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 关闭界面时把生成器槽内剩余物品退还玩家，避免物品丢失
        this.clearContainer(player, this.generator);
    }
}
