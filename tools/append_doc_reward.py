# -*- coding: utf-8 -*-
"""向玩家向文档追加「新获取方式」小节。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

PATH = r"D:\MCModDev\forge-mdk\docs\模组内容总览.md"

SECTION = """
## 十六、终局物品的获取方式（2026-11-12）

### 启示残卷（普通）

十张诫卷把十条诅咒**全部反转（补全）**之后，自动获得一张普通启示残卷，每人一次。

- 触发点：用第 N 张诫卷补全时，检查 `Completed_1..10` 是否全为真；
- 防重复：圣典 NBT 记 `RevelationGranted`，拿到过就不会再发；
- 与试炼版区分：这张**不带** `TrialCleared`，是「完成十诫」的凭证，指向《圣约·原典》的线索。

### 真我的史诗（临时）

拥有另外四块史诗（命运、创生、支柱、灾厄）并击败亚波伦后获得。

- 判定亚波伦不按注册名，读实体存档里的 `isApollyon` 标记；
- **延迟发放**：死亡事件只登记「待结算」，等亚波伦的实体真正从世界里消失（尾杀演出结束、死亡动画播完被移除）才发奖；最长等 20 秒，实体卡住也会兜底发放；
- 击杀者判定顺序：伤害来源的玩家 → 48 格内最近的玩家；
- 四块石板在背包、副手或饰品栏里任意位置都算。

### 启示试炼的结算时序（同步修正）

启示试炼原来用 `isAlive()` 判断最后一波清空，而亚波伦血量归零的瞬间 `isAlive()` 就为假，导致带 `TrialCleared` 的启示残卷在尾杀演出**开始前**就发放。现在改为「实体真正被移除才算清空」，与上面的真我判定一致。
"""

with open(PATH, "a", encoding="utf-8", newline="\n") as f:
    f.write(SECTION)

print("appended", len(SECTION), "chars")
