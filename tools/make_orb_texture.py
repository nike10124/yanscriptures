"""生成天体用的灰度球面贴图（32x32）。

作者 2026-11-07 的要求：不要那么圆，要有「方形感」，并且稍微小一点（大小在
CelestialOrbEntity.displaySize() 里改）。因此这里用超椭圆（squircle，指数 n）做轮廓，
明暗量化成 4 段，不做抗锯齿，保留像素块感；颜色由渲染器按天体染（金/冷白/蓝）。
"""

from PIL import Image

SIZE = 32
N = 2.7                      # 轮廓指数：2 是正圆，越大越方（作者要「一点方形的感觉」，不要变成方块）
BANDS = (0.12, 0.36, 0.62, 0.92)
SHRINK = 0.78                # 轮廓只占贴图 78%：超椭圆会顶到贴图四边，不缩就会显得比原来的圆还大
EDGE_BAND = 0.82             # 轮廓最外圈压暗的位置（超椭圆归一化后 1.0 就是边缘）
EDGE_DARKEN = 0.55
LIGHT = (-0.45, -0.50, 0.74)

OUT = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\textures\entity\orb.png"

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
px = img.load()
half = (SIZE - 1) / 2.0
lx, ly, lz = LIGHT

for y in range(SIZE):
    for x in range(SIZE):
        dx = (x - half) / half / SHRINK
        dy = (y - half) / half / SHRINK
        shape = abs(dx) ** N + abs(dy) ** N
        if shape > 1.0:
            continue
        r2 = min(1.0, dx * dx + dy * dy)
        nz = max(0.0, 1.0 - r2) ** 0.5
        lit = max(0.0, dx * lx + dy * ly + nz * lz)

        level = BANDS[0]
        for band in BANDS:
            if lit >= band - 0.12:
                level = band
        if shape > EDGE_BAND:
            level *= EDGE_DARKEN

        v = int(round(255 * level))
        px[x, y] = (v, v, v, 255)

img.save(OUT)
print("written:", OUT)
