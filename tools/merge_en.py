# -*- coding: utf-8 -*-
"""把 en_*.json 批次合并进 assets/lang/en_us.json（去掉中文书名号）。"""
import glob
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = r"D:\MCModDev\forge-mdk"
OUT = os.path.join(BASE, r"src\main\resources\assets\yanscriptures\lang\en_us.json")

merged = {}
if os.path.exists(OUT):
    merged = json.load(open(OUT, encoding="utf-8"))

for path in sorted(glob.glob(os.path.join(BASE, r"backup\en_batch*.json"))):
    part = json.load(open(path, encoding="utf-8"))
    for k, v in part.items():
        merged[k] = v.replace("\u300a", "").replace("\u300b", "")

json.dump(merged, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=2, sort_keys=True)
print("total keys:", len(merged))
for k in ("item.yanscriptures.republic", "item.yanscriptures.book_of_me", "curios.modifiers.book"):
    print(k, "=>", merged.get(k))
