"""
书籍类物品贴图生成器。

几何严格对齐《翠玉录》(emerald_tablet.png)——实测它的书身范围是：
    x = 6..27  →  宽 22 像素
    y = 2..29  →  高 28 像素（长宽比 22:28 ≈ 0.79，绝不是正方形）
    右侧书页边只有 1~2 像素，x=27 是最外层描边
    顶部 y=1 有一小截书签凸起

本脚本按同一套几何生成 32×32 的九本名著 + 《方块与和平》，
另外单独生成 64×64 的《空白的书页》（保留 ∞，书页边收窄、更正面）。

运行：
    <python> design/make_classic_covers.py
输出：
    src/main/resources/assets/yanscriptures/textures/item/<id>.png
    design/classic_sheet.png   (6 倍放大预览)
"""
from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/yanscriptures/textures/item")
SHEET = Path("design/classic_sheet.png")

CLEAR = (0, 0, 0, 0)
OUTLINE = (14, 12, 12, 255)
PAGE = (226, 218, 196, 255)
PAGE_SH = (168, 160, 140, 255)

# 《翠玉录》实测几何
X0, X1 = 6, 27
Y0, Y1 = 2, 29

BOOKS = {
    "dao_de_jing": dict(
        face=(64, 56, 46),
        cover=(30, 28, 26), dark=(14, 13, 12), frame=(198, 162, 76),
        glyph=(242, 238, 228), alt=(198, 162, 76), art=None,  # 太极图程序生成
    ),
    "republic": dict(
        face=(58, 66, 86),
        cover=(40, 48, 64), dark=(20, 24, 34), frame=(150, 162, 182),
        glyph=(236, 240, 248), alt=(26, 32, 44),
        art=[
            ".....ggggg...",
            "....g.....g..",
            "....g.aaa.g..",
            "....g.aaaa.g.",
            "....g.....g..",
            "....g.....g..",
            "....gg...gg..",
            "....ggggggg..",
        ],
    ),
    "zarathustra": dict(
        face=(56, 32, 30),
        cover=(36, 28, 26), dark=(16, 12, 12), frame=(156, 52, 46),
        glyph=(242, 200, 96), alt=(70, 64, 58),
        art=[
            ".g..g..g..g..",
            "..g.g.g.g.g..",
            "...aaaaaaa...",
            "..aaaaaaaaa..",
            "....aaaaa....",
            "...ggggggg...",
            "..ggg.ggg.gg.",
            ".ggggggggggg.",
        ],
    ),
    "divine_comedy": dict(
        face=(76, 28, 30),
        cover=(56, 20, 22), dark=(26, 8, 10), frame=(176, 126, 62),
        glyph=(232, 202, 156), alt=(196, 150, 90),
        art=[
            "....ggggg....",
            "..gg.....gg..",
            ".g..aaaaa..g.",
            "g..a.....a..g",
            "g.a..ggg..a.g",
            "g.a.ggggg.a.g",
            "g.a..ggg..a.g",
            "g..a.....a..g",
            ".g..aaaaa..g.",
            "..gg.....gg..",
            "....ggggg....",
        ],
    ),
    "don_quixote": dict(
        face=(112, 80, 46),
        cover=(152, 114, 68), dark=(100, 70, 38), frame=(214, 186, 138),
        glyph=(242, 228, 200), alt=(150, 28, 28),
        art=[
            ".....g.g.....",
            "...g.g.g.g...",
            "....ggggg....",
            ".....ggg.....",
            "ggggggggggggg",
            ".....ggg.....",
            "....ggggg....",
            "...g.g.g.g...",
            ".....g.g.....",
            "....aaaaa....",
            "....aaaaa....",
        ],
    ),
    "hundred_years": dict(
        face=(32, 56, 40),
        cover=(28, 48, 36), dark=(12, 24, 18), frame=(188, 158, 72),
        glyph=(242, 216, 98), alt=(214, 168, 58),
        art=[
            "....g...g....",
            "...ggg.ggg...",
            "..ggggggggg..",
            "...ggg.ggg...",
            "....g...g....",
        ],
    ),
    "euclid": dict(
        face=(22, 46, 56),
        cover=(24, 42, 52), dark=(10, 20, 28), frame=(84, 150, 162),
        glyph=(238, 242, 242), alt=(120, 200, 210),
        art=[
            "....ggggg....",
            "..gg.....gg..",
            ".g....a....g.",
            "g....aaa...g.",
            "g...aaaaa..g.",
            "g..aaaaaaa.g.",
            "g.aaaaaaaaa.g",
            "g..........g.",
            ".gg......gg..",
            "...gggggg....",
        ],
    ),
    "origin": dict(
        face=(38, 60, 34),
        cover=(34, 54, 32), dark=(14, 28, 14), frame=(152, 192, 112),
        glyph=(208, 226, 178), alt=(120, 160, 96),
        art=[
            ".....ggg.....",
            "....ggggg....",
            "...ggggggg...",
            "..ggg.ggg.gg.",
            ".....ggg.....",
            "......g......",
            "......g......",
            ".....ggg.....",
            "....ggggg....",
        ],
    ),
    "nine_chapters": dict(
        face=(98, 72, 40),
        cover=(122, 92, 52), dark=(78, 58, 30), frame=(210, 180, 120),
        glyph=(246, 238, 214), alt=(58, 40, 18),
        art=[
            ".g..g..g..g..",
            "ggggggggggggg",
            ".a..a..a..a..",
            "ggggggggggggg",
            ".a..a..a..a..",
            "ggggggggggggg",
            ".a..a..a..a..",
        ],
    ),
    "book_of_peace": dict(
        face=(88, 32, 34),
        cover=(96, 34, 36), dark=(58, 18, 20), frame=(212, 178, 108),
        glyph=(242, 240, 230), alt=(122, 176, 96),
        art=[
            "........ggg..",
            "......ggggg..",
            ".....ggggggg.",
            "....ggggggggg",
            "....ggggggggg",
            "....ggggg.gg.",
            ".....gggg....",
            "......gg.....",
            ".......g..aa.",
            ".........aaa.",
        ],
    ),
}


def yin_yang_art():
    """13×13 太极图：旋转对称的两鱼（左上白 / 右下白），各带一个鱼眼。"""
    n = 13
    c = 6.0
    R = 6.2
    grid = []
    for y in range(n):
        row = []
        for x in range(n):
            dx, dy = x - c, y - c
            if dx * dx + dy * dy > R * R:
                row.append(".")
                continue
            # 上半圆取右半黑、下半圆取左半黑 —— 形成旋转对称的 S 形
            black = (x >= c) if (y < c) else (x < c)
            if (x - c) ** 2 + (y - 3.0) ** 2 <= 2.2 ** 2:
                black = False
            if (x - c) ** 2 + (y - 9.0) ** 2 <= 2.2 ** 2:
                black = True
            row.append("a" if black else "g")
        grid.append("".join(row))
    return grid


TEMPLATE_PATH = "src/main/resources/assets/yanscriptures/textures/item/emerald_tablet.png"


def load_template():
    """直接拿《翠玉录》当结构模板：皮革底、上方吊饰、下方金带、右侧书页边都照抄。"""
    return Image.open(TEMPLATE_PATH).convert("RGBA")


def draw_classic(spec, template):
    img = template.copy()
    px = img.load()
    cover, dark, frame = spec["cover"], spec["dark"], spec["frame"]

    # 把封面主色换成本书配色（只动很暗的皮革像素）
    leather = tuple(int(c * 0.62) for c in cover)
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            r, g, b, a = px[x, y]
            if a and (r + g + b) / 3 < 80:
                px[x, y] = (*leather, 255)

    # 封面正面整块换成本书配色：不再画"正方形内框"，图案直接落在封面上
    face = spec.get("face") or tuple(
        min(255, int(c * 0.5 + f * 0.5)) for c, f in zip(cover, frame)
    )
    fx0, fy0, fx1, fy1 = 9, 5, 24, 26
    for y in range(fy0, fy1 + 1):
        for x in range(fx0, fx1 + 1):
            px[x, y] = (*face, 255)

    # 图案（行宽补齐到最长一行，按封面正面区域的像素中心对齐）
    raw = spec["art"] if spec["art"] else yin_yang_art()
    art = [row.ljust(max(len(r) for r in raw), ".") for row in raw]
    w, h = len(art[0]), len(art)
    ox = int((fx0 + fx1 + 1 - w) / 2 + 0.5)
    oy = int((fy0 + fy1 + 1 - h) / 2 + 0.5)
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            color = spec["glyph"] if ch == "g" else spec["alt"] if ch == "a" else dark
            px[ox + dx, oy + dy] = (*color, 255)

    return img


def draw_blank_page():
    """128×128：外形照《如我所书》（正面、右侧一窄条书页），封面是《空白的书页》的蓝灰 + ∞。"""
    import math
    import random

    ref = Image.open(OUT / "book_of_me.png").convert("RGBA")
    W = H = ref.width
    rpx = ref.load()

    cover = (150, 160, 172)
    cover_light = (182, 192, 204)
    cover_dark = (120, 130, 144)
    border = (48, 54, 64)
    gold = (198, 168, 96)
    gold_dark = (140, 112, 56)
    page = (232, 226, 206)
    page_line = (206, 198, 176)

    img = Image.new("RGBA", (W, H), CLEAR)
    px = img.load()

    rnd = random.Random(11)
    rows = {}
    for y in range(H):
        xs = [x for x in range(W) if rpx[x, y][3] > 40]
        if xs:
            rows[y] = (min(xs), max(xs))

    PAGE_W = 6
    for y, (lx, rx) in rows.items():
        cover_r = rx - PAGE_W - 2
        for x in range(lx, rx + 1):
            if x <= lx + 1 or x >= rx - 1:
                px[x, y] = (*border, 255)
            elif x > cover_r:
                # 右侧书页：竖条纹模拟纸页
                px[x, y] = (*(page if (y // 2) % 2 == 0 else page_line), 255)
            else:
                px[x, y] = (*(cover_light if rnd.random() < 0.07 else cover), 255)

        # 封面左侧压暗，做出书脊
        for x in range(lx + 2, min(lx + 6, cover_r)):
            px[x, y] = (*cover_dark, 255)

    # 四角金色护角（《空白的书页》原本的样式）
    def bracket(cx, cy, dx, dy, length=18, thick=4):
        for i in range(length):
            for t in range(thick):
                for X, Y in ((cx + dx * i, cy + dy * t), (cx + dx * t, cy + dy * i)):
                    if 0 <= X < W and 0 <= Y < H and px[X, Y][3]:
                        px[X, Y] = (*(gold if t < 2 else gold_dark), 255)

    top = min(rows)
    bottom = max(rows)
    left = min(l for l, _ in rows.values())
    bracket(left + 4, top + 4, 1, 1)
    bracket(left + 4, bottom - 4, 1, -1)
    right_cover = min(r for _, r in rows.values()) - PAGE_W - 6
    bracket(right_cover - 4, top + 4, -1, 1)
    bracket(right_cover - 4, bottom - 4, -1, -1)

    # ∞
    cx = (left + right_cover) / 2
    cy = (top + bottom) / 2
    a = (right_cover - left) * 0.42
    white = (250, 248, 246)

    def stamp(cx_, cy_, rad):
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if dx * dx + dy * dy <= rad * rad:
                    X, Y = int(cx_ + dx), int(cy_ + dy)
                    if 0 <= X < W and 0 <= Y < H and px[X, Y][3]:
                        px[X, Y] = (*white, 255)

    steps = 720
    for i in range(steps):
        t = 2 * math.pi * i / steps
        denom = 1 + math.sin(t) ** 2
        stamp(cx + a * math.cos(t) / denom, cy + a * math.sin(t) * math.cos(t) / denom, max(2, int(a * 0.1)))

    return img


def save_png(img, path):
    """Windows 上偶发 Invalid argument / 拒绝访问（杀软扫描占句柄），带重试地保存。"""
    import os
    import time

    path = Path(path)
    tmp = path.with_name(path.stem + ".tmp.png")
    last = None
    for _ in range(20):
        try:
            img.save(tmp)
            os.replace(tmp, path)
            return
        except OSError as exc:  # noqa: PERF203
            last = exc
            time.sleep(0.25)
    raise last


def restore_blank_page():
    """《空白的书页》用作者定的原版 64×64（已从旧 jar 里恢复），本脚本不再重画它。"""
    src = Path("design/recover/blank_page.png")
    if src.exists():
        img = Image.open(src).convert("RGBA")
        save_png(img, OUT / "blank_page.png")
    return Image.open(OUT / "blank_page.png").convert("RGBA")


def main():
    template = load_template()
    imgs = []
    for book_id, spec in BOOKS.items():
        img = draw_classic(spec, template)
        save_png(img, OUT / f"{book_id}.png")
        imgs.append(img)

    imgs.append(restore_blank_page())

    scale = 6
    pad = 8
    cell = 64 * scale
    sheet = Image.new("RGBA", (11 * (cell + pad), cell), (22, 22, 26, 255))
    for i, img in enumerate(imgs):
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        if big.width > cell:  # 64×64 的《空白的书页》缩一半
            big = big.resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(
            big, (i * (cell + pad) + (cell - big.width) // 2, (cell - big.height) // 2)
        )
    try:
        save_png(sheet, SHEET)
    except OSError:
        print('sheet busy, skipped')
    print("wrote", len(imgs), "textures +", SHEET)


if __name__ == "__main__":
    main()
