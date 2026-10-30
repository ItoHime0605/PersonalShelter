package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * 现代深色扁平风格按钮（演示/候选主题）。
 *
 * <p>沿用 {@link Button} 的点击 / 焦点 / 旁白逻辑，仅重写 {@link #renderWidget}：
 * 主强调 / 危险为<b>去饱和实心 + 近白文字 + 极淡顶部微光</b>（扁平，不做玻璃高光）；
 * 普通为<b>幽灵式</b>（半透明白叠加 + 细描边），随面板底色自然提亮。悬停微提亮、禁用降透明。
 * 纯 {@code fill} 实现，无贴图无 UI 库。
 */
public class ModernButton extends Button {

    /** 按钮语义：普通（幽灵）、主强调、危险。 */
    public enum Variant { NORMAL, PRIMARY, DANGER }

    private final Variant variant;

    public ModernButton(Component message, int x, int y, int width, int height, Variant variant, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.variant = variant;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        int r = ModernStyle.CHAMFER;
        boolean enabled = this.active;
        boolean hovered = enabled && this.isHoveredOrFocused();

        int textColor;
        if (this.variant == Variant.NORMAL) {
            // 幽灵按钮：半透明白叠加（随底色提亮）+ 中性描边 + 顶部极淡高光（保留立体感）
            int fill = !enabled ? 0x0AFFFFFF : (hovered ? ModernStyle.GHOST_FILL_HOVER : ModernStyle.GHOST_FILL);
            ModernStyle.roundedRect(graphics, x, y, w, h, r, fill, ModernStyle.BTN_BORDER);
            if (enabled) {
                ModernStyle.fillRound(graphics, x + 2, y + 1, w - 4, 1, 0, 0x1EFFFFFF); // 顶部高光
            }
            textColor = enabled ? ModernStyle.TEXT : ModernStyle.TEXT_WEAK;
        } else {
            // 实心：去饱和强调/危险色，扁平极轻渐变 + 极淡顶部微光
            int base = this.variant == Variant.PRIMARY ? ModernStyle.ACCENT : ModernStyle.DANGER;
            if (!enabled) {
                int fill = 0x0AFFFFFF;
                ModernStyle.roundedRect(graphics, x, y, w, h, r, fill, ModernStyle.BTN_BORDER);
                textColor = ModernStyle.TEXT_WEAK;
            } else {
                if (hovered) {
                    base = ModernStyle.lighten(base, 0.08f);
                }
                int top = ModernStyle.lighten(base, 0.07f);
                int bottom = ModernStyle.darken(base, 0.07f);
                int border = ModernStyle.darken(base, 0.28f);
                ModernStyle.roundedRectGrad(graphics, x, y, w, h, r, top, bottom, border);
                ModernStyle.fillRound(graphics, x + 2, y + 1, w - 4, 1, 0, 0x22FFFFFF); // 顶部极淡微光
                // The active palette can make an accent pale or saturated.
                // Choose text from actual fill luminance rather than reusing
                // the panel's text color (unreadable on light accents).
                textColor = ModernStyle.readableTextOn(base);
            }
        }

        Font font = Minecraft.getInstance().font;
        // Minecraft's native bold glyphs improve small Traditional Chinese
        // button labels without scaling or changing the established layout.
        // Measure AFTER applying bold; otherwise the label may clip.
        Component boldMessage = this.getMessage().copy().withStyle(Style.EMPTY.withBold(true));
        Component label = GuiText.ellipsize(font, boldMessage, w - 8);
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - font.lineHeight) / 2;
        graphics.drawString(font, label, textX, textY, textColor, true);
    }
}
