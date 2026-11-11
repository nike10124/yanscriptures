# -*- coding: utf-8 -*-
"""找出 Java 源码里「字符串字面量中含中文」的位置（注释里的中文不算）。"""
import io
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk\src\main\java"
cn = re.compile(r"[\u4e00-\u9fff]")
lit = re.compile(r'"((?:[^"\\]|\\.)*)"')

hits = []
for base, _, files in os.walk(ROOT):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(base, name)
        for no, line in enumerate(open(path, encoding="utf-8"), 1):
            code = line.split("//")[0] if "//" in line else line
            for m in lit.finditer(code):
                if cn.search(m.group(1)):
                    hits.append((os.path.relpath(path, ROOT), no, m.group(1)))

print("total", len(hits))
for path, no, text in hits:
    print("%s:%d\t%s" % (path, no, text))
