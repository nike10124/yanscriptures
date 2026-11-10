# -*- coding: utf-8 -*-
"""一次性：把生成脚本里的错误材料 id 换成真实 id，并把空白的书页加进如我所书。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

GEN = r"D:\MCModDev\forge-mdk\tools\gen_book_recipes.py"
DOC = r"D:\MCModDev\forge-mdk\docs\合成配方_草案.md"

FIX = {
    "celestial_artifacts:amethyst_reinforced_plate": "celestial_artifacts:amethyst_reinforce_plate",
    "celestial_artifacts:broken_ring": "goety_revelation:broken_halo",
    "celestial_artifacts:earth_core": "celestial_core:earth_core",
    "celestial_artifacts:golden_feather": "goety_revelation:gold_feather",
    "celestial_artifacts:ocean_ingot": "celestial_core:ocean_ingot",
    "celestial_artifacts:pure_star": "celestial_core:pure_nether_star",
    "celestial_core:evil_stone": "enigmaticlegacy:cursed_stone",
    "enigmaticlegacy:item_description_fragment": "enigmaticlegacy:lore_fragment",
    "enigmaticlegacy:obsidian_heart": "goety_revelation:soul_of_obsidian",
    "enigmaticlegacy:soul_redstone": "goety:soul_ruby",
    "enigmaticlegacy:supercharged_emerald": "goety:soul_emerald",
    "goety:blaze_core": "enigmaticlegacy:blazing_core",
    "goety:evil_essence": "enigmaticlegacy:evil_essence",
    "goety:fire_spine_fruit": "goety:firethorn_berries",
}

text = open(GEN, encoding="utf-8").read()
for old, new in FIX.items():
    text = text.replace(old, new)
if '"空白的书页"' not in text:
    text = text.replace(
        '    "真我的史诗": "yanscriptures:epic_ego",',
        '    "真我的史诗": "yanscriptures:epic_ego",\n    "空白的书页": "yanscriptures:blank_page",',
    )
open(GEN, "w", encoding="utf-8", newline="\n").write(text)

doc = open(DOC, encoding="utf-8").read()
doc = doc.replace(
    "| 如我所书 | IV ×4 | Ⅲ ×3 | Ⅲ ×3 |命运的史诗 支柱的史诗 创生的史诗 灾厄的史诗 真我的史诗 寂灭之星",
    "| 如我所书 | IV ×4 | Ⅲ ×3 | Ⅲ ×3 |命运的史诗 支柱的史诗 创生的史诗 灾厄的史诗 真我的史诗 寂灭之星 空白的书页",
)
open(DOC, "w", encoding="utf-8", newline="\n").write(doc)
print("patched generator + doc")
