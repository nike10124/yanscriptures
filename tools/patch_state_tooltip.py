# -*- coding: utf-8 -*-
"""BookStateTooltip 的动态提示外置（第 3 批）。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

p = r"src\main\java\com\example\examplemod\client\BookStateTooltip.java"
s = open(p, encoding="utf-8").read()

rep = [
    ('''tooltip.add(Component.literal("财富：" + wealth + " / " + WealthOfNationsHandler.MAX_WEALTH
                    + "（物品栏内 " + distinct + " 种不同的物品，每 "
                    + WealthOfNationsHandler.ITEMS_PER_WEALTH + " 种 +1）")
                    .withStyle(ChatFormatting.GOLD));''',
     '''tooltip.add(Component.translatable("tooltip.yanscriptures.state.wealth",
                    wealth, WealthOfNationsHandler.MAX_WEALTH, distinct,
                    WealthOfNationsHandler.ITEMS_PER_WEALTH)
                    .withStyle(ChatFormatting.GOLD));'''),
    ('''tooltip.add(Component.literal("当前：" + InnerCanonHandler.ELEMENTS[element] + "（"
                    + InnerCanonHandler.bonusText(element) + "，剩 " + seconds + " 秒轮换）")
                    .withStyle(ChatFormatting.GREEN));''',
     '''tooltip.add(Component.translatable("tooltip.yanscriptures.state.element",
                    InnerCanonHandler.ELEMENTS[element], InnerCanonHandler.bonusText(element), seconds)
                    .withStyle(ChatFormatting.GREEN));'''),
    ('''tooltip.add(Component.literal("附魔点球：" + points + "（伤害 +"
                    + trim(points * 0.5D) + "%，生命上限 +" + trim(points * 1.0D) + "%）")
                    .withStyle(ChatFormatting.AQUA));''',
     '''tooltip.add(Component.translatable("tooltip.yanscriptures.state.enchant_points",
                    points, trim(points * 0.5D), trim(points * 1.0D))
                    .withStyle(ChatFormatting.AQUA));'''),
    ('''tooltip.add(Component.literal("飞行：已获得（带附魔 " + enchanted + " 格）")
                        .withStyle(ChatFormatting.GREEN));''',
     '''tooltip.add(Component.translatable("tooltip.yanscriptures.state.flight.on", enchanted)
                        .withStyle(ChatFormatting.GREEN));'''),
    ('''tooltip.add(Component.literal("飞行：带附魔 " + enchanted + " / 需 > 21 格"
                        + (plain > 0 ? "，另有 " + plain + " 格没附魔" : ""))
                        .withStyle(ChatFormatting.DARK_GRAY));''',
     '''tooltip.add((plain > 0
                        ? Component.translatable("tooltip.yanscriptures.state.flight.off_extra", enchanted, plain)
                        : Component.translatable("tooltip.yanscriptures.state.flight.off", enchanted))
                        .withStyle(ChatFormatting.DARK_GRAY));'''),
    ('''tooltip.add(Component.literal("累计成长：" + total + " / " + OneThousandAndOneNightsHandler.CAP
                    + "（每次睡满一夜 +1，随机加到一项属性上）").withStyle(ChatFormatting.DARK_PURPLE));''',
     '''tooltip.add(Component.translatable("tooltip.yanscriptures.state.nights",
                    total, OneThousandAndOneNightsHandler.CAP).withStyle(ChatFormatting.DARK_PURPLE));'''),
    ('MutableComponent line = Component.literal("印记：").withStyle(ChatFormatting.GRAY);',
     'MutableComponent line = Component.translatable("tooltip.yanscriptures.state.marks").withStyle(ChatFormatting.GRAY);'),
    ('line.append(Component.literal("［子］").withStyle(',
     'line.append(Component.translatable("tooltip.yanscriptures.state.mark.son").withStyle('),
    ('line.append(Component.literal("［父］").withStyle(',
     'line.append(Component.translatable("tooltip.yanscriptures.state.mark.father").withStyle('),
    ('line.append(Component.literal("［灵］").withStyle(',
     'line.append(Component.translatable("tooltip.yanscriptures.state.mark.spirit").withStyle('),
]

n = 0
for a, b in rep:
    if a in s:
        s = s.replace(a, b)
        n += 1
    else:
        print("MISS", a[:50].replace("\n", " "))

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("replaced", n)
