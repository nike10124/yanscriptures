# -*- coding: utf-8 -*-
"""补《至理之书》的中英语言键。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang"
KEYS = {
    "zh_cn.json": {
        "item.yanscriptures.book_of_truth": "至理之书",
        "tooltip.yanscriptures.book_of_truth.line1": "这是「我」的书，这是「你」的书。",
        "tooltip.yanscriptures.book_of_truth.line2": "使用后获得随机一本史诗书籍，书会消耗。",
        "message.yanscriptures.book_of_truth.welcome": "§9有人把他的书留在了这里。",
        "message.yanscriptures.book_of_truth.granted": "§9你翻开书页，里面写着「%1$s」。",
        "message.yanscriptures.book_of_truth.empty": "§7书页一片空白。",
    },
    "en_us.json": {
        "item.yanscriptures.book_of_truth": "The Book of Truth",
        "tooltip.yanscriptures.book_of_truth.line1": "This is \"my\" book. This is \"your\" book.",
        "tooltip.yanscriptures.book_of_truth.line2": "Use it to receive a random epic book. The book is consumed.",
        "message.yanscriptures.book_of_truth.welcome": "§9Someone left his book here.",
        "message.yanscriptures.book_of_truth.granted": "§9You open the pages: \"%1$s\".",
        "message.yanscriptures.book_of_truth.empty": "§7The pages are blank.",
    },
}

for name, extra in KEYS.items():
    path = BASE + "\\" + name
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data.update(extra)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    print(name, len(data))
