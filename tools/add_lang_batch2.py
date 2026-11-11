# -*- coding: utf-8 -*-
"""语言外置第 2 批：TitanEpicItem / BlankPageKeyHandler / BookPageOpener / ScriptureOfCovenantItem 的提示文本。"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk"
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang")

pairs = {
    "message.yanscriptures.cooldown.seconds": ("§c冷却中，剩余 %s 秒", "§cOn cooldown, %s s remaining"),
    "message.yanscriptures.titan.fate": ("§d【命运的史诗】因果逆转已激活。", "§d[Epic of Fate] Karmic Reversal activated."),
    "message.yanscriptures.titan.creation": ("§a【创生的史诗】生息之环", "§a[Epic of Creation] Ring of Vitality"),
    "message.yanscriptures.titan.pillar": ("§b【支柱的史诗】地脉锚定", "§b[Epic of the Pillar] Leyline Anchor"),
    "message.yanscriptures.titan.calamity": ("§4【灾厄的史诗】祸乱之种", "§4[Epic of Calamity] Seed of Ruin"),
    "message.yanscriptures.titan.ego": ("§5【真我的史诗】自我觉醒", "§5[Epic of the Self] Self-Awakening"),
    "message.yanscriptures.blank_page.cooling": ("冷却中，剩余 %s 秒", "On cooldown, %s s remaining"),
    "message.yanscriptures.blank_page.restored": ("状态已恢复！", "State restored!"),
    "message.yanscriptures.blank_page.not_enough": ("可用附魔不足 10 种，无法记录！", "Fewer than 10 available enchantments — cannot record!"),
    "message.yanscriptures.blank_page.consume": ("消耗 10 种附魔，当前属性加成将降低！", "Consumed 10 enchantments — attribute bonus decreases!"),
    "message.yanscriptures.blank_page.recorded": ("状态已记录！", "State recorded!"),
    "gui.yanscriptures.book_page.covenant_title": ("《圣 约 · 原 典》", "THE COVENANT"),
    "gui.yanscriptures.book_page.commandments_title": ("《十 诫》", "THE TEN COMMANDMENTS"),
    "gui.yanscriptures.book_page.commandments_sub": ("守约者受祝福，违逆者受诅咒", "Blessed are those who keep it, cursed are those who break it"),
    "gui.yanscriptures.book_page.powers": ("§6§l▍ 十二权柄", "§6§l▍ The Twelve Powers"),
    "gui.yanscriptures.book_page.details": ("§6§l▍ 典章明细", "§6§l▍ Articles in Detail"),
    "gui.yanscriptures.book_page.book_of_me_title": ("《如 我 所 书》", "AS I HAVE WRITTEN"),
    "gui.yanscriptures.book_page.book_of_me_sub": ("一段史诗所结出的果实，其中闪耀着十四种光辉", "The fruit of an epic, gleaming with fourteen kinds of radiance"),
    "tooltip.yanscriptures.covenant.page_hint": ("§8第 %s 屏　·　滚轮翻阅", "§8Page %s　·　scroll to browse"),
    "tooltip.yanscriptures.covenant.title": ("§6§l✦  圣 约 · 原 典  ✦", "§6§l✦  THE COVENANT  ✦"),
    "tooltip.yanscriptures.covenant.line1": ("§7旧约已成，新约自血与火中订立；十诫归一，七印俱开。", "§7The old covenant is fulfilled; the new is sealed in blood and fire. Ten commandments made one, seven seals all broken."),
    "tooltip.yanscriptures.covenant.line2": ("§8「我是阿尔法，我是欧米伽；是初，是终。」", "§8\"I am Alpha and Omega, the beginning and the end.\""),
    "tooltip.yanscriptures.covenant.keys": ("§e[Shift] §7权柄经文　§e[Shift+Alt] §7完整书页", "§e[Shift] §7Powers of the Covenant　§e[Shift+Alt] §7Full page"),
    "tooltip.yanscriptures.covenant.hotkeys": ("§8K 脱困 · Z 日影停住 · J 七碗倾倒 · F 飞行", "§8K Break Free · Z Sun Stand Still · J Seven Bowls · F Flight"),
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
