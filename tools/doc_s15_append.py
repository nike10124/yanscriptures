# -*- coding: utf-8 -*-
"""§15 追加两行：史诗书成就、收集齐成就。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\docs\模组内容总览.md"
text = open(PATH, encoding="utf-8").read()

rows = """| 史诗书成就 ×14 | 新文件 `advancements/epic_book_<id>.json`，`inventory_changed` 认各自物品，父节点 `yanscriptures:epic`，goal 框；标题直接用物品名（`item.yanscriptures.<id>`），描述共用 `advancement.yanscriptures.epic_book.desc` | 作者：给所有史诗稀有度的书各一条获取成就 |
| 收集齐全部书籍 | `advancements/collect_all.json`：42 条 criteria（`#yanscriptures:books` 去掉空白的书页），`requirements` 全为必填 ⇒ 全部到手才完成；challenge 框、hidden；标题「百家争鸣」 | 作者要求加一条「收集齐所有书籍」的成就 |
"""

marker = "| 寂灭之笺 | 新增成就"
idx = text.index(marker)
end = text.index("\n", idx) + 1
text = text[:end] + rows + text[end:]
open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok")
