"""生成著书台的方块贴图与界面底板。运行一次即可，产物直接写进资源目录。"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path("src/main/resources/assets/yanscriptures")
BLOCK_DIR = ROOT / "textures" / "block"
GUI_DIR = ROOT / "textures" / "gui"
BLOCK_DIR.mkdir(parents=True, exist_ok=True)
GUI_DIR.mkdir(parents=True, exist_ok=True)

DARK = (58, 40, 26, 255)
WOOD = (112, 78, 46, 255)
WOOD_L = (138, 100, 62, 255)
WOOD_D = (84, 56, 32, 255)
IRON = (86, 84, 92, 255)
IRON_L = (128, 126, 136, 255)
SHADOW = (44, 30, 20, 255)
PAPER = (218, 206, 172, 255)
PAPER_D = (186, 172, 138, 255)


def planks(img, y0, y1, base=WOOD, light=WOOD_L, dark=WOOD_D):
    px = img.load()
    for y in range(y0, y1):
        for x in range(16):
            px[x, y] = base if (y % 4) else dark
    # 木纹：几条顺纹
    for y in range(y0, y1, 2):
        for x in range(0, 16, 5):
            gx = min(15, x + (y % 3))
            px[gx, y] = light


def side():
    img = Image.new("RGBA", (16, 16), WOOD)
    planks(img, 0, 16)
    px = img.load()
    # 上下铁箍
    for y in (2, 3, 12, 13):
        for x in range(16):
            px[x, y] = IRON if y % 2 else IRON_L
    # 中间一个浅浅的凹槽（暗示这是台面结构）
    for x in range(5, 11):
        px[x, 7] = SHADOW
        px[x, 8] = WOOD_D
    return img


def top():
    img = Image.new("RGBA", (16, 16), WOOD)
    planks(img, 0, 16)
    px = img.load()
    # 外圈包边
    for x in range(16):
        px[x, 0] = DARK
        px[x, 15] = DARK
    for y in range(16):
        px[0, y] = DARK
        px[15, y] = DARK
    # 书槽：左上角一块凹陷
    for y in range(3, 9):
        for x in range(3, 10):
            px[x, y] = SHADOW
            px[x, y + 1] = SHADOW
    for y in range(3, 9):
        for x in range(3, 10):
            if y < 8:
                px[x, y] = (32, 24, 18, 255) if y > 4 else SHADOW
    # 台面上铺着的一页纸（右边）
    for y in range(9, 14):
        for x in range(9, 14):
            px[x, y] = PAPER if y < 13 else PAPER_D
    # 纸的暗边
    for x in range(9, 14):
        px[x, 13] = PAPER_D
    for y in range(9, 14):
        px[13, y] = PAPER_D
    return img


def bottom():
    img = Image.new("RGBA", (16, 16), WOOD_D)
    planks(img, 0, 16, base=WOOD_D, light=WOOD, dark=SHADOW)
    return img


side().save(BLOCK_DIR / "book_writing_table_side.png")
top().save(BLOCK_DIR / "book_writing_table_top.png")
bottom().save(BLOCK_DIR / "book_writing_table_bottom.png")

# ==================== 界面底板 176×166 ====================
W, H = 176, 166
gui = Image.new("RGBA", (W, H), (198, 198, 198, 255))
d = ImageDraw.Draw(gui)

# 外框与内衬
d.rectangle([0, 0, W - 1, H - 1], outline=(24, 24, 24, 255))
d.rectangle([1, 1, W - 2, H - 2], outline=(86, 86, 86, 255))
d.rectangle([2, 2, W - 3, H - 3], fill=(198, 198, 198, 255))
d.line([3, 3, W - 4, 3], fill=(236, 236, 236, 255))
d.line([3, 3, 3, H - 4], fill=(236, 236, 236, 255))
d.line([W - 4, 4, W - 4, H - 4], fill=(150, 150, 150, 255))
d.line([4, H - 4, W - 4, H - 4], fill=(150, 150, 150, 255))


def slot(x, y):
    """画一个原版风格的槽位（左上是坐标，槽本身 18×18）。"""
    d.rectangle([x, y, x + 17, y + 17], fill=(140, 140, 140, 255))
    d.line([x, y, x + 17, y], fill=(70, 70, 70, 255))
    d.line([x, y, x, y + 17], fill=(70, 70, 70, 255))
    d.line([x + 1, y + 1, x + 16, y + 1], fill=(180, 180, 180, 255))
    d.line([x + 1, y + 1, x + 1, y + 16], fill=(180, 180, 180, 255))


# 书槽（80,20）与十格材料（44+col*18, 44+row*18）
slot(79, 19)
for i in range(10):
    col, row = i % 5, i // 5
    slot(44 + col * 18 - 1, 44 + row * 18 - 1)

# 玩家背包与快捷栏
for row in range(3):
    for col in range(9):
        slot(8 + col * 18 - 1, 84 + row * 18 - 1)
for col in range(9):
    slot(8 + col * 18 - 1, 142 - 1)

# 分区装饰线
d.line([6, 37, W - 7, 37], fill=(160, 160, 160, 255))
d.line([6, 79, W - 7, 79], fill=(160, 160, 160, 255))

gui.save(GUI_DIR / "book_writing_table.png")
print("wrote block + gui textures")
