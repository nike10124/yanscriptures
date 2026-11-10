# -*- coding: utf-8 -*-
"""台面一次摆满（额外材料 ≤10 格）最多能做几本不同的书。"""
import glob
import io
import itertools
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\MCModDev\forge-mdk"
RECIPES = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "book_writing")
SLOTS = 10

recipes = []
for path in sorted(glob.glob(os.path.join(RECIPES, "*.json"))):
    data = json.load(open(path, encoding="utf-8"))
    recipes.append({
        "id": os.path.basename(path)[:-5],
        "key": (data["paper"]["tier"], data["ink"]["tier"], data["thread"]["tier"]),
        "extras": frozenset(e["item"] for e in data.get("extras", [])),
    })

groups = {}
for r in recipes:
    groups.setdefault(r["key"], []).append(r)

best = (0, None, None)
for key, items in groups.items():
    n = len(items)
    for size in range(n, 0, -1):
        if size <= best[0]:
            break
        found = None
        # 组合太多时只做 3 以内的暴力，其余用贪心
        if size <= 3:
            for combo in itertools.combinations(items, size):
                union = frozenset().union(*(c["extras"] for c in combo))
                if len(union) <= SLOTS:
                    found = combo
                    break
        if found:
            best = (size, key, found)
            break

print("台面一次摆满（额外材料 ≤%d 格）最多：%d 本" % (SLOTS, best[0]))
if best[1]:
    print("基础材料：纸%s 墨%s 线%s" % best[1])
    for c in best[2]:
        print("  -", c["id"], sorted(c["extras"]))
