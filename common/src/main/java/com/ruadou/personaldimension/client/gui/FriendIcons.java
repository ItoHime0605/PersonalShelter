package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 好友系统 UI 的基元图标绘制工具。
 *
 * <p>全部图标以 {@link GuiGraphics#fill} 直接绘制，<b>不依赖任何 PNG 精灵或原版精灵图集</b>，
 * 因此不受各 MC 版本精灵路径变动影响，天然跨版本兼容（这也是本项目 UI 一贯的做法）。
 * 集中于此类避免各界面重复绘制逻辑（DRY）。
 */
public final class FriendIcons {

    private FriendIcons() {}

    // 统一引用 ModernStyle 语义调色板，保留本地别名以维持绘制代码可读性
    private static final int SLOT_BG = ModernStyle.TRACK_OFF;                         // 凹槽底（深）
    private static final int SLOT_BG_OFF = ModernStyle.darken(ModernStyle.TRACK_OFF, 0.25f); // 禁用凹槽底
    private static final int SLOT_DARK = ModernStyle.darken(ModernStyle.TRACK_OFF, 0.42f);   // 凹槽内深边
    private static final int SLOT_LIGHT = 0x22FFFFFF;                                 // 凹槽内浅边（半透明白）
    private static final int CHECK_ON = ModernStyle.SUCCESS;    // 勾选绿
    private static final int CHECK_OFF = ModernStyle.TEXT_WEAK; // 禁用灰
    private static final int MARK_ON = ModernStyle.SUCCESS;     // 开关"开"绿（√）
    private static final int MARK_OFF = ModernStyle.DANGER;     // 开关"关"红（✗）

    /**
     * 绘制一个复选框（12x12）。选中时在凹槽内画绿色对勾。
     *
     * @param checked 是否勾选
     * @param enabled 是否可用（禁用时对勾灰显、底色变暗）
     */
    public static void drawCheckbox(GuiGraphics g, int x, int y, boolean checked, boolean enabled) {
        int size = 12;
        g.fill(x, y, x + size, y + size, enabled ? SLOT_BG : SLOT_BG_OFF);
        g.fill(x, y, x + size, y + 1, SLOT_DARK);              // 顶部深
        g.fill(x, y, x + 1, y + size, SLOT_DARK);              // 左侧深
        g.fill(x, y + size - 1, x + size, y + size, SLOT_LIGHT); // 底部亮
        g.fill(x + size - 1, y, x + size, y + size, SLOT_LIGHT); // 右侧亮
        if (checked) {
            int c = enabled ? CHECK_ON : CHECK_OFF;
            // 对勾：短臂（下行）+ 长臂（上行），逐段 2px 粗
            g.fill(x + 2, y + 6, x + 4, y + 8, c);
            g.fill(x + 3, y + 7, x + 5, y + 9, c);
            g.fill(x + 4, y + 6, x + 6, y + 8, c);
            g.fill(x + 5, y + 5, x + 7, y + 7, c);
            g.fill(x + 6, y + 4, x + 8, y + 6, c);
            g.fill(x + 7, y + 3, x + 9, y + 5, c);
        }
    }

    /** 绘制以 (cx,cy) 为中心、半径 r 的实心菱形（用作默认组的星标）。 */
    public static void drawDiamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    /** 绘制一个简易人物剪影（约 10x11），用作好友 / 玩家条目的项目符号。 */
    public static void drawPerson(GuiGraphics g, int x, int y, int color) {
        // 头部
        g.fill(x + 3, y, x + 7, y + 4, color);
        // 肩部（上窄下宽）
        g.fill(x + 2, y + 5, x + 8, y + 6, color);
        g.fill(x + 1, y + 6, x + 9, y + 11, color);
    }

    /** 绘制一个向右的三角箭头（约 7x8），用作"进入 / 前往"提示。 */
    public static void drawArrow(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + i, x + i + 1, y + 8 - i, color);
        }
    }

    /**
     * 绘制一个约 7x6 的绿色对勾标记（<b>无边框</b>），用于叠加在开关按钮上表示"已加入名单"。
     * 与 {@link #drawCheckbox} 的对勾同形，但不含凹槽，便于直接盖在 {@code FlatButton} 之上。
     */
    public static void drawCheckMark(GuiGraphics g, int x, int y) {
        g.fill(x,     y + 3, x + 2, y + 5, MARK_ON);
        g.fill(x + 1, y + 4, x + 3, y + 6, MARK_ON);
        g.fill(x + 2, y + 3, x + 4, y + 5, MARK_ON);
        g.fill(x + 3, y + 2, x + 5, y + 4, MARK_ON);
        g.fill(x + 4, y + 1, x + 6, y + 3, MARK_ON);
        g.fill(x + 5, y,     x + 7, y + 2, MARK_ON);
    }

    /** 绘制一个约 7x7 的红色叉号标记（<b>无边框</b>），用于叠加在开关按钮上表示"未加入名单"。 */
    public static void drawCrossMark(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i,     y + i, x + i + 2, y + i + 2, MARK_OFF); // 左上 → 右下
            g.fill(x + 5 - i, y + i, x + 7 - i, y + i + 2, MARK_OFF); // 右上 → 左下
        }
    }
}
