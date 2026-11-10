"""启示残卷 32x32 —— 与其余九张十六进制残卷同源的羊皮纸剪影 + 凝固血色 + Ω。"""
from PIL import Image
import glob, os

W = H = 32
S = 12


def h(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


PAPER = [(224, 210, 180), (218, 203, 172), (211, 196, 164),
         (203, 188, 154), (196, 180, 145), (188, 171, 136)]
EDGE_HI = (238, 227, 203)
EDGE_LO = (150, 126, 88)
FOLD = (128, 105, 72)
GOLD = (255, 215, 98)
BLOOD_D = (70, 13, 13)
BLOOD_M = (108, 23, 21)
BLOOD_B = (84, 29, 21)
BLOOD_L = (144, 42, 32)

ROWS = {}
for y in range(4, 6):   ROWS[y] = (12, 25)
for y in range(6, 8):   ROWS[y] = (10, 27)
for y in range(8, 22):  ROWS[y] = (8, 27)
for y in range(22, 24): ROWS[y] = (8, 23)
for y in range(24, 26): ROWS[y] = (8, 23)
for y in range(26, 28): ROWS[y] = (10, 15)

inside = set()
for y, (x0, x1) in ROWS.items():
    for x in range(x0, x1 + 1):
        inside.add((x, y))

for (x, y) in [(27, 9), (26, 20), (9, 6)]:
    inside.discard((x, y))

img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()

BAY = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
for (x, y) in inside:
    t = (y - 4) / 24.0
    n = h(x, y, 1) * 0.75 + (BAY[y & 3][x & 3] / 16.0) * 0.25
    band = min(len(PAPER) - 1, int((t * 0.35 + n * 0.65) * (len(PAPER) - 1)))
    px[x, y] = PAPER[band] + (255,)

# 撕裂纤维：暗色只零星点在边缘，不做整圈描边
for (x, y) in [(12, 4), (18, 4), (22, 4), (10, 6), (8, 12), (8, 18),
               (27, 14), (27, 19), (26, 21), (23, 23), (15, 27), (12, 27),
               (8, 9), (9, 25), (25, 6)]:
    if (x, y) in inside:
        px[x, y] = EDGE_LO + (255,)

# 上缘受光（只在没有纤维的位置）
for (x, y) in inside:
    if (x, y - 1) not in inside and px[x, y][0] != EDGE_LO[0]:
        px[x, y] = EDGE_HI + (255,)

if (10, 25) in inside:
    px[10, 25] = FOLD + (255,)

# ---------------- 纸上的凝固血：三点 + 一道短血痕 ----------------
for (x, y, c) in [(26, 18, BLOOD_M), (21, 25, BLOOD_B), (25, 16, BLOOD_D)]:
    if (x, y) in inside:
        px[x, y] = c + (255,)

for (x, y, c) in [(24, 9, BLOOD_D), (24, 10, BLOOD_M), (23, 11, BLOOD_B)]:
    if (x, y) in inside:
        px[x, y] = c + (255,)

# ---------------- Ω：∩ 形圆环 + 底部开口的两条腿 ----------------
GLYPH = [
    "...#####...",
    "..##...##..",
    ".##.....##.",
    ".##.....##.",
    ".##.....##.",
    ".##.....##.",
    "..##...##..",
    "..##...##..",
    "..##...##..",
    "..##...##..",
]
gx, gy = 12, 10

# 环口渗血：写在 Ω 之前、落在两条腿之间的开口处，被金字围住
for (x, y) in [(gx + 5, gy + 7), (gx + 4, gy + 8), (gx + 5, gy + 8)]:
    if (x, y) in inside:
        px[x, y] = BLOOD_D + (255,)

for j, row in enumerate(GLYPH):
    for i, ch in enumerate(row):
        if ch != '#':
            continue
        x, y = gx + i, gy + j
        if (x, y) in inside:
            px[x, y] = GOLD + (255,)

img.save('design/out/scripture_of_revelation_32.png')
img.resize((W * S, H * S), Image.NEAREST).save('design/out/_preview.png')

refs = [f for f in sorted(glob.glob('design/refs/*.png')) if '_sheet' not in f]
CW = 16 * 8
cells = [Image.open(f).convert('RGBA').resize((CW, CW), Image.NEAREST) for f in refs]
cells.append(img.resize((CW, CW), Image.NEAREST))
sheet = Image.new('RGBA', (CW * 6, CW * 2), (26, 26, 30, 255))
for i, c in enumerate(cells):
    sheet.alpha_composite(c, ((i % 6) * CW, (i // 6) * CW))
sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save('design/out/_compare.png')
print('ok', os.path.getsize('design/out/scripture_of_revelation_32.png'))
