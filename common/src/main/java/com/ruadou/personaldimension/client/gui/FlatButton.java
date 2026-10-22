package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 现代深色扁平风格的通用按钮。
 *
 * <p>沿用 {@link Button} 的点击 / 焦点 / 旁白逻辑，仅重写 {@link #renderWidget}：常态为<b>幽灵式</b>
 * （半透明白叠加 + 中性描边 + 顶部极淡高光，随面板底色自然提亮），悬停微提亮、禁用降透明；
 * {@link #setSelected(boolean)} 置为选中时用<b>强调色描边 + 极淡强调底</b>替代旧金边（用于预设单选）。
 * 纯 {@code fill} 实现，无贴图无 UI 库。
 */
public class FlatButton extends Button {

    private boolean selected;
    private boolean highlighted;
    private boolean disabledAppearance;

    public FlatButton(Component message, int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    /** 设置为选中态：描强调色边以区别于悬停高亮，用于预设等单选场景。 */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** 设置为高亮态：比悬停更明显的选中效果。 */
    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    /** 设置为禁用外观：显示禁用颜色但仍可点击。 */
    public void setDisabledAppearance(boolean disabledAppearance) {
        this.disabledAppearance = disabledAppearance;
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
        if (this.selected) {
            ModernStyle.roundedRect(graphics, x, y, w, h, r,
                ModernStyle.withAlpha(ModernStyle.ACCENT, 0x2E), ModernStyle.ACCENT);
            if (enabled) {
                ModernStyle.fillRound(graphics, x + 2, y + 1, w - 4, 1, 0, 0x1EFFFFFF);
            }
            textColor = ModernStyle.ACCENT_TEXT;
        } else {
            int fill;
            if (disabledAppearance) {
                fill = 0x0A222222;
                textColor = ModernStyle.TEXT_WEAK;
            } else if (highlighted) {
                // 选中态：比悬停更明显的亮色填充
                fill = 0x30FFFFFF;
                textColor = ModernStyle.TEXT;
            } else {
                fill = !enabled ? 0x05222222 : (hovered ? ModernStyle.GHOST_FILL_HOVER : ModernStyle.GHOST_FILL);
                textColor = enabled ? ModernStyle.TEXT : ModernStyle.TEXT_WEAK;
            }
            ModernStyle.roundedRect(graphics, x, y, w, h, r, fill, ModernStyle.BTN_BORDER);
            if (enabled) {
                ModernStyle.fillRound(graphics, x + 2, y + 1, w - 4, 1, 0, 0x1EFFFFFF);
            }
        }

        Font font = Minecraft.getInstance().font;
        Component label = GuiText.ellipsize(font, this.getMessage(), w - 6);
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - font.lineHeight) / 2;
        graphics.drawString(font, label, textX, textY, textColor, false);
    }
}
