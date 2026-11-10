package com.example.examplemod;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 制书材料带来的加成（2026-10-31 新增，同日两次改版）。
 *
 * <p>三条材料线各记一个档位：
 * <ul>
 *   <li>纸：草纸 Ⅰ → 羊皮纸 Ⅱ → 至邪之笺 Ⅲ → 寂灭之笺 Ⅳ</li>
 *   <li>墨：墨水 Ⅰ → 血墨 Ⅱ → 不洁之墨 Ⅲ</li>
 *   <li>线：麻绳 Ⅰ → 金绳 Ⅱ → 龙筋 Ⅲ</li>
 * </ul>
 *
 * <p><b>三条线各自独立</b>（作者 m08693）：每条线除了一份「全属性」百分比，
 * 还各自掷一项自己的额外属性。所以三张纸墨线可能分别是「攻击力 / 护甲 / 移动速度」，
 * 换掉某一条线时只动它自己那一项，不会把整本书的随机属性一起洗掉。</p>
 *
 * <p>「独立乘区」＝ {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation#MULTIPLY_TOTAL}，
 * 原版属性计算里最后一步的乘算层：乘的是所有固定值加成算完之后的总数。</p>
 */
public final class MaterialBonus {

    /** 书上的 NBT 键：三条材料线各自的档位。 */
    public static final String TAG_PAPER = "YanPaper";
    public static final String TAG_INK = "YanInk";
    public static final String TAG_THREAD = "YanThread";
    /** 书上的 NBT 键：三条线各自的随机属性（attack / health / armor / speed）。 */
    public static final String TAG_PAPER_ATTR = "YanPaperAttr";
    public static final String TAG_INK_ATTR = "YanInkAttr";
    public static final String TAG_THREAD_ATTR = "YanThreadAttr";
    /** 旧版只记一个属性的键，读到就摊给三条线（老书兼容）。 */
    public static final String TAG_ATTR_OLD = "YanGradeAttr";

    /** 单条材料线在 Ⅰ~Ⅳ 档各给多少。下标就是档位。 */
    private static final double[] PERCENT = {0.0D, 0.0D, 0.005D, 0.025D, 0.06D};

    /** 四项「全属性」：攻击力、生命上限、护甲、移动速度。 */
    public static final String[] ATTR_KEYS = {"attack", "health", "armor", "speed"};

    private MaterialBonus() {
    }

    // ==================== 物品 → 档位 ====================

    public static int paperTier(ItemStack stack) {
        int tier = paperTier(stack.getItem());
        // 认不出的东西，只要在纸标签里，就按「第一档纸」算 —— 整合包往标签里加自家纸就能直接用
        if (tier <= 0 && stack.is(Materials.PAPERS)) {
            return 1;
        }
        return tier;
    }

    public static int inkTier(ItemStack stack) {
        int tier = inkTier(stack.getItem());
        if (tier <= 0 && stack.is(Materials.INKS)) {
            return 1;
        }
        return tier;
    }

    public static int threadTier(ItemStack stack) {
        int tier = threadTier(stack.getItem());
        if (tier <= 0 && stack.is(Materials.THREADS)) {
            return 1;
        }
        return tier;
    }

    public static int paperTier(Item item) {
        if (item == Materials.ANNIHILATED_PARCHMENT.get()) return 4;
        if (item == Materials.CORRUPT_PARCHMENT.get()) return 3;
        if (item == Materials.PARCHMENT.get()) return 2;
        if (item == Materials.FIBER_PAPER.get()) return 1;
        return 0;
    }

    public static int inkTier(Item item) {
        if (item == Materials.UNHOLY_INK.get()) return 3;
        if (item == Materials.BLOOD_INK.get()) return 2;
        if (item == Materials.INK.get()) return 1;
        return 0;
    }

    public static int threadTier(Item item) {
        if (item == Materials.DRAGON_SINEW.get()) return 3;
        if (item == Materials.GOLD_ROPE.get()) return 2;
        if (item == Materials.HEMP_ROPE.get()) return 1;
        return 0;
    }

    /** 某一档的代表物品（JEI 展示用）：纸 Ⅰ~Ⅳ、墨 Ⅰ~Ⅲ、线 Ⅰ~Ⅲ。 */
    public static Item paperOfTier(int tier) {
        return switch (tier) {
            case 4 -> Materials.ANNIHILATED_PARCHMENT.get();
            case 3 -> Materials.CORRUPT_PARCHMENT.get();
            case 2 -> Materials.PARCHMENT.get();
            default -> Materials.FIBER_PAPER.get();
        };
    }

    public static Item inkOfTier(int tier) {
        return switch (tier) {
            case 3 -> Materials.UNHOLY_INK.get();
            case 2 -> Materials.BLOOD_INK.get();
            default -> Materials.INK.get();
        };
    }

    public static Item threadOfTier(int tier) {
        return switch (tier) {
            case 3 -> Materials.DRAGON_SINEW.get();
            case 2 -> Materials.GOLD_ROPE.get();
            default -> Materials.HEMP_ROPE.get();
        };
    }

    /** 三样材料排成 {纸, 墨, 线} 三档；认不出的当作 0。 */
    public static int[] tiersOf(ItemStack paper, ItemStack ink, ItemStack thread) {
        return new int[]{paperTier(paper), inkTier(ink), threadTier(thread)};
    }

    /** 一件材料属于哪条线（0 纸 / 1 墨 / 2 线），都不属于返回 -1。 */
    public static int lineOf(ItemStack stack) {
        if (paperTier(stack) > 0) return 0;
        if (inkTier(stack) > 0) return 1;
        if (threadTier(stack) > 0) return 2;
        return -1;
    }

    public static int tierOf(ItemStack stack) {
        return switch (lineOf(stack)) {
            case 0 -> paperTier(stack);
            case 1 -> inkTier(stack);
            case 2 -> threadTier(stack);
            default -> 0;
        };
    }

    public static double percent(int tier) {
        if (tier < 0 || tier >= PERCENT.length) {
            return 0.0D;
        }
        return PERCENT[tier];
    }

    // ==================== 随机属性 ====================

    /** 掷一项随机属性。 */
    public static String rollAttr(RandomSource random) {
        return ATTR_KEYS[random.nextInt(ATTR_KEYS.length)];
    }

    /** 三条线各掷一项（可以重复，重复就把那一项叠在同一处）。 */
    public static String[] rollAttrs(RandomSource random) {
        return new String[]{rollAttr(random), rollAttr(random), rollAttr(random)};
    }

    /**
     * 给成品写三条线的档位与各自的随机属性。
     *
     * <p>全 Ⅰ 也写：tooltip 要老实显示「第一档：无加成」，而不是装作没这回事。</p>
     */
    public static void stamp(ItemStack stack, int[] tiers, String[] attrs) {
        if (stack.isEmpty()) {
            return;
        }
        var tag = stack.getOrCreateTag();
        tag.putInt(TAG_PAPER, tiers[0]);
        tag.putInt(TAG_INK, tiers[1]);
        tag.putInt(TAG_THREAD, tiers[2]);
        tag.putString(TAG_PAPER_ATTR, safeAttr(attrs.length > 0 ? attrs[0] : null));
        tag.putString(TAG_INK_ATTR, safeAttr(attrs.length > 1 ? attrs[1] : null));
        tag.putString(TAG_THREAD_ATTR, safeAttr(attrs.length > 2 ? attrs[2] : null));
        tag.remove(TAG_ATTR_OLD);
    }

    public static int[] tiers(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return new int[]{0, 0, 0};
        }
        var tag = stack.getOrCreateTag();
        return new int[]{
                tag.getInt(TAG_PAPER),
                tag.getInt(TAG_INK),
                tag.getInt(TAG_THREAD)
        };
    }

    /** 三条线各自的随机属性；老书只有一个属性键时，三条线都算它。 */
    public static String[] attrs(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return new String[]{ATTR_KEYS[0], ATTR_KEYS[0], ATTR_KEYS[0]};
        }
        var tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_PAPER_ATTR) && tag.contains(TAG_ATTR_OLD)) {
            String old = safeAttr(tag.getString(TAG_ATTR_OLD));
            return new String[]{old, old, old};
        }
        return new String[]{
                safeAttr(tag.getString(TAG_PAPER_ATTR)),
                safeAttr(tag.getString(TAG_INK_ATTR)),
                safeAttr(tag.getString(TAG_THREAD_ATTR))
        };
    }

    /** 改某一条线：只换它自己的档位与随机属性，另外两条原样保留。 */
    public static void replaceLine(ItemStack stack, int line, int tier, String attr) {
        if (stack.isEmpty()) {
            return;
        }
        int[] tiers = tiers(stack);
        String[] attrs = attrs(stack);
        // 2026-11-10（外部审查）：原来 attrs[line] 无条件覆盖，于是「档位不升也重掷词条」。
        // 作者定案（m18864）：「允许使用同档次来洗属性的操作」—— 所以同档也重掷，
        // 只是不能靠更低档的材料洗（那等于完全免费），因此条件是 tier >= 当前档。
        if (tier >= tiers[line]) {
            tiers[line] = Math.max(tiers[line], tier);
            attrs[line] = safeAttr(attr);
        }
        stamp(stack, tiers, attrs);
    }

    /** 这本书的总百分比（三条线相加）。 */
    public static double totalPercent(ItemStack stack) {
        int[] tiers = tiers(stack);
        return percent(tiers[0]) + percent(tiers[1]) + percent(tiers[2]);
    }

    /**
     * 四项属性各自的实际加成（不含别的书）：三条线相加给一项「全属性」，
     * 每条线再把自己那一份加到它掷中的那一项上（作者 m08876：全属性用三条相加表示）。
     *
     * @return 顺序同 {@link #ATTR_KEYS}
     */
    public static double[] perAttribute(ItemStack stack) {
        int[] tiers = tiers(stack);
        String[] attrs = attrs(stack);
        double total = totalPercent(stack);
        double[] out = {total, total, total, total};
        for (int line = 0; line < 3; line++) {
            out[indexOf(attrs[line])] += percent(tiers[line]);
        }
        return out;
    }

    private static int indexOf(String key) {
        for (int i = 0; i < ATTR_KEYS.length; i++) {
            if (ATTR_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }

    private static String safeAttr(String key) {
        if (key != null) {
            for (String candidate : ATTR_KEYS) {
                if (candidate.equals(key)) {
                    return candidate;
                }
            }
        }
        return ATTR_KEYS[0];
    }

    // ==================== tooltip ====================

    /**
     * 物品介绍下面的区块，做成「当装备在书籍栏：」那一段（作者 m08693 给的样式）：
     * 四条属性各一行，再一行材料档位、一行槽位。
     */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        int[] tiers = tiers(stack);
        if (tiers[0] <= 0 && tiers[1] <= 0 && tiers[2] <= 0) {
            return;
        }
        double total = totalPercent(stack);
        if (total <= 0.0D) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.yanscriptures.grade.none")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                .withStyle(ChatFormatting.GOLD));

        // 先一行「全属性」= 三条相加，再逐条列出各自掷中的那一项
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.all", fmt(total * 100.0D))
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        String[] attrs = attrs(stack);
        for (int line = 0; line < 3; line++) {
            double pct = percent(tiers[line]) * 100.0D;
            if (pct <= 0.0D) {
                continue;
            }
            String key = safeAttr(attrs[line]);
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.line",
                            fmt(pct), Component.translatable("attribute.name.generic." + fullAttrKey(key)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.yanscriptures.grade.head",
                        numeral(tiers[0]), numeral(tiers[1]), numeral(tiers[2]))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.slot")
                .withStyle(ChatFormatting.GOLD));
    }

    /** attack → attack_damage 这类，拼出原版的属性名键。 */
    private static String fullAttrKey(String key) {
        return switch (key) {
            case "attack" -> "attack_damage";
            case "health" -> "max_health";
            case "speed" -> "movement_speed";
            default -> "armor";
        };
    }

    private static String numeral(int tier) {
        return switch (tier) {
            case 2 -> "Ⅱ";
            case 3 -> "Ⅲ";
            case 4 -> "Ⅳ";
            default -> "Ⅰ";
        };
    }

    private static String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.1f", value);
    }
}
