# -*- coding: utf-8 -*-
"""重写 §15：只有名著进度下线；写书/第一本书成就保留；新增寂灭之笺成就。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\docs\模组内容总览.md"
text = open(PATH, encoding="utf-8").read()

head = text[: text.index("## 15. 2026-11-11")]
body = """## 15. 2026-11-11 成就范围收窄（作者定）

| 项 | 改动 | 原因 |
|---|---|---|
| 名著进度 | `MilestoneTracker.ALL` 去掉九本名著，只剩十诫 10 条 + 史诗 4 条；`ClassicMilestones.ENABLED` 改回 `false` | 作者：不需要「合成每一本书都弹成就」，只有史诗稀有度的书才给成就。九条判定代码保留，将来要恢复把 id 加回 `ALL` 并把开关改回 `true` 即可 |
| 自检 | `/ys milestone selftest` 回到 14 条（九条名著场景与标签已删） | 跟随上面的下线 |
| 写书 | `book_written` 成就保持原样（写任何一本书都触发） | 作者：「写书和第一本书可以有成就」 |
| 第一本书 | `first_book.json` 与两条文本保持原样（认 `#yanscriptures:books`） | 同上 |
| 寂灭之笺 | 新增成就 `annihilated.json`（`inventory_changed` 认 `yanscriptures:annihilated_parchment`，goal 框）+ 中英两条文本 | 作者要求「获得寂灭之笺时有成就」 |
"""

open(PATH, "w", encoding="utf-8", newline="\n").write(head + body)
print("ok")
