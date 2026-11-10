package com.example.examplemod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 著书台的配方表：<b>纸 + 墨 + 线</b>三格决定写出来的是哪本书，额外的材料放在台子下半部分的材料池里。
 *
 * <p>配方里写的是<b>档位</b>而不是具体物品（作者 m08693）：高等级的纸可以写低等级的书，
 * 所以「两张草纸」的《道德经》用羊皮纸、至邪之笺、寂灭之笺都成立。
 * 想加一档新材料，只要把它塞进 {@code #yanscriptures:papers / inks / threads} 标签，
 * 认不出的物品按第一档算，不用改 Java。</p>
 *
 * <p>这个类只是「数据 + 查表」，不碰界面也不碰方块逻辑。作者要改配方，只改下面的
 * {@code RECIPES} 列表即可；顺序即优先级。</p>
 */
public final class BookWritingRecipes {

    /**
     * 一条配方：纸 / 墨 / 线各要哪一档、总共要几个，外加材料池里另外要的东西 → 成品。
     *
     * <p>三个计数是<b>总共要几个</b>（选择格里那一个也算在内）。额外材料（{@code extras}）
     * 只能从材料池里出，因为界面里只有三个格子。</p>
     */
    public record Recipe(int paperTier, int paperCount, int inkTier, int inkCount,
                         int threadTier, int threadCount, List<ItemStack> extras, Item result) {

        /** 基础配方：三样各一，不要额外材料。 */
        public Recipe(int paperTier, int inkTier, int threadTier, Item result) {
            this(paperTier, 1, inkTier, 1, threadTier, 1, List.of(), result);
        }
    }

    /**
     * 现有配方。
     *
     * <p>《道德经》已由作者定稿；其余九本仍是草案档位，等作者逐本定。
     * 作者说到「书籍的合成表」时，指的就是这里。</p>
     */
    private static final List<Recipe> RECIPES = new ArrayList<>(List.of(
            // 哲学（《道德经》档位最低，任何纸墨线都写得出来）
            new Recipe(1, 2, 1, 1, 1, 2,
                    List.of(new ItemStack(Items.GOLDEN_CARROT), new ItemStack(Items.GOLDEN_APPLE)),
                    ExampleMod.BOOK_DAO_DE_JING.get()),
            new Recipe(2, 2, 2, ExampleMod.BOOK_REPUBLIC.get()),
            new Recipe(2, 3, 2, ExampleMod.BOOK_ZARATHUSTRA.get()),

            // 文学
            new Recipe(3, 2, 3, ExampleMod.BOOK_DIVINE_COMEDY.get()),
            new Recipe(2, 2, 3, ExampleMod.BOOK_DON_QUIXOTE.get()),
            new Recipe(3, 3, 2, ExampleMod.BOOK_HUNDRED_YEARS.get()),

            // 理科
            new Recipe(2, 2, 2, ExampleMod.BOOK_EUCLID.get()),
            new Recipe(1, 2, 2, ExampleMod.BOOK_ORIGIN.get()),
            new Recipe(1, 3, 2, ExampleMod.BOOK_NINE_CHAPTERS.get()),

            // 非名著
            new Recipe(1, 3, 3, ExampleMod.BOOK_OF_PEACE.get())
    ));

    private BookWritingRecipes() {
    }

    /** 只读视图，给 JEI 这类「只看不改」的地方用（顺序即优先级）。 */
    public static List<Recipe> all() {
        return List.copyOf(RECIPES);
    }

    /**
     * 三格 + 材料池，能写出哪几本书（顺序即列表顺序）。
     *
     * <p>材料池里可能同时有纸、墨、线和金胡萝卜，所以一次可能满足好几本，
     * 界面上就用上下箭头在这份列表里挑。</p>
     */
    public static List<Recipe> findAll(ItemStack paper, ItemStack ink, ItemStack thread, List<ItemStack> pool) {
        List<Recipe> out = new ArrayList<>();
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return out;
        }
        for (Recipe recipe : RECIPES) {
            if (canCraft(recipe, paper, ink, thread, pool)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /**
     * 三格一放好就列出来的候选：<b>全部书写法</b>，按「纸档位 + 墨档位 + 线档位」从小到大排。
     *
     * <p>作者的规则（2026-10-31）：纸、墨、线放好之后，成品框旁就要出现上下小三角，
     * 点一下能换下一本书，同时显示那一本还缺哪些材料（含档位不够时该换哪种纸/墨/线）。
     * 所以这里既不看材料池、也不看档位够不够——缺什么由 {@link #missing} 列出来，
     * 真写不写得成由 {@link #canCraft} 说了算。</p>
     */
    public static List<Recipe> matching(ItemStack paper, ItemStack ink, ItemStack thread) {
        List<Recipe> out = new ArrayList<>();
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return out;
        }
        out.addAll(RECIPES);
        // 便宜的排前面：《道德经》放好纸墨线就能看到，一开始不用先点箭头
        out.sort(Comparator.comparingInt(
                recipe -> recipe.paperTier() + recipe.inkTier() + recipe.threadTier()));
        return out;
    }

    /**
     * 这条配方还差哪些东西：基础材料不够的份数 + 材料池里缺的额外材料。
     *
     * <p>返回的 {@link ItemStack} 只用来显示名字与份数，数量 = 还差的份数；够了就返回空表。</p>
     */
    public static List<ItemStack> missing(Recipe recipe, ItemStack paper, ItemStack ink, ItemStack thread,
                                          List<ItemStack> pool) {
        List<ItemStack> out = new ArrayList<>();
        if (MaterialBonus.paperTier(paper) < recipe.paperTier()) {
            // 档位不够：直接按配方要求的那一档报，玩家知道该换成什么纸
            out.add(new ItemStack(MaterialBonus.paperOfTier(recipe.paperTier()), recipe.paperCount()));
        } else {
            int paperShort = recipe.paperCount() - available(paper.getItem(), paper, pool);
            if (paperShort > 0) {
                out.add(new ItemStack(paper.getItem(), paperShort));
            }
        }
        if (MaterialBonus.inkTier(ink) < recipe.inkTier()) {
            out.add(new ItemStack(MaterialBonus.inkOfTier(recipe.inkTier()), recipe.inkCount()));
        } else {
            int inkShort = recipe.inkCount() - available(ink.getItem(), ink, pool);
            if (inkShort > 0) {
                out.add(new ItemStack(ink.getItem(), inkShort));
            }
        }
        if (MaterialBonus.threadTier(thread) < recipe.threadTier()) {
            out.add(new ItemStack(MaterialBonus.threadOfTier(recipe.threadTier()), recipe.threadCount()));
        } else {
            int threadShort = recipe.threadCount() - available(thread.getItem(), thread, pool);
            if (threadShort > 0) {
                out.add(new ItemStack(thread.getItem(), threadShort));
            }
        }
        for (ItemStack extra : recipe.extras()) {
            int shortfall = extra.getCount() - poolCount(extra.getItem(), pool);
            if (shortfall > 0) {
                ItemStack copy = extra.copy();
                copy.setCount(shortfall);
                out.add(copy);
            }
        }
        return out;
    }

    /** 这一条配方在给定的三格 + 材料池下做不做得出来。 */
    public static boolean canCraft(Recipe recipe, ItemStack paper, ItemStack ink, ItemStack thread,
                                   List<ItemStack> pool) {
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return false;
        }
        // 档位只升不降：高等级材料能写低等级的书
        if (MaterialBonus.paperTier(paper) < recipe.paperTier()
                || MaterialBonus.inkTier(ink) < recipe.inkTier()
                || MaterialBonus.threadTier(thread) < recipe.threadTier()) {
            return false;
        }
        if (available(paper.getItem(), paper, pool) < recipe.paperCount()) {
            return false;
        }
        if (available(ink.getItem(), ink, pool) < recipe.inkCount()) {
            return false;
        }
        if (available(thread.getItem(), thread, pool) < recipe.threadCount()) {
            return false;
        }
        for (ItemStack extra : recipe.extras()) {
            if (poolCount(extra.getItem(), pool) < extra.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** 选择格 + 材料池里，某种东西一共有几个。 */
    public static int available(Item item, ItemStack selector, List<ItemStack> pool) {
        int count = selector.is(item) ? selector.getCount() : 0;
        return count + poolCount(item, pool);
    }

    /** 材料池里某种东西一共有几个。 */
    public static int poolCount(Item item, List<ItemStack> pool) {
        int count = 0;
        for (ItemStack stack : pool) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 某一格能放什么：纸/墨/线三张标签，认不出的东西也放得进去（当第一档算）。 */
    public static boolean isPaper(ItemStack stack) {
        return stack.is(Materials.PAPERS);
    }

    public static boolean isInk(ItemStack stack) {
        return stack.is(Materials.INKS);
    }

    public static boolean isThread(ItemStack stack) {
        return stack.is(Materials.THREADS);
    }

    /** 书槽能放的东西：书与笔，或任意带 {@code #yanscriptures:books} 标签的书。 */
    public static boolean isWritingTool(ItemStack stack) {
        return stack.is(Items.WRITABLE_BOOK)
                || stack.is(Items.WRITTEN_BOOK)
                || stack.is(BookWritingTableBlockEntity.BOOKS_TAG);
    }
}
