"""
书籍类物品贴图（32×32）——一套共用骨架、分装帧、带外延装饰的版本。

作者定死的两条：
- 书身长宽比与《翠玉录》一致：x=6..27（22 宽）、y=2..29（28 高）
- 右侧书页那块白色只有 **1 像素**（x=26）

这一版新增：
1. 图案带浮雕：符号周围压一圈暗描边，右下打一道高光，细节与对比度都比之前强
2. 书身外侧有外延装饰（书签带、搭扣、流苏、藤蔓、浮尘、烧灼缺角、磨损缺口），
   让每本书不再是一个方框

运行：
    <python> design/make_bookset.py
输出：
    src/main/resources/assets/yanscriptures/textures/item/<id>.png
    design/bookset_sheet.png（8 倍放大对照图）
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("src/main/resources/assets/yanscriptures/textures/item")
SHEET = Path("design/bookset_sheet.png")

CLEAR = (0, 0, 0, 0)
INK = (16, 14, 14, 255)
PAGE = (232, 224, 200, 255)
PAGE_YELLOW = (214, 200, 160, 255)

X0, X1 = 6, 27
Y0, Y1 = 2, 29
FACE_X0, FACE_X1 = 9, 24
FACE_Y0, FACE_Y1 = 5, 26


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color)


BOOKS = {
    "dao_de_jing": dict(face_field="trigram", backdrop="taiji", 
        cover=(46, 42, 38), binding="cloth", glyph=(198, 186, 162),
        accent=(96, 88, 74), page=PAGE, ornaments=("ring",),
        art=[
            "...a.....a...",
            "..ggggggggg..",
            ".gg..ggg..gg.",
            "gg..gg.gg..gg",
            "gg.gg...gg.gg",
            "gg.gg...gg.gg",
            "gg..gg.gg..gg",
            ".gg..ggg..gg.",
            "..ggggggggg..",
            "...a.....a...",
        ],
    ),
    "republic": dict(face_field="orbits", backdrop="temple", 
        cover=(44, 52, 70), binding="leather", glyph=(232, 238, 248),
        accent=(150, 162, 182), page=PAGE, ornaments=("bookmark", "sprig"),
        art=[
            "..aaaaaaaaa..",
            ".ggggggggggg.",
            "ggggggggggggg",
            ".ggggggggggg.",
            "..gg.gg.gg...",
            "..gg.gg.gg...",
            "..gg.gg.gg...",
            "..gg.gg.gg...",
            ".gggggggggg..",
        ],
    ),
    "zarathustra": dict(face_field="rays", backdrop="sun", 
        cover=(52, 34, 30), binding="leather", glyph=(240, 198, 98),
        accent=(150, 62, 52), page=PAGE, ornaments=("burn", "rune"),
        art=[
            "..a..a..a..a..",
            "...g.g.g.g.g..",
            "....aaaaaaa...",
            "...aaaaaaaaa..",
            ".....aaaaa....",
            "....ggggggg...",
            "...gg.ggg.gg..",
            "..ggggggggggg.",
            ".ggggggggggggg",
        ],
    ),
    "divine_comedy": dict(face_field="orbits_cross", backdrop="rings", 
        cover=(62, 24, 26), binding="gilded", glyph=(238, 212, 170),
        accent=(200, 152, 92), page=PAGE, ornaments=("clasp",),
        art=[
            "....gggggg....",
            "..gg......gg..",
            ".g..aaaaa...g.",
            "g..aa...aa...g",
            "g.aa.ggg.aa..g",
            "g.aa.ggg.aa..g",
            "g..aa...aa...g",
            ".g..aaaaa...g.",
            "..gg......gg..",
            "....gggggg....",
        ],
    ),
    "don_quixote": dict(face_field="windmill_big", backdrop="windmill", 
        cover=(156, 118, 72), binding="cloth", glyph=(250, 242, 220),
        accent=(152, 30, 30), page=PAGE_YELLOW, ornaments=("worn", "sprig"),
        art=[
            "......g......",
            "..g...g...g..",
            "...g..g..g...",
            "....g.g.g....",
            ".gggggggggggg.",
            "....g.g.g....",
            "...g..g..g...",
            "..g...g...g..",
            "......g......",
            "......g......",
        ],
    ),
    "hundred_years": dict(face_field="hourglass", backdrop="butterfly", 
        cover=(34, 56, 42), binding="leather", glyph=(112, 144, 112),
        accent=(200, 164, 88), page=PAGE, ornaments=("bookmark", "rune"),
        art=[
            "gg..........gg",
            "ggg...a...ggg.",
            ".ggg.ggg.ggg..",
            "..ggggggggg...",
            "...ggggggg....",
            "..ggggggggg...",
            ".ggg.ggg.ggg..",
            "ggg...a...ggg.",
            "gg..........gg",
        ],
    ),
    "euclid": dict(face_field="triangle_field", backdrop="triangle", 
        cover=(28, 48, 58), binding="stitched", glyph=(240, 244, 246),
        accent=(120, 200, 210), page=PAGE, ornaments=("ring",),
        art=[
            "....ggggg....",
            "..gg.....gg..",
            ".g...aaa...g.",
            ".g..aaaaa..g.",
            "g..aaaaaaa..g",
            "g.aaaaaaaaa.g",
            "g.aaaaaaaaa.g",
            ".g..aaaaa..g.",
            ".g...aaa...g.",
            "..gg.....gg..",
            "....ggggg....",
        ],
    ),
    "origin": dict(face_field="branch", backdrop="tree", 
        cover=(44, 66, 40), binding="cloth", glyph=(214, 234, 188),
        accent=(122, 164, 96), page=(210, 226, 190, 255),
        ornaments=("vine",),
        art=[
            "......g......",
            ".....ggg.....",
            "..g..ggg..g..",
            "..gg.ggg.gg..",
            "...ggggggg...",
            "....gg.gg....",
            ".....ggg.....",
            ".....g.g.....",
            "....gg.gg....",
            ".....g.g.....",
        ],
    ),
    "nine_chapters": dict(face_field="grid_field", backdrop="grid", 
        cover=(124, 96, 56), binding="stitched", glyph=(180, 150, 100),
        accent=(54, 38, 18), page=PAGE_YELLOW, ornaments=("tassel",),
        art=[
            "ggggggggggggg",
            "g.g.g.g.g.g.g",
            "aaaaaaaaaaaaa",
            "g.g.g.g.g.g.g",
            "aaaaaaaaaaaaa",
            "g.g.g.g.g.g.g",
            "aaaaaaaaaaaaa",
            "g.g.g.g.g.g.g",
        ],
    ),
    "book_of_peace": dict(face_field="wings_field", backdrop="wings", 
        cover=(104, 38, 40), binding="cloth", glyph=(248, 244, 236),
        accent=(122, 176, 96), page=PAGE, ornaments=("tassel", "rune"),
        art=[
            "..........gg...",
            "........gggg...",
            "......ggggggg..",
            "....gggggggggg.",
            "..gggggggggggg.",
            "..ggggg..gggg..",
            "...ggg....gg...",
            "....g......g...",
            "...........aa..",
            ".........aaaaa.",
        ],
    ),
}






MOTIFS = {
    "taiji": [
        ".ppp.",
        "p...p",
        "p.p.p",
        "p...p",
        ".ppp.",
    ],
    "temple": [
        ".ppp.",
        "ppppp",
        ".p.p.",
        ".p.p.",
        ".p.p.",
    ],
    "sun": [
        "..p..",
        "p.p.p",
        ".ppp.",
        "p.p.p",
        "..p..",
    ],
    "rings": [
        ".ppp.",
        "p...p",
        "p...p",
        "p...p",
        ".ppp.",
    ],
    "windmill": [
        "p...p",
        ".p.p.",
        "..p..",
        ".p.p.",
        "p...p",
    ],
    "butterfly": [
        "pp.pp",
        "pp.pp",
        "..p..",
        "pp.pp",
        "pp.pp",
    ],
    "triangle": [
        "..p..",
        ".p.p.",
        "p...p",
        "ppppp",
        ".....",
    ],
    "tree": [
        "..p..",
        "p.p.p",
        ".ppp.",
        "..p..",
        "..p..",
    ],
    "grid": [
        ".....",
        "ppppp",
        ".ppp.",
        "ppppp",
        ".....",
    ],
    "wings": [
        "..p..",
        ".ppp.",
        "ppppp",
        ".ppp.",
        "..p..",
    ],
}


def draw_backdrop(img, spec):
    """书后面的浅色标志物：贴在左侧空白，书盖不住。"""
    accent = spec.get("accent", spec["cover"])
    pale = tuple(int(c * 0.45 + 200 * 0.55) for c in accent)
    motif = MOTIFS.get(spec.get("backdrop", ""), None)
    if not motif:
        return img

    px = img.load()
    w = max(len(r) for r in motif)
    ox, oy = 0, 11
    for dy, row in enumerate(motif):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            x, y = ox + dx, oy + dy
            if 0 <= x < 32 and 0 <= y < 32:
                px[x, y] = (*pale, 255)
    # 底下一行阴影，让浅色块不至于糊在背景上
    for dx in range(w):
        x, y = ox + dx, oy + len(motif)
        if 0 <= x < 32 and 0 <= y < 32:
            px[x, y] = (*tuple(int(c * 0.55) for c in pale), 255)
    return img


FIELDS = {
    "dao_de_jing": "trigram",
    "republic": "orbits",
    "zarathustra": "rays",
    "divine_comedy": "orbits_cross",
    "don_quixote": "windmill_big",
    "hundred_years": "hourglass",
    "euclid": "triangle_field",
    "origin": "branch",
    "nine_chapters": "grid_field",
    "book_of_peace": "wings_field",
}



def draw_cover_field(img, kind, cover, accent):
    """封面大底纹：一整块浅色形状当"牌面"，比主图案大，让封面不空。"""
    base = tuple(int(c * 0.55 + 130 * 0.45) for c in accent)
    edge = tuple(max(0, int(c * 0.72)) for c in base)
    layer = Image.new("RGBA", (32, 32), CLEAR)
    d = ImageDraw.Draw(layer)
    cx, cy = (FACE_X0 + FACE_X1) / 2, (FACE_Y0 + FACE_Y1) / 2
    fill = (*base, 255)
    line = (*edge, 255)

    if kind == "trigram":
        d.ellipse([cx - 8, cy - 9, cx + 8, cy + 9], fill=fill, outline=line)
    elif kind == "orbits":
        d.polygon([(cx, FACE_Y0), (FACE_X1, cy), (cx, FACE_Y1), (FACE_X0, cy)], fill=fill, outline=line)
    elif kind == "rays":
        d.polygon([(cx, FACE_Y0), (cx + 8, cy + 4), (cx, FACE_Y1), (cx - 8, cy + 4)], fill=fill, outline=line)
    elif kind == "orbits_cross":
        d.ellipse([cx - 9, cy - 10, cx + 9, cy + 10], fill=fill, outline=line)
        d.ellipse([cx - 5, cy - 6, cx + 5, cy + 6], fill=(*cover, 255), outline=line)
    elif kind == "windmill_big":
        d.polygon([(cx, FACE_Y0 + 1), (cx + 7, cy), (cx, FACE_Y1 - 1), (cx - 7, cy)], fill=fill, outline=line)
    elif kind == "hourglass":
        d.polygon([(cx - 7, FACE_Y0 + 1), (cx + 7, FACE_Y0 + 1), (cx, cy), (cx + 7, FACE_Y1 - 1), (cx - 7, FACE_Y1 - 1), (cx, cy)], fill=fill, outline=line)
    elif kind == "triangle_field":
        d.polygon([(cx, FACE_Y0 + 1), (FACE_X1 - 1, FACE_Y1 - 1), (FACE_X0 + 1, FACE_Y1 - 1)], fill=fill, outline=line)
    elif kind == "branch":
        d.polygon([(cx - 4, FACE_Y1 - 1), (cx, FACE_Y0 + 1), (cx + 4, FACE_Y1 - 1)], fill=fill, outline=line)
    elif kind == "grid_field":
        d.rectangle([FACE_X0 + 2, FACE_Y0 + 3, FACE_X1 - 2, FACE_Y1 - 3], fill=fill, outline=line)
    elif kind == "wings_field":
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=fill, outline=line)
    return layer

def draw_book(spec):
    img = Image.new("RGBA", (32, 32), CLEAR)
    px = img.load()
    cover = spec["cover"]
    page = spec.get("page", PAGE)
    accent = spec.get("accent", shade(cover, 1.6))
    binding = spec["binding"]

    # 书身（带一点确定性噪点，避免整块死平）
    seed = sum(ord(c) for c in str(spec.get("glyph")) )
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            n = ((x * 73856093) ^ (y * 19349663) ^ seed) % 7
            delta = (-6, -3, 0, 0, 0, 3, 6)[n]
            px[x, y] = tuple(max(0, min(255, c + delta)) for c in cover)

    # 书脊
    for y in range(Y0, Y1 + 1):
        px[X0, y] = INK
        px[X0 + 1, y] = shade(cover, 0.5)
        px[X0 + 2, y] = shade(cover, 0.72)

    # 右侧书页 1 像素
    for y in range(Y0, Y1 + 1):
        px[X1, y] = INK
        px[X1 - 1, y] = page

    for x in range(X0 + 1, X1):
        px[x, Y0] = INK
        px[x, Y1] = INK

    for x in range(X0 + 3, X1 - 1):
        px[x, Y0 + 1] = shade(cover, 0.78)
        px[x, Y1 - 1] = shade(cover, 0.78)

    # 装订
    if binding == "leather":
        metal = (176, 132, 72)
        for cx, cy, dx, dy in (
            (FACE_X0, FACE_Y0, 1, 1),
            (FACE_X1, FACE_Y0, -1, 1),
            (FACE_X0, FACE_Y1, 1, -1),
            (FACE_X1, FACE_Y1, -1, -1),
        ):
            for i in range(4):
                for t in range(2):
                    for X, Y in ((cx + dx * i, cy + dy * t), (cx + dx * t, cy + dy * i)):
                        if 0 <= X < 32 and 0 <= Y < 32:
                            px[X, Y] = (*metal, 255) if t == 0 else (*shade(metal, 0.72), 255)
    elif binding == "cloth":
        edge = shade(cover, 0.76)
        for x in range(FACE_X0 - 1, FACE_X1 + 2):
            if 0 <= x < 32:
                px[x, FACE_Y0 - 1] = edge
                px[x, FACE_Y1 + 1] = edge
        for y in range(FACE_Y0 - 1, FACE_Y1 + 2):
            if 0 <= y < 32:
                px[FACE_X0 - 1, y] = edge
                px[FACE_X1 + 1, y] = edge
    elif binding == "gilded":
        gold = (208, 174, 98)
        for x in range(X0 + 3, X1 - 1):
            px[x, Y0 + 2] = gold
            px[x, Y1 - 2] = gold
        for y in range(Y0 + 2, Y1 - 1):
            px[X0 + 4, y] = gold
            px[X1 - 3, y] = gold
    elif binding == "stitched":
        thread = (216, 202, 162)
        for hy in (9, 16, 23):
            px[X0, hy] = thread
            px[X0 + 1, hy] = shade(cover, 0.4)
            px[X0 + 2, hy] = thread

    # ---------------- 封面大底纹（比主图案更大更浅，当背景用）
    field_kind = spec.get("face_field")
    if field_kind:
        layer = draw_cover_field(img, field_kind, cover, accent)
        lpx = layer.load()
        for y in range(FACE_Y0, FACE_Y1 + 1):
            for x in range(FACE_X0, FACE_X1 + 1):
                if lpx[x, y][3]:
                    src = lpx[x, y]
                    cur = px[x, y]
                    if cur[3]:
                        mix = 0.55
                        px[x, y] = (
                            int(cur[0] * (1 - mix) + src[0] * mix),
                            int(cur[1] * (1 - mix) + src[1] * mix),
                            int(cur[2] * (1 - mix) + src[2] * mix),
                            255,
                        )
                    else:
                        px[x, y] = src

    # ---------------- 图案（带浮雕描边与高光）
    raw = spec["art"]
    width = max(len(r) for r in raw)
    art = [row.ljust(width, ".") for row in raw]
    w, h = len(art[0]), len(art)
    ox = int((FACE_X0 + FACE_X1 + 1 - w) / 2 + 0.5)
    oy = int((FACE_Y0 + FACE_Y1 + 1 - h) / 2 + 0.5)

    glyph = spec["glyph"]
    pts = {}
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            pts[(ox + dx, oy + dy)] = accent if ch == "a" else glyph

    # 暗描边：让图案从封面上"浮"起来
    for (x, y), color in list(pts.items()):
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if (nx, ny) in pts:
                continue
            if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3]:
                px[nx, ny] = (*shade(color, 0.5), 255)

    for (x, y), color in pts.items():
        if 0 <= x < 32 and 0 <= y < 32:
            px[x, y] = (*color, 255)

    # 右下角高光
    for (x, y), color in pts.items():
        if (x + 1, y + 1) in pts or (x, y + 1) in pts:
            continue
        if 0 <= x + 1 < 32 and 0 <= y + 1 < 32 and px[x + 1, y + 1][3]:
            px[x + 1, y + 1] = (*shade(color, 1.35), 255)

    # ---------------- 外延装饰
    orn = spec.get("ornaments", ())
    if "bookmark" in orn:
        for y in range(Y0 - 1, Y0 + 5):
            px[X1 - 2, y] = (*accent, 255)
            px[X1 - 3, y] = (*shade(accent, 0.75), 255)
        px[X1 - 2, Y0 + 5] = (*shade(accent, 0.6), 255)

    if "tassel" in orn:
        px[X1 - 3, Y1 + 1] = (*accent, 255)
        px[X1 - 2, Y1 + 1] = (*shade(accent, 0.8), 255)
        for x in (X1 - 4, X1 - 3, X1 - 2):
            px[x, Y1 + 2] = (*shade(accent, 0.65), 255)

    if "clasp" in orn:
        for x in range(X1 + 1, X1 + 3):
            for y in (14, 15, 16):
                px[x, y] = (196, 166, 96, 255) if x == X1 + 1 else (140, 112, 56, 255)

    if "ring" in orn:
        px[16, Y0 - 1] = (176, 146, 84, 255)
        px[15, Y0 - 2] = (140, 114, 66, 255)
        px[17, Y0 - 2] = (140, 114, 66, 255)

    if "rune" in orn:
        for sx, sy in ((X0 - 2, Y0 + 7), (X0 - 2, Y0 + 8), (X0 - 2, Y0 + 10)):
            if 0 <= sx < 32 and 0 <= sy < 32 and px[sx, sy][3] == 0:
                px[sx, sy] = (*shade(accent, 1.3), 255)

    if "sprig" in orn:
        for sx, sy, col in (
            (X1 + 1, Y1 - 8, (110, 150, 84)),
            (X1 + 2, Y1 - 9, (132, 172, 100)),
            (X1 + 1, Y1 - 6, (88, 124, 68)),
        ):
            if 0 <= sx < 32 and 0 <= sy < 32:
                px[sx, sy] = (*col, 255)

    if "vine" in orn:
        for sx, sy in ((X0 - 1, Y0 + 6), (X0 - 1, Y0 + 9), (X0 - 2, Y0 + 7), (X0 - 2, Y0 + 10)):
            if 0 <= sx < 32 and 0 <= sy < 32:
                px[sx, sy] = (110, 150, 84, 255)

    if "worn" in orn:
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            xx, yy = X1 - 1 - dx, Y1 - dy
            if xx > X0 and yy > Y0:
                px[xx, yy] = CLEAR

    if "burn" in orn:
        for dx, dy in ((0, 0), (0, 1), (1, 1), (-1, 0)):
            xx, yy = X0 + 4 + dx, Y1 - 2 - dy
            if 0 <= xx < 32 and 0 <= yy < 32:
                px[xx, yy] = (26, 18, 14, 255)

    # 整圈暗描边：让图标在任何背景上都能立起来
    solid = [(x, y) for y in range(32) for x in range(32) if px[x, y][3]]
    edge = []
    for x, y in solid:
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3] == 0:
                edge.append((nx, ny))
    for x, y in edge:
        px[x, y] = (18, 16, 15, 255)

    # 左上轮廓光
    for y in range(Y0 + 1, Y1):
        if px[X0, y][3]:
            px[X0, y] = tuple(min(255, int(c * 1.45) + 12) for c in cover) + (255,)

    return img


def main():
    imgs = []
    for book_id, spec in BOOKS.items():
        img = draw_book(spec)
        img.save(OUT / f"{book_id}.png")
        imgs.append(img)

    scale = 8
    pad = 6
    cell = 32 * scale
    sheet = Image.new("RGBA", (len(imgs) * (cell + pad), cell), (24, 24, 28, 255))
    for i, img in enumerate(imgs):
        sheet.alpha_composite(img.resize((cell, cell), Image.NEAREST), (i * (cell + pad), 0))
    sheet.save(SHEET)
    print("wrote", len(imgs), "textures +", SHEET)


if __name__ == "__main__":
    main()
