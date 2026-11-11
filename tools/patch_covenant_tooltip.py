# -*- coding: utf-8 -*-
"""ScriptureOfCovenantItem 的摘要与翻页提示换成语言键。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"src\main\java\com\example\examplemod\ScriptureOfCovenantItem.java"
s = open(p, encoding="utf-8").read()

rep = [
    ('tooltip.add(Component.literal("§8第 " + page + " / " + pages + " 屏　·　滚轮翻阅")\n                .withStyle(ChatFormatting.DARK_GRAY));',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.page_hint", page, pages)\n                .withStyle(ChatFormatting.DARK_GRAY));'),
    ('tooltip.add(Component.literal("§6§l✦  圣 约 · 原 典  ✦"));',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.title"));'),
    ('tooltip.add(Component.literal("§7旧约已成，新约自血与火中订立；十诫归一，七印俱开。"));',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.line1"));'),
    ('tooltip.add(Component.literal("§8「我是阿尔法，我是欧米伽；是初，是终。」")',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.line2")'),
    ('tooltip.add(Component.literal("§e[Shift] §7权柄经文　§e[Shift+Alt] §7完整书页"));',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.keys"));'),
    ('tooltip.add(Component.literal("§8K 脱困 · Z 日影停住 · J 七碗倾倒 · F 飞行"));',
     'tooltip.add(Component.translatable("tooltip.yanscriptures.covenant.hotkeys"));'),
]

n = 0
for a, b in rep:
    if a in s:
        s = s.replace(a, b)
        n += 1
    else:
        print("MISS", a[:40])

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("replaced", n)
