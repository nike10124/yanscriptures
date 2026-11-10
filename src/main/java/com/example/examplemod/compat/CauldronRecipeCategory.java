package com.example.examplemod.compat;

import java.util.List;

import com.example.examplemod.CauldronRecipe;
import com.example.examplemod.ExampleMod;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI 里的「炼药锅投料」分类。
 *
 * <p>把 {@code data/yanscriptures/cauldron/*.json} 里的配方摆成
 * 「最多 9 样原料 → 箭头 → 产物」的样子，催化剂是水炼药锅。</p>
 */
public class CauldronRecipeCategory implements IRecipeCategory<CauldronRecipe> {

    public static final RecipeType<CauldronRecipe> TYPE =
            RecipeType.create(ExampleMod.MODID, "cauldron", CauldronRecipe.class);

    private static final int WIDTH = 132;
    private static final int HEIGHT = 64;

    private final IDrawable background;
    private final IDrawable icon;

    public CauldronRecipeCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(Blocks.WATER_CAULDRON));
    }

    @Override
    public RecipeType<CauldronRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.yanscriptures.cauldron");
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
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CauldronRecipe recipe, IFocusGroup focuses) {
        List<Ingredient> ingredients = recipe.ingredients();
        for (int index = 0; index < ingredients.size() && index < 9; index++) {
            int column = index % 3;
            int row = index / 3;
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                            4 + column * 20, 6 + row * 20)
                    .addItemStacks(List.of(ingredients.get(index).getItems()));
        }
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 106, 22)
                .addItemStack(recipe.result());
    }
}
