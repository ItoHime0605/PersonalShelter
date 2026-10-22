package com.ruadou.personaldimension.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 现代风格「按住确认」危险按钮。
 *
 * <p>常态为去饱和暖琥珀（{@link ModernStyle#WARN}，醒目而不刺眼，替代默认红）；
 * 按住后红色进度自左向右填充，充满即触发 {@code onPress} 并闪烁一下表示已执行；
 * 松手或移出按钮则进度回落，避免误触。纯 {@code fill} 绘制，进度前缘用裁剪切出干净竖切。
 * 适用于删除等破坏性操作，可复用于正式界面的安全确认。
 */
public class ModernHoldButton extends Button {

    /** 充满所需时长（秒）。 */
    private static final float HOLD_SECONDS = 1.2f;
    /** 松手后进度回落时长（秒）。 */
    private static final float DECAY_SECONDS = 0.35f;
    /** 完成后闪烁渐隐时长（秒）。 */
    private static final float FLASH_SECONDS = 0.4f;

    private float progress;
    private boolean holding;
    private boolean fired;
    private float flash;
    private long lastMs;

    public ModernHoldButton(Component message, int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = this.lastMs == 0 ? 0f : Math.min(0.1f, (now - this.lastMs) / 1000f);
        this.lastMs = now;

        int x = getX();
        int y = getY();
        int w = this.width;
        int h = this.height;
        int r = ModernStyle.CHAMFER;
        boolean enabled = this.active;
        boolean hovered = enabled && isHoveredOrFocused();

        // 移出按钮范围即停止蓄力
        if (this.holding && !isMouseOver(mouseX, mouseY)) {
            this.holding = false;
        }

        // 进度推进 / 触发 / 回落
        if (enabled && this.holding && !this.fired) {
            this.progress = Math.min(1f, this.progress + dt / HOLD_SECONDS);
            if (this.progress >= 1f) {
                this.fired = true;
                this.holding = false;
                this.flash = 1f;
                this.onPress(); // 达到满进度，触发执行
            }
        } else if (!this.fired && this.progress > 0f) {
            this.progress = Math.max(0f, this.progress - dt / DECAY_SECONDS);
        }

        // 闪烁渐隐，结束后复位（便于重复演示 / 再次确认）
        if (this.flash > 0f) {
            this.flash = Math.max(0f, this.flash - dt / FLASH_SECONDS);
            if (this.flash == 0f) {
                this.fired = false;
                this.progress = 0f;
            }
        }

        int textColor;
        if (!enabled) {
            ModernStyle.roundedRect(g, x, y, w, h, r, 0x0AFFFFFF, ModernStyle.BTN_BORDER);
            textColor = ModernStyle.TEXT_WEAK;
        } else {
            // 常态暖色底
            int base = hovered ? ModernStyle.WARN_HOVER : ModernStyle.WARN;
            ModernStyle.roundedRectGrad(g, x, y, w, h, r,
                ModernStyle.lighten(base, 0.07f), ModernStyle.darken(base, 0.07f), ModernStyle.darken(base, 0.30f));
            ModernStyle.fillRound(g, x + 2, y + 1, w - 4, 1, 0, 0x22FFFFFF);
            // 红色进度：裁剪出左侧宽度，右缘干净竖切
            int pw = Math.round(w * this.progress);
            if (pw > 0) {
                g.enableScissor(x, y, x + pw, y + h);
                ModernStyle.roundedRectGrad(g, x, y, w, h, r,
                    ModernStyle.lighten(ModernStyle.DANGER, 0.06f),
                    ModernStyle.darken(ModernStyle.DANGER, 0.08f),
                    ModernStyle.darken(ModernStyle.DANGER, 0.30f));
                ModernStyle.fillRound(g, x + 2, y + 1, w - 4, 1, 0, 0x22FFFFFF);
                g.disableScissor();
            }
            textColor = ModernStyle.ACCENT_TEXT;
        }

        // 完成闪烁：渐隐白光
        if (this.flash > 0f) {
            int a = (int) (this.flash * 0x88) & 0xFF;
            ModernStyle.fillRound(g, x, y, w, h, r, (a << 24) | 0x00FFFFFF);
        }

        Font font = Minecraft.getInstance().font;
        Component label = GuiText.ellipsize(font, getMessage(), w - 8);
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - font.lineHeight) / 2;
        g.drawString(font, label, tx, ty, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.clicked(mouseX, mouseY)) {
            this.holding = true;
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.holding = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
