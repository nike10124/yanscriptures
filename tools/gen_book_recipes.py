# -*- coding: utf-8 -*-
"""把 docs/合成配方_草案.md 的表格转成著书台配方 JSON。

作者 2026-11-10 定的分工：
  - 如我所书：改成著书台配方（原来的合成台配方删掉）
  - 空书架：合成台（无序合成）
  - 圣约：祭坛不动；十诫：开局给
  - 其余 36 本：按表走著书台配方
"""
import json
import os
import re
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(ROOT, "docs", "合成配方_草案.md")
MATLIST = os.path.join(ROOT, "docs", "材料清单.md")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang", "zh_cn.json")
OUTDIR = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes", "book_writing")
RECIPES = os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "recipes")
LIBS = os.path.join(ROOT, "libs")

VANILLA = {
    "书架": "minecraft:bookshelf", "书": "minecraft:book", "皮革": "minecraft:leather",
    "羽毛": "minecraft:feather", "糖": "minecraft:sugar", "小麦": "minecraft:wheat",
    "骨": "minecraft:bone", "金锭": "minecraft:gold_ingot", "铁锭": "minecraft:iron_ingot",
    "铜锭": "minecraft:copper_ingot", "绿宝石": "minecraft:emerald", "青金石": "minecraft:lapis_lazuli",
    "石英": "minecraft:quartz", "紫水晶碎片": "minecraft:amethyst_shard",
    "末影珍珠": "minecraft:ender_pearl", "黑曜石": "minecraft:obsidian",
    "岩浆膏": "minecraft:magma_cream", "恶魂之泪": "minecraft:ghast_tear",
    "龟壳": "minecraft:turtle_scute", "幻翼膜": "minecraft:phantom_membrane",
    "火药": "minecraft:gunpowder", "黏液球": "minecraft:slime_ball",
    "回响碎片": "minecraft:echo_shard", "蜜脾": "minecraft:honeycomb",
}

# 只能手填的（材料清单里没有，或名字对不上）
EXTRA = {
    "寂灭之星": "goety_revelation:quietus_star",
    "以太锭": "enigmaticlegacy:etherium_ingot",
    "紫水晶加固板": "celestial_artifacts:amethyst_reinforce_plate",
    "纯净之星": "celestial_core:pure_nether_star",
    "破碎之环": "goety_revelation:broken_halo",
    "黄金魔翎": "goety_revelation:gold_feather",
    "大地核心": "celestial_core:earth_core",
    "海洋锭": "celestial_core:ocean_ingot",
    "虚金锭": "enigmaticlegacy:etherium_ingot",
    "黑曜石之魂": "goety_revelation:soul_of_obsidian",
    "灵魂红宝石": "goety:soul_ruby",
    "物品描述片段": "enigmaticlegacy:lore_fragment",
    "坟尘": "goety:grave_dust",
    "诅咒金属锭": "goety:cursed_ingot",
    "黑暗金属锭": "goety:dark_ingot",
    "不祥碎片": "goety:ominous_shard",
    "暗影精粹": "goety:shadow_essence",
    "虚空碎片": "goety:void_shard",
    "邪恶精髓": "enigmaticlegacy:evil_essence",
    "禁书片段": "goety:forbidden_fragment",
    "烈焰之核": "enigmaticlegacy:blazing_core",
    "浓缩绿宝石": "goety:soul_emerald",
    "邪恶之石": "enigmaticlegacy:cursed_stone",
    "火棘果": "goety:firethorn_berries",
    "天仙子花": "goety:henbane_flower",
    "星尘": "enigmaticlegacy:astral_dust",
    "虚空精华": "celestial_core:void_essence",
    "海洋精华": "celestial_core:ocean_essence",
    "光明碎片": "celestial_core:light_fragment",
    "子夜碎片": "celestial_core:midnight_fragment",
    "樱花碎片": "celestial_core:sakura_fragment",
    "宝藏碎片": "celestial_core:treasure_fragment",
    "心之碎片": "celestial_core:heart_fragment",
    "死亡精华": "celestial_core:death_essence",
    "火焰精华": "celestial_core:fire_essence",
    "天使之心": "celestial_artifacts:angel_heart",
    "守卫者之心": "enigmaticlegacy:guardian_heart",
    "大地之心": "enigmaticlegacy:earth_heart",
    "命运的史诗": "yanscriptures:epic_fate",
    "支柱的史诗": "yanscriptures:epic_pillar",
    "创生的史诗": "yanscriptures:epic_creation",
    "灾厄的史诗": "yanscriptures:epic_calamity",
    "真我的史诗": "yanscriptures:epic_ego",
    "空白的书页": "yanscriptures:blank_page",
}

TIER = {"Ⅰ": 1, "Ⅱ": 2, "Ⅲ": 3, "Ⅳ": 4, "I": 1, "II": 2, "III": 3, "IV": 4}


def load_lang_names():
    """enabled lang 里作为物品名的中文 → id（用来把表格里的书名对到物品）。"""
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)
    names = {}
    for k, v in lang.items():
        m = re.fullmatch(r"item\.yanscriptures\.([a-z0-9_]+)", k)
        if not m:
            continue
        clean = v.strip().strip("《》")
        names[clean] = "yanscriptures:" + m.group(1)
    return names


def load_matlist():
    """材料清单里 `| 中文 | \\`id\\` |` 形式的两列表格。"""
    out = {}
    with open(MATLIST, encoding="utf-8") as f:
        for line in f:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 2:
                continue
            name = cells[0]
            m = re.search(r"`([a-z0-9_]+:[a-z0-9_/]+)`", cells[1] if len(cells) > 1 else "")
            if not m:
                continue
            if name.startswith("`") or not name or "（" in name:
                continue
            out.setdefault(name, m.group(1))
    return out


def lang_from_jars():
    """从 libs 里各模组的 zh_cn 反查中文 → id，补材料清单没收录的。"""
    out = {}
    if not os.path.isdir(LIBS):
        return out
    for jar in sorted(os.listdir(LIBS)):
        if not jar.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(LIBS, jar)) as zf:
                for entry in zf.namelist():
                    if not entry.endswith("lang/zh_cn.json"):
                        continue
                    ns = entry.split("/")[1]
                    data = json.loads(zf.read(entry).decode("utf-8-sig"))
                    for k, v in data.items():
                        if not isinstance(v, str) or "." in k.split(".")[-1]:
                            continue
                        if k.startswith("item.") or k.startswith("block."):
                            out.setdefault(v.strip(), ns + ":" + k.split(".", 2)[-1])
        except Exception:
            continue
    return out


def resolve(name, mats, jar_names, book_names):
    name = name.strip().strip("·").strip()
    if not name:
        return None
    if name in book_names:
        return book_names[name]
    if name in EXTRA:
        return EXTRA[name]
    if name in VANILLA:
        return VANILLA[name]
    if name in mats:
        return mats[name]
    if name in jar_names:
        return jar_names[name]
    return None


def parse_meta(cell):
    """`Ⅲ ×2` / `IV ×3` / `III ×2` → (tier, count)。"""
    cell = cell.replace("*", "×").replace("x", "×").replace("X", "×")
    m = re.search(r"([ⅠⅡⅢⅣIV]+)\s*×?\s*(\d+)", cell)
    if not m:
        return None
    tier = TIER.get(m.group(1))
    if tier is None:
        return None
    return tier, int(m.group(2))


def parse_extras(cell, mats, jar_names, book_names):
    cell = cell.replace("|", " ").strip()
    items, missing = [], []
    parts = re.split(r"[·,，、]| {2,}", cell)
    if len(parts) == 1 and " " in cell:
        # 作者写「命运的史诗 支柱的史诗 …」这种只有空格的写法
        parts = cell.split(" ")
    for part in parts:
        part = part.strip()
        if not part:
            continue
        m = re.match(r"(.+?)\s*\*\s*(\d+)$", part)
        if m:
            raw, count = m.group(1), int(m.group(2))
        else:
            raw, count = part, 1
        ident = resolve(raw, mats, jar_names, book_names)
        if ident is None:
            missing.append(raw)
            continue
        items.append({"item": ident, "count": count})
    return items, missing


def main():
    book_names = load_lang_names()
    # 表格里用的简称
    book_names["36计"] = book_names.get("三十六计")
    book_names["如我所书"] = "yanscriptures:book_of_me"
    mats = load_matlist()
    jar_names = lang_from_jars()

    with open(TABLE, encoding="utf-8") as f:
        lines = f.read().splitlines()

    rows = []
    for line in lines:
        if not line.startswith("|") or "|" not in line[1:]:
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 5:
            continue
        if cells[0] in ("饰品", "段位") or cells[0].startswith("---"):
            continue
        paper, ink, thread = parse_meta(cells[1]), parse_meta(cells[2]), parse_meta(cells[3])
        if not (paper and ink and thread):
            continue
        rows.append((cells[0], paper, ink, thread, cells[4]))

    os.makedirs(OUTDIR, exist_ok=True)
    unresolved = []
    written = 0
    for name, paper, ink, thread, extras_cell in rows:
        ident = resolve(name, mats, jar_names, book_names)
        if ident is None or not ident.startswith("yanscriptures:"):
            unresolved.append(name)
            continue
        extras, missing = parse_extras(extras_cell, mats, jar_names, book_names)
        unresolved.extend(missing)
        data = {
            "type": "yanscriptures:book_writing",
            "paper": {"tier": paper[0], "count": paper[1]},
            "ink": {"tier": ink[0], "count": ink[1]},
            "thread": {"tier": thread[0], "count": thread[1]},
            "extras": extras,
            "result": {"item": ident},
        }
        path = os.path.join(OUTDIR, ident.split(":")[1] + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        written += 1

    # 如我所书 的原版合成配方删掉（改走著书台）
    old = os.path.join(RECIPES, "book_of_me.json")
    if os.path.exists(old):
        os.remove(old)
        print("removed", old)

    # 空书架：合成台（无序）
    shelf = {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [
            {"item": "minecraft:bookshelf"},
            {"item": "yanscriptures:parchment"},
            {"item": "yanscriptures:parchment"},
            {"item": "enigmaticlegacy:etherium_ingot"},
            {"item": "celestial_core:void_essence"},
            {"item": "minecraft:leather"},
            {"item": "minecraft:leather"},
        ],
        "result": {"item": "yanscriptures:empty_bookshelf", "count": 1},
    }
    with open(os.path.join(RECIPES, "empty_bookshelf.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(shelf, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("written", written, "book recipes")
    if unresolved:
        print("UNRESOLVED:", sorted(set(unresolved)))


if __name__ == "__main__":
    main()
