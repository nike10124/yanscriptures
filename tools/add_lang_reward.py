# -*- coding: utf-8 -*-
"""追加两类新奖励提示的中英文本。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang"

ZH = {
    "message.yanscriptures.scroll.all_completed": "§6十条诫命尽数反转，残卷之外传来一声封印松动的轻响。",
    "message.yanscriptures.epic.ego_granted": "§d四块石板共鸣，灰尘落定之处，浮起最后一块。",
    "message.yanscriptures.epic.ego_missing": "§7石板回应了你，却不肯现身——它还缺四块同伴。",
}

EN = {
    "message.yanscriptures.scroll.all_completed": "§6All ten commandments are reversed. Beyond the fragments, a seal comes loose with a soft click.",
    "message.yanscriptures.epic.ego_granted": "§dThe four tablets resonate, and the last one rises where the dust settles.",
    "message.yanscriptures.epic.ego_missing": "§7The tablet answers you, yet refuses to appear — four companions are still missing.",
}


def merge(name, data):
    path = f"{BASE}\\{name}"
    with open(path, encoding="utf-8") as f:
        cur = json.load(f)
    added = 0
    for k, v in data.items():
        if k not in cur:
            cur[k] = v
            added += 1
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(cur, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    print(name, "keys", len(cur), "added", added)


merge("zh_cn.json", ZH)
merge("en_us.json", EN)
