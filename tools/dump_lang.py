# -*- coding: utf-8 -*-
"""按前缀与区间打印 zh_cn.json 里尚未翻译的键值，便于分批翻译。"""
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = r"D:\MCModDev\forge-mdk"
zh = json.load(open(BASE + r"\src\main\resources\assets\yanscriptures\lang\zh_cn.json", encoding="utf-8"))
try:
    en = json.load(open(BASE + r"\src\main\resources\assets\yanscriptures\lang\en_us.json", encoding="utf-8"))
except FileNotFoundError:
    en = {}

prefix = sys.argv[1]
start = int(sys.argv[2])
end = int(sys.argv[3])

todo = [k for k in zh if k.startswith(prefix) and k not in en]
print("TODO", prefix, len(todo))
for k in todo[start:end]:
    print(k + "\t" + zh[k])
