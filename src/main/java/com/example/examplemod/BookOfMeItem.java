package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class BookOfMeItem extends Item implements ICurioItem {
    public BookOfMeItem(Properties properties) { super(properties); }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        // 只有创造模式玩家才能取下
        if (slotContext.entity() instanceof Player player) {
            return player.isCreative();
        }
        return true;
    }

    // 恒定 93 种附魔，永久满配
    public static int getEnchantCount() { return 93; }

    // 物品名称：蓝色 → 粉色逐字渐变（组件级颜色，不会被稀有度样式覆盖）
    private static final int NAME_FROM = 0x66AAFF;   // 天蓝
    private static final int NAME_TO = 0xFF99CC;     // 浅粉

    @Override
    public Component getName(ItemStack stack) {
        String plain = Component.translatable(this.getDescriptionId(stack)).getString();
        int length = plain.length();
        net.minecraft.network.chat.MutableComponent root = Component.empty();
        for (int i = 0; i < length; i++) {
            float t = length <= 1 ? 0.0F : i / (float) (length - 1);
            int color = lerpColor(NAME_FROM, NAME_TO, t);
            root.append(Component.literal(String.valueOf(plain.charAt(i)))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(color))
                            .withBold(true)));
        }
        return root;
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // 属性加成：固定 93 附魔 = +93 生命，+93 攻击，+46.5 幸运
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        int count = getEnchantCount();

        UUID healthUuid = UUID.nameUUIDFromBytes("book_of_me_health".getBytes());
        UUID attackUuid = UUID.nameUUIDFromBytes("book_of_me_attack".getBytes());
        UUID luckUuid = UUID.nameUUIDFromBytes("book_of_me_luck".getBytes());
        UUID speedUuid = UUID.nameUUIDFromBytes("book_of_me_speed".getBytes()); // 🚨 新增

        map.put(Attributes.MAX_HEALTH, new AttributeModifier(healthUuid, "book_of_me_health", count * 1.0, AttributeModifier.Operation.ADDITION));
        map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(attackUuid, "book_of_me_attack", count * 1.0, AttributeModifier.Operation.ADDITION));
        map.put(Attributes.LUCK, new AttributeModifier(luckUuid, "book_of_me_luck", count * 0.5, AttributeModifier.Operation.ADDITION));
        // +30% 移速属于「属性百分比」→ 乘法乘区 MULTIPLY_BASE（2026-11-01 作者 m09952 指正）；
        // 独立乘区 MULTIPLY_TOTAL 只留给材料档位那套。
        map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(speedUuid, "book_of_me_speed", 0.30, AttributeModifier.Operation.MULTIPLY_BASE));

        return map;

    }

    // ==================== 滚轮翻页用的静态字段 ====================
    public static int scrollOffset = 0;
    public static final int PAGE_SIZE = 12;

    public static final List<Component> FULL_DESCRIPTION = List.of(
            Component.translatable("tooltip.yanscriptures.book_of_me.full.1").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.2").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.3").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.4").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.5").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.6").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.7").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.8").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.9").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.10").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.11").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.12").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.13").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.14").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.15").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.16").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.17").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.18").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.19").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.20").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.21").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.22").withStyle(ChatFormatting.GRAY),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.23").withStyle(ChatFormatting.GRAY),

            Component.translatable("tooltip.yanscriptures.book_of_me.full.24").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            Component.translatable("tooltip.yanscriptures.book_of_me.full.25").withStyle(ChatFormatting.GRAY)
    );

    // 悬浮提示：平时简洁，按 Shift 展开并支持滚轮翻页
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean isShiftDown = Screen.hasShiftDown();
        // 防止越界：强制归零
        if (scrollOffset < 0) scrollOffset = 0;

        if (isShiftDown) {
            // ===================== 按下 Shift 后的滚动窗口 =====================
            tooltip.add(Component.literal("§6§l✦ 如我所书 ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("§d一段史诗§5所结出的果实§9，其中闪耀着§b十四种光辉").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

            // 计算有效的滚动范围
            int maxOffset = Math.max(0, FULL_DESCRIPTION.size() - PAGE_SIZE);
            int offset = Math.min(scrollOffset, maxOffset);
            int end = Math.min(FULL_DESCRIPTION.size(), offset + PAGE_SIZE);

            // 显示切片后的内容
            for (int i = offset; i < end; i++) {
                tooltip.add(FULL_DESCRIPTION.get(i));
            }


        } else {
            // ===================== 平时简洁模式 =====================
            tooltip.add(Component.literal("§6§l✦ 如我所书 ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("§7§d一段史诗§5所结出的果实§9，其中闪耀着§b十四种光辉").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("§e[Shift] §7完整描述　§e[Shift+Alt] §7完整书页"));
        }
    }
}