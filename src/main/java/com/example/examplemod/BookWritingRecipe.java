package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 著书台的一条配方（2026-11-01 数据化，作者 m12715 的 A 方案）。
 *
 * <p>以前这张表硬编码在 {@link BookWritingRecipes} 里，整合包作者用 KubeJS
 * <b>碰不到</b>；现在它是一条真正的 {@link Recipe}，JSON 放在
 * {@code data/<命名空间>/recipes/book_writing/*.json}，KubeJS 可以直接
 * {@code event.custom({type: "yanscriptures:book_writing", ...})} 增删改。</p>
 *
 * <p>配方写的是<b>档位</b>而不是具体物品：纸 / 墨 / 线各要哪一档、要几个。
 * 高等级材料可以写低等级的书，这条规则在 {@link BookWritingRecipes#canCraft} 里。</p>
 *
 * <p>容器布局（{@link BookWritingTableMenu} 只有三个可见格，材料池是隐藏的）：
 * 第 0 格纸、第 1 格墨、第 2 格线、第 3 格起是材料池。</p>
 */
public class BookWritingRecipe implements Recipe<Container> {

    /** 配方类型：{@code yanscriptures:book_writing}。 */
    public static final RecipeType<BookWritingRecipe> TYPE =
            RecipeType.simple(new ResourceLocation(ExampleMod.MODID, "book_writing"));

    /** 容器里三个选择格的下标，必须和 {@link BookWritingTableMenu} 的槽位顺序一致。 */
    public static final int SLOT_PAPER = 0;
    public static final int SLOT_INK = 1;
    public static final int SLOT_THREAD = 2;
    /** 材料池起始下标。 */
    public static final int SLOT_POOL = 3;

    private final ResourceLocation id;
    private final int paperTier;
    private final int paperCount;
    private final int inkTier;
    private final int inkCount;
    private final int threadTier;
    private final int threadCount;
    private final List<ItemStack> extras;
    private final ItemStack result;

    public BookWritingRecipe(ResourceLocation id,
                             int paperTier, int paperCount,
                             int inkTier, int inkCount,
                             int threadTier, int threadCount,
                             List<ItemStack> extras, ItemStack result) {
        this.id = id;
        this.paperTier = paperTier;
        this.paperCount = paperCount;
        this.inkTier = inkTier;
        this.inkCount = inkCount;
        this.threadTier = threadTier;
        this.threadCount = threadCount;
        this.extras = List.copyOf(extras);
        this.result = result.copy();
    }

    public int paperTier() {
        return paperTier;
    }

    public int paperCount() {
        return paperCount;
    }

    public int inkTier() {
        return inkTier;
    }

    public int inkCount() {
        return inkCount;
    }

    public int threadTier() {
        return threadTier;
    }

    public int threadCount() {
        return threadCount;
    }

    /** 材料池里另外要的东西（金胡萝卜、金苹果之类），只能从材料池出。 */
    public List<ItemStack> extras() {
        return extras;
    }

    /** 成品。<b>没有</b>材料加成 NBT：档位由台子按实际用料现算（{@code MaterialBonus#stamp}）。 */
    public ItemStack result() {
        return result.copy();
    }

    /** 材料池里的东西（跳过空槽）。 */
    public static List<ItemStack> poolOf(Container container) {
        List<ItemStack> pool = new ArrayList<>();
        for (int slot = SLOT_POOL; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                pool.add(stack);
            }
        }
        return pool;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return BookWritingRecipes.canCraft(this,
                container.getItem(SLOT_PAPER),
                container.getItem(SLOT_INK),
                container.getItem(SLOT_THREAD),
                poolOf(container));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        // 档位加成要用「实际用了哪种纸墨线」，只有台子知道，所以这里给干净的一本书
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    /** 不在原版配方书里出现（有自己的 JEI 分类与界面）。 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExampleMod.BOOK_WRITING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    // ==================== JSON / 网络 ====================

    /**
     * 读写 JSON 与网络。
     *
     * <p>字段故意<b>严格校验</b>：KubeJS 的 {@code event.custom} 不做任何检查，
     * 少一个字段只会在服务端静默失败，所以这里一律抛 {@link JsonSyntaxException}，
     * 让错误直接打在日志里、看得见是哪一条配方少了什么。</p>
     */
    public static class Serializer implements RecipeSerializer<BookWritingRecipe> {

        @Override
        public BookWritingRecipe fromJson(ResourceLocation id, JsonObject json) {
            int paperTier = lineTier(json, "paper");
            int paperCount = lineCount(json, "paper");
            int inkTier = lineTier(json, "ink");
            int inkCount = lineCount(json, "ink");
            int threadTier = lineTier(json, "thread");
            int threadCount = lineCount(json, "thread");

            List<ItemStack> extras = new ArrayList<>();
            if (json.has("extras")) {
                JsonArray array = GsonHelper.getAsJsonArray(json, "extras");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        throw new JsonSyntaxException("著书台配方 " + id + " 的 extras 里有一项不是对象");
                    }
                    extras.add(readItem(element.getAsJsonObject(), id, "extras"));
                }
            }

            ItemStack result = readResult(json, id);
            return new BookWritingRecipe(id, paperTier, paperCount, inkTier, inkCount,
                    threadTier, threadCount, extras, result);
        }

        private static int lineTier(JsonObject json, String line) {
            JsonObject object = line(json, line);
            int tier = GsonHelper.getAsInt(object, "tier", -1);
            if (tier < 0) {
                throw new JsonSyntaxException("著书台配方缺少 " + line + ".tier（0 = 任何档位都行）");
            }
            return tier;
        }

        private static int lineCount(JsonObject json, String line) {
            JsonObject object = line(json, line);
            int count = GsonHelper.getAsInt(object, "count", -1);
            if (count <= 0) {
                throw new JsonSyntaxException("著书台配方 " + line + ".count 必须 ≥ 1");
            }
            return count;
        }

        private static JsonObject line(JsonObject json, String line) {
            if (!json.has(line) || !json.get(line).isJsonObject()) {
                throw new JsonSyntaxException("著书台配方缺少 \"" + line + "\" 对象（{ \"tier\": 1, \"count\": 1 }）");
            }
            return json.getAsJsonObject(line);
        }

        private static ItemStack readItem(JsonObject object, ResourceLocation id, String where) {
            String name = GsonHelper.getAsString(object, "item", "");
            if (name.isEmpty()) {
                throw new JsonSyntaxException("著书台配方 " + id + " 的 " + where + " 缺少 \"item\"");
            }
            ResourceLocation key = ResourceLocation.tryParse(name);
            Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            if (item == null) {
                throw new JsonSyntaxException("著书台配方 " + id + " 的 " + where + " 引用了不存在的物品：" + name);
            }
            int count = GsonHelper.getAsInt(object, "count", 1);
            if (count <= 0) {
                throw new JsonSyntaxException("著书台配方 " + id + " 的 " + where + " count 必须 ≥ 1");
            }
            return new ItemStack(item, count);
        }

        private static ItemStack readResult(JsonObject json, ResourceLocation id) {
            if (!json.has("result")) {
                throw new JsonSyntaxException("著书台配方 " + id + " 缺少 \"result\"");
            }
            JsonElement element = json.get("result");
            // 允许简写 "result": "yanscriptures:dao_de_jing"
            if (element.isJsonPrimitive()) {
                ResourceLocation key = ResourceLocation.tryParse(element.getAsString());
                Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
                if (item == null) {
                    throw new JsonSyntaxException("著书台配方 " + id + " 的 result 不存在：" + element.getAsString());
                }
                return new ItemStack(item);
            }
            if (!element.isJsonObject()) {
                throw new JsonSyntaxException("著书台配方 " + id + " 的 result 必须是字符串或对象");
            }
            return readItem(element.getAsJsonObject(), id, "result");
        }

        @Override
        public BookWritingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int paperTier = buffer.readVarInt();
            int paperCount = buffer.readVarInt();
            int inkTier = buffer.readVarInt();
            int inkCount = buffer.readVarInt();
            int threadTier = buffer.readVarInt();
            int threadCount = buffer.readVarInt();
            List<ItemStack> extras = buffer.readList(FriendlyByteBuf::readItem);
            ItemStack result = buffer.readItem();
            return new BookWritingRecipe(id, paperTier, paperCount, inkTier, inkCount,
                    threadTier, threadCount, extras, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, BookWritingRecipe recipe) {
            buffer.writeVarInt(recipe.paperTier);
            buffer.writeVarInt(recipe.paperCount);
            buffer.writeVarInt(recipe.inkTier);
            buffer.writeVarInt(recipe.inkCount);
            buffer.writeVarInt(recipe.threadTier);
            buffer.writeVarInt(recipe.threadCount);
            buffer.writeCollection(recipe.extras, FriendlyByteBuf::writeItem);
            buffer.writeItem(recipe.result);
        }
    }
}
