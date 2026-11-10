# -*- coding: utf-8 -*-
"""著书台顶部：三个 18×18 格子 + 成品格 18×18（同色同款）。
成品格外面那圈颜色由代码按成品稀有度画，贴图里不再留白框/深灰。"""
import io
import sys
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\textures\gui\book_writing_table.png"

img = Image.open(PATH).convert("RGBA")
px = img.load()
bg = px[2, 2]

DARK = (55, 55, 55, 255)
FACE = (170, 170, 170, 255)
HILIGHT = (208, 208, 208, 255)

# 只清顶部这一块，绝不碰 y=44 的材料池上边框
for y in range(12, 42):
    for x in range(58, 152):
        px[x, y] = bg


def slot(x, y):
    for xx in range(x, x + 18):
        px[xx, y - 1] = HILIGHT
    for yy in range(y, y + 18):
        px[x - 1, yy] = HILIGHT
    for yy in range(y, y + 18):
        for xx in range(x, x + 18):
            edge = xx in (x, x + 17) or yy in (y, y + 17)
            px[xx, yy] = DARK if edge else FACE


for x in (62, 84, 106):
    slot(x, 20)

# 成品格：与基础格同款同尺寸（18×18，y=20..37），稀有度彩框在代码里画
slot(127, 20)

img.save(PATH)
print("written", PATH)
