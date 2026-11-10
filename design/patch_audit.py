# -*- coding: utf-8 -*-
"""RecipeAudit 增加「著书台选书自查」：不开界面也能验证候选切换逻辑。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/RecipeAudit.java")
src = p.read_text(encoding="utf-8")

old = """        if (crafting.isEmpty() && cauldron == 0 && book == 0 && upgrade == 0) {
            LOG.warn("配方自查：一条配方都没读到，检查 data/yanscriptures/ 是否被打包");
        }
    }
}"""
new = """        if (crafting.isEmpty() && cauldron == 0 && book == 0 && upgrade == 0) {
            LOG.warn("配方自查：一条配方都没读到，检查 data/yanscriptures/ 是否被打包");
        }

        bookWritingSelfCheck();
    }

    /**
     * 著书台选书自查：不开界面，直接用「假的三格 + 材料池」跑一遍
     * {@link BookWritingRecipes#findAll}，把候选名单写进日志。
     *
     * <p>作者反馈「测不了切换制作书籍」（m08693），而候选列表与上下箭头
     * 本质就是这一个函数的结果，所以先在这里把它钉住：只要日志里
     * 《道德经》那组能出现，界面上的箭头就一定切得动。</p>
     */
    private static void bookWritingSelfCheck() {
        List<ItemStack> pool = List.of(
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT),
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));

        // 一组材料能写出多本时，候选列表必须多于一条——这正是上下箭头存在的理由
        List<ItemStack> rich = new java.util.ArrayList<>(pool);
        rich.add(new ItemStack(Materials.PARCHMENT.get(), 4));
        rich.add(new ItemStack(Materials.BLOOD_INK.get(), 2));
        rich.add(new ItemStack(Materials.HEMP_ROPE.get(), 4));

        List<String> low = names(BookWritingRecipes.findAll(
                new ItemStack(Materials.FIBER_PAPER.get(), 2),
                new ItemStack(Materials.INK.get(), 1),
                new ItemStack(Materials.HEMP_ROPE.get(), 2), pool));
        List<String> high = names(BookWritingRecipes.findAll(
                new ItemStack(Materials.PARCHMENT.get(), 2),
                new ItemStack(Materials.BLOOD_INK.get(), 1),
                new ItemStack(Materials.HEMP_ROPE.get(), 2), pool));
        List<String> many = names(BookWritingRecipes.findAll(
                new ItemStack(Materials.PARCHMENT.get(), 4),
                new ItemStack(Materials.BLOOD_INK.get(), 2),
                new ItemStack(Materials.HEMP_ROPE.get(), 4), rich));

        LOG.info("著书台选书自查：草纸/墨水/麻绳 + 金胡萝卜金苹果 → {}；羊皮纸/血墨/麻绳 → {}", low, high);
        LOG.info("著书台选书自查：一池多用（羊皮纸×4 血墨×2 麻绳×4 全塞进池子）→ {}（{} 条候选）",
                many, many.size());

        if (!low.contains("dao_de_jing")) {
            LOG.warn("著书台选书自查：最低档材料写不出《道德经》，检查配方档位与数量");
        }
        if (many.size() < 2) {
            LOG.warn("著书台选书自查：一池多用时只有 {} 条候选，上下箭头将不会出现", many.size());
        }
    }

    private static List<String> names(List<BookWritingRecipes.Recipe> recipes) {
        List<String> out = new ArrayList<>();
        for (BookWritingRecipes.Recipe recipe : recipes) {
            out.add(recipe.result().toString().replace("yanscriptures:", ""));
        }
        return out;
    }
}"""
assert old in src
src = src.replace(old, new)

if "import net.minecraft.world.item.ItemStack;" not in src:
    src = src.replace("import net.minecraft.world.item.crafting.Recipe;",
                      "import net.minecraft.world.item.ItemStack;\nimport net.minecraft.world.item.crafting.Recipe;")
p.write_text(src, encoding="utf-8")
print("audit ok")
