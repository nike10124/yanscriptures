# -*- coding: utf-8 -*-
"""成就文本调整：删掉「第一本书」，新增「寂灭之笺」；中英同步。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ZH = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang\zh_cn.json"
EN = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang\en_us.json"

zh_add = {
    "advancement.yanscriptures.annihilated.title": "寂灭之笺",
    "advancement.yanscriptures.annihilated.desc": "让至邪之笺在信标的光柱下化入岩浆。",
    "advancement.yanscriptures.written.desc": "在著书台上写出一本史诗级的书。",
}
en_add = {
    "advancement.yanscriptures.annihilated.title": "Annihilated Parchment",
    "advancement.yanscriptures.annihilated.desc": "Let Corrupt Parchment dissolve into lava under a beacon's beam.",
    "advancement.yanscriptures.written.desc": "Write an epic book on the Book Writing Table.",
}

for path, add in ((ZH, zh_add), (EN, en_add)):
    data = json.load(open(path, encoding="utf-8"))
    for key in [k for k in data if k.startswith("advancement.yanscriptures.first_book")]:
        del data[key]
    data.update(add)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2, sort_keys=True)
        out.write("\n")
    print(path, len(data))
