# -*- coding: utf-8 -*-
"""把《如我所书》的 25 行长描述搬进语言文件（中英）。"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk"
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang")

zh = [
    "【永恒的诗篇】",
    "已记录附魔恒定为 093，每个附魔 +1 最大生命值，+1 攻击力，+0.5 幸运值",
    "按 V 记录当前状态，再次按 V 进行回溯，冷却 180 秒",
    "【给予命运之诗】",
    "[岁月] 濒死时回复 70% 生命值，对半径 7 格内敌人造成 2 倍当前回复量的虚空伤害",
    "[门径] 禁用半径 10 格内的传送事件；按 K 沿视线传送到 8~16 格外（濒死时自动传至 16 格内安全位置）",
    "[律法] 生物试图伤害你时，损失其 5% 最大生命值并禁止回血",
    "【给予支柱之诗】",
    "[大地] 2 秒内至多受到一次伤害且不超过最大生命值的 20%，免疫大地伤害",
    "[天空] 飞行时获得 60% 伤害减免，其他情况 30%，免疫天空伤害",
    "[海洋] 水中获得生命恢复 IV，其他情况生命恢复 II，免疫海洋伤害",
    "【给予创生之诗】",
    "[负世] 免疫负面效果，无视秒杀",
    "[理性] 长按 Shift+L 使手中物品获得可获得的最高级附魔，冷却 10 秒",
    "[浪漫] 移速提升 30%（独立乘区），受击后造成 200% 反伤并抹除攻击者心智 2 秒",
    "【给予灾厄之诗】",
    "[纷争] 攻击削减敌人 8% 最大生命值，并施加凋零、缓慢",
    "[死亡] 每击杀一个生物获得 1% 独立全伤，上限 88%",
    "[诡计] 你杀死的生物掉落更多掉落物；有 20% 概率造成 2 倍伤害",
    "【给予真我之诗】",
    "创造飞行，飞行速度 +100%，所有属性 ×2，无视禁疗，88% 概率免疫死亡",
    "当你触发本饰品的任意保命效果时，获得 60 秒无敌",
    "按 Z 键暂停时间 20 秒，冷却 300 秒",
    "【隐藏效果】",
    "获得饱食 II、抗性提升 II、夜视 II、迅捷 II、急迫 II、发光 II、力量 II、幸运 V、潮涌能量 II、村庄英雄 V、抗火、跳跃提升 II。",
]

en = [
    "[Poem of Eternity]",
    "Recorded enchantment count is fixed at 093: each enchantment grants +1 max health, +1 attack damage and +0.5 luck.",
    "Press V to record your current state, press V again to rewind to it. 180 second cooldown.",
    "[Poem of Fate]",
    "[Years] Below 10% health, heal 70% and deal void damage equal to twice the healing to enemies within 7 blocks.",
    "[Gateway] Teleports within 10 blocks are disabled; press K to blink 8~16 blocks along your view (automatically to a safe spot within 16 blocks when near death).",
    "[Law] A creature that tries to hurt you loses 5% of its max health and cannot regenerate.",
    "[Poem of the Pillar]",
    "[Earth] Take at most one hit every 2 seconds and never more than 20% of max health; immune to earth damage.",
    "[Sky] 60% damage reduction while flying, 30% otherwise; immune to sky damage.",
    "[Sea] Regeneration IV while in water, Regeneration II otherwise; immune to sea damage.",
    "[Poem of Creation]",
    "[World-Bearing] Immune to harmful effects and to instant kills.",
    "[Reason] Hold Shift + L to give the held item the highest enchantment it can take. 10 second cooldown.",
    "[Romance] +30% movement speed (separate multiplier); when hit, reflect 200% damage and stun the attacker for 2 seconds.",
    "[Poem of Calamity]",
    "[Strife] Attacks remove 8% of the target's max health and apply Wither and Slowness.",
    "[Death] Each kill grants +1% separate damage, up to 88%.",
    "[Trickery] Creatures you kill drop more loot; 20% chance to deal double damage.",
    "[Poem of the Self]",
    "Creative flight, +100% flight speed, all attributes ×2, ignores healing prevention, 88% chance to ignore death.",
    "Whenever a survival effect of this charm triggers, gain 60 seconds of invulnerability.",
    "Press Z to stop time for 20 seconds. 300 second cooldown.",
    "[Hidden Effect]",
    "Grants Saturation II, Resistance II, Night Vision II, Speed II, Haste II, Glowing II, Strength II, Luck V, Conduit Power II, Hero of the Village V, Fire Resistance and Jump Boost II.",
]

for name, lines in (("zh_cn.json", zh), ("en_us.json", en)):
    path = os.path.join(LANG, name)
    data = json.load(open(path, encoding="utf-8"))
    for i, line in enumerate(lines, 1):
        data["tooltip.yanscriptures.book_of_me.full.%d" % i] = line
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2, sort_keys=True)
        out.write("\n")
    print(name, len(data))
