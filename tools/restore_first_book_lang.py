# -*- coding: utf-8 -*-
"""恢复「第一本书」的两条文本（中英），并撤掉对 written 描述的手改。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ZH = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang\zh_cn.json"
EN = r"D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\lang\en_us.json"

zh_add = {
    "advancement.yanscriptures.first_book.title": "第一本书",
    "advancement.yanscriptures.first_book.desc": "获得你的第一本名著。",
    "advancement.yanscriptures.written.desc": "在著书台上写出一本书。",
}
en_add = {
    "advancement.yanscriptures.first_book.title": "First Book",
    "advancement.yanscriptures.first_book.desc": "Obtain your first classic book.",
    "advancement.yanscriptures.written.desc": "Write a book on the Book Writing Table.",
}

for path, add in ((ZH, zh_add), (EN, en_add)):
    data = json.load(open(path, encoding="utf-8"))
    data.update(add)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2, sort_keys=True)
        out.write("\n")
    print(path, len(data))
