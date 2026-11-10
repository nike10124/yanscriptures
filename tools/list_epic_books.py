# -*- coding: utf-8 -*-
"""列出名著 id 与稀有度（EPIC 的用于生成成就）。"""
import io
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\src\main\java\com\example\examplemod\ClassicBook.java"
text = open(PATH, encoding="utf-8").read()

pattern = re.compile(r'(\w+)\("([a-z_0-9]+)",\s*Category\.(\w+)(?:,\s*Rarity\.(\w+))?')
epic = []
all_books = []
for m in pattern.finditer(text):
    name, ident, cat, rar = m.group(1), m.group(2), m.group(3), m.group(4)
    if ident == "id":
        continue
    all_books.append((ident, rar or "COMMON"))
    if (rar or "") == "EPIC":
        epic.append(ident)

print("total", len(all_books))
print("epic", len(epic))
print(",".join(epic))
print("all", ",".join(i for i, _ in all_books))
