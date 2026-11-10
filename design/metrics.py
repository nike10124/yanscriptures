from PIL import Image
import glob

def stats(p):
    im = Image.open(p).convert('RGBA')
    w, h = im.size
    px = im.load()
    lum, n = 0.0, 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0:
                lum += 0.299 * r + 0.587 * g + 0.114 * b
                n += 1
    return w, h, n, n / (w * h), lum / max(n, 1)

print('%-28s %-9s %-6s %-7s %s' % ('file', 'size', 'opaque', 'cover', 'meanLum'))
for f in sorted(glob.glob('design/refs/*.png')):
    if '_sheet' in f:
        continue
    w, h, n, cov, l = stats(f)
    print('%-28s %-9s %-6d %-7.2f %.1f' % (f.split('/')[-1], '%dx%d' % (w, h), n, cov, l))
w, h, n, cov, l = stats('design/out/scripture_of_revelation_32.png')
print('%-28s %-9s %-6d %-7.2f %.1f' % ('MINE 32x32', '%dx%d' % (w, h), n, cov, l))
