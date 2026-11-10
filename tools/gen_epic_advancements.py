# -*- coding: utf-8 -*-
"""生成成就：14 本史诗书各一条 + 收集齐全部书籍一条（父节点都是 epic）。"""
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = r"D:\MCModDev\forge-mdk"
ADV = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "advancements")

EPIC = [
    "republic", "don_quixote", "euclid", "origin", "materia_medica",
    "interpretation_of_dreams", "gilgamesh", "zhuangzi", "records_of_the_historian",
    "thirty_six_strategies", "on_war", "de_revolutionibus",
    "critique_of_pure_reason", "summa_theologica",
]

books_tag = json.load(open(os.path.join(
    ROOT, "src", "main", "resources", "data", "yanscriptures", "tags", "items", "books.json"),
    encoding="utf-8"))
ids = [v if isinstance(v, str) else v["id"] for v in books_tag["values"]]
collect = [i for i in ids if not i.endswith("blank_page")]
print("collect", len(collect))


def write(name, data):
    path = os.path.join(ADV, name)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write("\n")


for ident in EPIC:
    item = "yanscriptures:" + ident
    write("epic_book_%s.json" % ident, {
        "parent": "yanscriptures:epic",
        "display": {
            "icon": {"item": item},
            "title": {"translate": "item.yanscriptures." + ident},
            "description": {"translate": "advancement.yanscriptures.epic_book.desc"},
            "frame": "goal",
            "show_toast": True,
            "announce_to_chat": True,
            "hidden": False,
        },
        "criteria": {
            "got": {
                "trigger": "minecraft:inventory_changed",
                "conditions": {"items": [{"items": [item]}]},
            }
        },
    })

criteria = {}
requirements = []
for i, full in enumerate(collect):
    key = "b%02d" % i
    criteria[key] = {
        "trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": [full]}]},
    }
    requirements.append([key])

write("collect_all.json", {
    "parent": "yanscriptures:epic",
    "display": {
        "icon": {"item": "yanscriptures:covenant_codex"},
        "title": {"translate": "advancement.yanscriptures.collect_all.title"},
        "description": {"translate": "advancement.yanscriptures.collect_all.desc"},
        "frame": "challenge",
        "show_toast": True,
        "announce_to_chat": True,
        "hidden": True,
    },
    "criteria": criteria,
    "requirements": requirements,
})

LANGS = {
    "zh_cn.json": {
        "advancement.yanscriptures.epic_book.desc": "获得这本史诗级名著。",
        "advancement.yanscriptures.collect_all.title": "百家争鸣",
        "advancement.yanscriptures.collect_all.desc": "收集齐所有书籍。",
    },
    "en_us.json": {
        "advancement.yanscriptures.epic_book.desc": "Obtain this epic classic.",
        "advancement.yanscriptures.collect_all.title": "A Hundred Schools of Thought",
        "advancement.yanscriptures.collect_all.desc": "Collect every book in the mod.",
    },
}

for name, add in LANGS.items():
    path = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang", name)
    data = json.load(open(path, encoding="utf-8"))
    data.update(add)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(data, out, ensure_ascii=False, indent=2, sort_keys=True)
        out.write("\n")
    print(name, len(data))

print("ok")
