# -*- coding: utf-8 -*-
"""给 BookWritingTableBlockEntity 加上「配方选择 + 额外材料」支持。"""
from pathlib import Path

path = Path("src/main/java/com/example/examplemod/BookWritingTableBlockEntity.java")
src = path.read_text(encoding="utf-8")

# ---- 1. 类声明：实现 ContainerData（用原版 data slot 同步选中项） ----
old = "public class BookWritingTableBlockEntity extends BlockEntity implements Container {"
new = ("public class BookWritingTableBlockEntity extends BlockEntity\n"
       "        implements Container, net.minecraft.world.inventory.ContainerData {")
assert old in src, "class declaration not found"
src = src.replace(old, new)

# ---- 2. 字段：当前选中的候选配方 ----
old = "private final List<Integer> insertionOrder = new ArrayList<>();"
new = ("private final List<Integer> insertionOrder = new ArrayList<>();\n"
       "    /** 当前选中的是候选配方里的第几条（界面上的上下箭头改它）。 */\n"
       "    private int selectedIndex = 0;")
assert src.count(old) == 1, "insertionOrder field not unique"
src = src.replace(old, new)

# ---- 3. 替换 previewResult / craft / canConsume / consumeOne 整段 ----
start = src.index("    /** 三格选中的东西能写出什么；凑不成配方就是空。 */")
end = src.index("    private void giveOrDrop(Player player, ItemStack stack) {")
replacement = '''    /** 材料池里的东西（按槽位顺序，跳过空槽）。 */
    public List<ItemStack> pool() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    /** 当前三格 + 材料池能写出的所有书；材料池里东西多时会有好几条。 */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.findAll(items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT), pool());
    }

    /** 当前选中的配方；没有就是 null。 */
    public BookWritingRecipes.Recipe selectedRecipe() {
        List<BookWritingRecipes.Recipe> list = candidates();
        if (list.isEmpty()) {
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= list.size()) {
            selectedIndex = 0;
        }
        return list.get(selectedIndex);
    }

    /** 上下箭头：在候选配方里挪一格。 */
    public void cycleSelection(int delta) {
        List<BookWritingRecipes.Recipe> list = candidates();
        if (list.isEmpty()) {
            selectedIndex = 0;
        } else {
            selectedIndex = Math.floorMod(selectedIndex + delta, list.size());
        }
        setChanged();
        syncToClients();
    }

    /** 三格选中的东西能写出什么；凑不成配方就是空。 */
    public ItemStack previewResult() {
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        return recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.result());
    }

    /**
     * 扣一次材料并返回成品（书与笔/空书卷由调用方扣）。
     *
     * <p>扣的顺序：先从下面材料池里找同种物品，池里不够才扣选择槽里的。
     * 额外材料（金胡萝卜之类）只从材料池里扣。</p>
     */
    public ItemStack craft() {
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        // 先确认全都够再动手：否则「纸扣了、墨不够」会把纸白白吃掉
        if (!BookWritingRecipes.canCraft(recipe, items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT), pool())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(recipe.result());
        // 用料决定档次：三条材料线各记自己的档位，并给这本书掷一项永久的额外属性
        int[] tiers = MaterialBonus.tiersOf(items.get(PAPER_SLOT), items.get(INK_SLOT), items.get(THREAD_SLOT));
        String bonusAttr = level == null
                ? MaterialBonus.ATTR_KEYS[0]
                : MaterialBonus.rollAttr(level.getRandom());
        MaterialBonus.stamp(result, tiers, bonusAttr);

        consume(recipe.paper(), recipe.paperCount(), PAPER_SLOT);
        consume(recipe.ink(), recipe.inkCount(), INK_SLOT);
        consume(recipe.thread(), recipe.threadCount(), THREAD_SLOT);
        for (ItemStack extra : recipe.extras()) {
            consume(extra.getItem(), extra.getCount(), -1);
        }
        syncToClients();
        return result;
    }

    /**
     * 扣掉指定物品若干个：先从材料池扣，池里不够再扣选择格。
     *
     * @param selectorSlot 选择格下标；{@code -1} 表示只认材料池（额外材料走这条）
     */
    private void consume(net.minecraft.world.item.Item item, int amount, int selectorSlot) {
        int remaining = amount;
        for (int slot = MATERIAL_START; slot < MATERIAL_END && remaining > 0; slot++) {
            ItemStack pooled = items.get(slot);
            if (pooled.isEmpty() || !pooled.is(item)) {
                continue;
            }
            int take = Math.min(remaining, pooled.getCount());
            pooled.shrink(take);
            remaining -= take;
            if (pooled.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
                forgetSlot(slot);
            }
        }
        if (remaining > 0 && selectorSlot >= 0) {
            ItemStack selector = items.get(selectorSlot);
            if (!selector.isEmpty() && selector.is(item)) {
                int take = Math.min(remaining, selector.getCount());
                selector.shrink(take);
                if (selector.isEmpty()) {
                    items.set(selectorSlot, ItemStack.EMPTY);
                }
            }
        }
        setChanged();
    }

'''
src = src[:start] + replacement + src[end:]

# ---- 4. ContainerData 的两个数：选中项 + 候选数量 ----
old = "    public Component getDisplayName() {"
new = '''    // ==================== 界面同步的两个整数 ====================

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public int get(int index) {
        return index == 0 ? selectedIndex : candidates().size();
    }

    @Override
    public void set(int index, int value) {
        if (index == 0) {
            selectedIndex = Math.max(0, value);
        }
    }

    public Component getDisplayName() {'''
assert src.count(old) == 1, "getDisplayName not unique"
src = src.replace(old, new)

# ---- 5. 存档：记住选中项 ----
old = '        tag.put("InsertOrder", order);\n    }'
new = '        tag.put("InsertOrder", order);\n        tag.putInt("SelectedIndex", selectedIndex);\n    }'
assert src.count(old) == 1, "save tail not found"
src = src.replace(old, new)

old = "        // 老存档没有顺序信息时，按槽位顺序兜底"
new = '        selectedIndex = tag.getInt("SelectedIndex");\n        // 老存档没有顺序信息时，按槽位顺序兜底'
assert src.count(old) == 1, "load tail not found"
src = src.replace(old, new)

path.write_text(src, encoding="utf-8")
print("patched:", len(src.splitlines()), "lines")
