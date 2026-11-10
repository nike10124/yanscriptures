package com.example.examplemod.compat;

import java.util.List;

import com.example.examplemod.BookWritingRecipes;
import com.example.examplemod.CauldronRecipes;
import com.example.examplemod.ClassicBook;
import com.example.examplemod.ExampleMod;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI 接入点。
 *
 * <p>做两件事：</p>
 * <ol>
 *   <li>把模组自己的炼药锅配方注册成一个 JEI 分类，并把水炼药锅登记成催化剂 ——
 *       这样在 JEI 里点水炼药锅就能看到本模组的全部投料配方；</li>
 *   <li>给十张残卷与四件泰坦史诗挂「信息」页，写清获取条件（和蚀刻版一样的用法：
 *       在 JEI 里对着物品按 <kbd>U</kbd>/<kbd>I</kbd> 就能读到「怎么拿到」）。</li>
 * </ol>
 *
 * <p>信息页的正文全部走语言键，见 {@code lang/zh_cn.json} 的
 * {@code jei.yanscriptures.scroll.*} 与 {@code jei.yanscriptures.epic.*}。</p>
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(ExampleMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new CauldronRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BookWritingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CauldronRecipeCategory.TYPE, CauldronRecipes.all());
        registration.addRecipes(BookWritingRecipeCategory.TYPE, BookWritingRecipes.all());
        registerUsageInfo(registration);
        registerScrollInfo(registration);
        registerEpicInfo(registration);
        registerBookDetailInfo(registration);
    }

    /**
     * 18 本名著的「详细机制」信息页（作者 m11711：按 U 要能看到每种卦象的具体数值）。
     *
     * <p>三本入口的写法不同，所以这里自己拼行：
     * ①第一行固定是「怎么用」（装备到书籍栏）；
     * ②`tooltip.yanscriptures.classic.<id>.effect1/effect2` 是物品提示里那两行，直接复用，
     *   保证信息页与 tooltip 不会各说一套；
     * ③`jei.yanscriptures.book.<id>.detail.N` 是补充的细节（数值、冷却、例外），
     *   从 1 开始连续写到缺为止 —— 加一行只要补一个语言键，不用改代码。</p>
     *
     * <p>语言键用 {@link net.minecraft.locale.Language#getInstance()#has} 探测存在性，
     * 缺键时静默跳过，绝不把生键名糊到玩家脸上（§11.14 的规矩）。</p>
     */
    private static void registerBookDetailInfo(IRecipeRegistration registration) {
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> holder
                : ExampleMod.CLASSIC_BOOKS) {
            net.minecraft.world.item.Item item = holder.get();
            String desc = item.getDescriptionId();
            if (!desc.startsWith("item.yanscriptures.")) {
                continue;
            }
            String id = desc.substring("item.yanscriptures.".length());
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("jei.yanscriptures.book.how").withStyle(s -> s.withColor(0x777777)));
            lines.add(Component.translatable("tooltip.yanscriptures.classic." + id + ".effect1")
                    .withStyle(s -> s.withColor(0x404040)));
            lines.add(Component.translatable("tooltip.yanscriptures.classic." + id + ".effect2")
                    .withStyle(s -> s.withColor(0x404040)));
            for (int n = 1; n <= 12; n++) {
                String key = "jei.yanscriptures.book." + id + ".detail." + n;
                if (!net.minecraft.locale.Language.getInstance().has(key)) {
                    break;
                }
                lines.add(Component.translatable(key).withStyle(s -> s.withColor(0x2F4F6F)));
            }
            registration.addItemStackInfo(List.of(new ItemStack(item)),
                    lines.toArray(new Component[0]));
        }
    }

    /**
     * JEI 跑起来之后做一次物品自查。
     *
     * <p>为什么要这一步：JEI 只索引它自己能拿到的 {@code ItemStack}。如果某个物品
     * 注册了却不在 JEI 物品列表里，症状就是「搜名字搜不到、对着创造栏按 U 也没反应」。
     * 这行日志把结论直接写出来，省得靠肉眼在 JEI 里翻。</p>
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        var stacks = runtime.getIngredientManager().getAllItemStacks();
        boolean scroll = false;
        boolean quill = false;
        for (ItemStack stack : stacks) {
            if (stack.is(com.example.examplemod.Materials.BLANK_SCROLL.get())) {
                scroll = true;
            } else if (stack.is(ExampleMod.WRITING_QUILL.get())) {
                quill = true;
            }
        }
        org.slf4j.Logger log = com.mojang.logging.LogUtils.getLogger();
        log.info("JEI 物品自查：空书卷={}，著书笔={}，JEI 物品总数={}",
                scroll, quill, stacks.size());

        // 再数一遍客户端手上的配方表：JEI 的配方是从这里来的，
        // 如果服务端有、客户端没有，那 JEI 里就只会看到物品、看不到配方。
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var level = minecraft.level;
        if (level != null) {
            int craftingForScroll = 0;
            int craftingForQuill = 0;
            for (var recipe : level.getRecipeManager().getRecipes()) {
                if (!(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting)) {
                    continue;
                }
                ItemStack result = crafting.getResultItem(level.registryAccess());
                if (result.is(com.example.examplemod.Materials.BLANK_SCROLL.get())) {
                    craftingForScroll++;
                } else if (result.is(ExampleMod.WRITING_QUILL.get())) {
                    craftingForQuill++;
                }
            }
            log.info("JEI 配方自查：客户端工作台配方里，空书卷={} 条，著书笔={} 条",
                    craftingForScroll, craftingForQuill);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Blocks.WATER_CAULDRON),
                CauldronRecipeCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ExampleMod.BOOK_WRITING_TABLE_ITEM.get()),
                BookWritingRecipeCategory.TYPE);
    }

    /**
     * 空书卷 / 著书笔：信息页只写<b>用途</b>（作者 m08876）。
     * 合成方式按 <kbd>R</kbd> 就能看到，不在这里重复。
     */
    private static void registerUsageInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                List.of(new ItemStack(com.example.examplemod.Materials.BLANK_SCROLL.get())),
                Component.translatable("jei.yanscriptures.blank_scroll.use")
                        .withStyle(s -> s.withColor(0x404040)));

        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.WRITING_QUILL.get())),
                Component.translatable("jei.yanscriptures.writing_quill.use")
                        .withStyle(s -> s.withColor(0x404040)));

        // 著书台方块本身：用途 + 来源（作者 2026-10-31：它也缺一个信息页）
        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.BOOK_WRITING_TABLE_ITEM.get())),
                Component.translatable("jei.yanscriptures.book_table.use")
                        .withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.book_table.source")
                        .withStyle(s -> s.withColor(0x777777)));

        // 寂灭之笺：Ⅳ 档纸，来源是一个岩浆 + 信标的小仪式，物品本身只有一个名字，必须写清楚
        registration.addItemStackInfo(
                List.of(new ItemStack(com.example.examplemod.Materials.ANNIHILATED_PARCHMENT.get())),
                Component.translatable("jei.yanscriptures.annihilated.use")
                        .withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.annihilated.source")
                        .withStyle(s -> s.withColor(0x777777)));
    }

    /** 十张残卷：条件 + 那句「先看见自己的罪」。 */
    private static void registerScrollInfo(IRecipeRegistration registration) {
        addScroll(registration, ExampleMod.SCROLL_SHEMA.get(), "shema");
        addScroll(registration, ExampleMod.SCROLL_SILENCE.get(), "silence");
        addScroll(registration, ExampleMod.SCROLL_THORNS.get(), "thorns");
        addScroll(registration, ExampleMod.SCROLL_SABBATH.get(), "sabbath");
        addScroll(registration, ExampleMod.SCROLL_REBELLION.get(), "rebellion");
        addScroll(registration, ExampleMod.SCROLL_BLOOD.get(), "blood");
        addScroll(registration, ExampleMod.SCROLL_HARP.get(), "harp");
        addScroll(registration, ExampleMod.SCROLL_SHINAR.get(), "shinar");
        addScroll(registration, ExampleMod.SCROLL_TONGUE.get(), "tongue");
        addScroll(registration, ExampleMod.SCROLL_VANITY.get(), "vanity");
    }

    private static void addScroll(IRecipeRegistration registration, net.minecraft.world.item.Item item,
                                  String key) {
        registration.addItemStackInfo(
                List.of(new ItemStack(item)),
                Component.translatable("jei.yanscriptures.scroll." + key + ".condition")
                        .withStyle(style -> style.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.scroll.hint")
                        .withStyle(style -> style.withColor(0x777777)));
    }

    /** 四件泰坦史诗（第五件《真我》由整合包任务发放，不在模组内）。 */
    private static void registerEpicInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(List.of(new ItemStack(ExampleMod.EPIC_FATE.get())),
                Component.translatable("jei.yanscriptures.epic.fate").withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.epic.hint").withStyle(s -> s.withColor(0x777777)));

        registration.addItemStackInfo(List.of(new ItemStack(ExampleMod.EPIC_CREATION.get())),
                Component.translatable("jei.yanscriptures.epic.creation").withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.epic.hint").withStyle(s -> s.withColor(0x777777)));

        registration.addItemStackInfo(List.of(new ItemStack(ExampleMod.EPIC_PILLAR.get())),
                Component.translatable("jei.yanscriptures.epic.pillar").withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.epic.hint").withStyle(s -> s.withColor(0x777777)));

        registration.addItemStackInfo(List.of(new ItemStack(ExampleMod.EPIC_CALAMITY.get())),
                Component.translatable("jei.yanscriptures.epic.calamity").withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.epic.hint").withStyle(s -> s.withColor(0x777777)));
    }

}
