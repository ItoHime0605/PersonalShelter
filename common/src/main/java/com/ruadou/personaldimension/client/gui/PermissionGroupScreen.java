package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientFriendData;
import com.ruadou.personaldimension.client.FriendDataSnapshot;
import com.ruadou.personaldimension.client.FriendDataSnapshot.GroupView;
import com.ruadou.personaldimension.config.ConfigManager;
import com.ruadou.personaldimension.friend.Permission;
import com.ruadou.personaldimension.network.FriendConfigActionPacket;
import com.ruadou.personaldimension.network.RequestFriendDataPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 权限组管理界面（维度主人）。
 *
 * <p>左列为权限组列表（金色菱形标记默认组），可新建 / 重命名 / 删除 / 设为默认；
 * 右列为当前所选组的全部权限复选框（超过可见行数时显示滚动条），点击即开关。命名时切换为输入覆盖层。
 * 所有改动经 {@link FriendConfigActionPacket} 下发，服务端回传刷新后界面按内容变化重建。
 */
public class PermissionGroupScreen extends FriendBaseScreen {

    private static final int LEFT_W = 118;
    private static final int RIGHT_W = 158;
    private static final int ROW_H = 15;
    private static final int VISIBLE_GROUPS = 5;
    /** 右列权限一次可见行数；超出则显示滚动条（参考切石机右侧滚动条）。 */
    private static final int VISIBLE_PERMS = 9;

    /** 当前可用的权限列表（按配置过滤，每次重建界面时刷新）。 */
    private Permission[] visiblePermissions = Permission.values();

    /** 命名覆盖层的用途。 */
    private enum NamingMode { NONE, CREATE, RENAME }

    private FriendDataSnapshot lastConfig;
    private String selectedGroup;
    private int groupScroll;
    private int permScroll;
    private boolean draggingPermBar;
    private boolean draggingGroupBar;
    private NamingMode namingMode = NamingMode.NONE;
    private EditBox nameInput;

    public PermissionGroupScreen(Screen parent) {
        super(Component.translatable("gui.personaldimension.group.title"), parent);
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestFriendDataPacket());
    }

    private int leftX() {
        return contentLeft();
    }

    private int rightX() {
        return this.leftPos + PADDING + LEFT_W + 8;
    }

    private int rowsTop() {
        return this.topPos + 34;
    }

    /** 右列权限最大可滚动偏移（0 表示无需滚动）。 */
    private int maxPermScroll() {
        return Math.max(0, this.visiblePermissions.length - VISIBLE_PERMS);
    }

    /** 右列权限滚动条轨道左沿 x（贴右列右缘）。 */
    private int scrollbarX() {
        return rightX() + RIGHT_W - ScrollBar.WIDTH;
    }

    /** 右列权限滚动条轨道高度（覆盖可见权限行区域）。 */
    private int scrollbarTrackH() {
        return VISIBLE_PERMS * ROW_H;
    }

    /** 左列组列表滚动条轨道左沿 x（贴左列右缘）。 */
    private int groupScrollbarX() {
        return leftX() + LEFT_W - ScrollBar.WIDTH;
    }

    /** 左列组列表滚动条轨道高度（覆盖可见组行区域）。 */
    private int groupTrackH() {
        return VISIBLE_GROUPS * ROW_H;
    }

    @Override
    protected void buildWidgets() {
        // 刷新可用权限列表（按配置过滤被禁用的权限项）
        this.visiblePermissions = ConfigManager.availablePermissions().toArray(new Permission[0]);

        FriendDataSnapshot config = ClientFriendData.ownConfig();
        this.lastConfig = config;
        ensureSelection(config);

        if (this.namingMode != NamingMode.NONE) {
            buildNamingOverlay();
            return;
        }

        List<GroupView> groups = config == null ? List.of() : config.groups();
        int maxOffset = Math.max(0, groups.size() - VISIBLE_GROUPS);
        this.groupScroll = Math.min(this.groupScroll, maxOffset);

        boolean hasSelection = this.selectedGroup != null;

        // —— 左列底部：4 个组操作按钮（2x2）——
        int btnW = 56;
        int gap = 6;
        int col2 = leftX() + btnW + gap;
        int rowA = this.topPos + 118;
        int rowB = rowA + 22;
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.group.create"),
            leftX(), rowA, btnW, 18, b -> beginNaming(NamingMode.CREATE)));
        FlatButton rename = new FlatButton(
            Component.translatable("gui.personaldimension.group.rename"),
            col2, rowA, btnW, 18, b -> beginNaming(NamingMode.RENAME));
        rename.active = hasSelection;
        this.addRenderableWidget(rename);
        FlatButton delete = new FlatButton(
            Component.translatable("gui.personaldimension.group.delete"),
            leftX(), rowB, btnW, 18,
            b -> Platform.sendToServer(FriendConfigActionPacket.deleteGroup(this.selectedGroup)));
        delete.active = hasSelection;
        this.addRenderableWidget(delete);
        FlatButton setDefault = new FlatButton(
            Component.translatable("gui.personaldimension.group.set_default"),
            col2, rowB, btnW, 18,
            b -> Platform.sendToServer(FriendConfigActionPacket.setDefaultGroup(this.selectedGroup)));
        setDefault.active = hasSelection;
        this.addRenderableWidget(setDefault);

        // —— 返回 ——
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft(), this.topPos + PANEL_HEIGHT - PADDING - 20, contentWidth(), 20, b -> back()));
    }

    /** 命名覆盖层：提示 + 输入框 + 确认 / 取消。 */
    private void buildNamingOverlay() {
        int boxW = 200;
        int boxX = this.leftPos + (PANEL_WIDTH - boxW) / 2;
        this.nameInput = new EditBox(this.font, boxX, this.topPos + 90, boxW, 20,
            Component.translatable("gui.personaldimension.group.name_hint"));
        this.nameInput.setMaxLength(24);
        this.nameInput.setHint(Component.translatable("gui.personaldimension.group.name_hint"));
        if (this.namingMode == NamingMode.RENAME && this.selectedGroup != null) {
            this.nameInput.setValue(this.selectedGroup);
        }
        this.addRenderableWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);

        int btnW = 90;
        int gap = 8;
        int totalW = btnW * 2 + gap;
        int x = this.leftPos + (PANEL_WIDTH - totalW) / 2;
        int y = this.topPos + 120;
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.group.confirm"),
            x, y, btnW, 20, b -> confirmNaming()));
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.delete.cancel"),
            x + btnW + gap, y, btnW, 20, b -> cancelNaming()));
    }

    /** 确保 selectedGroup 指向一个存在的组；否则回落到首个组或 null。 */
    private void ensureSelection(FriendDataSnapshot config) {
        List<GroupView> groups = config == null ? List.of() : config.groups();
        if (this.selectedGroup != null) {
            for (GroupView g : groups) {
                if (g.name().equals(this.selectedGroup)) {
                    return;
                }
            }
        }
        this.selectedGroup = groups.isEmpty() ? null : groups.get(0).name();
    }

    private int selectedMask() {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        if (config != null && this.selectedGroup != null) {
            for (GroupView g : config.groups()) {
                if (g.name().equals(this.selectedGroup)) {
                    return g.mask();
                }
            }
        }
        return 0;
    }

    private void beginNaming(NamingMode mode) {
        this.namingMode = mode;
        rebuildWidgets();
    }

    private void cancelNaming() {
        this.namingMode = NamingMode.NONE;
        rebuildWidgets();
    }

    private void confirmNaming() {
        if (this.nameInput == null) {
            return;
        }
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty()) {
            if (this.namingMode == NamingMode.CREATE) {
                Platform.sendToServer(FriendConfigActionPacket.createGroup(name));
                this.selectedGroup = name; // 乐观选中，刷新到达后校正
            } else if (this.namingMode == NamingMode.RENAME && this.selectedGroup != null) {
                Platform.sendToServer(FriendConfigActionPacket.renameGroup(this.selectedGroup, name));
                this.selectedGroup = name;
            }
        }
        this.namingMode = NamingMode.NONE;
        rebuildWidgets();
    }

    @Override
    protected void onTick() {
        if (!Objects.equals(ClientFriendData.ownConfig(), this.lastConfig)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.namingMode != NamingMode.NONE) {
            String key = this.namingMode == NamingMode.CREATE
                ? "gui.personaldimension.group.create_prompt"
                : "gui.personaldimension.group.rename_prompt";
            graphics.drawString(this.font, Component.translatable(key),
                this.leftPos + (PANEL_WIDTH - this.font.width(Component.translatable(key))) / 2,
                this.topPos + 72, ModernStyle.TEXT, false);
            return;
        }

        FriendDataSnapshot config = ClientFriendData.ownConfig();
        List<GroupView> groups = config == null ? List.of() : config.groups();
        String defaultGroup = config == null ? "" : config.defaultGroup();

        // 列标题 + 竖直分隔线
        graphics.drawString(this.font, Component.translatable("gui.personaldimension.group.list_title"),
            leftX(), this.topPos + 20, ModernStyle.TEXT, false);
        graphics.drawString(this.font, Component.translatable("gui.personaldimension.group.perms_title"),
            rightX(), this.topPos + 20, ModernStyle.TEXT, false);
        graphics.fill(rightX() - 5, this.topPos + 20, rightX() - 4, this.topPos + 184, ModernStyle.DIVIDER);

        // —— 左列：组列表 ——
        if (groups.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.personaldimension.group.empty"),
                leftX(), rowsTop() + 4, ModernStyle.TEXT_SUB, false);
        }
        for (int i = 0; i < VISIBLE_GROUPS && this.groupScroll + i < groups.size(); i++) {
            GroupView g = groups.get(this.groupScroll + i);
            int rowY = rowsTop() + i * ROW_H;
            if (g.name().equals(this.selectedGroup)) {
                graphics.fill(leftX() - 2, rowY - 1, groupScrollbarX() - ScrollBar.GAP, rowY + ROW_H - 2, ModernStyle.withAlpha(ModernStyle.ACCENT, 0x33));
            }
            graphics.drawString(this.font,
                GuiText.ellipsize(this.font, g.name(), groupScrollbarX() - ScrollBar.GAP - 9 - leftX()),
                leftX(), rowY + 3, ModernStyle.TEXT, false);
            if (g.name().equals(defaultGroup)) {
                FriendIcons.drawDiamond(graphics, groupScrollbarX() - ScrollBar.GAP - 4, rowY + 6, 3, ModernStyle.WARN);
            }
        }
        if (groups.size() > VISIBLE_GROUPS) {
            graphics.drawString(this.font,
                Component.literal((this.groupScroll + 1) + "/" + groups.size()),
                leftX(), this.topPos + 8, ModernStyle.TEXT_SUB, false);
            ScrollBar.draw(graphics, groupScrollbarX(), rowsTop(), groupTrackH(),
                this.groupScroll, VISIBLE_GROUPS, groups.size());
        }

        // —— 右列：权限复选框（可滚动，仅显示配置启用的权限）——
        boolean enabled = this.selectedGroup != null;
        int mask = selectedMask();
        this.permScroll = Math.min(this.permScroll, maxPermScroll());
        for (int i = 0; i < VISIBLE_PERMS && this.permScroll + i < this.visiblePermissions.length; i++) {
            Permission p = this.visiblePermissions[this.permScroll + i];
            int rowY = rowsTop() + i * ROW_H;
            boolean checked = (mask & (1 << p.ordinal())) != 0;
            FriendIcons.drawCheckbox(graphics, rightX(), rowY, checked, enabled);
            graphics.drawString(this.font, Component.translatable(p.translationKey()),
                rightX() + 16, rowY + 2, enabled ? ModernStyle.TEXT : ModernStyle.TEXT_WEAK, false);
        }
        if (maxPermScroll() > 0) {
            ScrollBar.draw(graphics, scrollbarX(), rowsTop(), scrollbarTrackH(),
                this.permScroll, VISIBLE_PERMS, this.visiblePermissions.length);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.namingMode == NamingMode.NONE && button == 0) {
            // 右列权限滚动条：点击即抓取滑块，随后可拖动（优先于权限行判定，避免误触右列复选框）
            if (maxPermScroll() > 0
                && ScrollBar.isOver(mouseX, mouseY, scrollbarX(), rowsTop(), scrollbarTrackH())) {
                this.draggingPermBar = true;
                setPermScrollByMouse(mouseY);
                return true;
            }
            FriendDataSnapshot config = ClientFriendData.ownConfig();
            List<GroupView> groups = config == null ? List.of() : config.groups();
            // 左列组滚动条：点击即抓取滑块（优先于组行选中判定，避免误选）
            if (ScrollBar.needed(groups.size(), VISIBLE_GROUPS)
                && ScrollBar.isOver(mouseX, mouseY, groupScrollbarX(), rowsTop(), groupTrackH())) {
                this.draggingGroupBar = true;
                setGroupScrollByMouse(mouseY);
                return true;
            }
            // 左列组行：选中
            for (int i = 0; i < VISIBLE_GROUPS && this.groupScroll + i < groups.size(); i++) {
                int rowY = rowsTop() + i * ROW_H;
                if (mouseX >= leftX() - 2 && mouseX < leftX() + LEFT_W
                    && mouseY >= rowY - 1 && mouseY < rowY + ROW_H - 2) {
                    this.selectedGroup = groups.get(this.groupScroll + i).name();
                    return true;
                }
            }
            // 右列权限行：开关（需已选中组；按当前滚动偏移映射到实际权限项，且避开滚动条区域）
            if (this.selectedGroup != null) {
                int mask = selectedMask();
                for (int i = 0; i < VISIBLE_PERMS && this.permScroll + i < this.visiblePermissions.length; i++) {
                    int rowY = rowsTop() + i * ROW_H;
                    if (mouseX >= rightX() && mouseX < scrollbarX()
                        && mouseY >= rowY && mouseY < rowY + ROW_H) {
                        Permission p = this.visiblePermissions[this.permScroll + i];
                        boolean cur = (mask & (1 << p.ordinal())) != 0;
                        Platform.sendToServer(
                            FriendConfigActionPacket.setPermission(this.selectedGroup, p, !cur));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingPermBar) {
            setPermScrollByMouse(mouseY);
            return true;
        }
        if (button == 0 && this.draggingGroupBar) {
            setGroupScrollByMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingPermBar = false;
            this.draggingGroupBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 依据鼠标 y 将滑块中心对齐到光标，换算成右列权限滚动偏移。 */
    private void setPermScrollByMouse(double mouseY) {
        this.permScroll = ScrollBar.scrollFromMouse(
            mouseY, rowsTop(), scrollbarTrackH(), VISIBLE_PERMS, this.visiblePermissions.length);
    }

    /** 依据鼠标 y 将滑块中心对齐到光标，换算成左列组列表滚动偏移；变化时重建行控件。 */
    private void setGroupScrollByMouse(double mouseY) {
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        int total = config == null ? 0 : config.groups().size();
        int next = ScrollBar.scrollFromMouse(mouseY, rowsTop(), groupTrackH(), VISIBLE_GROUPS, total);
        if (next != this.groupScroll) {
            this.groupScroll = next;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.namingMode != NamingMode.NONE) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int step = -(int) Math.signum(scrollY);
        // 光标在右列权限区域内滚动权限，否则滚动左列组列表
        boolean overPerms = mouseX >= rightX() - 5 && mouseY >= rowsTop()
            && mouseY < rowsTop() + scrollbarTrackH();
        if (overPerms && maxPermScroll() > 0) {
            this.permScroll = Math.max(0, Math.min(maxPermScroll(), this.permScroll + step));
            return true;
        }
        FriendDataSnapshot config = ClientFriendData.ownConfig();
        int size = config == null ? 0 : config.groups().size();
        int maxOffset = Math.max(0, size - VISIBLE_GROUPS);
        int next = Math.max(0, Math.min(maxOffset, this.groupScroll + step));
        if (next != this.groupScroll) {
            this.groupScroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 命名输入时回车确认
        if (this.namingMode != NamingMode.NONE && this.nameInput != null
            && this.nameInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            confirmNaming();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
