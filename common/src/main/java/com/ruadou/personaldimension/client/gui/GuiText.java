package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 文本排版小工具：按可用宽度做省略号截断。
 *
 * <p>纯几何计算的<b>无状态工具</b>，是全套 UI 行文本截断的唯一实现（复制到其它 MC 版本段时只改此一处）。
 * 用于避免不定长文本（玩家名 / 组名 / 群系名 / 实体名）溢出到滚动条、右侧按钮或预览框之外。
 */
public final class GuiText {

    private static final String ELLIPSIS = "…";

    private GuiText() {}

    /** 若字符串宽度超过 maxWidth，则截断并以省略号结尾；否则原样返回。 */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int room = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, room) + ELLIPSIS;
    }

    /** 组件重载：宽度足够时保留原组件（含样式），否则回退为截断后的纯文本。 */
    public static Component ellipsize(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int room = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return Component.literal(font.plainSubstrByWidth(text.getString(), room) + ELLIPSIS);
    }
}
