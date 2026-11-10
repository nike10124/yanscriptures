package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 读 {@code data/*​/cauldron/*.json} 里的炼药锅配方。
 *
 * <p>故意不走原版 {@code RecipeManager}：炼药锅的“配方”不是网格合成，
 * 而是一堆扔进去的东西，用原版配方体系反而更绕。这里用一份简单的重载监听器，
 * 服务端负责合成，客户端同一份数据供 JEI 展示。</p>
 */
public class CauldronRecipes extends SimpleJsonResourceReloadListener {

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "cauldron";

    private static volatile List<CauldronRecipe> recipes = List.of();

    public CauldronRecipes() {
        super(GSON, DIRECTORY);
    }

    public static List<CauldronRecipe> all() {
        return recipes;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        List<CauldronRecipe> loaded = new ArrayList<>();
        Map<ResourceLocation, CauldronRecipe> byId = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                CauldronRecipe recipe = CauldronRecipe.fromJson(json);
                loaded.add(recipe);
                byId.put(entry.getKey(), recipe);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "炼药锅配方 {} 读取失败：{}", entry.getKey(), exception.toString());
            }
        }
        recipes = List.copyOf(loaded);
        LOGGER.info("已载入 {} 条炼药锅配方", recipes.size());
    }

    /**
     * 在一堆“已扔进去的物品”里找一条能成的配方。
     *
     * <p>按数量匹配：同一种原料要 4 个时，可以来自 4 个各 1 个的物品实体，
     * 也可以来自一个 16 个的物品堆。返回每堆各吃掉几个。</p>
     */
    public static Match match(List<ItemStack> stacks) {
        for (CauldronRecipe recipe : recipes) {
            int[] counts = new int[stacks.size()];
            boolean ok = true;
            for (Ingredient ingredient : recipe.ingredients()) {
                int found = -1;
                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack stack = stacks.get(i);
                    if (counts[i] < stack.getCount() && ingredient.test(stack)) {
                        found = i;
                        break;
                    }
                }
                if (found < 0) {
                    ok = false;
                    break;
                }
                counts[found]++;
            }
            if (ok) {
                return new Match(recipe, counts);
            }
        }
        return null;
    }

    /** 命中的配方 + 每堆要消耗的数量（下标对应传入的 stacks）。 */
    public record Match(CauldronRecipe recipe, int[] counts) {
    }
}
