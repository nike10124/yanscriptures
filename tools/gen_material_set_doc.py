# -*- coding: utf-8 -*-
"""生成 docs/一次性材料组.md：一套材料最多能连做多少种不同的书。"""
import glob
import io
import json
import os
import sys
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
ROOT = r"D:\MCModDev\forge-mdk"
REC = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "book_writing")
OUT = os.path.join(ROOT, "docs", "一次性材料组.md")

# 1) 汇总所有模组的中文名
NAMES = {}
for jar in glob.glob(os.path.join(ROOT, "libs", "*.jar")):
    try:
        zf = zipfile.ZipFile(jar)
    except Exception:
        continue
    with zf:
        for entry in zf.namelist():
            if not entry.endswith("lang/zh_cn.json"):
                continue
            ns = entry.split("/")[1]
            try:
                data = json.loads(zf.read(entry).decode("utf-8-sig"))
            except Exception:
                continue
            for key, value in data.items():
                if not isinstance(value, str):
                    continue
                if key.startswith("item.") or key.startswith("block."):
                    NAMES[(ns, key.split(".")[-1])] = value
for path in glob.glob(os.path.join(ROOT, "src", "main", "resources", "assets", "*", "lang", "zh_cn.json")):
    ns = path.split(os.sep)[-3]
    for key, value in json.load(open(path, encoding="utf-8")).items():
        if (key.startswith("item.") or key.startswith("block.")) and isinstance(value, str):
            NAMES[(ns, key.split(".")[-1])] = value


VANILLA = {
    "leather": "皮革", "gold_ingot": "金锭", "bone": "骨头", "ender_pearl": "末影珍珠",
    "iron_ingot": "铁锭", "lapis_lazuli": "青金石", "quartz": "石英", "sugar": "糖",
    "wheat": "小麦", "honeycomb": "蜜脾", "magma_cream": "岩浆膏", "obsidian": "黑曜石",
    "feather": "羽毛", "ghast_tear": "恶魂之泪", "phantom_membrane": "幻翼膜",
    "turtle_scute": "海龟壳", "copper_ingot": "铜锭", "book": "书", "paper": "纸",
    "cobblestone": "圆石", "string": "线", "ink_sac": "墨囊", "honey_bottle": "蜂蜜瓶",
}


def zh(item):
    ns, name = item.split(":", 1)
    if ns == "minecraft":
        return VANILLA.get(name, name)
    return NAMES.get((ns, name), item)


SOURCE = {
    "minecraft": "原版", "goety": "诡厄巫法", "goety_revelation": "诡厄启示录",
    "celestial_core": "星月核心", "celestial_artifacts": "星月饰品",
    "enigmaticlegacy": "神秘遗物", "yanscriptures": "本模组",
}

recipes = []
for path in sorted(glob.glob(os.path.join(REC, "*.json"))):
    data = json.load(open(path, encoding="utf-8"))
    recipes.append({
        "id": os.path.basename(path)[:-5],
        "key": (data["paper"]["tier"], data["ink"]["tier"], data["thread"]["tier"]),
        "counts": (data["paper"]["count"], data["ink"]["count"], data["thread"]["count"]),
        "extras": [(e["item"], e.get("count", 1)) for e in data.get("extras", [])],
    })

groups = {}
for r in recipes:
    groups.setdefault(r["key"], []).append(r)
key, items = max(groups.items(), key=lambda kv: len(kv[1]))

paper_name = ["草纸", "羊皮纸", "至邪之笺", "寂灭之笺"][key[0] - 1]
ink_name = ["墨水", "血墨", "不洁之墨"][key[1] - 1]
thread_name = ["麻绳", "金绳", "龙筋"][key[2] - 1]

tot_p = sum(r["counts"][0] for r in items)
tot_i = sum(r["counts"][1] for r in items)
tot_t = sum(r["counts"][2] for r in items)
extras = {}
for r in items:
    for item, count in r["extras"]:
        extras[item] = extras.get(item, 0) + count

lines = []
lines.append("# 一次性最全材料组\n")
lines.append("> 目标：**凑一套材料，连续把尽可能多种不同的书各做一本**。"
             "统计依据是 `data/yanscriptures/recipes/book_writing/*.json` 的实际配方。\n")
lines.append("## 结论\n")
lines.append("**%s × %d　%s × %d　%s × %d**，外加下面 %d 种额外材料。\n"
             % (paper_name, tot_p, ink_name, tot_i, thread_name, tot_t, len(extras)))
lines.append("这一套能连做 **%d 本**不同的书：\n" % len(items))
for r in sorted(items, key=lambda x: x["id"]):
    name = NAMES.get(("yanscriptures", r["id"]), r["id"])
    lines.append("- %s　（纸 %d · 墨 %d · 线 %d · 额外 %d 种）"
                 % (name, r["counts"][0], r["counts"][1], r["counts"][2], len(r["extras"])))
lines.append("\n## 额外材料清单\n")
lines.append("| 材料 | id | 数量 | 来源 |")
lines.append("| --- | --- | --- | --- |")
for item, count in sorted(extras.items(), key=lambda kv: (-kv[1], kv[0])):
    lines.append("| %s | `%s` | %d | %s |"
                 % (zh(item), item, count, SOURCE.get(item.split(":")[0], item.split(":")[0])))
lines.append("\n合计 **%d 种**，背包里占 %d 格（同种可叠）。\n" % (len(extras), len(extras)))
lines.append("## 为什么不是全部 37 本\n")
lines.append("纸、墨、线各只有一个档位能放进选择格，所以一次只能覆盖「同一档位组合」里的书。"
             "全部配方只落在 %d 种组合上：\n" % len(groups))
lines.append("| 纸 / 墨 / 线 | 本数 |")
lines.append("| --- | --- |")
for k, v in sorted(groups.items(), key=lambda kv: -len(kv[1])):
    lines.append("| 纸%s / 墨%s / 线%s | %d |" % (k[0], k[1], k[2], len(v)))
lines.append("\n纸Ⅱ / 墨Ⅱ / 线Ⅱ 这一组最大（%d 本）；剩下的 %d 本分散在别的档位上，想一次做完必须换档。\n"
             % (len(items), len(recipes) - len(items)))
lines.append("## 如果只想「台面一次摆满、中途不补料」\n")
lines.append("著书台的材料池只有 10 格（一格一种额外材料），这时最多只能同时满足 **3 本**：\n")
lines.append("- 《埃涅阿斯纪》《孙子兵法》《罪与罚》，基础料同样是纸Ⅱ / 墨Ⅱ / 线Ⅱ，"
             "额外材料并集 9 种：大地之心、守护者之心、坟尘、骨头、暗影精粹、灵魂红宝石、皮革、暗金锭、铁锭。\n")
lines.append("想连做更多本，就把额外材料放背包、中途往台面补——台面是工作区，不是仓库。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("written", OUT, len(lines), "lines")
