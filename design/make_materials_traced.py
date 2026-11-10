"""制书材料贴图：逐像素照参考图重画（16×16）。

参考图（作者提供）按整数倍缩小回 16×16 后，用「亮度分层 + 换调色板」的方式重上色：
形状、明暗节奏、轮廓全部照抄参考图，只换颜色与个别细节（符印）。
这样做出来的东西才和参考图是同一个画风。

- 纸     ← 原版纸
- 装订   ← 原版线
- 墨     ← 铁魔法墨水
- 空书卷 ← 诡厄巫法的道具
"""
from pathlib import Path

from PIL import Image

REF_DIR = Path("design/ref/small")
OUT = Path("src/main/resources/assets/yanscriptures/textures/item")

REFS = {
    "paper": (REF_DIR / "paper_16.png", 1),
    "string": (REF_DIR / "string_16.png", 1),
    "tome": (REF_DIR / "tome_16.png", 1),
    "ink": (REF_DIR / "ink_16.png", 1),
}


def load(name):
    path, _ = REFS[name]
    return Image.open(path).convert("RGBA")


def remap(img, bands, sigil=None):
    """按亮度分档换色。bands = [(上限亮度, 颜色), ...]，从小到大。"""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a < 128:
                continue
            lum = (r * 299 + g * 587 + b * 114) // 1000
            color = bands[-1][1]
            for limit, col in bands:
                if lum <= limit:
                    color = col
                    break
            dst[x, y] = color
    if sigil is not None:
        for x, y, col in sigil:
            if dst[x, y][3]:
                dst[x, y] = col
    return out


# 各物品的调色板：从最暗到最亮
PAPER = [
    (150, (198, 192, 176, 255)),
    (205, (222, 216, 200, 255)),
    (238, (238, 235, 226, 255)),
    (999, (250, 250, 246, 255)),
]
PARCHMENT = [
    (150, (176, 148, 104, 255)),
    (205, (206, 176, 128, 255)),
    (238, (228, 204, 158, 255)),
    (999, (242, 224, 186, 255)),
]
CORRUPT = [
    (150, (44, 30, 52, 255)),
    (205, (64, 44, 76, 255)),
    (238, (86, 62, 100, 255)),
    (999, (108, 82, 122, 255)),
]
ANNIHILATED = [
    (150, (14, 13, 18, 255)),
    (205, (26, 24, 32, 255)),
    (238, (40, 38, 48, 255)),
    (999, (58, 56, 68, 255)),
]

STRING_HEMP = [
    (110, (120, 98, 62, 255)),
    (190, (176, 150, 100, 255)),
    (999, (216, 196, 148, 255)),
]
STRING_SINEW = [
    (110, (150, 126, 124, 255)),
    (190, (206, 184, 182, 255)),
    (999, (240, 230, 228, 255)),
]
STRING_GOLD = [
    (110, (140, 100, 26, 255)),
    (190, (214, 168, 62, 255)),
    (999, (250, 224, 138, 255)),
]

INK_BLOOD = [
    (60, (44, 6, 8, 255)),
    (110, (86, 12, 16, 255)),
    (190, (140, 26, 30, 255)),
    (235, (206, 186, 160, 255)),
    (999, (238, 230, 212, 255)),
]
INK_UNHOLY = [
    (60, (18, 30, 12, 255)),
    (110, (34, 58, 24, 255)),
    (190, (64, 104, 44, 255)),
    (235, (206, 186, 160, 255)),
    (999, (238, 230, 212, 255)),
]

TOME = [
    (90, (66, 54, 46, 255)),
    (130, (92, 76, 64, 255)),
    (175, (156, 134, 112, 255)),
    (215, (196, 180, 158, 255)),
    (999, (226, 214, 192, 255)),
]

SIGIL_LIGHT = (236, 214, 250, 255)
SIGIL_VOID = (150, 226, 212, 255)


def sigil_points():
    """纸中央一个简单符印，只落在不透明像素上。"""
    points = [(8, y, SIGIL_LIGHT) for y in range(5, 11)]
    points += [(7, 6, SIGIL_LIGHT), (9, 6, SIGIL_LIGHT), (7, 9, SIGIL_LIGHT), (9, 9, SIGIL_LIGHT)]
    return points


def main():
    paper = load("paper")
    string = load("string")
    tome = load("tome")
    ink = load("ink")

    remap(paper, PAPER).save(OUT / "fiber_paper.png")
    remap(paper, PARCHMENT).save(OUT / "parchment.png")
    remap(paper, CORRUPT, sigil=sigil_points()).save(OUT / "corrupt_parchment.png")
    remap(paper, ANNIHILATED, sigil=[(x, y, SIGIL_VOID) for x, y, _ in sigil_points()]).save(
        OUT / "annihilated_parchment.png")

    remap(ink, INK_BLOOD).save(OUT / "blood_ink.png")
    remap(ink, INK_UNHOLY).save(OUT / "unholy_ink.png")

    remap(string, STRING_HEMP).save(OUT / "hemp_rope.png")
    remap(string, STRING_SINEW).save(OUT / "dragon_sinew.png")
    remap(string, STRING_GOLD).save(OUT / "gold_rope.png")

    remap(tome, TOME).save(OUT / "blank_scroll.png")

    print("10 textures written from references")


if __name__ == "__main__":
    main()
