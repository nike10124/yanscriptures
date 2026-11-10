"""试点：重建书类图标的“分层画法”，先只做两本给作者看。

分层顺序（正是作者要的“图层”关系）：
    1) 背板层   —— 一整块浅色图形，形状自定，会从书四周露出来
    2) 书本层   —— 书身盖在背板上，遮住背板中间那块
    3) 图案层   —— 封面上的符号
    4) 收边层   —— 整圈 1 像素暗描边

配色限制：每本书只用 4~5 个颜色（深底 / 中间调 / 亮面 / 金 / 纸），
全部取原版物品的低饱和区间，避免之前那种灰绿、灰蓝的“AI 调和色”。

输出：design/pilot/*.png  与 design/pilot_sheet.png
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("design/pilot")

INK = (33, 29, 26)
PAPER = (232, 220, 192)
GOLD = (222, 186, 96)
GOLD_DARK = (150, 116, 52)
WHITE = (244, 240, 232)

# 书身：20×26，加外侧 1 像素描边后正好 22×28，与《翠玉录》同框
BX0, BX1 = 7, 26
BY0, BY1 = 3, 28


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def shadow(img, color=(46, 38, 30)):
    """书身周围一圈半透明暗色，让压在背板上的书本有层次。"""
    px = img.load()
    solid = [(x, y) for y in range(32) for x in range(32) if px[x, y][3]]
    for x, y in solid:
        for nx, ny in ((x + 1, y + 1), (x + 2, y + 1), (x + 1, y + 2)):
            if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3]:
                c = px[nx, ny]
                px[nx, ny] = (
                    int(c[0] * 0.6 + color[0] * 0.4),
                    int(c[1] * 0.6 + color[1] * 0.4),
                    int(c[2] * 0.6 + color[2] * 0.4),
                    255,
                )


def outline(img, color=(20, 18, 16)):
    px = img.load()
    solid = [(x, y) for y in range(32) for x in range(32) if px[x, y][3]]
    for x, y in solid:
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3] == 0:
                px[nx, ny] = (*color, 255)


def draw_taiji(d, cx, cy, r, light, dark):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=light)
    d.pieslice([cx - r, cy - r, cx + r, cy + r], 90, 270, fill=dark)
    d.ellipse([cx - r / 2, cy - r, cx + r / 2, cy], fill=light)
    d.ellipse([cx - r / 2, cy, cx + r / 2, cy + r], fill=dark)
    d.ellipse([cx - 1, cy - r * 0.5 - 1, cx + 1, cy - r * 0.5 + 1], fill=dark)
    d.ellipse([cx - 1, cy + r * 0.5 - 1, cx + 1, cy + r * 0.5 + 1], fill=light)


def book_of_dao():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 1) 背板：浅色圆盘（会从书周围露出一圈）
    d.ellipse([1, 1, 31, 31], fill=(206, 190, 150, 255), outline=GOLD_DARK)
    d.ellipse([3, 3, 29, 29], outline=(176, 158, 118, 255))

    # 2) 书本
    d.rectangle([BX0, BY0, BX1, BY1], fill=(38, 33, 29, 255))
    d.rectangle([BX0, BY0, BX0 + 2, BY1], fill=(28, 24, 21, 255))
    d.rectangle([BX1 - 1, BY0, BX1 - 1, BY1], fill=(*PAPER, 255))
    d.rectangle([BX0 + 3, BY0, BX0 + 3, BY1], fill=(46, 40, 35, 255))

    # 3) 封面图案：太极
    draw_taiji(d, 17, 16, 6, (*PAPER, 255), (96, 82, 62, 255))

    outline(img)
    return img


def book_of_euclid():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 1) 背板：浅色三角（缩小，只从书边露出）
    d.polygon([(16, 2), (30, 29), (2, 29)], fill=(176, 202, 200, 255), outline=(92, 128, 128, 255))

    # 2) 书本
    d.rectangle([BX0, BY0, BX1, BY1], fill=(34, 58, 62, 255))
    d.rectangle([BX0, BY0, BX0 + 2, BY1], fill=(24, 42, 46, 255))
    d.rectangle([BX1 - 1, BY0, BX1 - 1, BY1], fill=(*PAPER, 255))
    d.rectangle([BX0 + 3, BY0, BX0 + 3, BY1], fill=(44, 72, 76, 255))

    # 3) 封面图案：圆规
    d.polygon([(17, 9), (15, 10), (11, 23), (13, 23)], fill=(*WHITE, 255))
    d.polygon([(17, 9), (19, 10), (23, 23), (21, 23)], fill=(*WHITE, 255))
    d.ellipse([15, 7, 19, 11], fill=(*GOLD, 255))
    d.line([(12, 20), (22, 20)], fill=(*WHITE, 255))

    outline(img)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    items = [("dao_de_jing", book_of_dao()), ("euclid", book_of_euclid())]
    for name, img in items:
        img.save(OUT / f"{name}.png")

    cells = []
    for name, img in items:
        for scale in (8, 4):
            cells.append(img.resize((32 * scale, 32 * scale), Image.NEAREST))
    width = sum(c.width for c in cells) + 12 * (len(cells) - 1)
    sheet = Image.new("RGBA", (width, 256), (42, 42, 46, 255))
    x = 0
    for c in cells:
        sheet.alpha_composite(c, (x, 0))
        x += c.width + 12
    sheet.save("design/pilot_sheet.png")
    print("wrote pilot")


if __name__ == "__main__":
    main()
