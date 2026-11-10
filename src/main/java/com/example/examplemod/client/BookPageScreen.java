package com.example.examplemod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用的「大书页」界面：给文字量大的书籍饰品（如我所书 / 圣约·原典 / 十诫）用。
 *
 * <p>为什么不继续用 tooltip 翻页：原版 tooltip 的高度由屏幕决定，一页只能塞几行
 * （十诫的 {@code PAGE_SIZE} 只能是 5），几十行正文要翻六到十页。这里改成整屏书页：
 * 宽最多 760 像素，一屏二三十行，滚轮 / 方向键 / PgUp PgDn 自由滚动。
 *
 * <p>折行交给 {@link net.minecraft.client.gui.Font#split(FormattedText, int)}，
 * 所以 §x 颜色、粗体、斜体都会跟着换行保留。
 */
public class BookPageScreen extends Screen {

    /** 一行文字占的高度（原版字体 9 像素 + 1 像素行距）。 */
    private static final int LINE_H = 10;
    /** 面板内边距。 */
    private static final int PAD = 16;
    /** 标题区高度。 */
    private static final int TITLE_H = 30;
    /** 底部提示区高度。 */
    private static final int FOOTER_H = 16;
    /** 每格滚轮滚动的行数。 */
    private static final int WHEEL_STEP = 3;

    private static final int COLOR_FRAME = 0xFFC8A24A;
    private static final int COLOR_TEXT = 0xFFEFE6D8;
    private static final int COLOR_TITLE = 0xFFFFD24A;
    private static final int COLOR_SUBTITLE = 0xFF9A9186;
    private static final int COLOR_SUBTITLE_FROM = 0xFFB3A894;
    private static final int COLOR_SUBTITLE_TO = 0xFF7A7264;
    private static final int COLOR_FOOTER = 0xFF6E675C;

    private final Component subtitle;
    private final List<Component> source;
    /** 折行之后的最终渲染序列。 */
    private final List<FormattedCharSequence> wrapped = new ArrayList<>();
    /** 标题的渐变起止色（逐字插值），副标题用固定的暖灰渐变。 */
    private final int titleFrom;
    private final int titleTo;

    private int scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int visibleLines = 1;
    /** 标题/副标题去掉 §x 后的纯文本：颜色由这里的渐变换算，不再用字符串自带的颜色。 */
    private String titleText = "";
    private String subtitleText = "";

    public BookPageScreen(Component title, Component subtitle, List<Component> lines) {
        this(title, subtitle, lines, COLOR_TITLE, COLOR_TITLE);
    }

    public BookPageScreen(Component title, Component subtitle, List<Component> lines, int titleFrom, int titleTo) {
        super(title);
        this.subtitle = subtitle;
        this.source = lines;
        this.titleFrom = titleFrom;
        this.titleTo = titleTo;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(this.width - 40, 760);
        this.panelH = this.height - 40;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int bodyH = this.panelH - TITLE_H - FOOTER_H;
        this.visibleLines = Math.max(1, bodyH / LINE_H);

        this.titleText = stripCodes(this.title.getString());
        this.subtitleText = this.subtitle == null ? "" : stripCodes(this.subtitle.getString());

        int bodyW = this.panelW - PAD * 2 - 10;
        this.wrapped.clear();
        boolean lastBlank = true; // 行首不留空行
        for (Component line : this.source) {
            if (line.getString().isEmpty()) {
                // 段落之间最多留一行空行，避免整页被空白撑开
                if (!lastBlank) {
                    this.wrapped.add(FormattedCharSequence.EMPTY);
                    lastBlank = true;
                }
                continue;
            }
            for (FormattedCharSequence piece : this.font.split(line, bodyW)) {
                this.wrapped.add(piece);
            }
            lastBlank = false;
        }
        while (!this.wrapped.isEmpty() && this.wrapped.get(this.wrapped.size() - 1) == FormattedCharSequence.EMPTY) {
            this.wrapped.remove(this.wrapped.size() - 1);
        }
        this.clampScroll();
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 书页底板 + 金色边框
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE6100C08);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, COLOR_FRAME);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COLOR_FRAME);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_FRAME);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COLOR_FRAME);

        // 标题与副标题：逐字渐变（副标题不压线，横线在标题区底部）
        drawGradient(graphics, this.titleText, panelX + panelW / 2, panelY + 4,
                this.titleFrom, this.titleTo, true);
        if (!this.subtitleText.isEmpty()) {
            drawGradient(graphics, this.subtitleText, panelX + panelW / 2, panelY + 14,
                    COLOR_SUBTITLE_FROM, COLOR_SUBTITLE_TO, false);
        }
        graphics.fill(panelX + PAD, panelY + TITLE_H - 5, panelX + panelW - PAD, panelY + TITLE_H - 4, 0x66C8A24A);

        // 正文
        int textTop = panelY + TITLE_H;
        int y = textTop;
        int end = Math.min(this.wrapped.size(), this.scroll + this.visibleLines);
        for (int i = this.scroll; i < end; i++) {
            graphics.drawString(this.font, this.wrapped.get(i), panelX + PAD, y, COLOR_TEXT, false);
            y += LINE_H;
        }

        renderScrollbar(graphics, textTop);
        renderFooter(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollbar(GuiGraphics graphics, int trackTop) {
        if (this.wrapped.size() <= this.visibleLines) {
            return;
        }
        int trackH = this.panelH - TITLE_H - FOOTER_H;
        int trackX = panelX + panelW - 8;
        graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0x33FFFFFF);

        int thumbH = Math.max(14, (int) (trackH * (this.visibleLines / (float) this.wrapped.size())));
        int denom = Math.max(1, this.wrapped.size() - this.visibleLines);
        int thumbY = trackTop + (int) ((trackH - thumbH) * (this.scroll / (float) denom));
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, COLOR_FRAME);
    }

    private void renderFooter(GuiGraphics graphics) {
        int shownEnd = Math.min(this.wrapped.size(), this.scroll + this.visibleLines);
        String total = this.wrapped.size() <= this.visibleLines
                ? "全 " + this.wrapped.size() + " 行"
                : (this.scroll + 1) + "-" + shownEnd + " / " + this.wrapped.size() + " 行";
        String hint = this.wrapped.size() <= this.visibleLines
                ? total + "　·　Esc 关闭"
                : "滚轮 / ↑↓ / PgUp PgDn 翻动　·　" + total + "　·　Esc 关闭";
        graphics.drawCenteredString(this.font,
                Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY),
                panelX + panelW / 2, panelY + panelH - FOOTER_H + 4, COLOR_FOOTER);
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.wrapped.size() > this.visibleLines) {
            this.scroll -= (int) Math.signum(delta) * WHEEL_STEP;
            this.clampScroll();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 264 -> this.scrollBy(1);                    // 方向键 下
            case 265 -> this.scrollBy(-1);                   // 方向键 上
            case 266 -> this.scrollBy(-this.visibleLines);   // Page Up
            case 267 -> this.scrollBy(this.visibleLines);    // Page Down
            case 268 -> this.scrollTo(0);                    // Home
            case 269 -> this.scrollTo(Integer.MAX_VALUE);    // End
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void scrollBy(int lines) {
        this.scrollTo(this.scroll + lines);
    }

    private void scrollTo(int value) {
        this.scroll = value;
        this.clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, this.wrapped.size() - this.visibleLines);
        this.scroll = Mth.clamp(this.scroll, 0, max);
    }

    /** 去掉 §x 旧式颜色码：标题与副标题统一由这里给色，避免各书自带的花色打架。 */
    private static String stripCodes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * 逐字渐变的居中文字：第 i 个字取 from→to 的插值色。
     *
     * <p>为什么不用 {@code drawCenteredString}：那个只能整串一个颜色，
     * 而作者要的是原来 tooltip 里那种「一段史诗所结出的果实」式的逐字变色。
     */
    private void drawGradient(GuiGraphics graphics, String text, int centerX, int y,
                              int from, int to, boolean bold) {
        if (text.isEmpty()) {
            return;
        }
        int totalW = this.font.width(text);
        int x = centerX - totalW / 2;
        int span = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int color = lerpColor(from, to, i / (float) span);
            Component piece = Component.literal(ch);
            if (bold) {
                piece = piece.copy().withStyle(ChatFormatting.BOLD);
            }
            graphics.drawString(this.font, piece, x, y, color, false);
            x += this.font.width(ch);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF;
        int ag = (from >> 8) & 0xFF;
        int ab = from & 0xFF;
        int br = (to >> 16) & 0xFF;
        int bg = (to >> 8) & 0xFF;
        int bb = to & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
