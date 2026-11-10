# -*- coding: utf-8 -*-
"""JEI 著书台分类：显示数量与额外材料；MaterialBonus 单线数值改档；补语言键。"""
import json
from pathlib import Path

# ==================== 1. JEI 分类 ====================
p = Path("src/main/java/com/example/examplemod/compat/BookWritingRecipeCategory.java")
src = p.read_text(encoding="utf-8")

src = src.replace("""    private static final int WIDTH = 132;
    private static final int HEIGHT = 40;""",
"""    private static final int WIDTH = 150;
    private static final int HEIGHT = 40;

    /** 额外材料的图标从 x=70 起往右排，每格 18。 */
    private static final int EXTRA_X = 70;
    private static final int EXTRA_STEP = 18;""")

old = """        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 4, 10)
                .addItemStack(new ItemStack(recipe.paper()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26, 10)
                .addItemStack(new ItemStack(recipe.ink()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 48, 10)
                .addItemStack(new ItemStack(recipe.thread()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 104, 10)
                .addItemStack(new ItemStack(recipe.result()));"""
new = """        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 4, 10)
                .addItemStack(new ItemStack(recipe.paper(), recipe.paperCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26, 10)
                .addItemStack(new ItemStack(recipe.ink(), recipe.inkCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 48, 10)
                .addItemStack(new ItemStack(recipe.thread(), recipe.threadCount()));
        for (int i = 0; i < recipe.extras().size() && i < 2; i++) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, EXTRA_X + i * EXTRA_STEP, 10)
                    .addItemStack(recipe.extras().get(i));
        }
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 122, 10)
                .addItemStack(new ItemStack(recipe.result()));"""
assert old in src
src = src.replace(old, new)

src = src.replace("""    /** 排成「纸 · 墨 · 线」的一行文字，JEI 信息栏里也能读。 */
    public static List<String> describe(BookWritingRecipes.Recipe recipe) {
        return List.of(
                recipe.paper().getDescriptionId(),
                recipe.ink().getDescriptionId(),
                recipe.thread().getDescriptionId());
    }""",
"""    /** 排成「纸 · 墨 · 线 · 额外材料」的一行文字，JEI 信息栏里也能读。 */
    public static List<String> describe(BookWritingRecipes.Recipe recipe) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(recipe.paper().getDescriptionId() + " ×" + recipe.paperCount());
        lines.add(recipe.ink().getDescriptionId() + " ×" + recipe.inkCount());
        lines.add(recipe.thread().getDescriptionId() + " ×" + recipe.threadCount());
        for (ItemStack extra : recipe.extras()) {
            lines.add(extra.getDescriptionId() + " ×" + extra.getCount());
        }
        return lines;
    }""")
p.write_text(src, encoding="utf-8")
print("JEI category ok")

# ==================== 2. MaterialBonus：单线数值 Ⅰ0 / Ⅱ0.5% / Ⅲ2.5% / Ⅳ6% ====================
p = Path("src/main/java/com/example/examplemod/MaterialBonus.java")
src = p.read_text(encoding="utf-8")
old = "private static final double[] PERCENT = {0.0D, 0.0D, 0.005D, 0.02D, 0.03D};"
assert old in src, "PERCENT not found"
src = src.replace(old, "private static final double[] PERCENT = {0.0D, 0.0D, 0.005D, 0.025D, 0.06D};")
p.write_text(src, encoding="utf-8")
print("PERCENT ok")

# ==================== 3. 语言键 ====================
p = Path("src/main/resources/assets/yanscriptures/lang/zh_cn.json")
data = json.loads(p.read_text(encoding="utf-8"))
data["gui.yanscriptures.book_table.extras"] = "另需：%s"
p.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print("lang keys:", len(data))
