package com.example.examplemod.compat;

import java.util.List;

import com.example.examplemod.BookWritingRecipe;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.MaterialBonus;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 里的「著书台」分类：<b>一种纸 + 一种墨 + 一根线 → 一本书</b>。
 *
 * <p>配方来源是世界里的配方表（{@link BookWritingRecipe}，JSON 在
 * {@code data/<命名空间>/recipes/book_writing/}）—— 加一条 JSON，这里自动跟着变，
 * 整合包用 KubeJS 加的书也会出现在这个分类里。</p>
 */
public class BookWritingRecipeCategory implements IRecipeCategory<BookWritingRecipe> {

    public static final RecipeType<BookWritingRecipe> TYPE =
            RecipeType.create(ExampleMod.MODID, "book_writing", BookWritingRecipe.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 44;

    /** 额外材料单独占一行：最多 7 格（《如我所书》是 7 项），每格 18 宽。 */
    private static final int EXTRA_X = 4;
    private static final int EXTRA_Y = 24;
    private static final int EXTRA_STEP = 18;
    private static final int EXTRA_MAX = 7;

    private final IDrawable background;
    private final IDrawable icon;

    public BookWritingRecipeCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ExampleMod.BOOK_WRITING_TABLE_ITEM.get()));
    }

    @Override
    public RecipeType<BookWritingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.yanscriptures.book_writing");
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BookWritingRecipe recipe,
                          IFocusGroup focuses) {
        // 第一行：三条基础材料（纸 / 墨 / 线）与成品
        // 作者 2026-11-10：稀有及以下的书「三种基础材料任取一档都行」，
        // 所以这类配方在 JEI 里要把每一档都列成候选，而不是固定写死配方要求的档位；
        // 史诗与要求寂灭之笺的终局书仍然只显示那一个档位（strictTiers）。
        boolean strict = com.example.examplemod.BookWritingRecipes.strictTiers(recipe);
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 4, 4)
                .addItemStacks(tierStacks(strict, recipe.paperTier(), recipe.paperCount(),
                        MaterialBonus::paperOfTier));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 26, 4)
                .addItemStacks(tierStacks(strict, recipe.inkTier(), recipe.inkCount(),
                        MaterialBonus::inkOfTier));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, 48, 4)
                .addItemStacks(tierStacks(strict, recipe.threadTier(), recipe.threadCount(),
                        MaterialBonus::threadOfTier));
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 126, 4)
                .addItemStack(recipe.result());
        // 第二行：额外材料。作者 2026-11-10：一张配方最多 7 种，原来的「只画 2 格」显示不全。
        List<ItemStack> extras = recipe.extras();
        for (int i = 0; i < extras.size() && i < EXTRA_MAX; i++) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                    EXTRA_X + i * EXTRA_STEP, EXTRA_Y).addItemStack(extras.get(i));
        }
    }

    /** 这一格在 JEI 里该放哪些候选：固定档 → 只有那一个；不卡档 → 从要求档到最高档全列（含低档）。 */
    private static List<ItemStack> tierStacks(boolean strict, int requiredTier, int count,
                                              java.util.function.IntFunction<net.minecraft.world.item.Item> ofTier) {
        if (strict) {
            return List.of(new ItemStack(ofTier.apply(requiredTier), count));
        }
        List<ItemStack> out = new java.util.ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            net.minecraft.world.item.Item item = ofTier.apply(tier);
            if (item != null) {
                out.add(new ItemStack(item, count));
            }
        }
        return out;
    }

    /** 排成「纸 · 墨 · 线 · 额外材料」的一行文字，JEI 信息栏里也能读。 */
    public static List<String> describe(BookWritingRecipe recipe) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(MaterialBonus.paperOfTier(recipe.paperTier()).getDescriptionId() + " ×" + recipe.paperCount());
        lines.add(MaterialBonus.inkOfTier(recipe.inkTier()).getDescriptionId() + " ×" + recipe.inkCount());
        lines.add(MaterialBonus.threadOfTier(recipe.threadTier()).getDescriptionId() + " ×" + recipe.threadCount());
        for (ItemStack extra : recipe.extras()) {
            lines.add(extra.getDescriptionId() + " ×" + extra.getCount());
        }
        return lines;
    }
}
