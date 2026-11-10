# -*- coding: utf-8 -*-
"""文档同步：三条线各记一项属性、tooltip 改「当装备在书籍栏」、候选自查、JEI 信息页精简。"""
from pathlib import Path

p = Path("docs/模组内容总览.md")
s = p.read_text(encoding="utf-8")

# ---------- 1. 档位加成语义 ----------
old = """单条线的加成：**Ⅰ 0、Ⅱ +0.5%、Ⅲ +2.5%、Ⅳ +6%**（作者 m08532 定稿）；一本书的总加成 = 三条线相加，随机到的那一项再多拿一份。"""
new = """单条线的加成：**Ⅰ 0、Ⅱ +0.5%、Ⅲ +2.5%、Ⅳ +6%**（作者 m08532 定稿）。

每条材料线出书时各自掷一项属性，**只加到自己掷中的那一项上**（作者 m08693：三种材料按三个统计）：

| 材料线 | 掷中的属性 | 加成 |
| --- | --- | --- |
| 纸（Ⅳ 寂灭之笺） | 例如攻击力 | +6% 攻击力 |
| 墨（Ⅲ 不洁之墨） | 例如生命上限 | +2.5% 生命上限 |
| 线（Ⅲ 龙筋） | 例如护甲 | +2.5% 护甲 |

三条线互不影响：换料 / 重掷只改被替换那一条线的属性，另外两条原封不动。"""
assert old in s
s = s.replace(old, new)

old = """**「全属性」= 攻击力、生命上限、护甲、移动速度**。一本 Ⅳ/Ⅲ/Ⅲ 的书 = 6+2.5+2.5 = +11% 全属性，掷中攻击力时攻击力实际 +22%、其余三项 +11%。"""
new = """四项属性 = **攻击力、生命上限、护甲、移动速度**。上面那本书的最终加成是「攻击力 +6%、生命上限 +2.5%、护甲 +2.5%、移动速度 +0%」——不是把 11% 摊到四项上。"""
assert old in s
s = s.replace(old, new)

old = """**饰品栏里的每一本书都算**（作者 m08405）：四本带档位的书就是四份相加，处理器会把当前戴着的所有书的百分比先加起来，再往同一项属性上挂一条聚合修正器。"""
new = """**饰品栏里的每一本书都算**（作者 m08405）：四本带档位的书就是四份相加，处理器按属性分别累计当前戴着的所有书，再往每一项属性上挂一条聚合修正器。

**显示方式**（作者 m08693）：物品介绍末尾是仿饰品模组的区块 ——

```
当装备在书籍栏：
+6.0% 攻击力
+2.5% 生命上限
+2.5% 护甲
材料档位：纸 Ⅳ · 墨 Ⅲ · 线 Ⅲ
槽位：书
```"""
assert old in s
s = s.replace(old, new)

old = """- 每次合成都会重掷一次 `YanGradeAttr`。"""
new = """- 每次合成都会**只重掷被替换那一条线**的属性（`MaterialBonus.replaceLine`）；另外两条线保持原样。"""
assert old in s
s = s.replace(old, new)

old = """| `ClassicBookItem` / `BookOfPeaceItem` | tooltip 末尾追加「材料档位：纸 Ⅲ · 墨 Ⅱ · 线 Ⅰ / 全属性 +4% / X 额外 +4%」 |"""
new = """| `ClassicBookItem` / `BookOfPeaceItem` | tooltip 末尾追加「当装备在书籍栏：」区块（逐属性一行 + 材料档位 + 槽位） |"""
assert old in s
s = s.replace(old, new)

old = """- 全 Ⅰ 材料的书会写 NBT 但总加成为 0，tooltip 显示「第一档：无加成」。"""
new = """- 全 Ⅰ 材料的书会写 NBT 但总加成为 0，tooltip 只显示「当装备在书籍栏：」与「第一档：无加成」两行。"""
assert old in s
s = s.replace(old, new)

# ---------- 2. 候选配方自查 ----------
old = """- 配方结构见 `BookWritingRecipes.Recipe`：`纸 + 纸数量 + 墨 + 墨数量 + 线 + 线数量 + 额外材料列表 + 成品`。"""
new = """- 配方结构见 `BookWritingRecipes.Recipe`：`纸 + 纸数量 + 墨 + 墨数量 + 线 + 线数量 + 额外材料列表 + 成品`。
- 配方里写的是**档位**（Ⅰ~Ⅳ）而不是具体物品，所以高等级纸能写低等级的书；
  想加新材料只改标签 `#yanscriptures:papers / inks / threads`，不用改 Java。
- 不开游戏也能验证候选：服务器启动时 `RecipeAudit` 会打一行自查，例如
  `著书台选书自查：高档材料（至邪之笺×4 不洁之墨×2 龙筋×4 都在池子里）→ [republic, … , book_of_peace]（9 条候选）`
  —— 候选多于一条就说明上下箭头会出现。"""
assert old in s
s = s.replace(old, new)

# ---------- 3. JEI 信息页精简 ----------
old = """> **JEI**：对着著书台按 `U`（或在 JEI 搜书）能看到 `yanscriptures:book_writing` 分类，"""
new = """> **JEI**：对着著书台按 `U`（或在 JEI 搜书）能看到 `yanscriptures:book_writing` 分类，"""
assert old in s

p.write_text(s, encoding="utf-8")
print("docs ok")
