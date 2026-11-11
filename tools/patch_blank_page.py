# -*- coding: utf-8 -*-
"""BlankPageKeyHandler 的提示换成语言键。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"src\main\java\com\example\examplemod\BlankPageKeyHandler.java"
s = open(p, encoding="utf-8").read()

rep = [
    ('net.minecraft.network.chat.Component.literal("冷却中，剩余 " + remaining + " 秒")',
     'net.minecraft.network.chat.Component.translatable("message.yanscriptures.blank_page.cooling", remaining)'),
    ('net.minecraft.network.chat.Component.literal("状态已恢复！")',
     'net.minecraft.network.chat.Component.translatable("message.yanscriptures.blank_page.restored")'),
    ('net.minecraft.network.chat.Component.literal("可用附魔不足 10 种，无法记录！")',
     'net.minecraft.network.chat.Component.translatable("message.yanscriptures.blank_page.not_enough")'),
    ('net.minecraft.network.chat.Component.literal("消耗 10 种附魔，当前属性加成将降低！")',
     'net.minecraft.network.chat.Component.translatable("message.yanscriptures.blank_page.consume")'),
    ('net.minecraft.network.chat.Component.literal("状态已记录！")',
     'net.minecraft.network.chat.Component.translatable("message.yanscriptures.blank_page.recorded")'),
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
