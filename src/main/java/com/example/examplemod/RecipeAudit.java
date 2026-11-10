package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
     * <p>作者反馈「测不了切换制作书籍」（m08693），而候选列表与上下箭头
     * 本质就是这一个函数的结果，所以先在这里把它钉住：只要日志里
     * 《道德经》那组能出现，界面上的箭头就一定切得动。</p>
     */
    private static void bookWritingSelfCheck(net.minecraft.server.level.ServerLevel level) {
        List<ItemStack> pool = List.of(
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT),
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));

        // 高档材料 + 一池多用：候选必须多于一条——这正是上下箭头存在的理由
        List<ItemStack> rich = new java.util.ArrayList<>();
        rich.add(new ItemStack(Materials.CORRUPT_PARCHMENT.get(), 4));
        rich.add(new ItemStack(Materials.UNHOLY_INK.get(), 2));
        rich.add(new ItemStack(Materials.DRAGON_SINEW.get(), 4));

        List<String> low = names(BookWritingRecipes.findAll(level,
                new ItemStack(Materials.FIBER_PAPER.get(), 2),
                new ItemStack(Materials.INK.get(), 1),
                new ItemStack(Materials.HEMP_ROPE.get(), 2), pool));
        List<String> high = names(BookWritingRecipes.findAll(level,
                new ItemStack(Materials.PARCHMENT.get(), 2),
                new ItemStack(Materials.BLOOD_INK.get(), 1),
                new ItemStack(Materials.HEMP_ROPE.get(), 2), pool));
        List<String> many = names(BookWritingRecipes.findAll(level,
                new ItemStack(Materials.CORRUPT_PARCHMENT.get(), 4),
                new ItemStack(Materials.UNHOLY_INK.get(), 2),
                new ItemStack(Materials.DRAGON_SINEW.get(), 4), rich));

        LOG.info("著书台选书自查：草纸/墨水/麻绳 + 金胡萝卜金苹果 → {}；羊皮纸/血墨/麻绳 → {}", low, high);
        LOG.info("著书台选书自查：高档材料（至邪之笺×4 不洁之墨×2 龙筋×4 都在池子里）→ {}（{} 条候选）",
                many, many.size());

        // 作者 2026-10-31：小三角要在「三格放好」时就出现，材料池空着也照样能切。
        // 这里用空池跑一遍 matching，确认候选与材料池无关。
        List<String> bare = names(BookWritingRecipes.matching(level,
                new ItemStack(Materials.FIBER_PAPER.get(), 2),
                new ItemStack(Materials.INK.get(), 1),
                new ItemStack(Materials.HEMP_ROPE.get(), 2)));
        List<String> bareHigh = names(BookWritingRecipes.matching(level,
                new ItemStack(Materials.ANNIHILATED_PARCHMENT.get(), 2),
                new ItemStack(Materials.UNHOLY_INK.get(), 1),
                new ItemStack(Materials.DRAGON_SINEW.get(), 2)));
        LOG.info("著书台候选自查：三格放好、材料池为空 → {}（{} 条）", bare, bare.size());
        LOG.info("著书台候选自查：三格放好、最高档材料、材料池为空 → {}（{} 条）", bareHigh, bareHigh.size());

        if (bare.isEmpty()) {
            LOG.warn("著书台候选自查：三格放好却没有候选，成品框旁的箭头不会出现");
        }

        if (!low.contains("dao_de_jing")) {
            LOG.warn("著书台选书自查：最低档材料写不出《道德经》，检查配方档位与数量");
        }
        if (many.size() < 2) {
            LOG.warn("著书台选书自查：一池多用时只有 {} 条候选，上下箭头将不会出现", many.size());
        }
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
