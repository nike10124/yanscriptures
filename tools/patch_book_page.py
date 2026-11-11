# -*- coding: utf-8 -*-
"""把 BookPageOpener 里的标题与提示换成语言键。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"src\main\java\com\example\examplemod\client\BookPageOpener.java"
s = open(p, encoding="utf-8").read()

rep = [
    ('Component.literal("《十 诫》")', 'Component.translatable("gui.yanscriptures.book_page.commandments_title")'),
    ('Component.literal("守约者受祝福，违逆者受诅咒")', 'Component.translatable("gui.yanscriptures.book_page.commandments_sub")'),
    ('Component.literal("§6§l▍ 十二权柄")', 'Component.translatable("gui.yanscriptures.book_page.powers")'),
    ('Component.literal("§6§l▍ 典章明细")', 'Component.translatable("gui.yanscriptures.book_page.details")'),
    ('Component.literal("《圣 约 · 原 典》")', 'Component.translatable("gui.yanscriptures.book_page.covenant_title")'),
    ('Component.literal("《如 我 所 书》")', 'Component.translatable("gui.yanscriptures.book_page.book_of_me_title")'),
    ('Component.literal("一段史诗所结出的果实，其中闪耀着十四种光辉")', 'Component.translatable("gui.yanscriptures.book_page.book_of_me_sub")'),
]

n = 0
for a, b in rep:
    if a in s:
        s = s.replace(a, b)
        n += 1
    else:
        print("MISS", a)

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("replaced", n)
