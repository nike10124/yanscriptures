package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    // ==================== 《翠玉录》数值（整合包可调）====================

    private static final ForgeConfigSpec.DoubleValue TABLET_ATTACK_BONUS = BUILDER
            .comment("《翠玉录》的基础攻击力加成（近战补偿，不随进度成长）")
            .defineInRange("emeraldTablet.attackBonus", 4.0, 0.0, 64.0);

    private static final ForgeConfigSpec.DoubleValue TABLET_MAGIC_PERCENT_PER_KIND = BUILDER
            .comment("《翠玉录》每解锁一种药水，获得的魔法伤害百分比加成")
            .defineInRange("emeraldTablet.magicPercentPerPotion", 0.03, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue TABLET_MAGIC_PERCENT_CAP = BUILDER
            .comment("魔法伤害百分比加成的上限（0.5 = 最多 +50%）")
            .defineInRange("emeraldTablet.magicPercentCap", 1.0, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue TABLET_MAGIC_FLAT_PER_KIND = BUILDER
            .comment("魔法伤害的固定保底加成：每种药水加多少点。低数值法术模组里百分比几乎看不见，靠这一段兜底")
            .defineInRange("emeraldTablet.magicFlatPerPotion", 0.5, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue TABLET_MAGIC_FLAT_CAP = BUILDER
            .comment("魔法伤害固定保底加成的上限（点）")
            .defineInRange("emeraldTablet.magicFlatCap", 10.0, 0.0, 100.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    public static float tabletAttackBonus = 4.0F;
    public static float tabletMagicPercentPerKind = 0.03F;
    public static float tabletMagicPercentCap = 1.0F;
    public static float tabletMagicFlatPerKind = 0.5F;
    public static float tabletMagicFlatCap = 10.0F;

    private static boolean validateItemName(final Object obj)
    {
        if (!(obj instanceof final String itemName)) {
            return false;
        }
        final ResourceLocation id = ResourceLocation.tryParse(itemName);
        return id != null && ForgeRegistries.ITEMS.containsKey(id);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ResourceLocation.tryParse(itemName))
                .filter(Objects::nonNull)
                .map(id -> ForgeRegistries.ITEMS.getValue(id))
                .collect(Collectors.toSet());

        // 《翠玉录》数值
        tabletAttackBonus = TABLET_ATTACK_BONUS.get().floatValue();
        tabletMagicPercentPerKind = TABLET_MAGIC_PERCENT_PER_KIND.get().floatValue();
        tabletMagicPercentCap = TABLET_MAGIC_PERCENT_CAP.get().floatValue();
        tabletMagicFlatPerKind = TABLET_MAGIC_FLAT_PER_KIND.get().floatValue();
        tabletMagicFlatCap = TABLET_MAGIC_FLAT_CAP.get().floatValue();
    }
}
