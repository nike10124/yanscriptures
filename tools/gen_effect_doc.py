# -*- coding: utf-8 -*-
"""从 lang 与源码生成「饰品效果一览」（只含效果，不含实现/获取/设计说明）。"""
import json
import re

ROOT = r"D:\MCModDev\forge-mdk"
LANG = ROOT + r"\src\main\resources\assets\yanscriptures\lang\zh_cn.json"
BOOK = ROOT + r"\src\main\java\com\example\examplemod\ClassicBook.java"
ME = ROOT + r"\src\main\java\com\example\examplemod\BookOfMeItem.java"
DOC = ROOT + r"\docs\模组内容总览.md"
OUT = ROOT + r"\docs\饰品效果一览.md"

lang = json.loads(open(LANG, encoding="utf-8").read())

CAT = {
    "PHILOSOPHY": "哲学", "LITERATURE": "文学", "SCIENCE": "理科", "HISTORY": "史书",
    "MILITARY": "军事", "POLITICS": "政治", "MEDICINE": "医学", "CRAFT": "工艺",
    "PSYCHOLOGY": "心理", "RELIGION": "宗教",
}
CAT_ORDER = ["哲学", "文学", "理科", "史书", "军事", "政治", "医学", "工艺", "心理", "宗教"]


def clean(text):
    text = re.sub(r"§.", "", text)
    text = re.sub(r"^【[^】]*】\s*", "", text)
    text = re.sub(r"%\d*\$?s", "N", text)
    text = text.replace("%%", "%")
    return text.strip()


def item_name(bid):
    return lang.get("item.yanscriptures." + bid, bid)


def effects(bid):
    out = []
    for suffix in ("effect1", "effect2", "effect3"):
        key = "tooltip.yanscriptures.classic.%s.%s" % (bid, suffix)
        if key in lang:
            line = clean(lang[key])
            if line:
                out.append(line)
    return out


book_src = open(BOOK, encoding="utf-8").read()
pairs = re.findall(r'\(\s*"([a-z_]+)"\s*,\s*(?:Category\.)?([A-Z_]+)', book_src)

groups = {}
for bid, cat in pairs:
    groups.setdefault(CAT.get(cat, cat), []).append(bid)

lines = []
lines.append("# 饰品效果一览")
lines.append("")
lines.append("本文件只列饰品效果，不含获取方式、设计思路与实现细节。数值为默认值。")
lines.append("")
lines.append("## 一、名著")
lines.append("")
for cat in CAT_ORDER:
    if cat not in groups:
        continue
    lines.append("### " + cat)
    lines.append("")
    for bid in groups[cat]:
        lines.append("**" + item_name(bid) + "**")
        lines.append("")
        for line in effects(bid):
            lines.append("- " + line)
        lines.append("")

lines.append("## 二、史诗")
lines.append("")
EPICS = [
    ("epic_fate", "命运的史诗"),
    ("epic_pillar", "支柱的史诗"),
    ("epic_creation", "创生的史诗"),
    ("epic_calamity", "灾厄的史诗"),
    ("epic_ego", "真我的史诗"),
]
for bid, fallback in EPICS:
    lines.append("**" + lang.get("item.yanscriptures." + bid, fallback) + "**")
    lines.append("")
    for suffix in ("desc", "desc1", "desc2", "desc3"):
        key = "tooltip.yanscriptures.%s.%s" % (bid, suffix)
        if key in lang:
            lines.append("- " + clean(lang[key]))
    cd = lang.get("tooltip.yanscriptures.%s.cooldown" % bid)
    if cd:
        lines.append("- " + clean(cd))
    lines.append("")

lines.append("## 三、其他饰品")
lines.append("")

lines.append("**《方块与和平》**")
lines.append("")
for suffix in ("attack", "defense"):
    key = "tooltip.yanscriptures.book_of_peace." + suffix
    if key in lang:
        lines.append("- " + clean(lang[key]))
lines.append("")

lines.append("**空白的书页**")
lines.append("")
for suffix in ("desc", "how_to_record", "available", "bonus", "state_hint"):
    key = "tooltip.yanscriptures.blank_page." + suffix
    if key in lang:
        lines.append("- " + clean(lang[key]))
lines.append("")

lines.append("**《自然哲学的数学原理》**")
lines.append("")
for suffix in ("desc", "percent_bonus", "fixed_bonus", "max", "perfect", "ultimate"):
    key = "tooltip.yanscriptures.principia." + suffix
    if key in lang:
        lines.append("- " + clean(lang[key]))
lines.append("")

lines.append("**《翠玉录》**")
lines.append("")
for suffix in ("desc", "magic"):
    key = "tooltip.yanscriptures.emerald_tablet." + suffix
    if key in lang:
        lines.append("- " + clean(lang[key]))
lines.append("")

lines.append("**空书架**")
lines.append("")
lines.append("- " + clean(lang.get("tooltip.yanscriptures.empty_bookshelf.desc", "一座空着的小书架。")))
lines.append("- 佩戴时书籍栏位 +2。")
lines.append("")

lines.append("**如我所书**")
lines.append("")
me_src = open(ME, encoding="utf-8").read()
literals = re.findall(r'"((?:[^"\\]|\\.)*)"', me_src)
seen = set()
for lit in literals:
    if not re.search(r"[\u4e00-\u9fff]", lit):
        continue
    if "如我所书" in lit or "完整描述" in lit or "一段史诗" in lit or "其中闪耀着" in lit:
        continue
    text = clean(lit)
    if not text or text in seen:
        continue
    seen.add(text)
    lines.append("- " + text)
lines.append("")

lines.append("**圣典·戒律（十诫）**")
lines.append("")
doc = open(DOC, encoding="utf-8").read()
table_lines = []
capture = False
for raw in doc.splitlines():
    if raw.strip().startswith("| # | 诫条"):
        capture = True
    if capture:
        if raw.strip().startswith("|"):
            table_lines.append(raw.rstrip())
        elif table_lines:
            break
if table_lines:
    lines.extend(table_lines)
    lines.append("")

lines.append("**《圣约·原典》**")
lines.append("")
lines.append("佩戴后开启七印，并逐条生效下列十二权柄：")
lines.append("")
POWERS = [
    ("［万邦止戈］", "生物不再把你视作目标；你主动攻击的那只会还手。"),
    ("［不死之约］", "生命被钉在 1 点以上，每秒回复最大生命的 7%。"),
    ("［日影停住］", "按 Z 停住时间 7 秒（半径 64 格），冷却 30 秒。"),
    ("［七碗倾倒］", "按 J 发动毁灭权柄，持续 7 秒，冷却 30 秒。"),
    ("［不可近身］", "免疫负面效果与禁疗；伤害由特殊生命吸收、777% 反伤、7 秒无敌帧、创造飞行。"),
    ("［申命之赐］", "七级恩典：授予多种有益效果；违逆则降灾。"),
    ("［万军之磐石］", "777 点特殊生命，另加 777 点护甲、幸运、攻击与韧性。"),
    ("［复活在我］", "永久图腾：死亡事件直接取消，满血复起。"),
    ("［万军之约］", "击杀生物使你的力量持续成长。"),
    ("［五饼二鱼］", "击杀生物时拾取它的全部可能掉落，且数量翻倍。"),
    ("［云柱火柱］", "按 K 沿视线脱困传送（濒死时自动传至安全位置）。"),
    ("［圣所］", "同伴受到的伤害由你承担 77%。"),
]
for title, text in POWERS:
    lines.append("- " + title + " " + text)
lines.append("")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines).rstrip() + "\n")
print("written", OUT, "lines:", len(lines))
