"""生成"方案 B"预览：书本缩小一点，左侧腾出 8 像素画浅色标志物。

不覆盖正式贴图，输出到 design/preview_b/ 与 design/_variant_compare.png。
"""
from pathlib import Path

from PIL import Image

import make_bookset as mb

out_dir = Path("design/preview_b")
out_dir.mkdir(parents=True, exist_ok=True)

# 方案 B 的几何：书身 18×24，靠右，左边留 8 像素
mb.X0, mb.X1 = 9, 26
mb.Y0, mb.Y1 = 4, 27
mb.FACE_X0, mb.FACE_X1 = 12, 23
mb.FACE_Y0, mb.FACE_Y1 = 7, 24


def add_side_motif(img, kind, accent):
    pale = tuple(int(c * 0.45 + 200 * 0.55) for c in accent)
    motif = mb.MOTIFS.get(kind)
    if not motif:
        return
    px = img.load()
    scale = 2
    mw, mh = max(len(r) for r in motif), len(motif)
    ox = int((mb.X0 - mw * scale) / 2 + 0.5)
    oy = int((32 - mh * scale) / 2 + 0.5)
    for dy, row in enumerate(motif):
        for dx, ch in enumerate(row):
            if ch == ".":
                continue
            for sy in range(scale):
                for sx in range(scale):
                    x, y = ox + dx * scale + sx, oy + dy * scale + sy
                    if 0 <= x < 32 and 0 <= y < 32 and px[x, y][3] == 0:
                        px[x, y] = (*pale, 255)


images = []
for book_id, spec in mb.BOOKS.items():
    img = mb.draw_book(spec)
    add_side_motif(img, spec.get("backdrop", ""), spec.get("accent", spec["cover"]))
    img.save(out_dir / f"{book_id}.png")
    images.append((book_id, img))

cell, pad = 128, 8
sheet = Image.new("RGBA", (len(images) * (cell + pad), cell), (40, 40, 46, 255))
for i, (_, img) in enumerate(images):
    sheet.alpha_composite(img.resize((cell, cell), Image.NEAREST), (i * (cell + pad), 0))
sheet.save("design/_variant_b.png")
print("wrote variant B")
