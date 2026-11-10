package com.example.examplemod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 著书台的配方工具：<b>查表 + 档位判定 + 扣料计算</b>，本身不存任何配方数据。
 *
 * <p>配方自 2026-11-01 起是数据驱动的（{@link BookWritingRecipe}），放在
 * {@code data/<命名空间>/recipes/book_writing/*.json}，整合包作者可以用 KubeJS
 * 增删改；这里只负责「拿当前世界里的配方 → 排序 → 判断够不够」。</p>
 *
 * <p>配方里写的是<b>档位</b>而不是具体物品：高等级的纸可以写低等级的书，
 * 所以「两张草纸」的《道德经》用羊皮纸、至邪之笺、寂灭之笺都成立。
 * 想加一档新材料，只要把它塞进 {@code #yanscriptures:papers / inks / threads} 标签。</p>
 */
public final class BookWritingRecipes {

    private BookWritingRecipes() {
    }

    /** 当前世界里的全部著书台配方；世界还没加载好时给空表。 */
    public static List<BookWritingRecipe> all(Level level) {
        if (level == null) {
            return List.of();
        }
        return List.copyOf(level.getRecipeManager().getAllRecipesFor(BookWritingRecipe.TYPE));
    }

    /**
     * 三格 + 材料池，能写出哪几本书（顺序按档位从低到高）。
     *
     * <p>材料池里可能同时有纸、墨、线和金胡萝卜，所以一次可能满足好几本。</p>
     */
    public static List<BookWritingRecipe> findAll(Level level, ItemStack paper, ItemStack ink, ItemStack thread,
                                                 List<ItemStack> pool) {
        List<BookWritingRecipe> out = new ArrayList<>();
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return out;
        }
        for (BookWritingRecipe recipe : all(level)) {
            if (canCraft(recipe, paper, ink, thread, pool)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /**
     * 三格一放好就列出来的候选：<b>只列这三种基础材料档次够得着的书</b>，按档位和从小到大排。
     *
     * <p>作者的规则（2026-10-31 定候选上下三角；2026-11-10 改为按材料过滤）：纸、墨、线放好之后，
     * 成品框旁的上下三角只在这几本能写的书之间切换——草纸放上去就只看到草纸能写的那些，
     * 不会让玩家点到一本「还要换纸」的书。额外材料缺不缺由 {@link #missing} 列出，
     * 真写不写得成由 {@link #canCraft} 说了算。</p>
     */
    public static List<BookWritingRecipe> matching(Level level, ItemStack paper, ItemStack ink, ItemStack thread) {
        List<BookWritingRecipe> out = new ArrayList<>();
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return out;
        }
        int paperTier = MaterialBonus.paperTier(paper);
        int inkTier = MaterialBonus.inkTier(ink);
        int threadTier = MaterialBonus.threadTier(thread);
        for (BookWritingRecipe recipe : all(level)) {
            // 档位只升不降：手里是三档纸，就能写要求一到三档纸的书；要求更高的直接不列。
            // 2026-11-10 作者：普通（稀有及以下）书不卡档位，只有史诗书与需 Ⅳ 档纸的终局书才要求指定档。
            if (!strictTiers(recipe)
                    || (recipe.paperTier() <= paperTier
                    && recipe.inkTier() <= inkTier
                    && recipe.threadTier() <= threadTier)) {
                out.add(recipe);
            }
        }
        // 便宜的排前面：《道德经》放好纸墨线就能看到，一开始不用先点箭头。
        // 2026-11-10 补 id 兜底：只比三档之和时，同分那几本的先后取决于配方表遍历顺序，
        // 客户端（客户端配方表）与服务端（存档配方表）可能不一样 → 同一个下标指向两本书，
        // 界面显示《罪与罚》、服务端却按《天工开物》扣料。作者遇到的「显示是罪与罚、就是写不出来」正是这个。
        out.sort(Comparator.comparingInt(
                        (BookWritingRecipe recipe) -> recipe.paperTier() + recipe.inkTier() + recipe.threadTier())
                .thenComparing(recipe -> recipe.getId().toString()));
        return out;
    }

    /**
     * 这条配方还差哪些东西：基础材料不够的份数 + 材料池里缺的额外材料。
     *
     * <p>返回的 {@link ItemStack} 只用来显示名字与份数，数量 = 还差的份数；够了就返回空表。</p>
     */
    public static List<ItemStack> missing(BookWritingRecipe recipe, ItemStack paper, ItemStack ink,
                                          ItemStack thread, List<ItemStack> pool) {
        List<ItemStack> out = new ArrayList<>();
        boolean strict = strictTiers(recipe);
        if (strict && MaterialBonus.paperTier(paper) < recipe.paperTier()) {
            // 档位不够：直接按配方要求的那一档报，玩家知道该换成什么纸
            out.add(new ItemStack(MaterialBonus.paperOfTier(recipe.paperTier()), recipe.paperCount()));
        } else {
            int paperShort = recipe.paperCount() - available(paper.getItem(), paper, pool);
            if (paperShort > 0) {
                out.add(new ItemStack(paper.getItem(), paperShort));
            }
        }
        if (strict && MaterialBonus.inkTier(ink) < recipe.inkTier()) {
            out.add(new ItemStack(MaterialBonus.inkOfTier(recipe.inkTier()), recipe.inkCount()));
        } else {
            int inkShort = recipe.inkCount() - available(ink.getItem(), ink, pool);
            if (inkShort > 0) {
                out.add(new ItemStack(ink.getItem(), inkShort));
            }
        }
        if (strict && MaterialBonus.threadTier(thread) < recipe.threadTier()) {
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

    /**
     * 这本书的配方是否「卡档位」。
     *
     * <p>作者 2026-11-10：低稀有度（普通/稀有）的书三种基础材料随手拿哪一档都行，
     * 只有高等级的书才必须用指定档的材料。这里把「史诗」与「需要 Ⅳ 档纸」的终局书算作要卡档，
     * 其余一律放开；成品强不强仍然取决于实际用料的档位（材料加成不骗人）。</p>
     */
    public static boolean strictTiers(BookWritingRecipe recipe) {
        return recipe.result().getRarity() == net.minecraft.world.item.Rarity.EPIC
                || recipe.paperTier() >= 4;
    }

    /** 这一条配方在给定的三格 + 材料池下做不做得出来。 */
    public static boolean canCraft(BookWritingRecipe recipe, ItemStack paper, ItemStack ink, ItemStack thread,
                                   List<ItemStack> pool) {
        if (paper.isEmpty() || ink.isEmpty() || thread.isEmpty()) {
            return false;
        }
        // 档位只升不降：高等级材料能写低等级的书；不卡档的书随便哪一档都算数
        if (strictTiers(recipe)
                && (MaterialBonus.paperTier(paper) < recipe.paperTier()
                || MaterialBonus.inkTier(ink) < recipe.inkTier()
                || MaterialBonus.threadTier(thread) < recipe.threadTier())) {
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
