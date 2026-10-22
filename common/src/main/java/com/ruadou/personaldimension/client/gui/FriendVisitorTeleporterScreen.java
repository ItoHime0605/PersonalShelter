package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.client.FriendDataSnapshot;
import com.ruadou.personaldimension.client.FriendVisitorTeleporterClientData;
import com.ruadou.personaldimension.network.RequestFriendDataPacket;
import com.ruadou.personaldimension.network.RequestFriendVisitorTeleporterStatusPacket;
import com.ruadou.personaldimension.network.SetFriendVisitorTeleporterPacket;
import com.ruadou.personaldimension.network.UnbindOtherTeleporterPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class FriendVisitorTeleporterScreen extends PanelScreen {

    private static final int ITEM_HEIGHT = 22;
    private static final int VISIBLE_COUNT = 5;

    private final BlockPos pos;
    private List<String> allGroups = new ArrayList<>();
    private List<String> boundToCurrent = new ArrayList<>();
    private List<String> boundToOthers = new ArrayList<>();
    private boolean controllerOnly = true;
    private String selectedGroup = null;
    private int scrollOffset = 0;
    private boolean draggingBar = false;
    private FriendDataSnapshot lastConfig;
    private int lastStatusHash = 0;

    public FriendVisitorTeleporterScreen(BlockPos pos) {
        super(Component.translatable("gui.personaldimension.friend_visitor_teleporter.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        Platform.sendToServer(new RequestFriendDataPacket());
        Platform.sendToServer(new RequestFriendVisitorTeleporterStatusPacket(this.pos));
        rebuild();
    }

    @Override
    public void tick() {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        List<String> currentBound = FriendVisitorTeleporterClientData.boundToCurrent();
        List<String> otherBound = FriendVisitorTeleporterClientData.boundToOthers();
        boolean co = FriendVisitorTeleporterClientData.controllerOnly();
        int statusHash = currentBound.hashCode() * 31 + otherBound.hashCode() * 17 + (co ? 1 : 0);

        boolean changed = false;
        if (config != null && config != this.lastConfig) {
            this.lastConfig = config;
            this.allGroups.clear();
            for (FriendDataSnapshot.GroupView group : config.groups()) {
                this.allGroups.add(group.name());
            }
            changed = true;
        }
        if (statusHash != this.lastStatusHash) {
            this.lastStatusHash = statusHash;
            this.boundToCurrent = new ArrayList<>(currentBound);
            this.boundToOthers = new ArrayList<>(otherBound);
            this.controllerOnly = co;
            changed = true;
        }
        if (changed) {
            rebuild();
        }
    }

    private int listWidth() {
        return contentWidth() - ScrollBar.WIDTH - 4;
    }

    private void rebuild() {
        clearWidgets();
        int x = contentLeft();
        int w = listWidth();
        int top = contentTop() + 14;

        for (int i = 0; i < Math.min(VISIBLE_COUNT, allGroups.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            String groupName = allGroups.get(idx);
            int y = top + i * ITEM_HEIGHT;

            State state = getState(groupName);
            boolean selected = groupName.equals(selectedGroup);
            FlatButton groupBtn = new FlatButton(Component.literal(groupName),
                x, y, w, 20, b -> selectGroup(groupName));
            applyState(groupBtn, state, selected);
            addRenderableWidget(groupBtn);
        }

        int dividerY = top + VISIBLE_COUNT * ITEM_HEIGHT + 8;
        int actionY = dividerY + 6;
        int gap = 4;
        int btnW = (w - gap) / 2;

        // 左：绑定/取消绑定切换按钮
        Component bindLabel;
        boolean bindSelected = false;
        if (selectedGroup != null) {
            State selState = getState(selectedGroup);
            if (selState == State.AVAILABLE) {
                bindLabel = Component.translatable("gui.personaldimension.friend_visitor_teleporter.bind");
                bindSelected = true;
            } else {
                bindLabel = Component.translatable("gui.personaldimension.friend_visitor_teleporter.unbind");
                bindSelected = true;
            }
        } else {
            bindLabel = Component.translatable("gui.personaldimension.friend_visitor_teleporter.bind");
            bindSelected = false;
        }
        FlatButton bindBtn = new FlatButton(bindLabel, x, actionY, btnW, 20, b -> toggleBind());
        bindBtn.setHighlighted(bindSelected);
        bindBtn.active = selectedGroup != null;
        addRenderableWidget(bindBtn);

        // 右：传送模式切换按钮
        Component modeLabel = controllerOnly
            ? Component.translatable("gui.personaldimension.friend_visitor_teleporter.controller_only")
            : Component.translatable("gui.personaldimension.friend_visitor_teleporter.all_methods");
        FlatButton modeBtn = new FlatButton(modeLabel, x + btnW + gap, actionY, btnW, 20, b -> {
            this.controllerOnly = !this.controllerOnly;
            saveData();
            Platform.sendToServer(new RequestFriendVisitorTeleporterStatusPacket(this.pos));
        });
        addRenderableWidget(modeBtn);

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            x, bottomY, w, 20, b -> onClose()));
    }

    private State getState(String groupName) {
        if (boundToCurrent.contains(groupName)) {
            return State.BOUND_CURRENT;
        }
        if (boundToOthers.contains(groupName)) {
            return State.BOUND_OTHER;
        }
        return State.AVAILABLE;
    }

    private void applyState(FlatButton btn, State state, boolean selected) {
        switch (state) {
            case BOUND_CURRENT -> {
                btn.setSelected(true);
                btn.setHighlighted(false);
                btn.setDisabledAppearance(false);
                btn.active = true;
            }
            case BOUND_OTHER -> {
                btn.setSelected(false);
                btn.setHighlighted(selected);
                btn.setDisabledAppearance(!selected);
                btn.active = true;
            }
            case AVAILABLE -> {
                btn.setSelected(false);
                btn.setHighlighted(selected);
                btn.setDisabledAppearance(false);
                btn.active = true;
            }
        }
    }

    private void selectGroup(String groupName) {
        if (groupName.equals(this.selectedGroup)) {
            this.selectedGroup = null;
        } else {
            this.selectedGroup = groupName;
        }
        rebuild();
    }

    private void toggleBind() {
        if (selectedGroup == null) return;
        State state = getState(selectedGroup);
        if (state == State.AVAILABLE) {
            List<String> newGroups = new ArrayList<>(boundToCurrent);
            newGroups.add(selectedGroup);
            Platform.sendToServer(new SetFriendVisitorTeleporterPacket(this.pos, newGroups, controllerOnly));
        } else if (state == State.BOUND_CURRENT) {
            List<String> newGroups = new ArrayList<>(boundToCurrent);
            newGroups.remove(selectedGroup);
            Platform.sendToServer(new SetFriendVisitorTeleporterPacket(this.pos, newGroups, controllerOnly));
        } else if (state == State.BOUND_OTHER) {
            Platform.sendToServer(new UnbindOtherTeleporterPacket(this.pos, selectedGroup));
        }
        this.selectedGroup = null;
        Platform.sendToServer(new RequestFriendVisitorTeleporterStatusPacket(this.pos));
    }

    private void saveData() {
        Platform.sendToServer(new SetFriendVisitorTeleporterPacket(this.pos, boundToCurrent, controllerOnly));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int top = contentTop() + 14;
        int listH = VISIBLE_COUNT * ITEM_HEIGHT;
        int totalCount = allGroups.size();

        if (isInListArea(mouseX, mouseY, top, listH)) {
            int old = scrollOffset;
            scrollOffset = Math.max(0, Math.min(ScrollBar.maxScroll(totalCount, VISIBLE_COUNT),
                scrollOffset + (deltaY > 0 ? -1 : 1)));
            if (scrollOffset != old) {
                rebuild();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = contentTop() + 14;
        int listH = VISIBLE_COUNT * ITEM_HEIGHT;
        int totalCount = allGroups.size();
        int barX = this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH;

        if (ScrollBar.isOver(mouseX, mouseY, barX, top, listH)) {
            draggingBar = true;
            scrollOffset = ScrollBar.scrollFromMouse(mouseY, top, listH, VISIBLE_COUNT, totalCount);
            rebuild();
            return true;
        }

        if (selectedGroup != null && !isInListArea(mouseX, mouseY, top, listH)) {
            if (!isOnActionButtons(mouseX, mouseY)) {
                selectedGroup = null;
                rebuild();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOnActionButtons(double mouseX, double mouseY) {
        int x = contentLeft();
        int w = listWidth();
        int top = contentTop() + 14;
        int dividerY = top + VISIBLE_COUNT * ITEM_HEIGHT + 8;
        int actionY = dividerY + 6;
        return mouseX >= x && mouseX <= x + w
            && mouseY >= actionY && mouseY <= actionY + 20;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            int top = contentTop() + 14;
            int listH = VISIBLE_COUNT * ITEM_HEIGHT;
            int totalCount = allGroups.size();
            scrollOffset = ScrollBar.scrollFromMouse(mouseY, top, listH, VISIBLE_COUNT, totalCount);
            rebuild();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInListArea(double mouseX, double mouseY, int top, int height) {
        return mouseX >= contentLeft() && mouseX <= contentLeft() + listWidth()
            && mouseY >= top && mouseY <= top + height;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font,
            Component.translatable("gui.personaldimension.friend_visitor_teleporter.select_groups"),
            contentLeft(), contentTop() + 2, ModernStyle.TEXT_SUB, false);

        int top = contentTop() + 14;
        int listH = VISIBLE_COUNT * ITEM_HEIGHT;
        int totalCount = allGroups.size();
        if (totalCount > VISIBLE_COUNT) {
            ScrollBar.draw(graphics,
                this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH,
                top, listH, scrollOffset, VISIBLE_COUNT, totalCount);
        }

        // 列表与操作区之间的分隔线
        int dividerY = top + VISIBLE_COUNT * ITEM_HEIGHT + 8;
        ModernStyle.divider(graphics, contentLeft(), dividerY, listWidth());
    }

    private enum State {
        BOUND_CURRENT,
        BOUND_OTHER,
        AVAILABLE
    }
}
