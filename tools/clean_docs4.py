# -*- coding: utf-8 -*-
"""玩家向文档第五步：删掉「三条实现决策」整块、改成就表与两条措辞。"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "模组内容总览.md")

PAIRS = [
    ("| `written.json` | 落笔 | 普通 | 事件 `book_written`（著书台写出一本书） |",
     "| `written.json` | 落笔 | 普通 | 第一次在著书台写出一本书 |"),
    ("| `cauldron.json` | 锅里熬出来的 | 普通 | 事件 `cauldron_brew`（炼药锅成锅一次） |",
     "| `cauldron.json` | 锅里熬出来的 | 普通 | 第一次用炼药锅熬出成品 |"),
    ("| `trial_start.json` | 启示开始 | 目标 | 事件 `trial_start`（`TrialManager.start`） |",
     "| `trial_start.json` | 启示开始 | 目标 | 第一次开始启示试炼 |"),
    ("| `trial_clear.json` | 越过审判 | **挑战** | 事件 `trial_clear`（`TrialManager.succeed`） |",
     "| `trial_clear.json` | 越过审判 | **挑战** | 第一次通过启示试炼 |"),
    ("| `trial_fail.json` | 审判还在 | 普通（隐藏） | 事件 `trial_fail`（`TrialManager.fail`） |",
     "| `trial_fail.json` | 审判还在 | 普通（隐藏） | 第一次在启示试炼里倒下 |"),
    ("| `debug dummy clear [range]` | 2 | **直接清除**半径内（默认 32 格）的训练假人：走 `Entity#discard()`，不触发死亡动画 / 掉落 / 死亡事件，也不进死亡后逻辑 |",
     "| `debug dummy clear [range]` | 2 | **直接清除**半径内（默认 32 格）的训练假人：不播死亡动画、不掉落、也不进死亡后逻辑 |"),
    ("十诫残卷、五件泰坦史诗与九本名著**都不做合成表**，全部由「第一次做到某件事」触发。整套系统是纯事件驱动的，没有轮询、没有配方解锁。",
     "十诫残卷、五件泰坦史诗与九本名著**都不做合成表**，全部由「第一次做到某件事」触发，没有轮询、也没有配方解锁。"),
]


def main():
    with io.open(SRC, encoding="utf-8") as f:
        text = f.read()

    for old, new in PAIRS:
        if old not in text:
            print("MISS:", old[:30])
        text = text.replace(old, new)

    lines = text.split("\n")
    # 删除「三条实现决策」整块（从该行到「3. **卦象、契约…」那一行结束）
    start = next((i for i, s in enumerate(lines) if s.startswith("三条实现决策")), None)
    if start is not None:
        end = start
        while end < len(lines) and not lines[end].startswith("3. **卦象、契约"):
            end += 1
        del lines[start:end + 1]
        print("removed dev block:", start, end)

    with io.open(SRC, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print("lines:", len(lines))


if __name__ == "__main__":
    main()
