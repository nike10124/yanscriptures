# -*- coding: utf-8 -*-
"""锻造台换料：只动被替换的那一条线（档位 + 它自己的随机属性）。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/MaterialUpgradeRecipe.java")
src = p.read_text(encoding="utf-8")

old = """        int line = MaterialBonus.lineOf(additionStack.getItem());
        int tier = MaterialBonus.tierOf(additionStack.getItem());
        if (line < 0 || tier <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = baseStack.copy();
        int[] tiers = MaterialBonus.tiers(result);
        if (tier > tiers[line]) {
            tiers[line] = tier;
        }
        // 换一次料就重掷一次属性：作者要求「可以切换对应的属性，或者重新刷新随机属性」
        MaterialBonus.stamp(result, tiers, MaterialBonus.rollAttr(RandomSource.create()));"""
new = """        int line = MaterialBonus.lineOf(additionStack);
        int tier = MaterialBonus.tierOf(additionStack);
        if (line < 0 || tier <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = baseStack.copy();
        // 只动这一条线：档位只升不降，随机属性只重掷这条线的
        //（作者 m08693：换料不能把另外两条线的属性一起洗掉）
        MaterialBonus.replaceLine(result, line, tier, MaterialBonus.rollAttr(RandomSource.create()));"""
assert old in src
src = src.replace(old, new)
p.write_text(src, encoding="utf-8")
print("upgrade recipe ok")

# ==================== 工作台合成打档：三条线各掷一项 ====================
p = Path("src/main/java/com/example/examplemod/GradeCraftHandler.java")
src = p.read_text(encoding="utf-8")

old = "MaterialBonus.stamp(result, tiers, MaterialBonus.rollAttr(random));"
new = "MaterialBonus.stamp(result, tiers, MaterialBonus.rollAttrs(random));"
assert old in src
src = src.replace(old, new)

src = src.replace("int line = MaterialBonus.lineOf(stack.getItem());", "int line = MaterialBonus.lineOf(stack);")
src = src.replace("tiers[line] = Math.max(tiers[line], MaterialBonus.tierOf(stack.getItem()));",
                  "tiers[line] = Math.max(tiers[line], MaterialBonus.tierOf(stack));")
p.write_text(src, encoding="utf-8")
print("grade craft handler ok")
