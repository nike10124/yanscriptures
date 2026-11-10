# -*- coding: utf-8 -*-
"""按中文名在 libs 各模组 lang 里反查 id。"""
import glob
import io
import json
import os
import sys
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\MCModDev\forge-mdk"
KEYS = ["紫水晶加固", "破碎之环", "大地核心", "黄金魔翎", "海洋锭", "纯净之星", "邪恶之石",
        "物品描述", "黑曜石之魂", "灵魂红宝石", "浓缩绿宝石", "烈焰之核", "邪恶精髓", "火棘果",
        "书页", "空白"]

hits = {}
for jar in glob.glob(os.path.join(ROOT, "libs", "*.jar")):
    try:
        zf = zipfile.ZipFile(jar)
    except Exception:
        continue
    with zf:
        for entry in zf.namelist():
            if not entry.endswith("lang/zh_cn.json"):
                continue
            ns = entry.split("/")[1]
            try:
                data = json.loads(zf.read(entry).decode("utf-8-sig"))
            except Exception:
                continue
            for key, value in data.items():
                if not isinstance(value, str):
                    continue
                for need in KEYS:
                    if need in value:
                        hits.setdefault(need, []).append(
                            ns + ":" + key.split(".", 2)[-1] + "  =  " + value)

for need in KEYS:
    print("##", need)
    for line in sorted(set(hits.get(need, [])))[:40]:
        print("   ", line)
