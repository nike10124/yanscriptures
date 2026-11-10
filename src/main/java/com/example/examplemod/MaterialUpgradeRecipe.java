package com.example.examplemod;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

/**
 * 锻造台上的「换料重铸」（2026-10-31 新增，作者 m08401 指定）。
 *
 * <p>三格：
 * <ul>
 *   <li>模板（第一格）：空书卷，消耗 1 个；</li>
 *   <li>装备（中间格）：任意一本带档位的书；</li>
 *   <li>材料（第三格）：纸 / 墨 / 线任意一件。</li>
 * </ul>
 *
 * <p>效果：把这本书<b>对应那条材料线</b>的档位换成新材料的档位（只升不降），
 * 并重新掷一次随机额外属性。想换纸就放纸、想换墨就放墨、想换线就放线。</p>
 *
 * <p>用「只升不降」是刻意的：材料是消耗品，如果放低档材料能降档，
 * 玩家一不小心就把一本全 Ⅲ 的书洗成 Ⅰ；而「重掷属性」这个需求，用同档材料也能满足。</p>
 */
public class MaterialUpgradeRecipe implements SmithingRecipe {

    private final ResourceLocation id;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;

    public MaterialUpgradeRecipe(ResourceLocation id, Ingredient template, Ingredient base, Ingredient addition) {
        this.id = id;
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    public Ingredient template() {
        return template;
    }

    public Ingredient base() {
        return base;
    }

    public Ingredient addition() {
        return addition;
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack baseStack = container.getItem(1);
        return template.test(container.getItem(0))
                && base.test(baseStack)
                && addition.test(container.getItem(2));
        // 不要求 base 已经有 NBT：老存档里手写的书没有档位，也应该能在锻造台上从零开始加档
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        ItemStack baseStack = container.getItem(1);
        ItemStack additionStack = container.getItem(2);
        int line = MaterialBonus.lineOf(additionStack);
        int tier = MaterialBonus.tierOf(additionStack);
        if (line < 0 || tier <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = baseStack.copy();
        // 只动这一条线：档位只升不降，随机属性只重掷这条线的
        //（作者 m08693：换料不能把另外两条线的属性一起洗掉）
        MaterialBonus.replaceLine(result, line, tier, MaterialBonus.rollAttr(RandomSource.create()));
        result.setCount(1);
        return result;
    }

    /**
     * JEI 之类的界面只会拿这一项当「产物预览」。书本身带档位 NBT，
     * 但在这里不可能知道玩家会放什么材料，所以只给一本干净的书。
     */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        ItemStack[] candidates = base.getItems();
        if (candidates.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack preview = candidates[0].copy();
        preview.setCount(1);
        return preview;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExampleMod.MATERIAL_UPGRADE_SERIALIZER.get();
    }

    @Override
    public boolean isIncomplete() {
        return template.isEmpty() || base.isEmpty() || addition.isEmpty();
    }

    public static class Serializer implements RecipeSerializer<MaterialUpgradeRecipe> {

        @Override
        public MaterialUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            // 用 JsonElement 版本：addition 在这里是「标签数组」，getAsJsonObject 会直接抛异常
            Ingredient template = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "template"));
            Ingredient base = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "base"));
            Ingredient addition = Ingredient.fromJson(json.get("addition"));
            return new MaterialUpgradeRecipe(id, template, base, addition);
        }

        @Override
        public MaterialUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient template = Ingredient.fromNetwork(buffer);
            Ingredient base = Ingredient.fromNetwork(buffer);
            Ingredient addition = Ingredient.fromNetwork(buffer);
            return new MaterialUpgradeRecipe(id, template, base, addition);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, MaterialUpgradeRecipe recipe) {
            recipe.template.toNetwork(buffer);
            recipe.base.toNetwork(buffer);
            recipe.addition.toNetwork(buffer);
        }
    }
}
