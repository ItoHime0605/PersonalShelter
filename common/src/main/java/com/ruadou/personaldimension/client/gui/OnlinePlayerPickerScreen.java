package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.client.FriendDataSnapshot;
import com.ruadou.personaldimension.client.FriendDataSnapshot.FriendView;
import com.ruadou.personaldimension.friend.NamedPlayer;
import com.ruadou.personaldimension.network.FriendConfigActionPacket;
import com.ruadou.personaldimension.network.RequestFriendDataPacket;
import com.ruadou.personaldimension.network.RequestOnlinePlayersPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 从在线玩家添加好友界面（维度主人）。
 *
 * <p>列出服务器当前在线玩家（不含自己），点击"添加"即把该玩家加为好友；已是好友者显示"已添加"且禁用。
 * 在线列表与好友列表均由服务端权威提供，客户端仅展示与转发点击。
 */
public class OnlinePlayerPickerScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_H = 22;
    private static final int ADD_BTN_W = 72;

    private List<NamedPlayer> lastOnline;
    private FriendDataSnapshot lastConfig;
    private int scrollOffset;
    private boolean draggingBar;

    public OnlinePlayerPickerScreen(Screen parent) {
        super(Component.translatable("gui.personaldimension.friend.online_title"), parent);
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestOnlinePlayersPacket());
        Platform.sendToServer(new RequestFriendDataPacket());
    }

    @Override
    protected void buildWidgets() {
        List<NamedPlayer> online = ClientFriendData.onlinePlayers();
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        this.lastOnline = online;
        this.lastConfig = config;

        int maxOffset = Math.max(0, online.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);

        Set<UUID> friendIds = friendIds(config);
        int listTop = listTop();
        int btnX = addButtonX();

        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < online.size(); i++) {
            NamedPlayer p = online.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            boolean already = friendIds.contains(p.id());
            FlatButton add = new FlatButton(
                Component.translatable(already
                    ? "gui.personaldimension.friend.added"
                    : "gui.personaldimension.friend.add"),
                btnX, rowY + 1, ADD_BTN_W, 20,
                b -> Platform.sendToServer(FriendConfigActionPacket.addFriend(p.id())));
            add.active = !already;
            this.addRenderableWidget(add);
        }

        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft(), topPos + PANEL_HEIGHT - PADDING - 20, contentWidth(), 20, b -> back()));
    }

    private static Set<UUID> friendIds(FriendDataSnapshot config) {
        Set<UUID> ids = new HashSet<>();
        if (config != null) {
            for (FriendView f : config.friends()) {
                ids.add(f.id());
            }
        }
        return ids;
    }

    @Override
    protected void onTick() {
        if (!Objects.equals(ClientFriendData.onlinePlayers(), this.lastOnline)
            || !Objects.equals(ClientFriendData.ownConfig(), this.lastConfig)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<NamedPlayer> online = ClientFriendData.onlinePlayers();
        int listTop = listTop();

        if (online.isEmpty()) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.friend.online_empty"),
                contentLeft(), listTop + 4, ModernStyle.TEXT_SUB, false);
        }
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < online.size(); i++) {
            NamedPlayer p = online.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            FriendIcons.drawPerson(graphics, contentLeft(), rowY + 5, ModernStyle.TEXT_SUB);
            int nameX = contentLeft() + 14;
            int nameW = addButtonX() - nameX - 4;
            graphics.drawString(this.font, GuiText.ellipsize(this.font, p.name(), nameW),
                nameX, rowY + 6, ModernStyle.TEXT, false);
        }
        if (online.size() > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, online.size()) + "/" + online.size()),
                this.leftPos + PANEL_WIDTH - PADDING - 40, topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(online.size(), VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, online.size());
        }
    }

    private int scrollbarX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH;
    }

    /** 行尾"添加"按钮左沿 x（滚动条通道之左）。 */
    private int addButtonX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH - ScrollBar.GAP - ADD_BTN_W;
    }

    private int listTop() {
        return this.topPos + 20;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = ClientFriendData.onlinePlayers().size();
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
        if (button == 0 && ScrollBar.needed(ClientFriendData.onlinePlayers().size(), VISIBLE_ROWS)
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
        int total = ClientFriendData.onlinePlayers().size();
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, total);
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
    }
}
