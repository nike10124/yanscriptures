# -*- coding: utf-8 -*-
"""给中英语言文件补 advancement.yanscriptures.epic_item.desc。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang"
TEXTS = {
    "zh_cn.json": "曾经获得过这块史诗。",
    "en_us.json": "Obtain this epic.",
}
KEY = "advancement.yanscriptures.epic_item.desc"

for name, text in TEXTS.items():
    path = BASE + "\\" + name
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data[KEY] = text
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    print(name, len(data), data[KEY])
