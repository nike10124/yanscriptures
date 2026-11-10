# -*- coding: utf-8 -*-
"""生成三个候选版本的顶部条，放大后拼成对照图，让作者挑。"""
import io
import sys
from PIL import Image, ImageDraw

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

W, H = 176, 202
LEFT, TOP, RIGHT, BOTTOM = 40, 10, 160, 44


def base():
    img = Image.new("RGBA", (W, H), (198, 198, 198, 255))
    px = img.load()
    for y in range(TOP, BOTTOM):
        for x in range(LEFT, RIGHT):
            px[x, y] = (198, 198, 198, 255)
    return img


def box(px, x, y, size, outer, inner, face, inner_size=1):
    for yy in range(y, y + size):
        for xx in range(x, x + size):
            px[xx, yy] = outer
    for yy in range(y + inner_size, y + size - inner_size):
        for xx in range(x + inner_size, x + size - inner_size):
            px[xx, yy] = inner if inner_size else face
    for yy in range(y + inner_size * 2, y + size - inner_size * 2):
        for xx in range(x + inner_size * 2, x + size - inner_size * 2):
            px[xx, yy] = face


DARK = (55, 55, 55, 255)
MID = (85, 85, 85, 255)
FACE = (139, 139, 139, 255)
LIGHT = (170, 170, 170, 255)
WHITE = (255, 255, 255, 255)

variants = {}

# A：四边 1px 暗框 + 内高光（当前版本）
img = base()
px = img.load()
for x in (62, 84, 106):
    for yy in range(20, 38):
        for xx in range(x, x + 18):
            px[xx, yy] = DARK if (xx in (x, x + 17) or yy in (20, 37)) else FACE
    for xx in range(x + 1, x + 17):
        px[xx, 21] = LIGHT
    for yy in range(21, 37):
        px[x + 1, yy] = LIGHT
for yy in range(18, 38):
    for xx in range(126, 146):
        px[xx, yy] = WHITE
for yy in range(19, 37):
    for xx in range(127, 145):
        px[xx, yy] = DARK
for yy in range(20, 36):
    for xx in range(128, 144):
        px[xx, yy] = FACE
variants["A"] = img

# B：外圈白 1px + 内圈暗 1px（物品栏那种立体感）
img = base()
px = img.load()
for x in (62, 84, 106):
    for yy in range(20, 38):
        for xx in range(x, x + 18):
            edge = xx in (x, x + 17) or yy in (20, 37)
            px[xx, yy] = WHITE if edge else (DARK if (xx in (x + 1, x + 16) or yy in (21, 36)) else FACE)
for yy in range(18, 38):
    for xx in range(126, 146):
        px[xx, yy] = WHITE
for yy in range(19, 37):
    for xx in range(127, 145):
        px[xx, yy] = DARK
for yy in range(20, 36):
    for xx in range(128, 144):
        px[xx, yy] = FACE
variants["B"] = img

# C：暗框 + 内一圈亮灰，成品框外暗内白
img = base()
px = img.load()
for x in (62, 84, 106):
    for yy in range(20, 38):
        for xx in range(x, x + 18):
            edge = xx in (x, x + 17) or yy in (20, 37)
            px[xx, yy] = MID if edge else (LIGHT if (xx in (x + 1, x + 16) or yy in (21, 36)) else FACE)
for yy in range(18, 38):
    for xx in range(126, 146):
        px[xx, yy] = DARK
for yy in range(19, 37):
    for xx in range(127, 145):
        px[xx, yy] = WHITE
for yy in range(20, 36):
    for xx in range(128, 144):
        px[xx, yy] = FACE
variants["C"] = img

SCALE = 6
PAD = 26
out = Image.new("RGBA", ((RIGHT - LEFT) * SCALE + 60, (PAD + (BOTTOM - TOP) * SCALE + 10) * 3),
                (240, 240, 240, 255))
draw = ImageDraw.Draw(out)
for i, (name, img) in enumerate(variants.items()):
    y = i * (PAD + (BOTTOM - TOP) * SCALE + 10)
    draw.text((8, y + 4), name, fill=(0, 0, 0, 255))
    crop = img.crop((LEFT, TOP, RIGHT, BOTTOM)).resize(
        ((RIGHT - LEFT) * SCALE, (BOTTOM - TOP) * SCALE), Image.NEAREST)
    out.paste(crop, (40, y + PAD))

out.save(r"D:\MCModDev\forge-mdk\backup\_gui_variants.png")
print("ok")
