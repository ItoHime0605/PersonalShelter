package com.ruadou.personaldimension.neoforge.menu;

import com.ruadou.personaldimension.neoforge.item.ModItems;
import com.ruadou.personaldimension.dimension.PersonalDimensionData;
import com.ruadou.personaldimension.dimension.ModDimensions;
import com.ruadou.personaldimension.shelter.ShelterPermitTransaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative single permit slot, centered over the player inventory. */
public final class ShelterUpgradeMenu extends AbstractContainerMenu {
    private final Container permit = new SimpleContainer(1);

    public ShelterUpgradeMenu(int id, Inventory inventory) {
        super(ModMenus.SHELTER_UPGRADE_MENU.get(), id);
        addSlot(new Slot(permit, 0, 80, 57) {
            @Override public boolean mayPlace(ItemStack stack) { return isPermit(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, 9 + col + row * 9, 8 + col * 18, 112 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 170));
    }

    public static boolean isPermit(ItemStack stack) {
        return stack.is(ModItems.SHELTER_PERMIT_LV1.get()) || stack.is(ModItems.SHELTER_PERMIT_LV2.get())
            || stack.is(ModItems.SHELTER_PERMIT_LV3.get()) || stack.is(ModItems.SHELTER_PERMIT_LV4.get());
    }

    public ItemStack permitStack() { return permit.getItem(0); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!isPermit(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** Vanilla inventory-button packet; the client never supplies an item or target level. */
    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != 0 || !(player instanceof ServerPlayer actor) || !stillValid(player)) return false;
        if (!(actor.level() instanceof ServerLevel shelter)) return false;
        PersonalDimensionData data = shelter.getDataStorage().get(PersonalDimensionData.FACTORY, PersonalDimensionData.ID);
        if (data == null) {
            actor.sendSystemMessage(Component.literal("尚未建立個人空間資料。"));
            return false;
        }
        ShelterPermitTransaction.Result result = ShelterPermitTransaction.tryUpgrade(actor, shelter, data, permit.getItem(0));
        if (result == ShelterPermitTransaction.Result.SUCCESS) {
            permit.setChanged();
            broadcastChanges();
            actor.sendSystemMessage(Component.literal("土地擴張成功！"));
            return true;
        }
        actor.sendSystemMessage(Component.literal(switch (result) {
            case NOT_OWNER -> "只有空間擁有者可以擴張土地。";
            case EMPTY_STACK -> "請先放入擴充許可證。";
            case INVALID_PERMIT -> "這不是有效的擴充許可證。";
            case WRONG_LEVEL -> "許可證等級不符，必須依序擴張。";
            case OCCUPIED -> "新增土地範圍已有方塊，無法安全擴張。";
            case PLACEMENT_FAILED -> "土地生成失敗，許可證未消耗。";
            case SUCCESS -> "土地擴張成功！";
        }));
        return false;
    }

    @Override public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer actor) || !(actor.level() instanceof ServerLevel level)) {
            // Client cannot resolve the server's ownership registry; server is authoritative.
            return true;
        }
        return ModDimensions.ownerOf(level.dimension())
            .map(uuid -> uuid.equals(actor.getUUID())).orElse(false);
    }

    @Override public void removed(Player player) {
        super.removed(player);
        clearContainer(player, permit);
    }
}
