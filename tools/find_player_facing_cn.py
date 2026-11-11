# -*- coding: utf-8 -*-
"""只看「玩家能看到」的硬编码中文：literal / displayClientMessage / sendSystemMessage / hover / tooltip。"""
import io
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk\src\main\java"
cn = re.compile(r"[\u4e00-\u9fff]")
lit = re.compile(r'"((?:[^"\\]|\\.)*)"')
KEYS = ("Component.literal", "displayClientMessage", "sendSystemMessage", "withStyle",
        "getHoverName", "tooltip", "append", "TranslatableComponent", "sendSuccess", "sendFailure")

hits = []
for base, _, files in os.walk(ROOT):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(base, name)
        for no, line in enumerate(open(path, encoding="utf-8"), 1):
            code = line.split("//")[0]
            if not any(k in code for k in KEYS):
                continue
            for m in lit.finditer(code):
                if cn.search(m.group(1)):
                    hits.append((os.path.relpath(path, ROOT), no, m.group(1)))

print("total", len(hits))
per = {}
for path, no, text in hits:
    per[path] = per.get(path, 0) + 1
print("--- per file ---")
for path, n in sorted(per.items(), key=lambda kv: -kv[1]):
    print(n, path)
print("--- detail ---")
for path, no, text in hits:
    print("%s:%d\t%s" % (path, no, text))
