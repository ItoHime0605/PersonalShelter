package com.ruadou.personaldimension.client.gui;

import com.ruadou.personaldimension.client.ClientChunkBiomes;
import com.ruadou.personaldimension.client.ClientCollectedBiomes;
import com.ruadou.personaldimension.network.CollectChunkBiomesPacket;
import com.ruadou.personaldimension.network.RequestChunkBiomesPacket;
import com.ruadou.personaldimension.network.RequestCollectedBiomesPacket;
import com.ruadou.personaldimension.platform.Platform;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 群系收录器界面。
 *
 * <p>列出收录器方块所在区块内出现的群系，玩家可勾选<b>多个</b>后点击"收录"存入个人数据。
 * 已收录过的群系置灰标记且不可再选，避免重复收录。收录动作由服务端复检方块存在、交互距离
 * 并过滤伪造项后执行（见 {@link CollectChunkBiomesPacket}），本界面仅负责勾选与发起请求。
 * 复用 {@link FriendBaseScreen} 的面板/变暗背景/周期刷新骨架。
 */
public class BiomeCollectorScreen extends FriendBaseScreen {

    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_H = 22;

    private final BlockPos collectorPos;
    /** 本次勾选待收录的群系（不含已收录项），保序展示。 */
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private List<ResourceLocation> lastChunkList;
    private List<ResourceLocation> lastCollectedList;
    private int scrollOffset;
    private boolean draggingBar;

    public BiomeCollectorScreen(BlockPos collectorPos) {
        super(Component.translatable("gui.personaldimension.biome_collector.title"), null);
        this.collectorPos = collectorPos;
    }

    @Override
    protected void requestData() {
        // 区块群系（待选项）与已收录群系（置灰项）各请求一次
        Platform.sendToServer(new RequestChunkBiomesPacket(this.collectorPos));
        Platform.sendToServer(new RequestCollectedBiomesPacket());
    }

    @Override
    protected void buildWidgets() {
        List<ResourceLocation> chunk = ClientChunkBiomes.biomes();
        Set<ResourceLocation> collected = Set.copyOf(ClientCollectedBiomes.biomes());
        this.lastChunkList = chunk;
        this.lastCollectedList = ClientCollectedBiomes.biomes();

        // 勾选项若已不在区块列表或已被收录，则移除，避免提交无效候选
        this.selected.removeIf(id -> !chunk.contains(id) || collected.contains(id));
        int maxOffset = Math.max(0, chunk.size() - VISIBLE_ROWS);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);

        int listTop = listTop();
        int rowW = contentWidth() - ScrollBar.WIDTH - ScrollBar.GAP;
        for (int i = 0; i < VISIBLE_ROWS && this.scrollOffset + i < chunk.size(); i++) {
            ResourceLocation biome = chunk.get(this.scrollOffset + i);
            int rowY = listTop + i * ROW_H;
            boolean already = collected.contains(biome);
            FlatButton row = new FlatButton(rowLabel(biome, already),
                contentLeft(), rowY, rowW, 20, b -> toggle(biome));
            if (already) {
                row.active = false; // 已收录：置灰不可再选
            } else {
                row.setSelected(this.selected.contains(biome));
            }
            this.addRenderableWidget(row);
        }

        int bottomY = this.topPos + PANEL_HEIGHT - PADDING - 20;
        int half = (contentWidth() - 4) / 2;
        FlatButton collect = new FlatButton(
            Component.translatable("gui.personaldimension.biome_collector.collect", this.selected.size()),
            contentLeft(), bottomY, half, 20, b -> collect());
        collect.active = !this.selected.isEmpty();
        this.addRenderableWidget(collect);
        this.addRenderableWidget(new FlatButton(
            Component.translatable("gui.personaldimension.controller.back"),
            contentLeft() + half + 4, bottomY, contentWidth() - half - 4, 20, b -> onClose()));
    }

    private void toggle(ResourceLocation biome) {
        if (!this.selected.add(biome)) {
            this.selected.remove(biome);
        }
        rebuildWidgets();
    }

    /** 发起收录请求并关闭界面，收录结果由服务端以聊天提示反馈。 */
    private void collect() {
        if (!this.selected.isEmpty()) {
            Platform.sendToServer(
                new CollectChunkBiomesPacket(this.collectorPos, List.copyOf(this.selected)));
            onClose();
        }
    }

    @Override
    protected void onTick() {
        // 区块群系或已收录列表任一内容变化时重建行控件
        if (!Objects.equals(ClientChunkBiomes.biomes(), this.lastChunkList)
            || !Objects.equals(ClientCollectedBiomes.biomes(), this.lastCollectedList)) {
            rebuildWidgets();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<ResourceLocation> chunk = ClientChunkBiomes.biomes();
        if (chunk.isEmpty()) {
            graphics.drawString(this.font,
                Component.translatable("gui.personaldimension.biome_collector.empty"),
                contentLeft(), this.topPos + 24, ModernStyle.TEXT_SUB, false);
            return;
        }
        if (chunk.size() > VISIBLE_ROWS) {
            graphics.drawString(this.font,
                Component.literal((this.scrollOffset + 1) + "-"
                    + Math.min(this.scrollOffset + VISIBLE_ROWS, chunk.size()) + "/" + chunk.size()),
                this.leftPos + PANEL_WIDTH - PADDING - 44, this.topPos + 8, ModernStyle.TEXT_SUB, false);
        }
        if (ScrollBar.needed(chunk.size(), VISIBLE_ROWS)) {
            ScrollBar.draw(graphics, scrollbarX(), listTop(), VISIBLE_ROWS * ROW_H,
                this.scrollOffset, VISIBLE_ROWS, chunk.size());
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
        int size = ClientChunkBiomes.biomes().size();
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
        if (button == 0 && ScrollBar.needed(ClientChunkBiomes.biomes().size(), VISIBLE_ROWS)
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
        int total = ClientChunkBiomes.biomes().size();
        int next = ScrollBar.scrollFromMouse(mouseY, listTop(), VISIBLE_ROWS * ROW_H, VISIBLE_ROWS, total);
        if (next != this.scrollOffset) {
            this.scrollOffset = next;
            rebuildWidgets();
        }
    }

    /** 行标签：群系显示名；已收录项追加"已收录"标记。 */
    private static Component rowLabel(ResourceLocation biome, boolean already) {
        Component name = biomeName(biome);
        if (!already) {
            return name;
        }
        return Component.empty().append(name).append(" ")
            .append(Component.translatable("gui.personaldimension.biome_collector.already"));
    }

    /** 群系显示名：优先原版/模组的群系语言键（biome.命名空间.路径），无翻译时回退为原始 id。 */
    private static Component biomeName(ResourceLocation id) {
        String key = "biome." + id.getNamespace() + "." + id.getPath();
        return Component.translatableWithFallback(key, id.toString());
    }
}
