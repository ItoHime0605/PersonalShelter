package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 现代深色扁平风格手绘面板界面的公共基类。
 *
 * <p>统一提供：居中定位、深色半透明遮罩（不做原版高斯模糊）、{@link ModernStyle} 圆角面板、
 * 带强调色竖条的标题栏以及控件遍历渲染骨架，使所有手绘界面共享同一套外观与尺寸
 * （复制到其它 MC 版本段时只需在此改一处）。子类通过覆写
 * {@link #renderBehindPanel}/{@link #renderContent}/{@link #renderOverlay}/{@link #panelTitle}
 * 注入各自内容，避免重复相同的骨架逻辑（DRY）。
 *
 * <p><b>为何不调用 super.render()</b>：原版 {@link Screen#render} 会先执行带高斯模糊的背景绘制，
 * 会糊掉手绘面板，故此处自行铺背景并手动遍历 {@link #children()} 中的 {@link Renderable}
 * （所有控件均经 {@code addRenderableWidget} 添加，顺序一致；用公开 {@code children()}
 * 替代私有 {@code renderables} 字段，免于依赖 AccessTransformer，跨加载器通用）。
 */
public abstract class PanelScreen extends Screen {

    protected static final int PANEL_WIDTH = 260;
    protected static final int PANEL_HEIGHT = 250;
    protected static final int HEADER_HEIGHT = 18;
    protected static final int PADDING = 8;

    protected int leftPos;
    protected int topPos;

    protected PanelScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
    }

    /** 内容区顶部：面板顶部留出标题栏后的起始 y。 */
    protected int contentTop() {
        return this.topPos + HEADER_HEIGHT;
    }

    protected int contentLeft() {
        return this.leftPos + PADDING;
    }

    protected int contentWidth() {
        return PANEL_WIDTH - PADDING * 2;
    }

    /** 面板标题（默认取屏幕标题；动态标题的子类可覆写）。 */
    protected Component panelTitle() {
        return getTitle();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ModernStyle.SCRIM);
        renderBehindPanel(graphics, mouseX, mouseY, partialTick);
        ModernStyle.panel(graphics, this.leftPos, this.topPos, PANEL_WIDTH, PANEL_HEIGHT);

        renderHeader(graphics, mouseX, mouseY, partialTick);

        renderContent(graphics, mouseX, mouseY, partialTick);

        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        renderOverlay(graphics, mouseX, mouseY, partialTick);
    }

    /** 面板之下、背景之上的绘制（如侧边标签，需被面板边缘覆盖以贴合）。默认无操作。 */
    protected void renderBehindPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    /**
     * 标题栏：贴合面板圆角的顶部深色底（仅上两角圆）+ 分隔线 + 强调色竖条 + 标题文字。
     * 子类可覆写以自定义标题栏内容（如顶部横向标签），默认绘制单行标题。
     */
    protected void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ModernStyle.fillRoundTop(graphics, this.leftPos + 1, this.topPos + 1, PANEL_WIDTH - 2,
            HEADER_HEIGHT, ModernStyle.CHAMFER, ModernStyle.HEADER_BG);
        graphics.fill(this.leftPos + 1, this.topPos + HEADER_HEIGHT,
            this.leftPos + PANEL_WIDTH - 1, this.topPos + HEADER_HEIGHT + 1, ModernStyle.DIVIDER);
        graphics.fill(this.leftPos + PADDING, this.topPos + 5,
            this.leftPos + PADDING + 3, this.topPos + 14, ModernStyle.ACCENT);
        graphics.drawString(this.font, panelTitle(),
            this.leftPos + PADDING + 8, this.topPos + 6, ModernStyle.TEXT, false);
    }

    /** 面板正文（文本 / 列表行等非控件内容），绘制在控件之下。默认无操作。 */
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    /** 覆盖层（如提示、悬浮框），绘制在控件之上。默认无操作。 */
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
