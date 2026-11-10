"""把 11 件残卷的贴图与模型装进资源目录。"""
from PIL import Image
import json, os

TEX = 'src/main/resources/assets/examplemod/textures/item'
MODEL = 'src/main/resources/assets/examplemod/models/item'
os.makedirs(TEX, exist_ok=True)
os.makedirs(MODEL, exist_ok=True)

# (注册名, 贴图来源)
ITEMS = [
    ('scripture_of_shema',      'design/refs/01_I.png'),
    ('scripture_of_silence',    'design/refs/02_II.png'),
    ('scripture_of_thorns',     'design/refs/03_III.png'),
    ('scripture_of_sabbath',    'design/refs/04_IV.png'),
    ('scripture_of_rebellion',  'design/refs/05_V.png'),
    ('scripture_of_blood',      'design/refs/06_VI.png'),
    ('scripture_of_harp',       'design/refs/07_VII.png'),
    ('scripture_of_shinar',     'design/refs/08_VIII.png'),
    ('scripture_of_tongue',     'design/refs/09_IX.png'),
    ('scripture_of_vanity',     'design/refs/10_X.png'),
    ('scripture_of_revelation', 'design/out/scripture_of_revelation_32.png'),
]

report = []
for item_id, src in ITEMS:
    im = Image.open(src).convert('RGBA')
    dst = os.path.join(TEX, item_id + '.png')
    im.save(dst)

    model = {
        "parent": "item/generated",
        "textures": {"layer0": "examplemod:item/" + item_id},
        "display": {"gui": {"scale": [1.0, 1.0, 1.0]}},
    }
    with open(os.path.join(MODEL, item_id + '.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, indent=2, ensure_ascii=False)
        f.write('\n')

    report.append((item_id, im.size, os.path.getsize(dst)))

for r in report:
    print('%-28s %-9s %d B' % (r[0], '%dx%d' % r[1], r[2]))
print('\ntextures:', len(os.listdir(TEX)), ' models:', len(os.listdir(MODEL)))
