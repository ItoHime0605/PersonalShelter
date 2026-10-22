package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.client.FriendDataSnapshot;
import com.ruadou.personaldimension.client.FriendDataSnapshot.FriendView;
import com.ruadou.personaldimension.client.FriendDataSnapshot.GroupView;
import com.ruadou.personaldimension.network.FriendConfigActionPacket;
import com.ruadou.personaldimension.network.RequestFriendDataPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 好友管理界面（维度主人）。
 *
 * <p>列出本人的好友（人物图标 + 名称 + 所属组），每行可循环切换其权限组或将其移除；
 * 底部支持"按 UUID 添加"与跳转到"从在线玩家添加""权限组管理"。
 * 所有改动经 {@link FriendConfigActionPacket}（以发送者为主人）下发，服务端回传刷新。
 */
public class FriendManagerScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 4;
    private static final int ROW_H = 22;
    private static final int REMOVE_BTN_W = 44;
    private static final int GROUP_BTN_W = 96;
    private static final int BTN_GAP = 6;

    private FriendDataSnapshot lastConfig; // 上次据以构建控件的配置快照，用于检测变化
    private int scrollOffset;
    private boolean draggingBar;
    private EditBox uuidInput;
    private boolean uuidInvalid;

    public FriendManagerScreen(Screen parent) {
        super(Component.translatable("gui.personaldimension.friend.title"), parent);
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestFriendDataPacket());
    }

    @Override
    protected void buildWidgets() {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        this.lastConfig = config;
        List<FriendView> friends = config == null ? List.of() : config.friends();
        List<GroupView> groups = config == null ? List.of() : config.groups();

        int maxOffset = Math.max(0, friends.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);

        int listTop = listTop();
        int removeX = removeButtonX();
        int groupX = groupButtonX();

        // 好友行：每行右侧放"组"循环按钮与"移除"按钮（名称/图标在 renderContent 绘制）
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < friends.size(); i++) {
            FriendView f = friends.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            this.addRenderableWidget(new FlatButton(groupLabel(f.group()),
                groupX, rowY + 1, GROUP_BTN_W, 20, b -> cycleGroup(f, groups)));
            this.addRenderableWidget(new FlatButton(
                Component.translatable("gui.personaldimension.friend.remove"),
                removeX, rowY + 1, REMOVE_BTN_W, 20,
                b -> Platform.sendToServer(FriendConfigActionPacket.removeFriend(f.id()))));
        }

        // —— 底部：按 UUID 添加 ——
        int inputY = topPos + 110;
        int addW = 96;
        int inputW = contentWidth() - addW - 6;
        this.uuidInput = new EditBox(this.font, contentLeft(), inputY, inputW, 20,
            Component.translatable("gui.personaldimension.friend.uuid_hint"));
        this.uuidInput.setMaxLength(36);
        this.uuidInput.setHint(Component.translatable("gui.personaldimension.friend.uuid_hint"));
        this.uuidInput.setResponder(s -> this.uuidInvalid = false);
        this.addRenderableWidget(this.uuidInput);
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.friend.add_by_uuid"),
            contentLeft() + inputW + 6, inputY, addW, 20, b -> submitUuid()));

        // —— 跳转入口 ——
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.friend.add_from_online"),
            contentLeft(), topPos + 144, contentWidth(), 20,
            b -> this.minecraft.setScreen(new OnlinePlayerPickerScreen(this))));
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.friend.manage_groups"),
            contentLeft(), topPos + 168, contentWidth(), 20,
            b -> this.minecraft.setScreen(new PermissionGroupScreen(this))));

        // —— 返回 ——
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft(), topPos + PANEL_HEIGHT - PADDING - 20, contentWidth(), 20, b -> back()));
    }

    /** 组标签："组: 名称"，未分配时显示"组: 未分配"。 */
    private Component groupLabel(String group) {
        Component value = group == null || group.isEmpty()
            ? Component.translatable("gui.personaldimension.friend.group_none")
            : Component.literal(group);
        return Component.translatable("gui.personaldimension.friend.group_prefix").append(value);
    }

    /** 循环切换好友所属组：序列为 [未分配, 组1, 组2, ...]，点击轮转到下一个。 */
    private void cycleGroup(FriendView f, List<GroupView> groups) {
        List<String> seq = new ArrayList<>();
        seq.add(""); // 未分配
        for (GroupView g : groups) {
            seq.add(g.name());
        }
        int idx = seq.indexOf(f.group() == null ? "" : f.group());
        if (idx < 0) {
            idx = 0; // 组已被删除，视为未分配
        }
        String next = seq.get((idx + 1) % seq.size());
        Platform.sendToServer(FriendConfigActionPacket.assignGroup(f.id(), next));
    }

    /** 解析 UUID 输入并下发添加；格式非法则标红提示，不发送。 */
    private void submitUuid() {
        if (this.uuidInput == null) {
            return;
        }
        String text = this.uuidInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            UUID id = UUID.fromString(text);
            Platform.sendToServer(FriendConfigActionPacket.addFriend(id));
            this.uuidInput.setValue("");
            this.uuidInvalid = false;
        } catch (IllegalArgumentException e) {
            this.uuidInvalid = true;
        }
    }

    @Override
    protected void onTick() {
        // 配置内容变化（增删好友 / 改组）时重建行控件
        FriendDataSnapshot cur = ClientFriendData.ownConfig();
        if (!Objects.equals(cur, this.lastConfig)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        List<FriendView> friends = config == null ? List.of() : config.friends();
        int listTop = listTop();

        if (friends.isEmpty()) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.friend.empty"),
                contentLeft(), listTop + 4, ModernStyle.TEXT_SUB, false);
        }
        // 好友行：人物图标 + 名称（右侧按钮由控件绘制）
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < friends.size(); i++) {
            FriendView f = friends.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            FriendIcons.drawPerson(graphics, contentLeft(), rowY + 5, ModernStyle.TEXT_SUB);
            int nameX = contentLeft() + 14;
            int nameW = groupButtonX() - nameX - 4;
            graphics.drawString(this.font, GuiText.ellipsize(this.font, f.name(), nameW),
                nameX, rowY + 6, ModernStyle.TEXT, false);
        }

        // 滚动指示（好友数超过一屏时）
        if (friends.size() > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, friends.size()) + "/" + friends.size()),
                this.leftPos + PANEL_WIDTH - PADDING - 40, topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(friends.size(), VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, friends.size());
        }

        // UUID 非法提示
        if (this.uuidInvalid) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.friend.uuid_invalid"),
                contentLeft(), topPos + 132, ModernStyle.DANGER, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = friendCount();
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
        if (button == 0 && ScrollBar.needed(friendCount(), VISIBLE_ROWS)
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
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, friendCount());
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
    }

    private int friendCount() {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        return config == null ? 0 : config.friends().size();
    }

    private int scrollbarX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH;
    }

    /** 行尾"移除"按钮左沿 x（滚动条通道之左）。 */
    private int removeButtonX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH - ScrollBar.GAP - REMOVE_BTN_W;
    }

    /** "组"循环按钮左沿 x（在"移除"按钮之左）。 */
    private int groupButtonX() {
        return removeButtonX() - BTN_GAP - GROUP_BTN_W;
    }

    private int listTop() {
        return this.topPos + 20;
    }
}
