# -*- coding: utf-8 -*-
"""统计：同一套基础材料（纸/墨/线各一个档位）最多能覆盖多少本书。"""
import glob
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\MCModDev\forge-mdk"
RECIPES = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "book_writing")

recipes = []
for path in sorted(glob.glob(os.path.join(RECIPES, "*.json"))):
    data = json.load(open(path, encoding="utf-8"))
    recipes.append({
        "id": os.path.basename(path)[:-5],
        "paper": (data["paper"]["tier"], data["paper"]["count"]),
        "ink": (data["ink"]["tier"], data["ink"]["count"]),
        "thread": (data["thread"]["tier"], data["thread"]["count"]),
        "extras": [(e["item"], e.get("count", 1)) for e in data.get("extras", [])],
    })

groups = {}
for r in recipes:
    key = (r["paper"][0], r["ink"][0], r["thread"][0])
    groups.setdefault(key, []).append(r)

rank = sorted(groups.items(), key=lambda kv: -len(kv[1]))
print("共 %d 条配方，%d 种基础三件组合\n" % (len(recipes), len(groups)))
for key, items in rank:
    extras = {}
    for r in items:
        for item, count in r["extras"]:
            extras.setdefault(item, 0)
    print("纸%s/墨%s/线%s : %d 本，涉及 %d 种额外材料" % (key[0], key[1], key[2], len(items), len(extras)))

best_key, best_items = rank[0]
print("\n=== 最优：纸%s/墨%s/线%s  %d 本 ===" % (best_key[0], best_key[1], best_key[2], len(best_items)))
totals = {"paper": [0, best_key[0]], "ink": [0, best_key[1]], "thread": [0, best_key[2]]}
extras = {}
for r in best_items:
    totals["paper"][0] += r["paper"][1]
    totals["ink"][0] += r["ink"][1]
    totals["thread"][0] += r["thread"][1]
    for item, count in r["extras"]:
        extras[item] = extras.get(item, 0) + count
    print("  - %-32s 纸%d 墨%d 线%d  extras=%d" % (r["id"], r["paper"][1], r["ink"][1], r["thread"][1], len(r["extras"])))
print("\n基础材料合计：纸%d x%d、墨%d x%d、线%d x%d"
      % (totals["paper"][1], totals["paper"][0], totals["ink"][1], totals["ink"][0],
         totals["thread"][1], totals["thread"][0]))
print("额外材料 %d 种：" % len(extras))
for item, count in sorted(extras.items(), key=lambda kv: -kv[1]):
    print("  %-45s x%d" % (item, count))
print("额外材料槽位需求（一格一种）：%d 格" % len(extras))
