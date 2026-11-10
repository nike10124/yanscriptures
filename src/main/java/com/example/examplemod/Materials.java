package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 制书材料（2026-10-28 新增）。
 *
 * <p>这一层是「著书台」的原料，按三条线组织：纸 / 墨 / 装订。
 * 三条线各有两个档次，合起来能拼出九本名著与后续其他书的配方。</p>
 *
 * <p>只做物品与标签；具体怎么用（著书台的槽位与配方）留给后续的 {@code BookWritingRecipe}。</p>
 */
public final class Materials {

    private Materials() {
    }

    /**
     * 强制触发本类的静态初始化。
     *
     * <p>物品是在本类的静态字段里注册的：如果没人先碰过 {@code Materials}，
     * 这个类要等到第一次被引用（例如创造模式页签列举物品时）才初始化，
     * 那时 RegisterEvent 已经结束，Forge 会抛
     * {@code Cannot register new entries to DeferredRegister after RegisterEvent has been fired}。
     * 所以模组构造函数里必须显式调用一次。</p>
     */
    public static void init() {
        // 方法体留空：调用本身就是目的（触发类的静态初始化）
    }

    // ==================== 纸 ====================

    /** 草纸：最便宜的一档，甘蔗就能做。 */
    public static final RegistryObject<Item> FIBER_PAPER = ExampleMod.ITEMS.register("fiber_paper",
            () -> new Item(new Item.Properties()));

    /** 羊皮纸：皮革做的中档纸。 */
    public static final RegistryObject<Item> PARCHMENT = ExampleMod.ITEMS.register("parchment",
            () -> new Item(new Item.Properties()));

    /** 至邪之笺：羊皮纸浸透邪恶精华。Ⅲ 档纸（2026-11-05 作者定：稀有）。 */
    public static final RegistryObject<Item> CORRUPT_PARCHMENT = ExampleMod.ITEMS.register("corrupt_parchment",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    /** 寂灭之笺：至邪之笺在激活信标上的岩浆里烧出来的那一张。Ⅳ 档纸（2026-11-05 作者定：史诗）。 */
    public static final RegistryObject<Item> ANNIHILATED_PARCHMENT = ExampleMod.ITEMS.register("annihilated_parchment",
            () -> new Item(new Item.Properties().fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    // ==================== 墨 ====================

    /** 墨水：最朴素的一档，一个墨囊一个玻璃瓶，兑水熬出来。 */
    public static final RegistryObject<Item> INK = ExampleMod.ITEMS.register("ink",
            () -> new Item(new Item.Properties()));

    /** 血墨：墨囊加红色染料，写出来的字发红。 */
    public static final RegistryObject<Item> BLOOD_INK = ExampleMod.ITEMS.register("blood_ink",
            () -> new Item(new Item.Properties()));

    /** 不洁之墨：血墨掺入不洁之血。Ⅲ 档墨（2026-11-05 作者定：稀有）。 */
    public static final RegistryObject<Item> UNHOLY_INK = ExampleMod.ITEMS.register("unholy_ink",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    // ==================== 装订 ====================

    /** 麻绳：线搓出来的装订绳。 */
    public static final RegistryObject<Item> HEMP_ROPE = ExampleMod.ITEMS.register("hemp_rope",
            () -> new Item(new Item.Properties()));

    /** 龙筋：麻绳浸过龙息，韧得不像话。Ⅲ 档装订（2026-11-05 作者定：稀有）。 */
    public static final RegistryObject<Item> DRAGON_SINEW = ExampleMod.ITEMS.register("dragon_sinew",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));

    /** 金绳：麻绳夹了金粒，贵得不像话。 */
    public static final RegistryObject<Item> GOLD_ROPE = ExampleMod.ITEMS.register("gold_rope",
            () -> new Item(new Item.Properties()));

    /** 空书卷：还没写字的书，著书台把它做成真正的书。 */
    public static final RegistryObject<Item> BLANK_SCROLL = ExampleMod.ITEMS.register("blank_scroll",
            () -> new Item(new Item.Properties()));

    // ==================== 标签 ====================

    /** 任意一档纸。著书台用它判断「这一格是不是纸」。 */
    public static final TagKey<Item> PAPERS = tag("papers");

    /** 任意一档墨。 */
    public static final TagKey<Item> INKS = tag("inks");

    /** 任意一档装订线。 */
    public static final TagKey<Item> THREADS = tag("threads");

    /** 创造模式页签的排列顺序：纸 → 墨 → 装订 → 空书卷。 */
    public static final List<RegistryObject<Item>> ALL = List.of(
            FIBER_PAPER, PARCHMENT, CORRUPT_PARCHMENT, ANNIHILATED_PARCHMENT,
            INK, BLOOD_INK, UNHOLY_INK,
            HEMP_ROPE, GOLD_ROPE, DRAGON_SINEW,
            BLANK_SCROLL);

    private static TagKey<Item> tag(String name) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                new ResourceLocation(ExampleMod.MODID, name));
    }
}
