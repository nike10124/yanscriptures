package com.example.examplemod.client;

import java.util.List;

import com.example.examplemod.BookWritingTableMenu;
import com.example.examplemod.ExampleMod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 著书台的界面绘制。
 *
 * <p>布局（贴图 176×220）：</p>
 * <ul>
 *   <li>y=20 三个选择格：纸 / 墨 / 线；</li>
 *   <li>右侧小框：照三格能写出什么；</li>
 *   <li>y=46..122 材料清单：用文字列出下端材料池里存了什么（不走槽位，走数据包）；</li>
 *   <li>y=140 起玩家背包。</li>
 * </ul>
 */
public class BookWritingTableScreen extends AbstractContainerScreen<BookWritingTableMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/gui/book_writing_table.png");

    /** 成品书图标的位置：成品框 126..145 / 19..38（比基础格大一圈、上下各突 1 像素），
     *  白框+暗框各 1 像素，里面那圈 16×16 从 128/21 开始。 */
    private static final int RESULT_X = 128;
    private static final int RESULT_Y = 21;

    /** 成品框右边的两个小三角：上=前一条候选配方，下=后一条。 */
    private static final int ARROW_X = 150;
    private static final int ARROW_W = 7;
    private static final int ARROW_H = 6;
    private static final int ARROW_UP_Y = 20;
    private static final int ARROW_DOWN_Y = 33;

    /** 材料清单区。 */
    private static final int LIST_X = 12;
    /** 左上角窄缝：书名一行、状态一行，右边 x=62 起就是三个选择格，所以只能写到 54 像素宽。 */
    private static final int HEADER_NAME_Y = 20;
    private static final int HEADER_HINT_Y = 30;
    private static final int HEADER_TEXT_WIDTH = 52;
    private static final int POOL_TITLE_Y = 50;
    private static final int POOL_HINT_Y = 60;
    /** 材料池列表：提示行删掉后从这里直接接着写（作者 2026-11-10 报「中间空了一行」）。 */
    private static final int POOL_LIST_Y = 62;
    private static final int LIST_LINE = 9;
    private static final int LIST_MAX_LINES = 4;
    /** 材料池底下那行小字的左边界（与材料列表左对齐）与可用宽度（写到贴右边框，作者 2026-11-10）。 */
    private static final int HINT_LEFT = LIST_X - 3;
    private static final float HINT_MAX_WIDTH = 160.0F;
    private static final int POOL_HINT_LINE_Y = 104;

    /** 当前滚动到第几条（材料太多时用滚轮翻）。 */
    private int scroll;

    public BookWritingTableScreen(BookWritingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 202;
        this.inventoryLabelY = 114;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 202);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);

        // 书名与「还差什么」都挤在左边这条窄缝里（x 8..60，右边就是三个选择格）。
        // 作者 2026-11-10：之前写在 y=26/36 会压到选择格与材料池框线上，改成截断 + 只报条目数，
        // 完整清单看成品框的悬浮提示。
        ItemStack result = this.menu.previewResult();
        if (result.isEmpty()) {
            graphics.drawString(this.font,
                    trim(Component.translatable("gui.yanscriptures.book_table.no_recipe").getString(), HEADER_TEXT_WIDTH),
                    8, HEADER_NAME_Y, 0x707070, false);
        } else {
            graphics.drawString(this.font,
                    trim(result.getHoverName().getString(), HEADER_TEXT_WIDTH),
                    8, HEADER_NAME_Y, 0x3F2A10, false);
            // 成品图标画在这里（标签层已平移到 GUI 原点，正好是本地坐标）
            drawRarityFrame(graphics, result);
            graphics.renderItem(result, RESULT_X, RESULT_Y);
            // 还差什么材料：这里只写「还需 N 项」，明细在悬浮提示里（避免压到下面的材料池框）
            // 作者 2026-11-10：齐了的时候那行字太长、压住选择格，干脆不画，成品框能点就是能写。
            if (!this.menu.missing().isEmpty()) {
                graphics.drawString(this.font,
                        Component.translatable("gui.yanscriptures.book_table.missing_count",
                                this.menu.missing().size()),
                        8, HEADER_HINT_Y, 0xAA2020, false);
            }
        }

        // 候选配方：不止一条时画出上下小三角，右边写「第几条 / 共几条」
        int count = this.menu.candidateCount();
        if (count > 1) {
            int index = this.menu.selectedIndex();
            drawArrow(graphics, ARROW_UP_Y, true, mouseX, mouseY);
            drawArrow(graphics, ARROW_DOWN_Y, false, mouseX, mouseY);
            String indicator = "§8" + (index + 1) + "/" + count;
            // 放在上三角头顶那块空白里（右对齐），不再压材料池边框
            graphics.drawString(this.font, indicator,
                    168 - this.font.width(indicator), ARROW_UP_Y - 10, 0x808080, false);
        }

        // 材料清单：直接读槽位（影子槽由原版同步），不再依赖自定义数据包
        List<ItemStack> materials = this.menu.poolItems();
        int maxScroll = Math.max(0, materials.size() - LIST_MAX_LINES);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        graphics.drawString(this.font,
                Component.translatable("gui.yanscriptures.book_table.pool_title"),
                LIST_X, POOL_TITLE_Y, 0x404040, false);
        // 作者 2026-11-10：这一行「（Shift + 右键下半部可整格放入…）」和下面列表挤在一起，
        // 而且到处给提示反而吵，删掉；放材料的操作本来靠试一次就懂。

        if (materials.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.yanscriptures.book_table.pool_empty"),
                    LIST_X, POOL_LIST_Y, 0x909090, false);
        } else {
            int lines = Math.min(materials.size() - this.scroll, LIST_MAX_LINES);
            for (int i = 0; i < lines; i++) {
                ItemStack stack = materials.get(this.scroll + i);
                String line = stack.getHoverName().getString() + " ×" + stack.getCount();
                graphics.drawString(this.font, line, LIST_X, POOL_LIST_Y + i * LIST_LINE, 0x303030, false);
            }
            // 滚动提示：右上角显示“第几屏 / 共几屏”，并画一条细滚动条
            if (materials.size() > LIST_MAX_LINES) {
                int pages = (materials.size() + LIST_MAX_LINES - 1) / LIST_MAX_LINES;
                int page = this.scroll / LIST_MAX_LINES + 1;
                String indicator = "§8" + page + "/" + pages;
                int width = this.font.width(indicator);
                // 和「材料池」同一行（作者 2026-11-10：原来单独占一行、还和列表隔了空行）
                graphics.drawString(this.font, indicator, 168 - width, POOL_TITLE_Y, 0x808080, false);

                int barX = 170;
                int trackTop = POOL_LIST_Y - 1;
                int trackHeight = LIST_MAX_LINES * LIST_LINE;
                int thumbHeight = Math.max(6, trackHeight * LIST_MAX_LINES / materials.size());
                int thumbTop = trackTop + (trackHeight - thumbHeight) * this.scroll / maxScroll;
                graphics.fill(barX, trackTop, barX + 2, trackTop + trackHeight, 0x40FFFFFF);
                graphics.fill(barX, thumbTop, barX + 2, thumbTop + thumbHeight, 0xFF606060);
            }
        }
        // 材料池底下一行小字提示（作者 2026-11-10 指定文案）：按可用宽度自动缩放，
        // 尽量写满 176 宽、不留右边距，最后一个字顶着右边框。
        Component hint = Component.translatable("gui.yanscriptures.book_table.hint_ready");
        int natural = this.font.width(hint);
        float scale = natural <= 0 ? 1.0F : Math.min(1.0F, HINT_MAX_WIDTH / (float) natural);
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, hint,
                (int) (HINT_LEFT / scale), (int) (POOL_HINT_LINE_Y / scale), 0xFF5B9BD5, false);
        graphics.pose().popPose();
    }

    /** 按像素宽度截断并加省略号（左侧那条窄缝只有 52 像素）。 */
    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && this.font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    /**
     * 成品框的悬浮提示。
     *
     * <p>必须在 {@code render()} 里、{@code super.render()} 之后画：{@code renderLabels} 是在
     * 「已平移到 (leftPos, topPos)」的姿态下执行的，在那里调 {@code renderTooltip} 会把提示框
     * 整体往右下推一截（作者 2026-11-10 报「提示框不跟鼠标」）。</p>
     */
    private void renderResultTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack result = this.menu.previewResult();
        if (result.isEmpty() || !isHovering(RESULT_X, RESULT_Y, 16, 16, mouseX, mouseY)) {
            return;
        }
        List<ItemStack> missing = this.menu.missing();
        if (missing.isEmpty()) {
            graphics.renderTooltip(this.font, result, mouseX, mouseY);
            return;
        }
        // 「另需」那一行放不下全部材料，悬浮成品框给出完整清单
        List<Component> lines = new java.util.ArrayList<>(this.getTooltipFromContainerItem(result));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.yanscriptures.book_table.missing_title"));
        for (ItemStack stack : missing) {
            lines.add(Component.literal(" ").append(stack.getHoverName())
                    .append(Component.literal(" ×" + stack.getCount())));
        }
        graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    /** 「另需：金胡萝卜 ×1、金苹果 ×1」；材料齐了返回 null。太长就截断，全列在成品框的悬浮提示里。 */
    private Component missingLine() {
        List<ItemStack> missing = this.menu.missing();
        if (missing.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : missing) {
            String piece = (text.length() > 0 ? "、" : "")
                    + stack.getHoverName().getString() + " ×" + stack.getCount();
            if (text.length() > 0 && this.font.width(text + piece) > 140) {
                text.append("…");
                break;
            }
            text.append(piece);
        }
        return Component.translatable("gui.yanscriptures.book_table.extras", text.toString());
    }

    /**
     * 成品格外圈那一圈颜色 = 成品稀有度（作者 2026-11-10 定）。
     *
     * <p>贴图里这一格和其它基础格完全一样（18×18、同一个灰），彩色边框画在这一格外面 1 像素，
     * 所以空着的时候不画，看起来就是普通格子。</p>
     */
    private void drawRarityFrame(GuiGraphics graphics, ItemStack result) {
        int color = rarityColor(result);
        int x = RESULT_X - 2;
        int y = RESULT_Y - 2;
        int w = 20;
        int h = 20;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 稀有度 → ARGB。原版稀有度自带颜色；《如我所书》作者指定粉色。 */
    private static int rarityColor(ItemStack stack) {
        if (stack.is(ExampleMod.BOOK_OF_ME.get())) {
            return 0xFFFF69B4;
        }
        ChatFormatting formatting = stack.getRarity().color;
        Integer rgb = formatting.getColor();
        return rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb;
    }

    /** 画一个 7×6 的小三角。上下两枚必须左右对称、大小一致（作者 2026-11-10 报画得不一样）。 */
    private void drawArrow(GuiGraphics graphics, int y, boolean up, int mouseX, int mouseY) {
        int color = isHovering(ARROW_X, y, ARROW_W, ARROW_H, mouseX, mouseY) ? 0xFF3F2A10 : 0xFF6A6A6A;
        int center = ARROW_X + ARROW_W / 2;
        for (int row = 0; row < ARROW_H; row++) {
            // 上三角第 0 行最窄；下三角第 0 行最宽 —— 用同一套宽度序列，只是方向相反
            int step = up ? row : ARROW_H - 1 - row;
            int half = Math.min(ARROW_W / 2, step / 2 + 1);
            graphics.fill(center - half, y + row, center + half, y + row + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int localX = (int) mouseX - this.leftPos;
            int localY = (int) mouseY - this.topPos;
            if (localX >= ARROW_X - 2 && localX <= ARROW_X + ARROW_W + 2) {
                if (localY >= ARROW_UP_Y - 2 && localY <= ARROW_UP_Y + ARROW_H + 2) {
                    this.menu.cycle(1);
                    return true;
                }
                if (localY >= ARROW_DOWN_Y - 2 && localY <= ARROW_DOWN_Y + ARROW_H + 2) {
                    this.menu.cycle(-1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<ItemStack> materials = this.menu.poolItems();
        int maxScroll = Math.max(0, materials.size() - LIST_MAX_LINES);
        if (maxScroll > 0) {
            this.scroll = Math.max(0, Math.min(this.scroll - (int) Math.signum(delta), maxScroll));
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // 成品框的提示必须画在屏幕坐标系里，不能留在 renderLabels 的平移姿态中
        renderResultTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
