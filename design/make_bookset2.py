"""书籍图标：书身略窄，把余下的空间留给「书本周围的装饰」。

作者的定调（m05255）：
- 图层背板那条路放弃
- 改成在书本四周多加装饰
- 书的形状可以略作修改

实现要点：
1. 书身 18×26（x=8..25，y=3..28），右侧书页仍只占 1 像素；加上描边后整体约 20×28
2. 封面图案强制左右镜像（sym()），消灭手绘不对称
3. 每本书在左侧 / 右侧 / 底部挂 1~2 件装饰品，装饰都在书身之外
4. 每本只用 4 色：封面色、封面色暗部、图案亮色、金属/点缀色

输出：
    src/main/resources/assets/yanscriptures/textures/item/<id>.png
    design/bookset_sheet.png（8 倍）
    design/bookset_game.png（4 倍，接近游戏内观感）
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("src/main/resources/assets/yanscriptures/textures/item")

INK = (24, 21, 19)
PAPER = (234, 226, 204)

X0, X1 = 8, 25
Y0, Y1 = 3, 28


def shade(color, f):
    return tuple(max(0, min(255, int(c * f))) for c in color)


def sym(art):
    """把图案强制左右镜像，保证像素级的左右对称。"""
    width = max(len(r) for r in art)
    rows = [r.ljust(width, ".") for r in art]
    if width % 2 == 0:
        raise ValueError("art width must be odd")
    half = width // 2
    out = []
    for row in rows:
        left = row[:half]
        mid = row[half]
        out.append(left + mid + left[::-1])
    return out


BOOKS = {
    "dao_de_jing": dict(
        cover=(46, 41, 35), glyph=(226, 214, 186), accent=(122, 106, 78),
        ornament="chain", page=PAPER,
        art=sym([
            "...a...a...",
            "..ggggggg..",
            ".gg..g..gg.",
            "gg..ggg..gg",
            "gg.ggggg.gg",
            "gg..ggg..gg",
            ".gg..g..gg.",
            "..ggggggg..",
            "...a...a...",
        ]),
    ),
    "republic": dict(
        cover=(46, 54, 72), glyph=(226, 234, 244), accent=(146, 160, 182),
        ornament="column", page=PAPER,
        art=sym([
            "..aaaaaaa..",
            ".ggggggggg.",
            "ggggggggggg",
            "..g.g.g.g..",
            "..g.g.g.g..",
            "..g.g.g.g..",
            "..g.g.g.g..",
            "..g.g.g.g..",
            ".ggggggggg.",
        ]),
    ),
    "zarathustra": dict(
        cover=(54, 34, 30), glyph=(240, 198, 98), accent=(168, 74, 58),
        ornament="flame", page=PAPER,
        art=sym([
            "....a.a....",
            "..a.g.g.a..",
            "...ggggg...",
            "..ggggggg..",
            ".ggggggggg.",
            "...ggggg...",
            "..gg.gg.gg.",
            ".gggggggggg",
        ]),
    ),
    "divine_comedy": dict(
        cover=(62, 26, 28), glyph=(238, 214, 174), accent=(198, 152, 92),
        ornament="candle", page=PAPER,
        art=sym([
            "..aaaaaaa..",
            ".g.......g.",
            "g..aaaaa..g",
            "g.g.....g.g",
            "g.g.ggg.g.g",
            "g.g.....g.g",
            "g..aaaaa..g",
            ".g.......g.",
            "..aaaaaaa..",
        ]),
    ),
    "don_quixote": dict(
        cover=(152, 116, 72), glyph=(248, 242, 222), accent=(150, 34, 34),
        ornament="banner", page=(216, 202, 164),
        art=sym([
            "....g.g....",
            "..g.g.g.g..",
            "...g.g.g...",
            "....ggg....",
            "ggggggggggg",
            "....ggg....",
            "...g.g.g...",
            "..g.g.g.g..",
            "....g.g....",
        ]),
    ),
    "hundred_years": dict(
        cover=(36, 58, 44), glyph=(196, 216, 176), accent=(200, 164, 88),
        ornament="tassel", page=PAPER,
        art=sym([
            "gg.......gg",
            "ggg.....ggg",
            ".ggg...ggg.",
            "..ggg.ggg..",
            "...ggggg...",
            "..ggg.ggg..",
            ".ggg...ggg.",
            "ggg.....ggg",
            "gg.......gg",
        ]),
    ),
    "euclid": dict(
        cover=(30, 50, 60), glyph=(240, 244, 246), accent=(122, 198, 208),
        ornament="ruler", page=PAPER,
        art=sym([
            ".....g.....",
            "....ggg....",
            "...gg.gg...",
            "..gg...gg..",
            ".gg.....gg.",
            "ggggggggggg",
        ]),
    ),
    "origin": dict(
        cover=(44, 64, 40), glyph=(216, 234, 190), accent=(122, 164, 96),
        ornament="vine", page=(212, 226, 192),
        art=sym([
            ".....g.....",
            "....ggg....",
            "..g.ggg.g..",
            "..ggggggg..",
            "...gg.gg...",
            "....ggg....",
            "....g.g....",
            "...g...g...",
        ]),
    ),
    "nine_chapters": dict(
        cover=(126, 98, 58), glyph=(186, 158, 110), accent=(58, 40, 20),
        ornament="abacus", page=(216, 202, 164),
        art=sym([
            "ggggggggggg",
            "g.g.g.g.g.g",
            "aaaaaaaaaaa",
            "g.g.g.g.g.g",
            "aaaaaaaaaaa",
            "g.g.g.g.g.g",
            "aaaaaaaaaaa",
        ]),
    ),
    "book_of_peace": dict(
        cover=(104, 40, 42), glyph=(248, 244, 236), accent=(124, 176, 96),
        ornament="wing", page=PAPER,
        art=sym([
            "....ggg....",
            "...ggggg...",
            "..ggg.ggg..",
            ".ggg...ggg.",
            "gggg...gggg",
            "....g.g....",
            "...g...g...",
        ]),
    ),
}


def draw_ornament(px, kind, accent, metal):
    """在书身之外画装饰（左侧 x<X0 为主，底部 y>Y1 为辅）。"""
    gold = metal
    dark = shade(accent, 0.7)

    if kind == "chain":            # 铜环 + 短链
        for y in (1, 2, 4, 5, 7, 8):
            px[X0 - 2, y] = (*gold, 255) if y % 3 != 0 else (*shade(gold, 0.6), 255)
        for x in (X0 - 4, X0 - 3):
            px[x, 10] = (*gold, 255)
        px[X0 - 4, 11] = (*shade(gold, 0.75), 255)
    elif kind == "column":         # 立柱
        for y in range(7, 24):
            px[X0 - 3, y] = (*shade(accent, 1.15), 255)
            px[X0 - 2, y] = (*shade(accent, 0.85), 255)
        for x in (X0 - 4, X0 - 3, X0 - 2):
            px[x, 6] = (*shade(accent, 1.3), 255)
            px[x, 24] = (*shade(accent, 0.9), 255)
    elif kind == "flame":          # 火苗
        for y, xs in ((20, (X0 - 3,)), (21, (X0 - 4, X0 - 3)), (22, (X0 - 4, X0 - 3, X0 - 2)),
                      (23, (X0 - 3, X0 - 2)), (19, (X0 - 3,))):
            for x in xs:
                px[x, y] = (230, 132, 46, 255) if y < 22 else (196, 92, 36, 255)
        px[X0 - 3, 18] = (244, 196, 92, 255)
    elif kind == "candle":         # 蜡烛
        for y in range(9, 24):
            px[X0 - 3, y] = (224, 214, 186, 255)
            px[X0 - 2, y] = (198, 188, 160, 255)
        px[X0 - 3, 8] = (244, 208, 96, 255)
        px[X0 - 2, 8] = (232, 160, 60, 255)
        px[X0 - 3, 7] = (250, 232, 140, 255)
    elif kind == "banner":         # 旗
        for y in range(5, 24):
            px[X0 - 4, y] = (*shade(accent, 0.8), 255)
        for y in range(6, 13):
            span = 3 if y < 10 else 2
            for j in range(span):
                px[X0 - 3 + j, y] = (*shade(accent, 1.15), 255) if j == 0 else (*shade(accent, 0.9), 255)
    elif kind == "harp":           # 竖琴：两柱 + 弦
        for y in range(6, 23):
            px[X0 - 4, y] = (*shade(accent, 0.9), 255)
        for x in range(X0 - 5, X0 - 3):
            px[x, 5] = (*gold, 255)
            px[x, 23] = (*gold, 255)
        for y in range(8, 22, 3):
            px[X0 - 5, y] = (*shade(gold, 0.85), 255)
    elif kind == "feather":        # 羽毛
        for i in range(9):
            px[X0 - 3 - (i // 5), 7 + i] = (*shade(accent, 0.85), 255)
            if i % 2 == 0:
                px[X0 - 4 - (i // 5), 7 + i] = (*shade(accent, 1.2), 255)
    elif kind == "tassel":         # 流苏
        for x in range(X0 - 4, X0 - 1):
            px[x, 2] = (*gold, 255)
        for y in range(3, 10):
            px[X0 - 3, y] = (*shade(accent, 1.15), 255)
            px[X0 - 2, y] = (*shade(accent, 0.8), 255)
        for x in range(X0 - 4, X0 - 1):
            px[x, 10] = (*shade(accent, 0.65), 255)
    elif kind == "ruler":          # 直尺
        for y in range(5, 25):
            px[X0 - 4, y] = (*metal, 255)
            px[X0 - 3, y] = (*shade(metal, 0.8), 255)
        for y in range(6, 25, 3):
            px[X0 - 5, y] = (*shade(metal, 0.9), 255)
    elif kind == "vine":           # 藤蔓
        for y in range(4, 25):
            px[X0 - 3, y] = (96, 132, 72, 255)
        for y in (6, 11, 16, 21):
            px[X0 - 4, y] = (124, 168, 96, 255)
            px[X0 - 2, y + 1] = (124, 168, 96, 255)
            px[X0 - 4, y + 1] = (78, 108, 58, 255)
    elif kind == "abacus":         # 算珠
        for y in (4, 8, 12):
            for x in range(X0 - 5, X0 - 1):
                px[x, y] = (*gold, 255)
            for x in range(X0 - 5, X0 - 1, 2):
                px[x, y - 1] = (*shade(gold, 0.7), 255)
    elif kind == "wing":           # 书角羽翼
        for i in range(7):
            span = 3 if i < 4 else 2
            for j in range(span):
                px[X0 - 2 - j, 7 + i + j] = (
                    (232, 228, 216, 255) if (i + j) % 2 == 0 else (188, 184, 172, 255)
                )


def draw_book(spec):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    cover = spec["cover"]
    accent = spec["accent"]
    glyph = spec["glyph"]
    page = spec.get("page", PAPER)
    metal = shade(accent, 1.25)

    # 书身
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            px[x, y] = (*cover, 255)

    # 书脊三阶
    for y in range(Y0, Y1 + 1):
        px[X0, y] = (*shade(cover, 0.55), 255)
        px[X0 + 1, y] = (*shade(cover, 0.78), 255)

    # 上下边
    for x in range(X0, X1 + 1):
        px[x, Y0] = (*shade(cover, 0.7), 255)
        px[x, Y1] = (*shade(cover, 0.7), 255)

    # 书页 1 像素
    for y in range(Y0 + 1, Y1):
        px[X1, y] = (*page, 255)
        px[X1 - 1, y] = (*shade(cover, 0.9), 255)

    # 图案
    art = spec["art"]
    w, h = len(art[0]), len(art)
    ox = int((X0 + X1 + 1 - w) / 2 + 0.5)
    oy = int((Y0 + Y1 + 1 - h) / 2 + 0.5)
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            X, Y = ox + dx, oy + dy
            if X0 + 2 <= X <= X1 - 2 and Y0 + 1 <= Y <= Y1 - 1:
                px[X, Y] = (*glyph, 255) if ch == "g" else (*accent, 255)

    # 书外装饰
    draw_ornament(px, spec["ornament"], accent, metal)

    # 整圈描边
    solid = [(x, y) for y in range(32) for x in range(32) if px[x, y][3]]
    for x, y in solid:
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3] == 0:
                px[nx, ny] = (*INK, 255)
    return img


def main():
    images = []
    for book_id, spec in BOOKS.items():
        img = draw_book(spec)
        img.save(OUT / f"{book_id}.png")
        images.append(img)

    for scale, name in ((8, "design/bookset_sheet.png"), (4, "design/bookset_game.png")):
        pad = 10
        cell = 32 * scale
        sheet = Image.new("RGBA", (len(images) * (cell + pad), cell), (44, 44, 48, 255))
        for i, img in enumerate(images):
            sheet.alpha_composite(img.resize((cell, cell), Image.NEAREST), (i * (cell + pad), 0))
        sheet.save(name)
    print("wrote", len(images), "textures")


if __name__ == "__main__":
    main()
