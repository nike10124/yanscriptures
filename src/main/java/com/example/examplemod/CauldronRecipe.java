package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 一条「往水炼药锅里扔东西」的配方。
 *
 * <p>JSON 放在 {@code data/yanscriptures/cauldron/*.json}，格式：</p>
 * <pre>{@code
 * {
 *   "ingredients": [ { "item": "minecraft:paper" }, { "tag": "minecraft:logs" } ],
 *   "result": { "item": "yanscriptures:fiber_paper", "count": 2 }
 * }
 * }</pre>
 *
 * <p>每种原料各消耗 1 个，扔进去的顺序无所谓。</p>
 */
public record CauldronRecipe(List<Ingredient> ingredients, ItemStack result) {

    public static CauldronRecipe fromJson(JsonObject json) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Ingredient ingredient = Ingredient.fromJson(array.get(i), false);
            if (ingredient == Ingredient.EMPTY) {
                throw new IllegalArgumentException("Invalid ingredient at index " + i);
            }
            ingredients.add(ingredient);
        }
        JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
        ItemStack result = net.minecraftforge.common.crafting.CraftingHelper
                .getItemStack(resultJson, false);
        return new CauldronRecipe(List.copyOf(ingredients), result);
    }

    public NonNullList<Ingredient> ingredientList() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients);
        return list;
    }
}
