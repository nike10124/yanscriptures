# -*- coding: utf-8 -*-
"""把 docs/模组内容总览.md 拆成「玩家向」与「开发向」两份。

开发向：整份原文件原样归档到 docs/开发笔记_实现细节与试错.md（不丢任何内容）。
玩家向：从原文件里按标题整段删除开发/试错章节，其余保持原样，最后手动补目录。
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "模组内容总览.md")
ARCHIVE = os.path.join(ROOT, "docs", "开发笔记_实现细节与试错.md")

DROP = [
    "### 3.5 战斗规则模块（BookOfMeCombatHandler）",
    "### 7.6.1 第二批名著（2026-11-01，只注册）",
    "### 7.6.1a 第三批名著（2026-11-06，只注册）",
    "#### 2026-11-01 实机后修的三处（作者报「没生效 / 崩溃」）",
    "### 设计约定",
    "### 七·五·二、属性乘区与 Curios 显示（2026-11-01 新增）",
    "### 实现位置",
    "### 配方数据化：KubeJS 能改（2026-11-01 按作者 A 方案）",
    "### 自定义触发器",
    "### 加一条新进度的做法",
    "### 13.5 `/ys milestone selftest` 怎么用",
    "### 13.6 已知的取舍",
    "### 7.6.2 《神曲》的落点必须先加载区块（2026-10-30 记录）",
    "### 7.6.1 两个实现坑（2026-10-30 记录）",
    "## 十、与外部模组的兼容层",
    "## 十一、底层机制说明",
    "## 十二、已知问题与未实现项",
]

HEAD_RE = re.compile(r"^(#{1,6})\s+(.*)$")


def level(line):
    m = HEAD_RE.match(line)
    return len(m.group(1)) if m else 0


def drop_block(lines, start, lvl):
    """返回该标题段的结束下标（不含）。"""
    i = start + 1
    while i < len(lines):
        l = level(lines[i])
        if l and l <= lvl:
            break
        i += 1
    return i


def main():
    with io.open(SRC, encoding="utf-8") as f:
        lines = f.read().split("\n")

    # 1) 归档：原文件原样保存
    header = [
        "# Yan 的书籍饰品 · 开发笔记（实现细节与试错记录）",
        "",
        "> 这份文档是 `docs/模组内容总览.md` 的**开发向姊妹篇**：",
        "> 里面是类名、事件优先级、乘区算式、踩坑记录、逐轮修复表、调试开关、",
        "> 已知问题与未实现项——**给开发/维护者看的**，玩家不需要读。",
        ">",
        "> 玩家要看的玩法说明在 `docs/模组内容总览.md`。",
        "> 内容来自 2026-11-07 的拆分，原文一字未删（只把整份旧文件搬到这里）。",
        "",
        "---",
        "",
    ]
    with io.open(ARCHIVE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(header + lines))

    # 2) 玩家向：删掉开发章节
    out = []
    i = 0
    removed = []
    while i < len(lines):
        line = lines[i]
        title = HEAD_RE.match(line)
        if title and title.group(2).strip() in {d.split(" ", 1)[1] for d in DROP if " " in d}:
            lvl = len(title.group(1))
            j = drop_block(lines, i, lvl)
            removed.append((title.group(2).strip(), j - i))
            i = j
            continue
        out.append(line)
        i += 1

    # 3) 删掉旧目录（## 目录 到 下一个 ## 之前）
    start = next((k for k, l in enumerate(out) if l.strip() == "## 目录"), None)
    if start is not None:
        end = start + 1
        while end < len(out):
            l = level(out[end])
            if l and l <= 2:
                break
            end += 1
        out = out[:start] + out[end:]

    with io.open(SRC, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))

    print("removed:")
    for name, n in removed:
        print("  -%4d  %s" % (n, name))
    print("new line count:", len(out))


if __name__ == "__main__":
    main()
