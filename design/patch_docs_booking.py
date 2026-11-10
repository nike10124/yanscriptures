# -*- coding: utf-8 -*-
"""文档同步：著书台配方语义（额外材料 + 上下箭头）、单线数值、道德经改著作书台。"""
from pathlib import Path

p = Path("docs/模组内容总览.md")
src = p.read_text(encoding="utf-8")

# ---- 1. 单条线数值 ----
old = "单条线的加成：**Ⅰ 0、Ⅱ +0.5%、Ⅲ +2%、Ⅳ +3%**；一本书的总加成 = 三条线相加，随机到的那一项再多拿一份。"
new = "单条线的加成：**Ⅰ 0、Ⅱ +0.5%、Ⅲ +2.5%、Ⅳ +6%**（作者 m08532 定稿）；一本书的总加成 = 三条线相加，随机到的那一项再多拿一份。"
assert old in src
src = src.replace(old, new)

old = "> 作者最初给的是「Ⅱ +1% / Ⅲ +6% / Ⅳ +10%」，现在三条线会相加、且饰品栏里每一本书都算，\n> 按原数值叠起来太高（四本全 Ⅲ 就到 +72%），所以单线压成上面这组。需要更狠或更软，只改 `MaterialBonus.PERCENT` 一个数组。"
new = "> 作者最初给的是「Ⅱ +1% / Ⅲ +6% / Ⅳ +10%」，因为三条线会相加、饰品栏里每一本书都算，\n> 按原数值叠起来太高，所以单线先压过一次；2026-10-31 作者定稿为上面的 0 / 0.5% / 2.5% / 6%。\n> 要再调，只改 `MaterialBonus.PERCENT` 一个数组。"
assert old in src
src = src.replace(old, new)

old = "**「全属性」= 攻击力、生命上限、护甲、移动速度**。一本 Ⅳ/Ⅲ/Ⅲ 的书 = 3+2+2 = +7% 全属性，掷中攻击力时攻击力实际 +14%、其余三项 +7%。"
new = "**「全属性」= 攻击力、生命上限、护甲、移动速度**。一本 Ⅳ/Ⅲ/Ⅲ 的书 = 6+2.5+2.5 = +11% 全属性，掷中攻击力时攻击力实际 +22%、其余三项 +11%。"
assert old in src
src = src.replace(old, new)

# ---- 2. 打档路径表：去掉工作台那条 ----
old = """| 著书台（纸/墨/线三格 → 书） | `BookWritingTableBlockEntity.craft()` 出书时按三格材料打档 + 掷属性 |
| 工作台 JSON 配方（《道德经》） | `GradeCraftHandler` 在 `PlayerEvent.ItemCraftedEvent` 里回读合成格，按同样的规则打档 + 掷属性 |
| 锻造台「换料重铸」 | `MaterialUpgradeRecipe`：见下 |"""
new = """| 著书台（纸/墨/线三格 + 下半材料池 → 书） | `BookWritingTableBlockEntity.craft()` 出书时按三格材料打档 + 掷属性 |
| 锻造台「换料重铸」 | `MaterialUpgradeRecipe`：见下 |

> 2026-10-31 作者更正：**书类合成表一律在著书台**，工作台不再出书（《道德经》的临时工作台配方已删）。
> `GradeCraftHandler` 保留，只作用于仍留在工作台的那几张（方块与和平等）。"""
assert old in src
src = src.replace(old, new)

old = "| `GradeCraftHandler.java` | 工作台合成书时回读合成格打档 |"
new = "| `GradeCraftHandler.java` | 工作台合成书时回读合成格打档（书类已改去著书台，仅在留用的工作台配方上生效） |"
assert old in src
src = src.replace(old, new)

old = "- 寂灭之笺（Ⅳ）现在可达：任何用 `#yanscriptures:papers` 的配方，纸放它即可（例如《道德经》）。"
new = "- 寂灭之笺（Ⅳ）现在可达：著书台的纸格放它即可（配方里写的是「草纸」的书，换成更高档的纸照样成立）。"
assert old in src
src = src.replace(old, new)

# ---- 3. 道德经那一节：工作台 → 著书台 ----
start = src.index("#### 《道德经》的工作台配方（作者 m08401 定）")
end = src.index("### 设计约定")
replacement = """#### 《道德经》的著书台配方（作者 m08401 定，m08532 移到著书台）

| 格位 | 放什么 | 数量 |
| --- | --- | --- |
| 纸 | 草纸（`yanscriptures:fiber_paper`） | 2 |
| 墨 | 墨水（`yanscriptures:ink`） | 1 |
| 线 | 麻绳（`yanscriptures:hemp_rope`） | 2 |
| 材料池 | 金胡萝卜 + 金苹果 | 各 1 |

- 三种基础材料以外的东西（这里是金胡萝卜、金苹果）放在**台子下半部分的材料池**里，界面上不占格子。
- 数量是「总共要几个」：选择格放一张、池里再放一张，或者选择格直接放两张都成立。
- 高等级材料可以替换低等级的：草纸换羊皮纸/至邪之笺/寂灭之笺，墨水换血墨/不洁之墨，麻绳换金绳/龙筋，照样出书，档位按实际放进去的东西算。

> 其余九本名著仍是各 1 的草案用料，等作者逐本定稿。

"""
src = src[:start] + replacement + src[end:]

# ---- 4. §七·六：「界面只有三个格子」补上候选切换 ----
old = """- 材料清单靠 10 个**影子槽**（坐标 −10000、不可点不可放）借原版的槽位同步送到客户端，
  不再依赖自定义数据包。"""
new = """- 材料清单靠 10 个**影子槽**（坐标 −10000、不可点不可放）借原版的槽位同步送到客户端，
  不再依赖自定义数据包。

### 候选配方与上下小三角（2026-10-31 新增）

材料池里东西多的时候，同一组纸/墨/线可能同时满足好几本书——这时界面右侧成品框旁边会出现
**上下两个小三角**，在候选之间切换（右上角显示「第几条 / 共几条」）。

- 候选列表由 `BookWritingRecipes.findAll(纸, 墨, 线, 材料池)` 现算；选中项存在方块实体的 `selectedIndex` 里，
  经 `ContainerData`（原版 data slot）同步给客户端，重登也记得住。
- 点箭头：客户端先本地挪一格（立刻有反馈），再发 `BookTableCyclePacket` 给服务端，服务端按**自己算的列表**挪，
  两边不会因为同步延迟算错。
- 配方需要额外材料（例如《道德经》要金胡萝卜、金苹果）时，成品名下面会多一行「另需：金胡萝卜 ×1、金苹果 ×1」。
- 配方结构见 `BookWritingRecipes.Recipe`：`纸 + 纸数量 + 墨 + 墨数量 + 线 + 线数量 + 额外材料列表 + 成品`。"""
assert old in src
src = src.replace(old, new)

old = "| `BookWritingRecipes.java` | 纸 / 墨 / 线 → 成品的静态表（九本名著 + 方块与和平） |"
new = "| `BookWritingRecipes.java` | 纸 / 墨 / 线（含数量与额外材料）→ 成品的静态表（九本名著 + 方块与和平） |"
assert old in src
src = src.replace(old, new)

old = """> **JEI**：对着著书台按 `U`（或在 JEI 搜书）能看到 `yanscriptures:book_writing` 分类，
> 一条配方三格输入（纸 / 墨 / 线）+ 一格产物。配方来源是 `BookWritingRecipes.all()`，
> 作者在 `RECIPES` 里加一条，JEI 自动跟着出现（2026-10-30 接上）。"""
new = """> **JEI**：对着著书台按 `U`（或在 JEI 搜书）能看到 `yanscriptures:book_writing` 分类，
> 一条配方三格输入（纸 / 墨 / 线，图标上带数量）+ 最多两格额外材料 + 一格产物。
> 配方来源是 `BookWritingRecipes.all()`，作者在 `RECIPES` 里加一条，JEI 自动跟着出现。"""
assert old in src
src = src.replace(old, new)

# ---- 5. §11.14 通道表与自查样例 ----
old = "| 工作台 / 熔炉等原版配方（空书卷、著书笔、方块与和平、如我所书…） | `data/yanscriptures/recipes/*.json` | JEI 直接读原版配方表，**自动**收录，无需代码 |"
new = "| 工作台 / 熔炉等原版配方（空书卷、著书笔、方块与和平、如我所书…；**书类不放这里**） | `data/yanscriptures/recipes/*.json` | JEI 直接读原版配方表，**自动**收录，无需代码 |"
assert old in src
src = src.replace(old, new)

old = "| 著书台（一张纸 + 一种墨 + 一根线 → 一本书） | `BookWritingRecipes.RECIPES` | `compat/BookWritingRecipeCategory.java` 读 `BookWritingRecipes.all()`，**自动**收录 |"
new = "| 著书台（纸 + 墨 + 线，各带数量，可另加材料池里的额外材料 → 一本书） | `BookWritingRecipes.RECIPES` | `compat/BookWritingRecipeCategory.java` 读 `BookWritingRecipes.all()`，**自动**收录 |"
assert old in src
src = src.replace(old, new)

old = """配方自查：工作台/原版 6 条 [yanscriptures:blank_page, yanscriptures:blank_scroll,
yanscriptures:writing_quill, yanscriptures:book_of_peace, yanscriptures:book_of_me,
yanscriptures:dao_de_jing]，炼药锅 10 条，著书台 10 条，锻造台换料 1 条"""
new = """配方自查：工作台/原版 5 条 [yanscriptures:blank_page, yanscriptures:blank_scroll,
yanscriptures:writing_quill, yanscriptures:book_of_peace, yanscriptures:book_of_me]，
炼药锅 10 条，著书台 10 条，锻造台换料 1 条"""
assert old in src
src = src.replace(old, new)

p.write_text(src, encoding="utf-8")
print("docs ok")
