package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 配方自查：服务器启动时把本模组的配方来源清点一遍，写一行日志。
 *
 * <p>为什么需要它——「新配方一敲定就得在 JEI 里看得到」这件事，靠三个通道：
 * <ol>
 *   <li><b>工作台 / 其它原版配方</b>：写成 {@code data/yanscriptures/recipes/*.json}，
 *       JEI 直接读原版配方表，自动收录；</li>
 *   <li><b>炼药锅投料</b>：写成 {@code data/yanscriptures/cauldron/*.json}，
 *       {@code JeiCompat} 读 {@link CauldronRecipes#all()} 自动收录；</li>
 *   <li><b>著书台</b>：写在 {@link BookWritingRecipes#RECIPES}，
 *       {@code JeiCompat} 读 {@link BookWritingRecipes#all()} 自动收录。</li>
 * </ol>
 * 前两条是数据包，加载失败只会静默少一条，所以这里每条启动都数一遍：
 * 日志里能看到「工作台 7 条 / 炼药锅 9 条 / 著书台 10 条」这类数字；
 * 如果哪天数字掉了，说明对应的 JSON 解析失败或被误删。</p>
 *
 * <p>另外检查一件最容易漏的事：著书台能产出的每一本书，都必须在
 * 创造模式物品栏 / JEI 物品列表里存在——否则 JEI 分类里会出现一个
 * 悬空的「产物槽」。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeAudit {

    private static final Logger LOG = LogUtils.getLogger();

    private RecipeAudit() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();

        List<ResourceLocation> crafting = new ArrayList<>();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            if (!ExampleMod.MODID.equals(recipe.getId().getNamespace())) {
                continue;
            }
            if (recipe.getType() == RecipeType.CRAFTING || recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                crafting.add(recipe.getId());
            }
        }

        int cauldron = CauldronRecipes.all().size();
        int book = 0;
        int upgrade = 0;
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            if (recipe instanceof BookWritingRecipe) {
                book++;
            }
            if (recipe instanceof MaterialUpgradeRecipe) {
                upgrade++;
            }
        }

        LOG.info("配方自查：工作台/原版 {} 条 {}，炼药锅 {} 条，著书台 {} 条，锻造台换料 {} 条",
                crafting.size(), crafting, cauldron, book, upgrade);

        if (crafting.isEmpty() && cauldron == 0 && book == 0 && upgrade == 0) {
            LOG.warn("配方自查：一条配方都没读到，检查 data/yanscriptures/ 是否被打包");
        }

        bookWritingSelfCheck(server.overworld());
        tierBonusSelfCheck();
    }

    /**
     * 档位加成本自查：造一本 Ⅳ/Ⅲ/Ⅲ、三条线分别掷中攻击/生命/护甲的书，
     * 把 {@link MaterialBonus#perAttribute} 的四项数值写进日志。
     * 期望：全属性 11%，攻击力再多 6%、生命上限 2.5%、护甲 2.5%。
     */
    private static void tierBonusSelfCheck() {
        ItemStack sample = new ItemStack(ExampleMod.BOOK_DAO_DE_JING.get());
        MaterialBonus.stamp(sample, new int[]{4, 3, 3}, new String[]{"attack", "health", "armor"});
        double[] perAttr = MaterialBonus.perAttribute(sample);
        LOG.info("档位加成自查：Ⅳ/Ⅲ/Ⅲ（攻击/生命/护甲）→ 攻击 {}%、生命 {}%、护甲 {}%、移速 {}%（全属性 {}%）",
                perAttr[0] * 100.0D, perAttr[1] * 100.0D, perAttr[2] * 100.0D, perAttr[3] * 100.0D,
                MaterialBonus.totalPercent(sample) * 100.0D);
    }

    /**
     * 著书台选书自查：不开界面，直接用「假的三格 + 材料池」跑一遍
     * {@link BookWritingRecipes#findAll}，把候选名单写进日志。
     *
     * <p>2026-11-11：配方按作者的材料表重排过（《道德经》已是 Ⅱ 档 + 4 种额外材料），
     * 原来写死的「草纸/墨水/麻绳 + 金胡萝卜金苹果」已对不上，改成一律从配方本身取需求，
     * 这样以后改配方表也不用再动自查。</p>
     */
    private static void bookWritingSelfCheck(net.minecraft.server.level.ServerLevel level) {
        List<BookWritingRecipe> all = BookWritingRecipes.all(level);
        BookWritingRecipe dao = null;
        BookWritingRecipe fattest = null;
        for (BookWritingRecipe recipe : all) {
            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(recipe.result().getItem());
            String path = key == null ? "" : key.getPath();
            if ("dao_de_jing".equals(path)) {
                dao = recipe;
            }
            if (fattest == null || recipe.extras().size() > fattest.extras().size()) {
                fattest = recipe;
            }
        }

        List<String> bare = names(BookWritingRecipes.matching(level,
                new ItemStack(Materials.FIBER_PAPER.get(), 64),
                new ItemStack(Materials.INK.get(), 64),
                new ItemStack(Materials.HEMP_ROPE.get(), 64)));
        List<String> bareHigh = names(BookWritingRecipes.matching(level,
                new ItemStack(Materials.ANNIHILATED_PARCHMENT.get(), 64),
                new ItemStack(Materials.UNHOLY_INK.get(), 64),
                new ItemStack(Materials.DRAGON_SINEW.get(), 64)));
        LOG.info("著书台候选自查：三格放好（最低档）、材料池为空 → {}（{} 条）", bare, bare.size());
        LOG.info("著书台候选自查：三格放好（最高档）、材料池为空 → {}（{} 条）", bareHigh, bareHigh.size());
        if (bare.isEmpty()) {
            LOG.warn("著书台候选自查：三格放好却没有候选，成品框旁的箭头不会出现");
        }

        if (dao == null) {
            LOG.warn("著书台选书自查：找不到《道德经》的配方，检查 data/yanscriptures/recipes/book_writing");
            return;
        }

        // 完全按《道德经》配方自己的需求摆一遍，应该能写出它
        List<ItemStack> daoPool = new java.util.ArrayList<>(dao.extras());
        List<String> exact = names(BookWritingRecipes.findAll(level,
                materialFor(0, dao.paperTier(), dao.paperCount()),
                materialFor(1, dao.inkTier(), dao.inkCount()),
                materialFor(2, dao.threadTier(), dao.threadCount()), daoPool));
        LOG.info("著书台选书自查：照《道德经》配方摆料 → {}（{} 条）", exact, exact.size());
        if (!exact.contains("dao_de_jing")) {
            LOG.warn("著书台选书自查：照配方摆料却写不出《道德经》，检查素材档位映射");
        }

        // 额外材料最多的那条：给足它的额外材料，候选里必须有它自己
        if (fattest != null) {
            List<String> many = names(BookWritingRecipes.findAll(level,
                    materialFor(0, fattest.paperTier(), fattest.paperCount()),
                    materialFor(1, fattest.inkTier(), fattest.inkCount()),
                    materialFor(2, fattest.threadTier(), fattest.threadCount()),
                    new java.util.ArrayList<>(fattest.extras())));
            LOG.info("著书台选书自查：额外材料最多的一条（{} 种）→ {}（{} 条）", fattest.extras().size(),
                    many, many.size());
            if (many.isEmpty()) {
                LOG.warn("著书台选书自查：给足额外材料仍无候选，检查 BookWritingRecipes.canCraft");
            }
        }
    }

    /** 按档位取对应的纸（kind 0）/ 墨（kind 1）/ 线（kind 2）。 */
    private static ItemStack materialFor(int kind, int tier, int count) {
        Item item;
        if (kind == 0) {
            item = switch (Math.max(1, Math.min(4, tier))) {
                case 2 -> Materials.PARCHMENT.get();
                case 3 -> Materials.CORRUPT_PARCHMENT.get();
                case 4 -> Materials.ANNIHILATED_PARCHMENT.get();
                default -> Materials.FIBER_PAPER.get();
            };
        } else if (kind == 1) {
            item = switch (Math.max(1, Math.min(3, tier))) {
                case 2 -> Materials.BLOOD_INK.get();
                case 3 -> Materials.UNHOLY_INK.get();
                default -> Materials.INK.get();
            };
        } else {
            item = switch (Math.max(1, Math.min(3, tier))) {
                case 2 -> Materials.GOLD_ROPE.get();
                case 3 -> Materials.DRAGON_SINEW.get();
                default -> Materials.HEMP_ROPE.get();
            };
        }
        return new ItemStack(item, Math.max(1, count));
    }

    private static List<String> names(List<BookWritingRecipe> recipes) {
        List<String> out = new ArrayList<>();
        for (BookWritingRecipe recipe : recipes) {
            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(recipe.result().getItem());
            out.add(key == null ? recipe.getId().toString() : key.getPath());
        }
        return out;
    }
}
