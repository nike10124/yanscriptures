# -*- coding: utf-8 -*-
"""小三角的悬停高亮与「x/n」位置修正。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/client/BookWritingTableScreen.java")
src = p.read_text(encoding="utf-8")

old = """            drawArrow(graphics, ARROW_UP_Y, true);
            drawArrow(graphics, ARROW_DOWN_Y, false);
            String indicator = "§8" + (index + 1) + "/" + count;
            graphics.drawString(this.font, indicator, 158, 27, 0x808080, false);"""
new = """            drawArrow(graphics, ARROW_UP_Y, true, mouseX, mouseY);
            drawArrow(graphics, ARROW_DOWN_Y, false, mouseX, mouseY);
            String indicator = "§8" + (index + 1) + "/" + count;
            graphics.drawString(this.font, indicator, 148, 41, 0x808080, false);"""
assert old in src
src = src.replace(old, new)

old = """    /** 画一个 7×6 的小三角。 */
    private void drawArrow(GuiGraphics graphics, int y, boolean up) {
        int color = isHovering(ARROW_X, y, ARROW_W, ARROW_H, 0, 0) ? 0xFF3F2A10 : 0xFF6A6A6A;"""
new = """    /** 画一个 7×6 的小三角。 */
    private void drawArrow(GuiGraphics graphics, int y, boolean up, int mouseX, int mouseY) {
        int color = isHovering(ARROW_X, y, ARROW_W, ARROW_H, mouseX, mouseY) ? 0xFF3F2A10 : 0xFF6A6A6A;"""
assert old in src
src = src.replace(old, new)

p.write_text(src, encoding="utf-8")
print("screen arrow fix ok")
