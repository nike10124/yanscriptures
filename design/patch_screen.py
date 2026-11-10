# -*- coding: utf-8 -*-
"""著书台界面：上下小三角 + 额外材料那一行。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/client/BookWritingTableScreen.java")
src = p.read_text(encoding="utf-8")

src = src.replace("""    private static final int RESULT_X = 126;
    private static final int RESULT_Y = 21;""",
"""    private static final int RESULT_X = 126;
    private static final int RESULT_Y = 21;

    /** 成品框右边的两个小三角：上=前一条候选配方，下=后一条。 */
    private static final int ARROW_X = 150;
    private static final int ARROW_W = 7;
    private static final int ARROW_H = 6;
    private static final int ARROW_UP_Y = 20;
    private static final int ARROW_DOWN_Y = 33;""")

old = """        ItemStack result = this.menu.previewResult();
        if (result.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.yanscriptures.book_table.no_recipe"),
                    8, 26, 0x707070, false);
        } else {
            graphics.drawString(this.font, result.getHoverName(), 8, 26, 0x3F2A10, false);
            graphics.drawString(this.font,
                    Component.translatable("gui.yanscriptures.book_table.need_scroll"),
                    8, 36, 0x707070, false);
        }"""
new = """        ItemStack result = this.menu.previewResult();
        if (result.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.yanscriptures.book_table.no_recipe"),
                    8, 26, 0x707070, false);
        } else {
            graphics.drawString(this.font, result.getHoverName(), 8, 26, 0x3F2A10, false);
            Component extras = extrasLine();
            if (extras == null) {
                graphics.drawString(this.font,
                        Component.translatable("gui.yanscriptures.book_table.need_scroll"),
                        8, 36, 0x707070, false);
            } else {
                graphics.drawString(this.font, extras, 8, 36, 0x6A4A22, false);
            }
        }

        // 候选配方：不止一条时画出上下小三角，右边写「第几条 / 共几条」
        int count = this.menu.candidateCount();
        if (count > 1) {
            int index = this.menu.selectedIndex();
            drawArrow(graphics, ARROW_UP_Y, true);
            drawArrow(graphics, ARROW_DOWN_Y, false);
            String indicator = "§8" + (index + 1) + "/" + count;
            graphics.drawString(this.font, indicator, 158, 27, 0x808080, false);
        }"""
assert old in src
src = src.replace(old, new)

# 在 renderLabels 结尾之前插入辅助方法
old = """    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {"""
new = """    /** 「另需：金胡萝卜 ×1、金苹果 ×1」；没有额外材料就返回 null。 */
    private Component extrasLine() {
        var recipe = this.menu.selectedRecipe();
        if (recipe == null || recipe.extras().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (ItemStack extra : recipe.extras()) {
            if (text.length() > 0) {
                text.append('、');
            }
            text.append(extra.getHoverName().getString()).append(" ×").append(extra.getCount());
        }
        return Component.translatable("gui.yanscriptures.book_table.extras", text.toString());
    }

    /** 画一个 7×6 的小三角。 */
    private void drawArrow(GuiGraphics graphics, int y, boolean up) {
        int color = isHovering(ARROW_X, y, ARROW_W, ARROW_H, 0, 0) ? 0xFF3F2A10 : 0xFF6A6A6A;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {"""
assert old in src
src = src.replace(old, new)

p.write_text(src, encoding="utf-8")
print("Screen ok")
