# -*- coding: utf-8 -*-
"""收集成就改为「除空白的书页、如我所书、圣约·原典之外的全部书籍」。"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk"
TAG = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "tags", "items", "books.json")
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "advancements", "collect_all.json")

EXCLUDE = {
    "yanscriptures:blank_page",
    "yanscriptures:book_of_me",
    "yanscriptures:covenant_codex",
}

tag = json.load(open(TAG, encoding="utf-8"))
items = [v for v in tag["values"] if v not in EXCLUDE]

data = json.load(open(OUT, encoding="utf-8"))
data["criteria"] = {}
req = []
for i, item in enumerate(items):
    key = "b%02d" % i
    data["criteria"][key] = {
        "trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": [item]}]},
    }
    req.append([key])
data["requirements"] = req

with open(OUT, "w", encoding="utf-8", newline="\n") as out:
    json.dump(data, out, ensure_ascii=False, indent=2)
    out.write("\n")

print("items", len(items))
print(", ".join(i.split(":")[1] for i in items))
