package com.example.examplemod.client;

import java.util.List;

import com.example.examplemod.BookWritingTableMenu;
import com.example.examplemod.ExampleMod;

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

    private static final int RESULT_X = 126;
    private static final int RESULT_Y = 21;

    /** 成品框右边的两个小三角：上=前一条候选配方，下=后一条。 */
    private static final int ARROW_X = 150;
    private static final int ARROW_W = 7;
    private static final int ARROW_H = 6;
    private static final int ARROW_UP_Y = 20;
    private static final int ARROW_DOWN_Y = 33;

    /** 材料清单区。 */
    private static final int LIST_X = 12;
    private static final int POOL_TITLE_Y = 50;
    private static final int POOL_HINT_Y = 60;
    private static final int POOL_LIST_Y = 72;
    private static final int LIST_LINE = 9;
    private static final int LIST_MAX_LINES = 4;

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

        ItemStack result = this.menu.previewResult();
        if (result.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.yanscriptures.book_table.no_recipe"),
                    8, 26, 0x707070, false);
        } else {
            graphics.drawString(this.font, result.getHoverName(), 8, 26, 0x3F2A10, false);
            // 还差什么材料：缺的逐项列出来（红字），齐了就说齐了（绿字）。
            // 作者 2026-10-31：这一行要在「三格一放好」就按选中的那本书算，不看材料池够不够。
            Component missingLine = missingLine();
            if (missingLine == null) {
                graphics.drawString(this.font,
                        Component.translatable("gui.yanscriptures.book_table.ready"),
                        8, 36, 0x2E7D32, false);
            } else {
                graphics.drawString(this.font, missingLine, 8, 36, 0xAA2020, false);
            }        }

        // 候选配方：不止一条时画出上下小三角，右边写「第几条 / 共几条」
        int count = this.menu.candidateCount();
        if (count > 1) {
            int index = this.menu.selectedIndex();
            drawArrow(graphics, ARROW_UP_Y, true, mouseX, mouseY);
            drawArrow(graphics, ARROW_DOWN_Y, false, mouseX, mouseY);
            String indicator = "§8" + (index + 1) + "/" + count;
            graphics.drawString(this.font, indicator, 148, 41, 0x808080, false);
        }

        // 材料清单：直接读槽位（影子槽由原版同步），不再依赖自定义数据包
        List<ItemStack> materials = this.menu.poolItems();
        int maxScroll = Math.max(0, materials.size() - LIST_MAX_LINES);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        graphics.drawString(this.font,
                Component.translatable("gui.yanscriptures.book_table.pool_title"),
                LIST_X, POOL_TITLE_Y, 0x404040, false);
        graphics.drawString(this.font,
                Component.translatable("gui.yanscriptures.book_table.pool_hint"),
                LIST_X, POOL_HINT_Y, 0x808080, false);

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
                graphics.drawString(this.font, indicator, 168 - width, POOL_HINT_Y, 0x808080, false);

                int barX = 170;
                int trackTop = POOL_LIST_Y - 1;
                int trackHeight = LIST_MAX_LINES * LIST_LINE;
                int thumbHeight = Math.max(6, trackHeight * LIST_MAX_LINES / materials.size());
                int thumbTop = trackTop + (trackHeight - thumbHeight) * this.scroll / maxScroll;
                graphics.fill(barX, trackTop, barX + 2, trackTop + trackHeight, 0x40FFFFFF);
                graphics.fill(barX, thumbTop, barX + 2, thumbTop + thumbHeight, 0xFF606060);
            }
        }

        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, 0x404040, false);

        if (!result.isEmpty()) {
            graphics.renderItem(result, RESULT_X, RESULT_Y);
            if (isHovering(RESULT_X, RESULT_Y, 16, 16, mouseX, mouseY)) {
                List<ItemStack> missing = this.menu.missing();
                if (missing.isEmpty()) {
                    graphics.renderTooltip(this.font, result, mouseX, mouseY);
                } else {
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
            }
        }
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

    /** 画一个 7×6 的小三角。 */
    private void drawArrow(GuiGraphics graphics, int y, boolean up, int mouseX, int mouseY) {
        int color = isHovering(ARROW_X, y, ARROW_W, ARROW_H, mouseX, mouseY) ? 0xFF3F2A10 : 0xFF6A6A6A;
        for (int row = 0; row < ARROW_H; row++) {
            int half = up ? row / 2 + 1 : (ARROW_H - row) / 2 + 1;
            int x0 = ARROW_X + 3 - half;
            graphics.fill(x0, y + row, x0 + half * 2, y + row + 1, color);
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
        renderTooltip(graphics, mouseX, mouseY);
    }
}
