package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.friend.VisitShelterEntry;
import com.ruadou.personaldimension.network.RequestVisitListPacket;
import com.ruadou.personaldimension.network.RequestVisitPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 访问维度界面（访客自助）。
 *
 * <p>列出"我被授予 {@link com.ruadou.personaldimension.friend.Permission#ENTER 进入}权限、
 * 且维度已存在"的他人个人维度（主人无需在线），点击"进入"即请求前往。
 * 权限判定完全由服务端 {@link com.ruadou.personaldimension.dimension.PersonalDimensionService#handleVisit} 裁决。
 */
public class VisitScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_H = 26;
    private static final int ENTER_BTN_W = 52;

    private List<VisitShelterEntry> lastList;
    private int scrollOffset;
    private boolean draggingBar;

    public VisitScreen(Screen parent) {
        super(Component.translatable("gui.personaldimension.visit.title"), parent);
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestVisitListPacket());
    }

    @Override
    protected void buildWidgets() {
        List<VisitShelterEntry> owners = ClientFriendData.visitList();
        this.lastList = owners;

        int maxOffset = Math.max(0, owners.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);

        int listTop = listTop();
        int btnX = enterButtonX();
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < owners.size(); i++) {
            VisitShelterEntry owner = owners.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.visit.enter"),
                btnX, rowY + 1, ENTER_BTN_W, 20, b -> visit(owner)));
        }

        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft(), this.topPos + PANEL_HEIGHT - PADDING - 20, contentWidth(), 20, b -> back()));
    }

    /** 请求前往目标维度并关闭界面，便于玩家看到传送结果。 */
    private void visit(VisitShelterEntry owner) {
        Platform.sendToServer(new RequestVisitPacket(owner.id()));
        onClose();
    }

    @Override
    protected void onTick() {
        if (!Objects.equals(ClientFriendData.visitList(), this.lastList)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<VisitShelterEntry> owners = ClientFriendData.visitList();
        int listTop = listTop();

        if (owners.isEmpty()) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.visit.empty"),
                contentLeft(), listTop + 4, ModernStyle.TEXT_SUB, false);
        }
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < owners.size(); i++) {
            VisitShelterEntry owner = owners.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            FriendIcons.drawArrow(graphics, contentLeft(), rowY + 6, ModernStyle.SUCCESS);
            int nameX = contentLeft() + 12;
            int nameW = enterButtonX() - nameX - 4;
            graphics.drawString(this.font, GuiText.ellipsize(this.font, owner.ownerName(), nameW),
                nameX, rowY + 2, ModernStyle.TEXT, false);
            graphics.drawString(this.font, GuiText.ellipsize(this.font, owner.shelterName(), nameW),
                nameX, rowY + 13, ModernStyle.TEXT_SUB, false);
        }
        if (owners.size() > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, owners.size()) + "/" + owners.size()),
                this.leftPos + PANEL_WIDTH - PADDING - 40, this.topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(owners.size(), VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, owners.size());
        }
    }

    private int scrollbarX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH;
    }

    /** 行尾"进入"按钮左沿 x（滚动条通道之左）。 */
    private int enterButtonX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH - ScrollBar.GAP - ENTER_BTN_W;
    }

    private int listTop() {
        return this.topPos + 20;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = ClientFriendData.visitList().size();
        int maxOffset = Math.max(0, size - VISIBLE_ROWS);
        int next = Math.max(0, Math.min(maxOffset, this.scrollOffset - (int) Math.signum(scrollY)));
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ScrollBar.needed(ClientFriendData.visitList().size(), VISIBLE_ROWS)
            && ScrollBar.isOver(mouseX, mouseY, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H)) {
            this.draggingBar = true;
            setScrollByMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar && button == 0) {
            setScrollByMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 由鼠标 y 设置滚动偏移并重建行控件（仅在偏移变化时）。 */
    private void setScrollByMouse(double mouseY) {
        int total = ClientFriendData.visitList().size();
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, total);
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
    }
}
