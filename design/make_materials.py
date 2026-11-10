"""制书材料占位贴图（16×16，2026-10-28 第三版）。

四张参考图（作者提供）分别对应：
- 原版纸 paper.png —— 近白的纸片、切角、左上亮右下暗、无硬描边
- 原版线 string.png —— 斜向编织绳、亮面 + 暗面 + 断续的深灰轮廓
- 诡厄巫法道具 —— 米色叠纸/书册，深棕描边 + 中间几道横带
- 铁魔法墨水 —— 近黑的瓶身、奶油色木塞、右下一点白色反光

纸 → 草纸 / 羊皮纸 / 至邪之笺 / 寂灭之笺
墨 → 血墨 / 不洁之墨
装订 → 麻绳 / 龙筋 / 金绳
书坯 → 空书卷
"""
from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/yanscriptures/textures/item")


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


# ---------------------------------------------------------------- 纸

PAPER_SHAPE = [
    "................",
    "................",
    "...##########...",
    "..#..........#..",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    ".#............#.",
    "..#..........#..",
    "...##########...",
    "................",
    "................",
    "................",
]


def sheet(name, base, light, shade, seed, torn=False, sigil_color=None):
    img = canvas()
    px = img.load()
    for y, row in enumerate(PAPER_SHAPE):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = base

    # 把轮廓内部填满（PAPER_SHAPE 只画了边）
    for y in range(16):
        xs = [x for x in range(16) if px[x, y][3] != 0]
        if len(xs) >= 2:
            for x in range(min(xs), max(xs) + 1):
                if px[x, y][3] == 0:
                    px[x, y] = base

    # 左上提亮、右下压暗
    for y in range(2, 13):
        for x in range(2, 14):
            if px[x, y][3] == 0:
                continue
            if x + y <= 12:
                px[x, y] = light
            elif x + y >= 18:
                px[x, y] = shade

    # 纤维点
    for y in range(3, 12):
        for x in range(3, 13):
            if px[x, y][3] == 0:
                continue
            n = ((x * 73856093) ^ (y * 19349663) ^ seed) & 0xFFFF
            if n % 13 == 0:
                px[x, y] = shade
            elif n % 17 == 0:
                px[x, y] = light

    if torn:
        px[3, 4] = (0, 0, 0, 0)
        px[2, 5] = (0, 0, 0, 0)
        px[12, 10] = (0, 0, 0, 0)

    if sigil_color is not None:
        for y in range(5, 11):
            px[8, y] = sigil_color
        px[7, 6] = sigil_color
        px[9, 6] = sigil_color
        px[7, 9] = sigil_color
        px[9, 9] = sigil_color

    img.save(OUT / f"{name}.png")


# ---------------------------------------------------------------- 墨

def bottle(name, liquid_dark, liquid_mid, liquid_light, sparkle=True):
    img = canvas()
    px = img.load()
    cork = (226, 208, 176, 255)
    cork_shade = (190, 172, 142, 255)
    body_edge = (18, 16, 18, 255)

    # 木塞
    for x in range(6, 10):
        px[x, 1] = cork
        px[x, 2] = cork_shade
    px[6, 1] = cork_shade
    px[9, 1] = cork_shade

    # 瓶颈
    for y in range(3, 6):
        for x in range(6, 10):
            px[x, y] = body_edge
    px[7, 3] = liquid_mid
    px[8, 3] = liquid_dark

    # 瓶身：梯形
    for y in range(6, 14):
        half = 2 + (y - 6) // 2
        for x in range(8 - half, 8 + half):
            px[x, y] = liquid_dark
        px[8 - half, y] = body_edge
        px[7 + half, y] = body_edge

    # 液面与高光
    for x in range(6, 11):
        if px[x, 7][3]:
            px[x, 7] = liquid_mid
    px[7, 8] = liquid_mid
    px[7, 9] = liquid_light
    px[7, 10] = liquid_mid
    px[9, 12] = (250, 250, 250, 255) if sparkle else liquid_light
    px[10, 11] = (232, 236, 240, 255) if sparkle else liquid_mid

    img.save(OUT / f"{name}.png")


# ---------------------------------------------------------------- 装订

def cord(name, light, dark, edge):
    img = canvas()
    px = img.load()
    # 从左下到右上的编织绳，2 像素粗 + 断续描边
    points = [
        (3, 12), (4, 11), (5, 11), (6, 10), (7, 9), (8, 9),
        (9, 8), (10, 7), (11, 7), (12, 6), (13, 5),
    ]
    for index, (x, y) in enumerate(points):
        px[x, y] = light if index % 2 == 0 else edge
        px[x, y + 1] = light if index % 2 else dark
        px[x, y + 2] = edge if index % 2 == 0 else dark
    img.save(OUT / f"{name}.png")


# ---------------------------------------------------------------- 空书卷

def tome(name):
    """仿诡厄巫法那摞纸：米色册页 + 深棕描边 + 几道横带。"""
    img = canvas()
    px = img.load()
    edge = (92, 76, 64, 255)
    edge_dark = (66, 54, 46, 255)
    page = (226, 214, 192, 255)
    page_shade = (196, 180, 158, 255)
    band = (156, 134, 112, 255)

    for y in range(2, 14):
        for x in range(2, 14):
            px[x, y] = edge
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = page

    px[2, 2] = (0, 0, 0, 0)
    px[13, 2] = (0, 0, 0, 0)
    px[2, 13] = (0, 0, 0, 0)
    px[13, 13] = (0, 0, 0, 0)

    for x in range(3, 13):
        px[x, 5] = band
        px[x, 9] = band
    for x in range(4, 12):
        px[x, 6] = page_shade
        px[x, 10] = page_shade

    for y in range(6, 10):
        px[1, y] = edge_dark
        px[14, y] = edge_dark

    px[8, 8] = page_shade
    px[9, 8] = page_shade
    px[5, 9] = page_shade
    px[11, 4] = page_shade

    img.save(OUT / f"{name}.png")


# ---------------------------------------------------------------- 生成

sheet("fiber_paper", (246, 244, 236, 255), (252, 252, 248, 255), (226, 222, 210, 255), 11)
sheet("parchment", (232, 214, 176, 255), (244, 232, 200, 255), (202, 180, 140, 255), 77, torn=True)
sheet("corrupt_parchment", (78, 56, 92, 255), (98, 74, 112, 255), (52, 36, 62, 255), 33,
      sigil_color=(236, 214, 250, 255))
sheet("annihilated_parchment", (30, 28, 36, 255), (46, 43, 54, 255), (16, 15, 20, 255), 55,
      sigil_color=(150, 226, 212, 255))

bottle("blood_ink", (96, 12, 18, 255), (150, 24, 30, 255), (196, 48, 52, 255))
bottle("unholy_ink", (38, 62, 28, 255), (72, 112, 52, 255), (108, 156, 82, 255))

cord("hemp_rope", (208, 186, 140, 255), (160, 138, 98, 255), (96, 80, 54, 255))
cord("dragon_sinew", (238, 226, 222, 255), (196, 170, 168, 255), (120, 100, 100, 255))
cord("gold_rope", (248, 214, 120, 255), (208, 162, 62, 255), (132, 96, 30, 255))

tome("blank_scroll")

print("10 textures written")
