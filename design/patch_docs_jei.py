# -*- coding: utf-8 -*-
"""文档：JEI 信息页规则改为「只写作用/获取条件」；补著书台自查样例。"""
from pathlib import Path

p = Path("docs/模组内容总览.md")
s = p.read_text(encoding="utf-8")

old = """另有一类不算配方但同样要进 JEI 的：**获取方式的说明**。十张残卷、四件史诗、九本名著
都用 `addItemStackInfo` 写了「信息」页（见 §13.1 / §13.2），以后新增这种「只能靠条件拿」的
物品，也按同样方式补一条信息页。"""
new = """另有一类不算配方但同样要进 JEI 的：**获取方式的说明**。十张残卷、四件史诗
用 `addItemStackInfo` 写了「信息」页（见 §13.1 / §13.2），以后新增这种「只能靠条件拿」的
物品，也按同样方式补一条信息页。

> 信息页的取舍（2026-10-31 作者 m08693）：**只写玩家下一步要做什么**，不要写
> 「现在有哪几本」「还不能合成」「某某的合成方式」这类清单——合成方式按 `R` 就有，
> 信息页重复一遍反而淹掉真正需要的条件。原来给空书卷 / 著书笔 / 九本名著加的
> 三条信息页已按要求删除。

著书台还有一条**不依赖界面**的自查（`RecipeAudit.bookWritingSelfCheck()`）——用假的三格
加材料池直接跑 `BookWritingRecipes.findAll`，把候选名单写进日志：

```
著书台选书自查：草纸/墨水/麻绳 + 金胡萝卜金苹果 → [dao_de_jing]
著书台选书自查：高档材料（至邪之笺×4 不洁之墨×2 龙筋×4 都在池子里）→ [republic, …,
book_of_peace]（9 条候选）
```

候选多于一条 = 上下箭头会出现；一条都没有 = 档位或数量写错了。"""
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("docs jei ok")
