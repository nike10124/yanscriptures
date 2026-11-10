from PIL import Image
import glob, os

files = [f for f in sorted(glob.glob('design/refs/*.png')) if '_sheet' not in f]


def cls(p):
    r, g, b, a = p
    if a == 0:
        return '.'
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if r > 240 and g > 190 and b < 150:
        return 'G'          # gold numeral
    if abs(r - 0xAE) < 14 and abs(g - 0x92) < 14 and abs(b - 0x64) < 14:
        return 'K'          # dark outline
    if lum > 205:
        return 'p'          # paper bright
    if lum > 175:
        return 'P'          # paper mid
    return 'd'              # paper dark / shadow


for f in files:
    im = Image.open(f).convert('RGBA')
    px = im.load()
    print('\n== ' + os.path.basename(f))
    rows = []
    for y in range(16):
        rows.append(''.join(cls(px[x, y]) for x in range(16)))
    for r in rows:
        print('   ' + r)

im = Image.open(files[9]).convert('RGBA')
px = im.load()
print('\n== rows of 10_X with alpha/lum detail')
for y in range(16):
    line = []
    for x in range(16):
        p = px[x, y]
        line.append('----' if p[3] == 0 else '%02X%02X' % (p[0], p[1]))
    print('  %2d ' % y + ' '.join(line))
