package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 现代深色扁平风格竖向滚动条（overlay 风）：半透明黑凹槽 + 浅灰半透明白药丸滑块。
 *
 * <p>纯几何 / 绘制 / 命中换算的<b>无状态工具</b>（各界面自持 scrollOffset 与拖动标记），
 * 使 6 处可滚动列表共享同一套外观与拖动手感，复制到其它 MC 版本段时只需改此一处。
 * 滑块最小高度 16px、位置随滚动线性插值。
 */
public final class ScrollBar {

    /** 轨道 / 滑块宽度。 */
    public static final int WIDTH = 6;
    /** 轨道与相邻内容之间的建议留白（供调用方排版让位）。 */
    public static final int GAP = 2;

    private ScrollBar() {}

    /** 是否需要滚动条（内容行数超出一屏可见行数）。 */
    public static boolean needed(int total, int visible) {
        return total > visible;
    }

    /** 最大可滚动偏移（0 表示无需滚动）。 */
    public static int maxScroll(int total, int visible) {
        return Math.max(0, total - visible);
    }

    /** 绘制半透明黑凹槽 + 浅灰药丸滑块；scroll 应已 clamp 到 [0, maxScroll]。 */
    public static void draw(GuiGraphics graphics, int x, int top, int trackH,
                            int scroll, int visible, int total) {
        int max = maxScroll(total, visible);
        int thumbH = Math.max(16, trackH * visible / total);
        int thumbY = max == 0 ? top : top + (trackH - thumbH) * scroll / max;
        int c = WIDTH / 2;
        ModernStyle.fillRound(graphics, x, top, WIDTH, trackH, c, 0x33000000);       // 半透明黑凹槽
        ModernStyle.fillRound(graphics, x, thumbY, WIDTH, thumbH, c, 0x66FFFFFF);    // 浅灰药丸滑块
    }

    /** 鼠标是否落在滚动条轨道区域内（用于抓取滑块 / 拖动判定）。 */
    public static boolean isOver(double mouseX, double mouseY, int x, int top, int trackH) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < top + trackH;
    }

    /**
     * 由鼠标 y 反算滚动偏移（拖动滑块 / 点击轨道）：以滑块中心对齐鼠标，结果 clamp 到 [0, maxScroll]。
     */
    public static int scrollFromMouse(double mouseY, int top, int trackH, int visible, int total) {
        int max = maxScroll(total, visible);
        if (max == 0) {
            return 0;
        }
        int thumbH = Math.max(16, trackH * visible / total);
        double rel = (mouseY - top - thumbH / 2.0) / (trackH - thumbH);
        int value = (int) Math.round(rel * max);
        return Math.max(0, Math.min(max, value));
    }
}
