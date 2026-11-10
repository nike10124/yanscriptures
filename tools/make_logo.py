# -*- coding: utf-8 -*-
"""生成模组 logo.png（128x128，第三版）：合起的书 + 双金框 + 中央纹章 + 右下角一支短羽笔。"""
import io
import sys
from PIL import Image, ImageDraw

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

S = 128
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

GOLD = (216, 166, 74, 255)
GOLD_D = (140, 100, 40, 255)
PAGE = (240, 232, 208, 255)
PAGE_D = (200, 186, 156, 255)
COVER = (26, 33, 50, 255)
COVER_D = (16, 21, 33, 255)
RED = (132, 34, 34, 255)

for y in range(S):
    t = y / (S - 1)
    d.line([(0, y), (S, y)],
           fill=(int(11 + t * 9), int(15 + t * 11), int(24 + t * 15), 255))

# 纸页：右边与下边各露 4 像素
d.rectangle([98, 22, 102, 102], fill=PAGE)
d.rectangle([36, 100, 102, 104], fill=PAGE)
for y in range(24, 102, 3):
    d.line([(100, y), (100, y)], fill=PAGE_D)
for x in range(38, 100, 3):
    d.line([(x, 102), (x, 102)], fill=PAGE_D)

# 书封
d.rectangle([26, 20, 98, 100], fill=COVER)
d.rectangle([26, 20, 98, 100], outline=COVER_D)
d.rectangle([26, 20, 34, 100], fill=COVER_D)
d.line([(34, 20), (34, 100)], fill=GOLD_D)

# 烫金双框
d.rectangle([40, 26, 92, 94], outline=GOLD_D)
d.rectangle([42, 28, 90, 92], outline=GOLD)

# 中央纹章
cx, cy = 66, 57
d.ellipse([cx - 16, cy - 16, cx + 16, cy + 16], outline=GOLD, width=2)
d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=GOLD_D, width=1)
d.line([(cx, cy - 19), (cx, cy + 19)], fill=GOLD, width=1)
d.line([(cx - 19, cy), (cx + 19, cy)], fill=GOLD, width=1)
d.polygon([(cx, cy - 8), (cx + 8, cy), (cx, cy + 8), (cx - 8, cy)], fill=RED)
d.polygon([(cx, cy - 4), (cx + 4, cy), (cx, cy + 4), (cx - 4, cy)], fill=GOLD)

# 右下角一支短羽笔（不压纹章）
tx, ty = 78, 76          # 笔尖（羽毛顶端）
bx, by = 92, 92          # 笔杆末端
n = 18
for i in range(n + 1):
    t = i / n
    x = tx + (bx - tx) * t
    y = ty + (by - ty) * t
    w = 0.0 if i == 0 else min(2.0, 0.6 + i * 0.12)
    d.ellipse([x - w, y - w, x + w, y + w], fill=PAGE)
# 羽轴
d.line([(tx, ty), (bx, by)], fill=PAGE_D)
# 笔尖/笔头
d.polygon([(bx - 4, by - 4), (bx + 3, by - 5), (bx - 5, by + 3)], fill=GOLD)

# 四角星
def star(x, y):
    d.point((x, y), fill=GOLD)
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        d.point((x + dx, y + dy), fill=GOLD_D)

for p in ((13, 13), (114, 13), (13, 114), (114, 114)):
    star(*p)

img.save(r"D:\MCModDev\forge-mdk\src\main\resources\logo.png")
img.resize((384, 384), Image.NEAREST).save(r"D:\MCModDev\forge-mdk\backup\_logo_3x.png")
print("ok")
