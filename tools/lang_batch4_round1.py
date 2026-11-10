# -*- coding: utf-8 -*-
"""第四批第一轮：《黄帝内经》《国富论》的说明文字与 JEI 详细页。"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang", "zh_cn.json")

UPDATES = {
    "tooltip.yanscriptures.classic.inner_canon_of_the_yellow_emperor.effect1":
        "受到的伤害按「当前生命百分比 - 50%」减免：满血时减半，半血及以下不减。",
    "tooltip.yanscriptures.classic.inner_canon_of_the_yellow_emperor.effect2":
        "每 30 秒按木、火、土、金、水轮换：生命上限 +15% / 攻击 +15% / 不会被击退 / 护甲 +15% / 移动与攻击速度 +15%。",
    "jei.yanscriptures.book.inner_canon_of_the_yellow_emperor.detail.1":
        "· 伤害减免 = 当前生命百分比 - 50%（不满血越多减得越多，半血及以下没有减免）。",
    "jei.yanscriptures.book.inner_canon_of_the_yellow_emperor.detail.2":
        "· 减免在受到伤害时结算，和护甲、抗性叠加计算。",
    "jei.yanscriptures.book.inner_canon_of_the_yellow_emperor.detail.3":
        "· 五行每 30 秒轮换一次，顺序固定为木 → 火 → 土 → 金 → 水。",
    "jei.yanscriptures.book.inner_canon_of_the_yellow_emperor.detail.4":
        "· 轮换按世界时间走：中途戴上时会直接接上当前这一行，不从木重新开始。",
    "tooltip.yanscriptures.classic.wealth_of_nations.effect1":
        "快捷栏与物品栏里每 11 种不同的物品换来 1 点财富（最多 3 点）：1 点经验 +20%，2 点单次受伤不超过生命上限的 80%，3 点每次攻击附带 30% 伤害的小范围追击。",
    "tooltip.yanscriptures.classic.wealth_of_nations.effect2":
        "财富较高时低档效果依然生效；当前财富数量直接显示在物品介绍里。",
    "jei.yanscriptures.book.wealth_of_nations.detail.1":
        "· 财富只看快捷栏（9 格）与物品栏（27 格），装备栏与副手不算。",
    "jei.yanscriptures.book.wealth_of_nations.detail.2":
        "· 按物品 id 计数：同一个 id 无论占几格都只算 1 种。",
    "jei.yanscriptures.book.wealth_of_nations.detail.3":
        "· 11 种 = 1 点，22 种 = 2 点，33 种及以上 = 3 点。",
    "jei.yanscriptures.book.wealth_of_nations.detail.4":
        "· 三种效果同时生效，不会互相覆盖。",
}


def main():
    with io.open(LANG, encoding="utf-8") as f:
        data = json.load(f)
    before = len(data)
    for key, value in UPDATES.items():
        data[key] = value
    with io.open(LANG, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("lang keys:", before, "->", len(data))


if __name__ == "__main__":
    main()
