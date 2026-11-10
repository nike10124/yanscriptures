# -*- coding: utf-8 -*-
"""按「三条线各自一项属性」重算并汇总。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/MaterialBonusHandler.java")
src = p.read_text(encoding="utf-8")

start = src.index("        List<ItemStack> books = wornBooks(player);")
end = src.index("        apply(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, attack);")
replacement = """        List<ItemStack> books = wornBooks(player);
        // 四条属性分别累计：每条材料线掷中的那一项只加到自己那一项上
        double[] totals = new double[4];
        for (ItemStack stack : books) {
            double[] perAttr = MaterialBonus.perAttribute(stack);
            for (int i = 0; i < totals.length; i++) {
                totals[i] += perAttr[i];
            }
        }
        double attack = totals[0];
        double health = totals[1];
        double armor = totals[2];
        double speed = totals[3];

"""
src = src[:start] + replacement + src[end:]

src = src.replace("""    /** 饰品栏里所有带档位的书；同一本书戴不了两遍，不用去重。 */
    private static List<ItemStack> wornBooks(Player player) {
        List<ItemStack> books = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> handler.getCurios().forEach((id, stash) -> {
            for (int slot = 0; slot < stash.getSlots(); slot++) {
                ItemStack stack = stash.getStacks().getStackInSlot(slot);
                if (!stack.isEmpty() && MaterialBonus.totalPercent(stack) > 0.0D) {
                    books.add(stack);
                }
            }
        }));
        return books;
    }""",
"""    /** 饰品栏里所有带档位的书；同一本书戴不了两遍，不用去重。 */
    private static List<ItemStack> wornBooks(Player player) {
        List<ItemStack> books = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> handler.getCurios().forEach((id, stash) -> {
            for (int slot = 0; slot < stash.getSlots(); slot++) {
                ItemStack stack = stash.getStacks().getStackInSlot(slot);
                if (!stack.isEmpty() && MaterialBonus.totalPercent(stack) > 0.0D) {
                    books.add(stack);
                }
            }
        }));
        return books;
    }""")

# 注释更新
src = src.replace(""" * <p>规则（作者 m08405 明确）：饰品栏里的<b>每一本</b>带档位的书都各给一份加成；
 * 每本书自己的三条材料线相加，随机到的那一项属性再拿双份。四本书就是四份相加。</p>""",
""" * <p>规则（作者 m08405 / m08693）：饰品栏里的<b>每一本</b>带档位的书都各给一份加成；
 * 每本书的三条材料线各掷一项自己的属性，只加成到那一项上。四本书就是四份相加。</p>""")

p.write_text(src, encoding="utf-8")
print("handler ok")
