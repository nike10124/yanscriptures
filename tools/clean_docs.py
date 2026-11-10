# -*- coding: utf-8 -*-
"""玩家向文档的第二步清洗：去掉类名/路径/作者消息编号这类开发痕迹。

规则：
1. 删掉「（`Xxx.java`）」「（`Xxx.java`，说明）」这类括号补充；
2. 表格行里含 `.java` / `resources/` / `data/` / `client/` 的单元格整格删除；
3. 以 `-` `>` `|` 开头且仍然含 `.java` 的行先记账，交给人工改写（脚本只报告，不硬删正文）；
4. 删掉「（作者 m08123 指定）」这类内部编号；
5. 标题里的「（2026-xx-xx …）」日期注记去掉。
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "模组内容总览.md")

PAREN_JAVA = re.compile(r"（`[^`]*\.java`[^）]*）")
PAREN_AUTHOR = re.compile(r"（作者\s*m\d+[^）]*）")
PAREN_DATE_ONLY = re.compile(r"（\d{4}-\d{2}-\d{2}[^）]*）")
DEV_CELL = re.compile(r"\.java|resources/|data/|client/")


def strip_cells(line):
    if not line.lstrip().startswith("|"):
        return line
    cells = line.split("|")
    # cells[0] 与 cells[-1] 是行首/行尾的空串
    keep = [cells[0]]
    for c in cells[1:-1]:
        if DEV_CELL.search(c):
            continue
        keep.append(c)
    keep.append(cells[-1])
    if len(keep) <= 3:  # 只剩一列 => 整行没意义
        return ""
    return "|".join(keep)


def main():
    with io.open(SRC, encoding="utf-8") as f:
        lines = f.read().split("\n")

    out = []
    report = []
    for no, line in enumerate(lines, 1):
        orig = line
        line = PAREN_JAVA.sub("", line)
        line = PAREN_AUTHOR.sub("", line)
        line = strip_cells(line)
        if line and line.lstrip().startswith("#"):
            line = PAREN_DATE_ONLY.sub("", line)
        if DEV_CELL.search(line):
            report.append((no, line))
        if line.strip() == "" and orig.strip() != "":
            continue
        out.append(line)

    with io.open(SRC, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))

    print("remaining dev-ish lines:", len(report))
    for no, line in report[:60]:
        print("%5d  %s" % (no, line[:160]))


if __name__ == "__main__":
    main()
