# -*- coding: utf-8 -*-
"""著书台出书：档位配方 + 三条线各掷一项属性。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/BookWritingTableBlockEntity.java")
src = p.read_text(encoding="utf-8")

old = """        ItemStack result = new ItemStack(recipe.result());
        // 用料决定档次：三条材料线各记自己的档位，并给这本书掷一项永久的额外属性
        int[] tiers = MaterialBonus.tiersOf(items.get(PAPER_SLOT), items.get(INK_SLOT), items.get(THREAD_SLOT));
        String bonusAttr = level == null
                ? MaterialBonus.ATTR_KEYS[0]
                : MaterialBonus.rollAttr(level.getRandom());
        MaterialBonus.stamp(result, tiers, bonusAttr);

        consume(recipe.paper(), recipe.paperCount(), PAPER_SLOT);
        consume(recipe.ink(), recipe.inkCount(), INK_SLOT);
        consume(recipe.thread(), recipe.threadCount(), THREAD_SLOT);"""
new = """        ItemStack result = new ItemStack(recipe.result());
        // 用料决定档次：三条材料线各记自己的档位，并各自掷一项永久的额外属性
        int[] tiers = MaterialBonus.tiersOf(items.get(PAPER_SLOT), items.get(INK_SLOT), items.get(THREAD_SLOT));
        String[] attrs = level == null
                ? new String[]{MaterialBonus.ATTR_KEYS[0], MaterialBonus.ATTR_KEYS[0], MaterialBonus.ATTR_KEYS[0]}
                : MaterialBonus.rollAttrs(level.getRandom());
        MaterialBonus.stamp(result, tiers, attrs);

        consume(items.get(PAPER_SLOT).getItem(), recipe.paperCount(), PAPER_SLOT);
        consume(items.get(INK_SLOT).getItem(), recipe.inkCount(), INK_SLOT);
        consume(items.get(THREAD_SLOT).getItem(), recipe.threadCount(), THREAD_SLOT);"""
assert old in src
src = src.replace(old, new)
p.write_text(src, encoding="utf-8")
print("BE craft ok")

# ==================== JEI 分类：按档位展示 ====================
p = Path("src/main/java/com/example/examplemod/compat/BookWritingRecipeCategory.java")
src = p.read_text(encoding="utf-8")

old = """        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 4, 10)
                .addItemStack(new ItemStack(recipe.paper(), recipe.paperCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26, 10)
                .addItemStack(new ItemStack(recipe.ink(), recipe.inkCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 48, 10)
                .addItemStack(new ItemStack(recipe.thread(), recipe.threadCount()));"""
new = """        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 4, 10)
                .addItemStack(new ItemStack(MaterialBonus.paperOfTier(recipe.paperTier()), recipe.paperCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26, 10)
                .addItemStack(new ItemStack(MaterialBonus.inkOfTier(recipe.inkTier()), recipe.inkCount()));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 48, 10)
                .addItemStack(new ItemStack(MaterialBonus.threadOfTier(recipe.threadTier()), recipe.threadCount()));"""
assert old in src
src = src.replace(old, new)

old = """        List<String> lines = new java.util.ArrayList<>();
        lines.add(recipe.paper().getDescriptionId() + " ×" + recipe.paperCount());
        lines.add(recipe.ink().getDescriptionId() + " ×" + recipe.inkCount());
        lines.add(recipe.thread().getDescriptionId() + " ×" + recipe.threadCount());"""
new = """        List<String> lines = new java.util.ArrayList<>();
        lines.add(MaterialBonus.paperOfTier(recipe.paperTier()).getDescriptionId() + " ×" + recipe.paperCount());
        lines.add(MaterialBonus.inkOfTier(recipe.inkTier()).getDescriptionId() + " ×" + recipe.inkCount());
        lines.add(MaterialBonus.threadOfTier(recipe.threadTier()).getDescriptionId() + " ×" + recipe.threadCount());"""
assert old in src
src = src.replace(old, new)

if "import com.example.examplemod.MaterialBonus;" not in src:
    src = src.replace("import com.example.examplemod.ExampleMod;",
                      "import com.example.examplemod.ExampleMod;\nimport com.example.examplemod.MaterialBonus;")
p.write_text(src, encoding="utf-8")
print("JEI category ok")
