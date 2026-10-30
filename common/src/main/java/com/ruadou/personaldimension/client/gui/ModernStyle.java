package com.ruadou.personaldimension.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 现代深色扁平风格的样式常量与绘制原语。
 *
 * <p>作为"深色扁平 + 强调色"方向的<b>唯一配色与绘制来源</b>。
 * 设计遵循现代深色 UI 规范：不使用纯黑、强调色去饱和、层次靠<b>亮度</b>（越靠上越亮）而非黑色投影表达、
 * 全局仅用<b>单一强调色</b>（60-30-10），卡片以背景色差而非硬边框区分。圆角以纯 {@code fill} 按圆方程
 * 逐行内缩近似，无贴图无 UI 库。若该方向定稿，套用到各界面时只需引用此处常量与方法。
 */
public final class ModernStyle {

    private ModernStyle() {}

    // ===== 结构色（层次靠亮度：面板 < 标题 < 卡片，越靠上越亮）=====
    /** 全屏遮罩（深黑半透明，令面板"浮"起而不靠黑投影）。 */
    public static final int SCRIM = 0xCC0A0D14;
    /** 主面板底色（0dp 基底，深灰非纯黑）。 */
    public static int PANEL_BG = 0xFF181D28;
    /** 主面板描边（低对比，仅轻勾勒）。 */
    public static int PANEL_BORDER = 0xFF2F394D;
    /** 标题栏底色（略亮于面板）。 */
    public static int HEADER_BG = 0xFF1E2431;
    /** 次级卡片底色（明显亮于面板，以亮度表达抬升）。 */
    public static int CARD_BG = 0xFF232B3A;
    /** 次级卡片描边（极淡，几乎融入）。 */
    public static int CARD_BORDER = 0xFF2B3444;
    /** 分隔线。 */
    public static int DIVIDER = 0xFF262E3D;

    // ===== 文本（不使用纯白，避免深色下光晕）=====
    /** 主文字（≈90%）。 */
    public static int TEXT = 0xFFE3E7EF;
    /** 次要文字（≈60%）。 */
    public static int TEXT_SUB = 0xFF98A2B3;
    /** 弱化文字（≈40%）。 */
    public static int TEXT_WEAK = 0xFF626C7D;

    // ===== 强调 / 语义（去饱和，深色下不灼眼）=====
    /** 主强调色（去饱和电蓝，全局唯一强调色）。 */
    public static int ACCENT = 0xFF58A6DB;
    /** 主强调色悬停提亮。 */
    public static int ACCENT_HOVER = 0xFF74B7E6;
    /** 实心强调/危险填充上的近白文字（不用深字，观感更干净）。 */
    public static int ACCENT_TEXT = 0xFFF5F8FC;
    /** 次强调色（去饱和紫，仅极少量点缀）。 */
    public static final int ACCENT2 = 0xFF9A8CD8;
    /** 危险色（去饱和红）。 */
    public static final int DANGER = 0xFFD46A6A;
    /** 危险色悬停提亮。 */
    public static final int DANGER_HOVER = 0xFFDE8181;
    /** 成功 / 开启（去饱和绿）。 */
    public static final int SUCCESS = 0xFF5FB98C;
    /** 警示按钮常态色（去饱和暖琥珀，融于色调又醒目，替代刺眼红作危险操作默认态）。 */
    public static final int WARN = 0xFFC99C55;
    /** 警示色悬停提亮。 */
    public static final int WARN_HOVER = 0xFFD8AE68;

    // ===== 控件 =====
    /** 次级（幽灵）按钮半透明白叠加，随底色自然提亮。 */
    public static int GHOST_FILL = 0x12FFFFFF;
    /** 次级按钮悬停叠加。 */
    public static int GHOST_FILL_HOVER = 0x1FFFFFFF;
    /** 中性控件描边。 */
    public static int BTN_BORDER = 0xFF3C475B;
    /** 开关关闭态轨道（中性暗灰凹槽）。 */
    public static final int TRACK_OFF = 0xFF333C4D;
    /** 开关开启态轨道（点亮为灰白偏灰的浅色凹槽，不用彩色，不刺眼；靠明亮滑钮形成对比）。 */
    public static final int TRACK_ON = 0xFFB2BAC8;
    /** 开关滑钮（始终明亮的凸起手柄，近白偏冷）。 */
    public static final int KNOB = 0xFFF2F4F8;
    /** 悬停淡白叠加。 */
    public static final int HOVER_OVERLAY = 0x14FFFFFF;
    /** 深色内凹物品槽底色（比面板更暗，形成凹陷）。 */
    public static int SLOT_BG = 0xFF10131A;
    /** 深色内凹物品槽深描边。 */
    public static int SLOT_BORDER = 0xFF090B10;

    /** 默认圆角半径（逐行圆弧近似）。 */
    public static final int CHAMFER = 4;


    // Client-only preferences. Loaded once; theme changes are saved locally.
    private static int selectedTheme = 0;
    private static boolean themeLoaded = false;

    public static int themeIndex() {
        ensureThemeLoaded();
        return selectedTheme;
    }

    public static void ensureThemeLoaded() {
        if (themeLoaded) return;
        themeLoaded = true;
        try {
            java.nio.file.Path path = net.minecraft.client.Minecraft.getInstance()
                .gameDirectory.toPath().resolve("config/personalshelter-ui-theme.txt");
            if (java.nio.file.Files.isRegularFile(path)) {
                int saved = Integer.parseInt(java.nio.file.Files.readString(path).trim());
                setTheme(saved, false);
            }
        } catch (Exception ignored) { setTheme(0, false); }
        try {
            java.nio.file.Path path = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config/personalshelter-large-text.txt");
            largeText = java.nio.file.Files.isRegularFile(path) && Boolean.parseBoolean(java.nio.file.Files.readString(path).trim());
        } catch (Exception ignored) {}
    }

    public static void applyTheme(int theme) { setTheme(theme, true); }

    private static void setTheme(int theme, boolean save) {
        selectedTheme = Math.max(0, Math.min(9, theme));
        // Background, header, card, border, text, secondary text, accent.
        int[][] palette = {
            {0xFF181D28,0xFF1E2431,0xFF232B3A,0xFF2F394D,0xFFE3E7EF,0xFF98A2B3,0xFF58A6DB},
            {0xFFF0F1F4,0xFFE2E5EA,0xFFFFFFFF,0xFFBFC6D0,0xFF273247,0xFF596778,0xFF447FC0},
            {0xFF14365C,0xFF194A7B,0xFF21558B,0xFF2F6EAD,0xFFF1F8FF,0xFFB7D8F1,0xFF6EC5FF},
            {0xFFF4DBE7,0xFFE9C4D7,0xFFFFF1F7,0xFFD49EBB,0xFF55364B,0xFF795A6C,0xFFB9548B},
            {0xFFDCEACC,0xFFBFD6AA,0xFFEEF7E5,0xFF91B27D,0xFF2B462D,0xFF527052,0xFF559C55},
            {0xFFD6EFF6,0xFFB8E1ED,0xFFEAF9FD,0xFF86BED0,0xFF244A5B,0xFF47798B,0xFF299FC4},
            {0xFF090A0C,0xFF121417,0xFF1B1D21,0xFF373B44,0xFFF0F1F3,0xFFA4ABB5,0xFF8AABDD},
            {0xFF414448,0xFF50545A,0xFF5B6066,0xFF777E85,0xFFE3E5E8,0xFFBFC5CB,0xFF9BA9B4},
            {0xFF111C16,0xFF1B2C22,0xFF263A2D,0xFF54735B,0xFFDCF6C8,0xFFBDD8AF,0xFF83C78C},
            {0xFF291B3D,0xFF3E2557,0xFF4E2B69,0xFF9153B1,0xFFF3C3FF,0xFFCF9FE8,0xFFDB65C8}
        };
        int[] c = palette[selectedTheme];
        PANEL_BG=c[0]; HEADER_BG=c[1]; CARD_BG=c[2]; PANEL_BORDER=c[3];
        CARD_BORDER=c[3]; BTN_BORDER=c[3]; DIVIDER=c[3];
        TEXT=c[4]; TEXT_SUB=c[5]; TEXT_WEAK=c[5]; ACCENT=c[6];
        ACCENT_HOVER=lighten(c[6],0.14f);
        ACCENT_TEXT=selectedTheme == 8 ? 0xFF102317 : c[4]; GHOST_FILL=withAlpha(c[2],0xBB);
        GHOST_FILL_HOVER=withAlpha(c[6],0x55);
        SLOT_BG=darken(c[0],0.08f); SLOT_BORDER=darken(c[3],0.18f);
        if (save) {
            try {
                java.nio.file.Path path = net.minecraft.client.Minecraft.getInstance()
                    .gameDirectory.toPath().resolve("config/personalshelter-ui-theme.txt");
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path, Integer.toString(selectedTheme));
            } catch (Exception ignored) { }
        }
    }

    private static boolean largeText;
    public static boolean largeText() { return largeText; }
    public static void setLargeText(boolean enabled) {
        largeText = enabled;
        try {
            java.nio.file.Path path = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/personalshelter-large-text.txt");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, Boolean.toString(enabled));
        } catch (Exception ignored) {}
    }

    // ===== 圆角几何原语 =====

    /**
     * 逐行圆弧填充圆角矩形：按圆方程计算每行的水平内缩量，四角平滑过渡（近似真圆角）。
     * 半径超出 {@code min(w,h)/2} 时自动收敛，避免越界。
     */
    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color); // 中段全宽
        for (int dy = 0; dy < r; dy++) {
            int inset = cornerInset(dy, r);
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);              // 顶部圆角行
            g.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, color);      // 底部圆角行
        }
    }

    /** 圆角某一行的水平内缩量：edgeDist 为该行到最近水平边的行数（0=最边缘），≥r 时不内缩。 */
    private static int cornerInset(int edgeDist, int r) {
        if (edgeDist >= r) {
            return 0;
        }
        double dist = r - edgeDist - 0.5;
        int half = (int) Math.round(Math.sqrt((double) r * r - dist * dist));
        return r - half;
    }

    /** 竖向渐变填充圆角矩形（顶色→底色逐行插值），用于按钮/滑块等需要立体感的实心控件。 */
    public static void fillRoundVGradient(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            float t = h <= 1 ? 0f : (float) row / (h - 1);
            int color = lerpColor(top, bottom, t);
            int edgeDist = Math.min(row, h - 1 - row);
            int inset = cornerInset(edgeDist, r);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** 仅上方两角圆角（下方直角）的填充，用于贴合面板圆角的标题栏底。 */
    public static void fillRoundTop(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w / 2, h)));
        for (int dy = 0; dy < r; dy++) {
            int inset = cornerInset(dy, r);
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
        }
        g.fill(x, y + r, x + w, y + h, color);
    }

    /** 圆角矩形 + 1px 描边（描边色在外、填充色内缩 1px，边缘沿圆角均匀）。 */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int border) {
        fillRound(g, x, y, w, h, r, border);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** 竖向渐变圆角矩形 + 1px 描边（顶色→底色渐变内芯 + 描边），现代实心控件主力绘制。 */
    public static void roundedRectGrad(GuiGraphics g, int x, int y, int w, int h, int r,
                                       int top, int bottom, int border) {
        fillRound(g, x, y, w, h, r, border);
        fillRoundVGradient(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top, bottom);
    }

    // ===== 颜色工具 =====

    /** 两个 ARGB 颜色按 t∈[0,1] 线性插值。 */
    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** 提亮：RGB 向白色插值，保留原 alpha。 */
    public static int lighten(int color, float amt) {
        int rgb = lerpColor(color | 0xFF000000, 0xFFFFFFFF, amt) & 0x00FFFFFF;
        return (color & 0xFF000000) | rgb;
    }

    /** 压暗：RGB 向黑色插值，保留原 alpha。 */
    public static int darken(int color, float amt) {
        int rgb = lerpColor(color | 0xFF000000, 0xFF000000, amt) & 0x00FFFFFF;
        return (color & 0xFF000000) | rgb;
    }

    /** 把 RGB 色叠加指定 alpha（0..255），用于半透明底/描边。 */
    public static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    // ===== 复合控件 =====

    /** 极简列表指示点：略暗外圈 + 纯色内芯 + 一道极淡顶部微光（去饱和、不炫）。 */
    public static void dot(GuiGraphics g, int x, int y, int size, int color) {
        fillRound(g, x, y, size, size, size / 2, darken(color, 0.28f));   // 外圈略暗
        fillRound(g, x + 1, y + 1, size - 2, size - 2, (size - 2) / 2, color); // 内芯
        fillRound(g, x + 2, y + 1, Math.max(1, size - 4), 1, 0, 0x1FFFFFFF);   // 顶部微光（弱）
    }

    /** 主面板：极淡投影分离 + 描边 + 顶部内高光（层次主要靠自身亮于遮罩）。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        fillRound(g, x - 1, y + 2, w + 2, h, CHAMFER + 2, 0x2E000000); // 单层极淡投影，仅作分离
        roundedRect(g, x, y, w, h, CHAMFER + 1, PANEL_BG, PANEL_BORDER);
        fillRound(g, x + 3, y + 1, w - 6, 1, 0, 0x12FFFFFF);           // 顶部 1px 内高光（抬升感）
        if (selectedTheme == 7) {
            g.fill(x + 4, y + 3, x + w - 4, y + 5, 0xFF30343A);
            g.fill(x + 4, y + h - 6, x + w - 4, y + h - 4, 0xFF24282D);
            for (int yy = y + 12; yy < y + h - 10; yy += 18) {
                g.fill(x + 2, yy, x + 4, yy + 3, 0xFF8E969E);
                g.fill(x + w - 4, yy, x + w - 2, yy + 3, 0xFF8E969E);
            }
        } else if (selectedTheme == 8) {
            g.fill(x + 4, y + 4, x + w - 4, y + 5, 0xFF6AB96A);
            g.fill(x + 4, y + h - 5, x + w - 4, y + h - 4, 0xFF6AB96A);
            g.fill(x + 4, y + 4, x + 5, y + h - 4, 0xFF6AB96A);
            g.fill(x + w - 5, y + 4, x + w - 4, y + h - 4, 0xFF6AB96A);
            for (int yy = y + 24; yy < y + h - 8; yy += 4)
                g.fill(x + 6, yy, x + w - 6, yy + 1, 0x0A80FF80);
        } else if (selectedTheme == 9) {
            for (int i = 0; i < 13; i++) {
                g.fill(x + 4 + i, y + 4, x + 5 + i, y + 5, 0xFFD97BF5);
                g.fill(x + w - 5 - i, y + 4, x + w - 4 - i, y + 5, 0xFFD97BF5);
                g.fill(x + 4 + i, y + h - 5, x + 5 + i, y + h - 4, 0xFFD97BF5);
            }
            g.fill(x + 3, y + 20, x + 5, y + h - 20, 0xFF9055B6);
            g.fill(x + w - 5, y + 20, x + w - 3, y + h - 20, 0xFF9055B6);
        }
    }

    /** 次级卡片：更亮底色（以亮度表达抬升）+ 极淡描边 + 顶部极淡内高光。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        roundedRect(g, x, y, w, h, CHAMFER, CARD_BG, CARD_BORDER);
        fillRound(g, x + 3, y + 1, w - 6, 1, 0, 0x0CFFFFFF);
    }

    /** 横向分隔线。 */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, DIVIDER);
    }

    /** 深色内凹物品槽（默认 18×18）：更暗槽底 + 深描边 + 顶/左内阴影、底/右内高光，营造凹陷。 */
    public static void slot(GuiGraphics g, int x, int y) {
        slot(g, x, y, 18, 18);
    }

    /** 深色内凹物品槽（任意尺寸）：绘制逻辑同 {@link #slot(GuiGraphics, int, int)}，供画板等大区域复用。 */
    public static void slot(GuiGraphics g, int x, int y, int w, int h) {
        roundedRect(g, x, y, w, h, 2, SLOT_BG, SLOT_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33000000);        // 顶部内阴影
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x26000000);        // 左侧内阴影
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x14FFFFFF); // 底部内高光
    }

    /**
     * 现代开关（经典对比型，像素风）：<b>凹陷凹槽</b>轨道（关=深灰、开=点亮为浅色，不用彩色，
     * 内顶 1px 阴影 + 内底 1px 高光表达凹陷）+ <b>始终明亮</b>的圆角滑钮（关/开同色，仅位置变化）。
     * 滑钮带一圈<b>深描边 + 落影</b>，因此压在深轨道（关）或浅轨道（开）上都能清晰分离——
     * 遵循"手柄恒亮、轨道示态"的经典拨钮范式，避免反色滑钮在激活态反而变暗的违和感。
     * progress ∈ [0,1] 表示钮从左到右的位置，便于过渡动画。
     */
    public static void toggle(GuiGraphics g, int x, int y, int w, int h, boolean on, float progress) {
        int r = CHAMFER;
        int track = on ? TRACK_ON : TRACK_OFF;
        // 轨道：圆角矩形凹槽 + 深描边；像素风内凹（顶部内阴影深、底部内高光浅）
        roundedRect(g, x, y, w, h, r, track, darken(track, 0.42f));
        fillRound(g, x + 2, y + 1, w - 4, 1, 0, 0x33000000);        // 顶部内阴影（深）
        fillRound(g, x + 2, y + h - 2, w - 4, 1, 0, 0x22FFFFFF);    // 底部内高光（浅）
        // 滑钮：始终明亮手柄 + 深描边（任意轨道上都清晰）+ 落影/顶高光呈明确"凸起"
        int knob = h - 6;
        int travel = w - knob - 6;
        int knobX = x + 3 + Math.round(travel * clamp01(progress));
        int knobY = y + 3;
        int kr = Math.max(2, r - 1);
        fillRound(g, knobX + 1, knobY + 1, knob, knob, kr, 0x50000000);       // 落影（右下 1px）
        roundedRectGrad(g, knobX, knobY, knob, knob, kr,
            lighten(KNOB, 0.04f), darken(KNOB, 0.10f), darken(KNOB, 0.48f));  // 明亮手柄 + 深描边
        fillRound(g, knobX + 2, knobY + 1, knob - 4, 1, 0, 0x66FFFFFF);       // 顶部高光
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /**
     * 现代滚动条（overlay 风）：半透明黑凹槽 + 浅灰（半透明白）药丸滑块，无描边无强高光。
     * scroll 已 clamp 到 [0,max]。
     */
    public static void scrollbar(GuiGraphics g, int x, int top, int w, int trackH,
                                 int scroll, int visible, int total) {
        int max = Math.max(0, total - visible);
        int thumbH = Math.max(16, trackH * visible / total);
        int thumbY = max == 0 ? top : top + (trackH - thumbH) * scroll / max;
        int c = w / 2;
        fillRound(g, x, top, w, trackH, c, 0x33000000);      // 半透明黑凹槽（内嵌感）
        fillRound(g, x, thumbY, w, thumbH, c, 0x66FFFFFF);   // 浅灰药丸滑块（半透明白）
    }
}
