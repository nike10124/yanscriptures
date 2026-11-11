# -*- coding: utf-8 -*-
"""第 3 批语言键：BookStateTooltip 的动态提示。"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk"
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang")

pairs = {
    "tooltip.yanscriptures.state.wealth": (
        "财富：%s / %s（物品栏内 %s 种不同的物品，每 %s 种 +1）",
        "Wealth: %s / %s (%s distinct items in your inventory, +1 per %s)"),
    "tooltip.yanscriptures.state.element": (
        "当前：%s（%s，剩 %s 秒轮换）",
        "Now: %s (%s, rotates in %s s)"),
    "tooltip.yanscriptures.state.enchant_points": (
        "附魔点球：%s（伤害 +%s%%，生命上限 +%s%%）",
        "Enchant points: %s (damage +%s%%, max health +%s%%)"),
    "tooltip.yanscriptures.state.flight.on": (
        "飞行：已获得（带附魔 %s 格）",
        "Flight: granted (%s enchanted slots)"),
    "tooltip.yanscriptures.state.flight.off": (
        "飞行：带附魔 %s / 需 > 21 格",
        "Flight: %s enchanted slots / needs more than 21"),
    "tooltip.yanscriptures.state.flight.off_extra": (
        "飞行：带附魔 %s / 需 > 21 格，另有 %s 格没附魔",
        "Flight: %s enchanted slots / needs more than 21, plus %s unenchanted"),
    "tooltip.yanscriptures.state.nights": (
        "累计成长：%s / %s（每次睡满一夜 +1，随机加到一项属性上）",
        "Growth: %s / %s (+1 per full night's sleep, applied to a random attribute)"),
    "tooltip.yanscriptures.state.marks": ("印记：", "Marks: "),
    "tooltip.yanscriptures.state.mark.son": ("［子］", "[Son]"),
    "tooltip.yanscriptures.state.mark.father": ("［父］", "[Father]"),
    "tooltip.yanscriptures.state.mark.spirit": ("［灵］", "[Spirit]"),
}

for name, idx in (("zh_cn.json", 0), ("en_us.json", 1)):
    path = os.path.join(LANG, name)
    data = json.load(open(path, encoding="utf-8"))
    for key, val in pairs.items():
        data[key] = val[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2, sort_keys=True)
        out.write("\n")
    print(name, len(data))
