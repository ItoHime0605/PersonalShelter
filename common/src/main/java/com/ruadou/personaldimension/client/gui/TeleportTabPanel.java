package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientTeleporterData;
import com.ruadou.personaldimension.dimension.TeleporterEntry;
import com.ruadou.personaldimension.network.RenameTeleporterPacket;
import com.ruadou.personaldimension.network.RequestTeleporterListPacket;
import com.ruadou.personaldimension.network.TeleportToTargetPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 传送标签页面板：展示玩家放置的所有维度传送器，支持传送、重命名和备注。
 *
 * <p>布局：
 * <ul>
 *   <li>非编辑态：列表占满内容区，底部左「编辑」右「计数」</li>
 *   <li>编辑态：列表 + 分隔线 + 底部编辑区（名称+备注+保存/重置+完成）</li>
 * </ul>
 */
class TeleportTabPanel {

    private static final int ROW_H = 22;
    private static final int TELEPORT_BTN_W = 44;
    private static final int SMALL_BTN_W = 42;
    private static final int EDIT_AREA_H = 50;
    private static final int FOOTER_H = 14;
    private static final int GAP = 4;

    private final PersonalDimensionControllerScreen screen;
    private int scrollOffset;
    private boolean draggingBar;
    private List<TeleporterEntry> lastList;

    private int highlightIndex = -1;
    private boolean editing;
    private EditBox nameInput;
    private EditBox noteInput;

    TeleportTabPanel(PersonalDimensionControllerScreen screen) {
        this.screen = screen;
    }

    void requestList() {
        if (Minecraft.getInstance().getConnection() != null) {
            Platform.sendToServer(new RequestTeleporterListPacket());
        }
    }

    void build() {
        List<TeleporterEntry> entries = ClientTeleporterData.teleporters();
        this.lastList = entries;

        int maxOffset = Math.max(0, entries.size() - visibleRows());
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxOffset));

        if (this.highlightIndex >= entries.size()) {
            this.highlightIndex = entries.isEmpty() ? -1 : entries.size() - 1;
        }

        int listTop = listTop();
        int tpBtnX = teleportBtnX();

        // 列表行：名称 + 传送按钮
        for (int i = 0; i < visibleRows() && this.scrollOffset + i < entries.size(); i++) {
            int idx = this.scrollOffset + i;
            int rowY = listTop + i * ROW_H;
            screen.add(new FlatButton(
                Component.translatable("gui.personaldimension.teleport.button"),
                tpBtnX, rowY + 1, TELEPORT_BTN_W, 20, b -> teleport(idx)));
        }

        if (entries.isEmpty()) {
            // 空列表无按钮
        } else if (this.editing && this.highlightIndex >= 0) {
            buildEditArea();
        } else {
            buildFooter();
        }
    }

    /** 底栏：左侧编辑按钮，右侧计数。 */
    private void buildFooter() {
        List<TeleporterEntry> entries = ClientTeleporterData.teleporters();
        if (entries.isEmpty()) return;
        int footerY = footerY();
        FlatButton editBtn = new FlatButton(
            Component.translatable("gui.personaldimension.teleport.edit"),
            screen.panelContentLeft(), footerY, SMALL_BTN_W, 14,
            b -> toggleEdit());
        editBtn.setSelected(this.editing);
        screen.add(editBtn);
    }

    /** 底部编辑区：名称 + 备注 + 重置/保存/完成，贴面板底部。 */
    private void buildEditArea() {
        int areaTop = editAreaTop();
        int x = screen.panelContentLeft();
        int w = screen.panelContentWidth();

        int btnW = (w - GAP * 2) / 3;

        // 第一行：名称输入 + 重置
        this.nameInput = new EditBox(Minecraft.getInstance().font, x, areaTop, w - btnW - GAP, 18,
            Component.translatable("gui.personaldimension.teleport.name_hint"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setHint(Component.translatable("gui.personaldimension.teleport.name_hint"));
        screen.add(this.nameInput);

        FlatButton resetBtn = new FlatButton(
            Component.translatable("gui.personaldimension.teleport.reset"),
            x + w - btnW, areaTop, btnW, 18, b -> resetSelection());
        screen.add(resetBtn);

        // 第二行：备注输入 + 保存 + 完成
        int secondY = areaTop + 22;
        int noteW = w - btnW * 2 - GAP * 2;
        this.noteInput = new EditBox(Minecraft.getInstance().font, x, secondY, noteW, 18,
            Component.translatable("gui.personaldimension.teleport.note_hint"));
        this.noteInput.setMaxLength(64);
        this.noteInput.setHint(Component.translatable("gui.personaldimension.teleport.note_hint"));
        screen.add(this.noteInput);

        int saveX = x + noteW + GAP;
        FlatButton saveBtn = new FlatButton(
            Component.translatable("gui.personaldimension.teleport.save"),
            saveX, secondY, btnW, 18, b -> saveRename());
        screen.add(saveBtn);

        int doneX = saveX + btnW + GAP;
        FlatButton doneBtn = new FlatButton(
            Component.translatable("gui.personaldimension.teleport.edit_exit"),
            doneX, secondY, btnW, 18, b -> toggleEdit());
        doneBtn.setSelected(true);
        screen.add(doneBtn);

        TeleporterEntry entry = ClientTeleporterData.teleporters().get(this.highlightIndex);
        this.nameInput.setValue(entry.name());
        this.noteInput.setValue(entry.note());
    }

    /** 点击行文字区域：切换高亮行。 */
    private void clickRow(int index) {
        this.highlightIndex = index;
        screen.rebuildPanel();
    }

    /** 切换编辑态。进入时若无选中则选第一行，滚动确保可见。 */
    private void toggleEdit() {
        this.editing = !this.editing;
        if (this.editing) {
            List<TeleporterEntry> entries = ClientTeleporterData.teleporters();
            if (this.highlightIndex < 0 && !entries.isEmpty()) {
                this.highlightIndex = 0;
            }
            if (this.highlightIndex >= 0) {
                scrollToVisible(this.highlightIndex);
            }
        }
        screen.rebuildPanel();
    }

    private void scrollToVisible(int index) {
        int visible = visibleRows();
        int max = Math.max(0, ClientTeleporterData.teleporters().size() - visible);
        if (index < this.scrollOffset) {
            this.scrollOffset = index;
        } else if (index >= this.scrollOffset + visible) {
            this.scrollOffset = index - visible + 1;
        }
        this.scrollOffset = Math.max(0, Math.min(max, this.scrollOffset));
    }

    private void resetSelection() {
        if (this.highlightIndex < 0) return;
        Platform.sendToServer(new RenameTeleporterPacket(this.highlightIndex, "", ""));
    }

    private void saveRename() {
        if (this.highlightIndex < 0) return;
        Platform.sendToServer(new RenameTeleporterPacket(
            this.highlightIndex, this.nameInput.getValue(), this.noteInput.getValue()));
    }

    private void teleport(int index) {
        Platform.sendToServer(new TeleportToTargetPacket(index));
        screen.onClose();
    }

    void tick() {
        if (!Objects.equals(ClientTeleporterData.teleporters(), this.lastList)) {
            screen.rebuildPanel();
        }
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        List<TeleporterEntry> entries = ClientTeleporterData.teleporters();
        int listTop = listTop();
        int listBottom = listBottom();

        if (entries.isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.personaldimension.teleport.empty"),
                screen.panelContentLeft(), listTop + 4, ModernStyle.TEXT_SUB, false);
            return;
        }

        int nameX = screen.panelContentLeft();
        int nameW = teleportBtnX() - nameX - GAP * 2;
        for (int i = 0; i < visibleRows() && this.scrollOffset + i < entries.size(); i++) {
            TeleporterEntry entry = entries.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            int idx = this.scrollOffset + i;

            if (this.highlightIndex == idx) {
                graphics.fill(nameX - 2, rowY - 1, teleportBtnX() - GAP, rowY + ROW_H - 1,
                    ModernStyle.withAlpha(ModernStyle.ACCENT, 0x14));
            }

            String display = entry.name().isEmpty() ? resolveDimensionName(entry.dimension()) : entry.name();
            graphics.drawString(Minecraft.getInstance().font,
                ellipsize(display, nameW), nameX, rowY + 2, ModernStyle.TEXT, false);
            String second = entry.note().isEmpty()
                ? String.format("(%d, %d, %d)", entry.x(), entry.y(), entry.z())
                : ellipsize(entry.note(), nameW);
            graphics.drawString(Minecraft.getInstance().font, second,
                nameX, rowY + 12, ModernStyle.TEXT_SUB, false);
        }

        // 滚动条
        if (ScrollBar.needed(entries.size(), visibleRows())) {
            ScrollBar.draw(graphics, scrollbarX(), listTop, listBottom - listTop,
                this.scrollOffset, visibleRows(), entries.size());
        }

        // 底部分隔线（编辑态）
        if (this.editing) {
            int sepY = editAreaTop() - GAP;
            graphics.fill(screen.panelContentLeft(), sepY,
                screen.panelContentLeft() + screen.panelContentWidth(), sepY + 1,
                ModernStyle.DIVIDER);
        }

        // 计数（非编辑态在底栏右侧，编辑态在列表底部右下角）
        if (!entries.isEmpty()) {
            String count = (this.scrollOffset + 1) + "-"
                + Math.min(this.scrollOffset + visibleRows(), entries.size())
                + "/" + entries.size();
            int countW = Minecraft.getInstance().font.width(count);
            int countX = scrollbarX() - GAP - countW;
            int countY = this.editing ? (listBottom - 9) : (footerY() + 3);
            graphics.drawString(Minecraft.getInstance().font, count,
                countX, countY, ModernStyle.TEXT_SUB, false);
        }
    }

    // ——— 鼠标事件 ———

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = ClientTeleporterData.teleporters().size();
        int maxOffset = Math.max(0, size - visibleRows());
        int next = Math.max(0, Math.min(maxOffset, this.scrollOffset - (int) Math.signum(scrollY)));
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            screen.rebuildPanel();
        }
        return true;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<TeleporterEntry> entries = ClientTeleporterData.teleporters();
        int listTop = listTop();
        int listBottom = listBottom();
        int nameRight = teleportBtnX() - GAP;

        // 滚动条点击
        if (button == 0 && ScrollBar.needed(entries.size(), visibleRows())
            && ScrollBar.isOver(mouseX, mouseY, scrollbarX(), listTop, listBottom - listTop)) {
            this.draggingBar = true;
            setScrollByMouse(mouseY);
            return true;
        }

        // 行文字区域点击：切换高亮
        if (button == 0 && mouseY >= listTop && mouseY < listBottom
            && mouseX >= screen.panelContentLeft() && mouseX < nameRight) {
            int row = (int) ((mouseY - listTop) / ROW_H);
            int idx = this.scrollOffset + row;
            if (row >= 0 && row < visibleRows() && idx < entries.size()) {
                clickRow(idx);
                return true;
            }
        }

        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (this.draggingBar && button == 0) {
            setScrollByMouse(mouseY);
            return true;
        }
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingBar = false;
        }
        return false;
    }

    // ——— 几何辅助 ———

    private void setScrollByMouse(double mouseY) {
        int total = ClientTeleporterData.teleporters().size();
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), listBottom() - listTop(), visibleRows(), total);
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            screen.rebuildPanel();
        }
    }

    private int listTop() {
        return screen.panelContentTop() + screen.panelPadding();
    }

    private int listBottom() {
        if (this.editing) {
            return editAreaTop() - GAP * 2;
        }
        return footerY() - GAP;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private int footerY() {
        return screen.panelTopPos() + screen.panelHeight() - screen.panelPadding() - FOOTER_H;
    }

    private int editAreaTop() {
        return screen.panelTopPos() + screen.panelHeight() - screen.panelPadding() - EDIT_AREA_H;
    }

    private int scrollbarX() {
        return screen.panelLeftPos() + screen.panelWidth() - screen.panelPadding() - ScrollBar.WIDTH;
    }

    private int teleportBtnX() {
        return scrollbarX() - ScrollBar.GAP - TELEPORT_BTN_W;
    }

    private String ellipsize(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        while (font.width(text + "...") > maxWidth && text.length() > 0) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private String resolveDimensionName(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (Platform.isPersonalDimension(dim)) {
            return Component.translatable("biome.personaldimension.personal").getString();
        }
        String namespace = dim.location().getNamespace();
        String path = dim.location().getPath();
        if ("minecraft".equals(namespace)) {
            return switch (path) {
                case "overworld" -> "主世界";
                case "the_nether" -> "下界";
                case "the_end" -> "末地";
                default -> path;
            };
        }
        String[] keys = {
            "dimension_type." + namespace + "." + path,
            "dimension." + namespace + "." + path
        };
        for (String key : keys) {
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return path;
    }
}
