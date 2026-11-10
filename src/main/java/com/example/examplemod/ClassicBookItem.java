package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * 九本名著饰品的共同实现。
 *
 * <p>每本书都只是一件「戴着就生效」的被动饰品：
 * <ul>
 *   <li>不能重复佩戴同类（{@link CurioDuplicateGuard}）</li>
 *   <li>可以随时取下（不像毕业饰品那样绑定）</li>
 *   <li>没有任何静态属性修饰符，全部靠 {@link ClassicBooksHandler} 在事件里算</li>
 * </ul>
 *
 * <p>tooltip 固定两段：一句原文味道的引语 → 两条机制说明。
 * 书名不再重复（物品名已经是书名），分类只保留在 {@link ClassicBook#category()} 里，
 * 供日后 JEI/搜索做内部分组，不上屏。
 */
public class ClassicBookItem extends Item implements ICurioItem {

    private final ClassicBook book;

    public ClassicBookItem(Properties properties, ClassicBook book) {
        super(properties);
        this.book = book;
    }

    public ClassicBook book() {
        return book;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /**
     * 固定数值的加成走饰品自身的属性修饰符，Curios 会在提示里自动列成
     * 「当装备在书籍栏：」那一段（`curios.modifiers.book`），同时把修饰符挂到玩家身上。
     *
     * <p>只有「只要戴着就是这么多」的才写在这里：理想国的攻防、神曲的移速。
     * 随状态变化的那几本（查拉图斯特拉的祝福条数、几何原本的公设、九章算术的斐波那契 …）
     * 仍然由 {@link ClassicBooksHandler} 在事件/每刻里算，喂不给 Curios 的静态展示。
     *
     * <p>乘区（作者 m09952 指正）：这里写的是「攻击力提高 10%」这类**属性百分比**，
     * 该走 {@code MULTIPLY_BASE}（同一乘区的百分比先相加，再乘到基础值上）；
     * {@code MULTIPLY_TOTAL} 是逐条独立相乘的「独立乘区」，留给材料档位那套专门设计的东西。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid,
                                                                        ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        switch (book) {
            case REPUBLIC -> {
                map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        UUID.nameUUIDFromBytes("classic_republic_atk".getBytes()),
                        "classic_republic_atk", REPUBLIC_ATK_PERCENT, AttributeModifier.Operation.MULTIPLY_BASE));
                map.put(Attributes.MAX_HEALTH, new AttributeModifier(
                        UUID.nameUUIDFromBytes("classic_republic_health".getBytes()),
                        "classic_republic_health", REPUBLIC_HP_PERCENT, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            case DIVINE_COMEDY -> map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                    UUID.nameUUIDFromBytes("classic_comedy_speed".getBytes()),
                    "classic_comedy_speed", COMEDY_SPEED_PERCENT, AttributeModifier.Operation.MULTIPLY_BASE));
            // 《史记》：成长型的**固定数值**加成（作者 2026-11-07：这类也要写成「当装备在书籍栏：」）
            // 进度存在物品自身 NBT 里，所以 Curios 能直接读、直接显示，不用 Handler 每刻去挂修饰符
            case RECORDS_OF_THE_HISTORIAN -> {
                int count = RecordsOfTheHistorianHandler.countOf(stack);
                if (count <= 0) {
                    break;
                }
                addFixed(map, RecordsOfTheHistorianHandler.critRate(), "classic_historian_crit_rate",
                        count * RecordsOfTheHistorianHandler.CRIT_RATE_PER);
                addFixed(map, RecordsOfTheHistorianHandler.critDamage(), "classic_historian_crit_damage",
                        count * RecordsOfTheHistorianHandler.CRIT_DAMAGE_PER);
                addFixed(map, Attributes.LUCK, "classic_historian_luck",
                        count * RecordsOfTheHistorianHandler.LUCK_PER);
            }
            // 《一千零一夜》：睡眠累计的成长（作者 2026-11-08：成长型属性也写成「当装备在书籍栏：」）
            // 百分比类走 MULTIPLY_BASE；护甲/韧性是扁平值，没有「基础值」可乘，只能按点数加。
            case ONE_THOUSAND_AND_ONE_NIGHTS -> {
                int[] growth = OneThousandAndOneNightsHandler.growthOf(stack);
                int total = 0;
                for (int value : growth) {
                    total += value;
                }
                if (total <= 0) {
                    break;
                }
                addPercent(map, OneThousandAndOneNightsHandler.Attrs.ATTACK, "classic_nights_attack",
                        growth[0] * OneThousandAndOneNightsHandler.PER_STACK);
                addFixed(map, OneThousandAndOneNightsHandler.Attrs.ARMOR, "classic_nights_armor",
                        growth[1] * OneThousandAndOneNightsHandler.PER_STACK);
                addFixed(map, OneThousandAndOneNightsHandler.Attrs.TOUGHNESS, "classic_nights_toughness",
                        growth[2] * OneThousandAndOneNightsHandler.PER_STACK);
                addPercent(map, OneThousandAndOneNightsHandler.Attrs.HEALTH, "classic_nights_health",
                        growth[3] * OneThousandAndOneNightsHandler.PER_STACK);
                addPercent(map, OneThousandAndOneNightsHandler.Attrs.ATTACK_SPEED, "classic_nights_attack_speed",
                        growth[4] * OneThousandAndOneNightsHandler.PER_STACK);
                addPercent(map, OneThousandAndOneNightsHandler.Attrs.SPEED, "classic_nights_speed",
                        growth[5] * OneThousandAndOneNightsHandler.PER_STACK);
            }
            default -> {
            }
        }
        return map;
    }

    /**
     * 往属性表里放一条**固定数值**（{@code ADDITION}）的修饰符。
     *
     * <p>属性可能来自别的模组（比如暴击率），没装就查不到、传 null 进来 —— 这时静默跳过，
     * 不让一件饰品因为「别人没装」而整块失效。
     */
    private static void addFixed(Multimap<Attribute, AttributeModifier> map, Attribute attribute,
                                 String key, double amount) {
        if (attribute == null || Math.abs(amount) < 1.0E-9D) {
            return;
        }
        map.put(attribute, new AttributeModifier(UUID.nameUUIDFromBytes(key.getBytes()), key, amount,
                AttributeModifier.Operation.ADDITION));
    }

    /** 百分比加成（{@code MULTIPLY_BASE} 乘区，同一乘区内的百分比先相加）。 */
    private static void addPercent(Multimap<Attribute, AttributeModifier> map, Attribute attribute,
                                   String key, double amount) {
        if (attribute == null || Math.abs(amount) < 1.0E-9D) {
            return;
        }
        map.put(attribute, new AttributeModifier(UUID.nameUUIDFromBytes(key.getBytes()), key, amount,
                AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /** 《理想国》：攻击力与生命上限各 +10%。{@link ClassicBooksHandler} 也引用这两个常量。 */
    public static final double REPUBLIC_ATK_PERCENT = 0.10D;
    public static final double REPUBLIC_HP_PERCENT = 0.10D;
    /** 《神曲》：移动速度 +15%。 */
    public static final double COMEDY_SPEED_PERCENT = 0.15D;

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String prefix = book.langPrefix();

        // 引语：斜体暗灰，像从书页上抄下来的一行
        tooltip.add(Component.translatable(prefix + ".quote")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        // 机制说明：青色，按标点折行
        // 原版 tooltip 会自动换行，但它按像素硬切，会出现「入梦 30 / 秒」这种把数字和单位劈开的
        // 断行（作者 2026-11-01 截图反馈）。所以这里自己按显示宽度折，优先在标点处断开。
        addWrapped(tooltip, prefix + ".effect1", ChatFormatting.AQUA);
        addWrapped(tooltip, prefix + ".effect2", ChatFormatting.AQUA);

        // 需要实时数字的那几本（作者 2026-11-08：直接写在介绍里，不做 HUD）。
        // 只有客户端会调 appendHoverText，但还是用 DistExecutor 隔离一下，服务端类加载更干净。
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.example.examplemod.client.BookStateTooltip.append(book, stack, tooltip));

        // 材料档位：始终挂在最后一行下面（只有著书台写出来的书才有）
        MaterialBonus.appendTooltip(stack, tooltip);
    }

    /**
     * 折行宽度，单位是「半角列」：汉字算 2 列。
     *
     * <p>原版提示框大约能放 36 列左右，留到 32 列是为了不同分辨率下都不触发它自己的折行。
     */
    private static final int WRAP_COLUMNS = 32;
    private static final String BREAK_AFTER = "，。；：、）】」』!?！？";

    private static void addWrapped(List<Component> tooltip, String langKey, ChatFormatting style) {
        for (String line : wrap(Component.translatable(langKey).getString())) {
            tooltip.add(Component.literal(line).withStyle(style));
        }
    }

    /** 按显示宽度折行，尽量在标点后断（避免把「30 秒」这种数字和单位劈成两行）。 */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int columns = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = c < 0x2E80 ? 1 : 2;
            if (columns + w > WRAP_COLUMNS && current.length() > 0) {
                int cut = lastBreak(current);
                if (cut > 0) {
                    lines.add(current.substring(0, cut));
                    current.delete(0, cut);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                columns = displayWidth(current);
            }
            current.append(c);
            columns += w;
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** 在本行里找一个合适的断点（标点之后），找不到返回 0。 */
    private static int lastBreak(StringBuilder line) {
        for (int i = line.length() - 1; i >= line.length() / 2; i--) {
            if (BREAK_AFTER.indexOf(line.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int displayWidth(CharSequence text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 0x2E80 ? 1 : 2;
        }
        return width;
    }
}
