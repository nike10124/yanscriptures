from PIL import Image
from collections import Counter
import glob, os

files = sorted(glob.glob('design/refs/*.png'))
files = [f for f in files if '_sheet' not in f]
ims = [Image.open(f).convert('RGBA') for f in files]

S = 14
cols = 5
w, h = 16 * S, 16 * S
sheet = Image.new('RGBA', (cols * w, 2 * h), (24, 24, 28, 255))
for i, im in enumerate(ims):
    big = im.resize((w, h), Image.NEAREST)
    x = (i % cols) * w
    y = (i // cols) * h
    sheet.alpha_composite(big, (x, y))
sheet.save('design/refs/_sheet.png')

print("=== per-file palette (top 10) ===")
for f, im in zip(files, ims):
    c = Counter(im.getdata())
    print("\n--", os.path.basename(f), "size", im.size)
    for col, n in c.most_common(10):
        print("   #%02X%02X%02X a=%3d  x%d" % (col[0], col[1], col[2], col[3], n))

print("\n=== grid of 10_X.png (char per unique color) ===")
im = ims[-1]
c = Counter(im.getdata())
pal = [col for col, _ in c.most_common()]
sym = "0123456789abcdefghijklmnop"
idx = {col: sym[i] for i, col in enumerate(pal)}
print("legend:")
for col in pal:
    print("  %s = #%02X%02X%02X a=%d" % (idx[col], col[0], col[1], col[2], col[3]))
px = im.load()
for y in range(16):
    print("  " + "".join(idx[px[x, y]] if px[x, y][3] > 0 else '.' for x in range(16)))
