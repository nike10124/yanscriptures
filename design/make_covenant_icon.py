"""圣约·原典 图标 v5：以参考图的版面为骨架，32x32 网格重绘，NEAREST x2 -> 64x64。

v1~v4 的教训：自创构图在 32px 里必然读不出来。参考图的版面（金描边 + 上十字 + 下纹章 + 右书页）
本身是清楚的，所以 v5 沿用它，只做三处提升：
  1. 调色板从参考图取样，保证色相统一，再各自加深/提亮一档做层次
  2. 金色描边内侧加一圈暗红内阴影 —— 让封面"陷"进去，不再像贴纸
  3. 下方的八芒雪花换成「七印」：七颗铆钉围成一环，对应天启七印

版式（32x32，取自参考图 32x32 下采样的字符图）：
  x=7 左金边 | x=8..22 封面 | x=23..24 书页 | x=25 右金边
  y=2 上金边 | y=29 下金边
  十字：竖 x=14..16 y=6..16；横 y=8..10 x=11..19
  七印：圆心 (15,22) 半径 3.2
"""

import math
from PIL import Image

REF = r"C:\Users\Y9000P\.dsh\attachments\v1\objects\66\6604148d8242562b7037a5c82ebc00ec1966e78cd1ff15ab967459dd2f162a8b"

# ---- 从参考图取样，保持色相一致 ----
ref = Image.open(REF).convert("RGBA")
rp = ref.load()

# 自动定位三类基准色，而不是凭坐标猜：
#   金   = 最「黄」的亮像素（r+g 最大且 b 明显低于 g）
#   书页 = 最亮的近中性像素
#   封面 = 出现最多的暗像素
gold_px, page_px = None, None
best_gold, best_page = -1, -1
dark_count = {}
for y in range(64):
    for x in range(64):
        c = rp[x, y]
        if c[3] < 128:
            continue
        r, g, b = c[0], c[1], c[2]
        if r > 170 and g > 120 and (g - b) > 40 and r >= g:
            score = r + g - b
            if score > best_gold:
                best_gold, gold_px = score, (r, g, b, 255)
        if r > 170 and abs(r - g) < 30 and abs(g - b) < 40:
            score = r + g + b
            if score > best_page:
                best_page, page_px = score, (r, g, b, 255)
        if r + g + b < 330 and r >= g and r >= b:
            key = (r // 8 * 8, g // 8 * 8, b // 8 * 8)
            dark_count[key] = dark_count.get(key, 0) + 1

cover_key = max(dark_count.items(), key=lambda kv: kv[1])[0]
GOLD_EDGE = gold_px or (230, 200, 90, 255)
CROSS_GOLD = GOLD_EDGE
COVER_BASE = (cover_key[0] + 4, cover_key[1] + 2, cover_key[2] + 2, 255)
PAGE_CREAM = page_px or (235, 228, 210, 255)

def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), 255)

W = H = 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = c


COVER = shade(COVER_BASE, 1.0)
COVER_LIGHT = shade(COVER_BASE, 1.28)
COVER_DARK = shade(COVER_BASE, 0.66)
COVER_SHADOW = shade(COVER_BASE, 0.42)
GOLD = GOLD_EDGE
# 参考图的金偏橙，取样拿到的偏柠檬黄；往暖色拉一档，避免"荧光笔"感
GOLD_HI = (min(255, int(GOLD[0] * 1.04)), int(GOLD[1] * 0.96), int(GOLD[2] * 0.72), 255)
GOLD_DIM = (int(GOLD[0] * 0.80), int(GOLD[1] * 0.70), int(GOLD[2] * 0.48), 255)
GOLD_DEEP = (int(GOLD[0] * 0.52), int(GOLD[1] * 0.42), int(GOLD[2] * 0.26), 255)
PAGE = PAGE_CREAM
PAGE_DIM = shade(PAGE_CREAM, 0.78)

# ---------- 1. 书页（先画，会被封面压住一部分） ----------
# v5 的书页被每 6 行一道暗线切成了梯子；v6 改成整体渐层 + 只在两处留极淡的纸缝
for y in range(3, 30):
    put(23, y, PAGE_DIM)
    put(24, y, PAGE)
    put(25, y, PAGE_DIM)
for y in (11, 20):
    put(24, y, shade(PAGE, 0.92))

# ---------- 2. 封面 ----------
for y in range(3, 29):
    for x in range(8, 23):
        put(x, y, COVER)
# 竖向渐变 + 左侧书脊压暗
for y in range(3, 29):
    t = (y - 3) / 25.0
    base = shade(COVER_BASE, 1.12 - 0.30 * t)
    put(8, y, shade(base, 0.78))
    put(9, y, shade(base, 0.88))
    put(22, y, shade(base, 1.10))
    for x in range(10, 22):
        put(x, y, base)
# 顶部受光
for x in range(8, 23):
    put(x, 3, COVER_LIGHT)

# ---------- 3. 内阴影（金边内侧一圈暗红，做出凹陷感） ----------
for x in range(8, 23):
    put(x, 4, COVER_SHADOW)
    put(x, 28, COVER_SHADOW)
for y in range(4, 29):
    put(8, y, COVER_SHADOW)
    put(22, y, COVER_SHADOW)

# ---------- 4. 金描边（外圈） ----------
for y in range(2, 30):
    put(6, y, GOLD)
    put(26, y, GOLD_DIM)
for x in range(6, 27):
    put(x, 2, GOLD_HI)
    put(x, 29, GOLD_DEEP)
# 四角加亮
for (x, y) in [(6, 2), (26, 2), (6, 29), (26, 29)]:
    put(x, y, GOLD_HI)

# ---------- 5. 十字 ----------
for y in range(6, 17):
    put(14, y, GOLD_HI)
    put(15, y, GOLD)
    put(16, y, GOLD_DIM)
for x in range(11, 20):
    put(x, 8, GOLD_HI)
    put(x, 9, GOLD)
    put(x, 10, GOLD_DIM)
put(15, 6, (255, 248, 214, 255))
put(14, 6, GOLD_HI)

# ---------- 6. 七印：七角星 ----------
# v6 的七颗铆钉+印心被读成了"笑脸"；v7 换成七道短射线构成的七角星，
# 与上方的十字形成"十字 / 七星"的对照，形状上也不会和人脸混淆。
CX, CY = 15, 22
for i in range(7):
    a = -math.pi / 2 + i * (2 * math.pi / 7)
    for step in range(1, 4):
        x = int(round(CX + math.cos(a) * step * 1.15))
        y = int(round(CY + math.sin(a) * step * 1.15))
        put(x, y, GOLD_HI if step < 3 else GOLD_DIM)
put(CX, CY, (255, 248, 214, 255))
# 射线之间点一颗暗金，让星形有"缝"，不会糊成圆斑
for i in range(7):
    a = -math.pi / 2 + (i + 0.5) * (2 * math.pi / 7)
    x = int(round(CX + math.cos(a) * 2.0))
    y = int(round(CY + math.sin(a) * 2.0))
    if px[x, y][:3] != COVER[:3]:
        continue
    put(x, y, GOLD_DEEP)

img.save("design/icon_covenant_grid.png")
img.resize((W * 8, H * 8), Image.NEAREST).save("design/icon_covenant_preview.png")
img.resize((64, 64), Image.NEAREST).save("design/icon_covenant_64.png")
print("v5 done; palette:", GOLD_EDGE, CROSS_GOLD, COVER_BASE, PAGE_CREAM)
