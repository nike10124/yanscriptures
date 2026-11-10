# -*- coding: utf-8 -*-
"""自检回退：移除九本名著的标签与九个场景（作者 2026-11-11 决定下线这些进度）。"""
import io
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\src\main\java\com\example\examplemod\MilestoneSelfTest.java"
text = open(PATH, encoding="utf-8").read()

# 1) 删掉九个 LABELS.put 行
before = text
text = re.sub(
    r'\n\s*LABELS\.put\(MilestoneTracker\.(DAO_DE_JING|REPUBLIC|ZARATHUSTRA|DIVINE_COMEDY|'
    r'DON_QUIXOTE|HUNDRED_YEARS|EUCLID|ORIGIN|NINE_CHAPTERS), "[^"]*"\);',
    "", text)
print("labels removed:", before.count("LABELS.put(MilestoneTracker.") - text.count("LABELS.put(MilestoneTracker."))

# 2) 删掉「第一批九本名著」整段，直到 finally 之前
start_marker = "            // ==================== 第一批九本名著 ===================="
start = text.index(start_marker)
end = text.index("\n        } finally {", start)
removed = text[start:end]
text = text[:start] + text[end + 1:]
print("chars removed:", len(removed))

open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok")
