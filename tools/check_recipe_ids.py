# -*- coding: utf-8 -*-
"""校验生成配方里的材料 id 是否真实存在（只查别家模组与自家）。"""
import glob
import io
import json
import os
import re
import sys
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\MCModDev\forge-mdk"
have = {}
for jar in glob.glob(os.path.join(ROOT, "libs", "*.jar")):
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            m = re.match(r"assets/([^/]+)/models/item/([a-z0-9_/]+)\.json", name)
            if m:
                have[m.group(1) + ":" + m.group(2)] = os.path.basename(jar)
for path in glob.glob(os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "models", "item", "*.json")):
    have["yanscriptures:" + os.path.splitext(os.path.basename(path))[0]] = "self"

IDS = set()
FILES = glob.glob(os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "book_writing", "*.json"))
FILES.append(os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "empty_bookshelf.json"))
for path in FILES:
    data = json.load(open(path, encoding="utf-8"))
    for extra in data.get("extras", []):
        IDS.add(extra["item"])
    for ing in data.get("ingredients", []):
        IDS.add(ing["item"])

missing = [i for i in sorted(IDS) if not i.startswith("minecraft:") and i not in have]
print("checked", len(IDS), "ids")
print("missing:", missing)
