"""著书台：候选改成「三格不空就列出所有档位够的书」，箭头挪到成品框右侧，另需材料常显（2026-10-31）。"""

from pathlib import Path

# ---------------- 1) BookWritingRecipes ----------------
p = Path("src/main/java/com/example/examplemod/BookWritingRecipes.java")
s = p.read_text(encoding="utf-8")

anchor = """    /** 这一条配方在给定的三格 + 材料池下做不做得出来。 */"""
add = """    /**
     * 三格一放好就列出来的候选：<b>只看档位，不看数量、不看材料池</b>。
     *
     * <p>作者的规则（2026-10-31）：纸、墨、线放好之后，成品框旁就要出现上下小三角，
     * 点一下能换下一本书，同时显示那一本还缺哪些下方材料。
     * 所以这里**不**因为「材料池里没金胡萝卜」就把《道德经》筛掉——
     * 缺什么由界面单独列出来，写不写得成由 {@link #canCraft} 说了算。</p>
     */
    public static List<Recipe> matching(ItemStack paper, ItemStack ink, ItemStack thread) {
        List<Recipe> out = new ArrayList<>();
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return out;
        }
        int p = MaterialBonus.paperTier(paper);
        int i = MaterialBonus.inkTier(ink);
        int t = MaterialBonus.threadTier(thread);
        for (Recipe recipe : RECIPES) {
            if (p >= recipe.paperTier() && i >= recipe.inkTier() && t >= recipe.threadTier()) {
                out.add(recipe);
            }
        }
        return out;
    }

    /**
     * 这条配方还差哪些东西：三样基础材料不够的份数 + 材料池里缺的额外材料。
     *
     * <p>返回的 {@link ItemStack} 只用来显示名字与份数，数量 = 还差的份数。
     * 够了就返回空表。</p>
     */
    public static List<ItemStack> missing(Recipe recipe, ItemStack paper, ItemStack ink, ItemStack thread,
                                          List<ItemStack> pool) {
        List<ItemStack> out = new ArrayList<>();
        int paperShort = recipe.paperCount() - available(paper.getItem(), paper, pool);
        if (paperShort > 0) {
            out.add(new ItemStack(paper.getItem(), paperShort));
        }
        int inkShort = recipe.inkCount() - available(ink.getItem(), ink, pool);
        if (inkShort > 0) {
            out.add(new ItemStack(ink.getItem(), inkShort));
        }
        int threadShort = recipe.threadCount() - available(thread.getItem(), thread, pool);
        if (threadShort > 0) {
            out.add(new ItemStack(thread.getItem(), threadShort));
        }
        for (ItemStack extra : recipe.extras()) {
            int short_ = extra.getCount() - poolCount(extra.getItem(), pool);
            if (short_ > 0) {
                ItemStack copy = extra.copy();
                copy.setCount(short_);
                out.add(copy);
            }
        }
        return out;
    }

    /** 这一条配方在给定的三格 + 材料池下做不做得出来。 */"""

assert anchor in s
s = s.replace(anchor, add, 1)
p.write_text(s, encoding="utf-8")
print("recipes ok")

# ---------------- 2) 方块实体：候选只看档位 ----------------
p = Path("src/main/java/com/example/examplemod/BookWritingTableBlockEntity.java")
s = p.read_text(encoding="utf-8")
old = """    /** 当前三格 + 材料池能写出的所有书；材料池里东西多时会有好几条。 */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.findAll(items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT), pool());
    }"""
new = """    /**
     * 三格放好后能写的所有书：<b>只看档位</b>，不看材料池。
     *
     * <p>作者 2026-10-31：小三角要在「三格放好」时就出现，而不是等材料池凑齐；
     * 缺什么由界面上的「另需」行显示。</p>
     */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.matching(items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT));
    }"""
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("blockentity ok")

# ---------------- 3) 菜单：候选 + 缺料列表 ----------------
p = Path("src/main/java/com/example/examplemod/BookWritingTableMenu.java")
s = p.read_text(encoding="utf-8")
old = """    /** 三格 + 材料池现在能写出哪几本书（材料池里东西多时会有好几条）。 */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.findAll(container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT),
                poolItems());
    }"""
new = """    /** 三格放好后能写哪几本书：只看档位，不看材料池（作者 2026-10-31）。 */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.matching(container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT));
    }

    /** 当前选中的这本书还缺哪些材料（空表 = 可以写）。 */
    public List<ItemStack> missing() {
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        if (recipe == null) {
            return List.of();
        }
        return BookWritingRecipes.missing(recipe,
                container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT),
                poolItems());
    }"""
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("menu ok")

# ---------------- 4) 成品提示：先取名字再加进背包 ----------------
p = Path("src/main/java/com/example/examplemod/BookWritingTableBlock.java")
s = p.read_text(encoding="utf-8")
old = """        scroll.shrink(1);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }"""
new = """        scroll.shrink(1);
        // 先把名字取下来：原版 Inventory#add 放得下时会把 result 直接缩到 0 个，
        // 之后再取 getHoverName() 拿到的就是「空气」了（2026-10-31 实测）。
        Component resultName = result.getHoverName().copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }"""
assert old in s
s = s.replace(old, new)
s = s.replace(
    'Component.translatable("message.yanscriptures.book_table.crafted", result.getHoverName()),',
    'Component.translatable("message.yanscriptures.book_table.crafted", resultName),')
p.write_text(s, encoding="utf-8")
print("block ok")
