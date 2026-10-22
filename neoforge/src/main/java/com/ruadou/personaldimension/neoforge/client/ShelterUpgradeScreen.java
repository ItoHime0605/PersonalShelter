package com.ruadou.personaldimension.neoforge.client;

import com.ruadou.personaldimension.neoforge.menu.ShelterUpgradeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Single centered permit slot; server validates and executes every upgrade. */
public final class ShelterUpgradeScreen extends AbstractContainerScreen<ShelterUpgradeMenu> {
    public ShelterUpgradeScreen(ShelterUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 194;
        inventoryLabelY = 100;
        titleLabelX = 8;
        titleLabelY = 8;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("上一頁"), b -> {
            onClose();
            var controller = new com.ruadou.personaldimension.client.gui.PersonalDimensionControllerScreen();
            controller.showSettingsHome();
            Minecraft.getInstance().setScreen(controller);
        }).bounds(leftPos + 111, topPos + 7, 57, 18).build());
        if (menu.shelterLevel() >= 4) return;
        Button confirm = Button.builder(Component.literal("使用許可證擴張"), b -> {
            if (Minecraft.getInstance().gameMode != null)
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, 0);
        })
            .bounds(leftPos + 25, topPos + 84, 126, 18).build();
        addRenderableWidget(confirm);
    }

    private int lastLevel = -1;
    @Override public void containerTick() {
        super.containerTick();
        if (lastLevel != menu.shelterLevel()) {
            lastLevel = menu.shelterLevel();
            rebuildWidgets();
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.literal("土地擴張"), 8, 8, 0xFFE6E8ED, false);
        if (menu.shelterLevel() >= 4) {
            String text = "你已擁有太多土地，留點給別人吧！";
            int tx = Math.max(8, (imageWidth - font.width(text)) / 2);
            if (font.width(text) > imageWidth - 16) {
                graphics.drawString(font, Component.literal("你已擁有太多土地，"), 28, 38, 0xFFFFD38A, false);
                graphics.drawString(font, Component.literal("留點給別人吧！"), 36, 51, 0xFFFFD38A, false);
            } else graphics.drawString(font, Component.literal(text), tx, 38, 0xFFFFD38A, false);
            return;
        }
        String heading = "末日房產特惠！";
        graphics.drawString(font, Component.literal(heading), (imageWidth - font.width(heading)) / 2, 24, 0xFFFFD38A, false);
        graphics.drawString(font, Component.literal("一張許可證，換來更多安身之地。"), 8, 38, 0xFFE0E3EA, false);
        graphics.drawString(font, Component.literal("這筆買賣，保證物超所值！"), 8, 48, 0xFFADB7C6, false);
        graphics.drawString(font, Component.literal("物品欄"), 8, 100, 0xFFD9E1EB, false);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252F);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + 170, topPos + 190, 0xFF303744);
        if (menu.shelterLevel() < 4) graphics.fill(leftPos + 79, topPos + 56, leftPos + 97, topPos + 74, 0xFF111820);
        graphics.fill(leftPos + 7, topPos + 111, leftPos + 169, topPos + 188, 0xFF252B35);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slotBg(graphics, 8 + col * 18, 112 + row * 18);
        for (int col = 0; col < 9; col++) slotBg(graphics, 8 + col * 18, 170);
    }

    private void slotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xFF111820);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
