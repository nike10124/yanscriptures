# -*- coding: utf-8 -*-
"""修掉追加章节里被转义吃掉的字母（\\f / \\t / \\a 变成了控制字符）。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\docs\模组内容总览.md"
text = open(PATH, encoding="utf-8").read()

fixes = {
    "\x0calse": "false",
    "\x09rue": "true",
    "\x0cirst_book": "first_book",
    "\x07nnihilated": "annihilated",
    "\x0c": "f",
    "\x09": "t",
    "\x07": "a",
}
for bad, good in fixes.items():
    text = text.replace(bad, good)

open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok")
