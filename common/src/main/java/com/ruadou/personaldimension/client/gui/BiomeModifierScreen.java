package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientCollectedBiomes;
import com.ruadou.personaldimension.network.ApplyBiomePacket;
import com.ruadou.personaldimension.network.RequestCollectedBiomesPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 群系改造器界面。
 *
 * <p>列出玩家已收录的群系，选中其一后点击"改造"，把改造器方块所在区块的群系覆盖为该群系。
 * 覆盖动作由服务端复检维度归属、方块存在与收录状态后执行（见 {@link ApplyBiomePacket}），
 * 本界面仅负责选择与发起请求。复用 {@link FriendBaseScreen} 的面板/变暗背景/周期刷新骨架。
 */
public class BiomeModifierScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_H = 22;

    private final BlockPos modifierPos;
    private ResourceLocation selected;
    private List<ResourceLocation> lastList;
    private int scrollOffset;
    private boolean draggingBar;

    public BiomeModifierScreen(BlockPos modifierPos) {
        super(Component.translatable("gui.personaldimension.biome_modifier.title"), null);
        this.modifierPos = modifierPos;
    }

    @Override
    protected void requestData() {
        Platform.sendToServer(new RequestCollectedBiomesPacket());
    }

    @Override
    protected void buildWidgets() {
        List<ResourceLocation> biomes = ClientCollectedBiomes.biomes();
        this.lastList = biomes;

        // 选中项若已不在最新列表中则清空，避免应用一个已消失的候选
        if (this.selected != null && !biomes.contains(this.selected)) {
            this.selected = null;
        }
        int maxOffset = Math.max(0, biomes.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);

        int listTop = listTop();
        int rowW = contentWidth() - ScrollBar.WIDTH - ScrollBar.GAP;
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < biomes.size(); i++) {
            ResourceLocation biome = biomes.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            FlatButton row = new FlatButton(biomeName(biome),
                contentLeft(), rowY, rowW, 20, b -> select(biome));
            row.setSelected(biome.equals(this.selected));
            this.addRenderableWidget(row);
        }

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        int half = (contentWidth() - 4) / 2;
        FlatButton apply = new FlatButton(
            Component.translatable("gui.personaldimension.biome_modifier.apply"),
            contentLeft(), bottomY, half, 20, b -> apply());
        apply.active = this.selected != null;
        this.addRenderableWidget(apply);
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft() + half + 4, bottomY, contentWidth() - half - 4, 20, b -> onClose()));
    }

    private void select(ResourceLocation biome) {
        this.selected = biome;
        rebuildWidgets();
    }

    /** 发起改造请求并关闭界面，便于玩家直接看到覆盖结果。 */
    private void apply() {
        if (this.selected != null) {
            Platform.sendToServer(new ApplyBiomePacket(this.modifierPos, this.selected));
            onClose();
        }
    }

    @Override
    protected void onTick() {
        if (!Objects.equals(ClientCollectedBiomes.biomes(), this.lastList)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<ResourceLocation> biomes = ClientCollectedBiomes.biomes();
        if (biomes.isEmpty()) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.biome_modifier.empty"),
                contentLeft(), this.topPos + 24, ModernStyle.TEXT_SUB, false);
            return;
        }
        if (biomes.size() > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, biomes.size()) + "/" + biomes.size()),
                this.leftPos + PANEL_WIDTH - PADDING - 44, this.topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(biomes.size(), VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, biomes.size());
        }
    }

    private int scrollbarX() {
        return this.leftPos + PANEL_WIDTH - PADDING - ScrollBar.WIDTH;
    }

    private int listTop() {
        return this.topPos + 20;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = ClientCollectedBiomes.biomes().size();
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
        if (button == 0 && ScrollBar.needed(ClientCollectedBiomes.biomes().size(), VISIBLE_ROWS)
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
        int total = ClientCollectedBiomes.biomes().size();
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, total);
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
    }

    /** 群系显示名：优先原版/模组的群系语言键（biome.命名空间.路径），无翻译时回退为原始 id。 */
    private static Component biomeName(ResourceLocation id) {
        String key = "biome." + id.getNamespace() + "." + id.getPath();
        return Component.translatableWithFallback(key, id.toString());
    }
}
