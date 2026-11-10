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
            Component.literal("§6【永恒的诗篇】").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.literal("§7已记录附魔恒定为 093，每个附魔 +1 最大生命值，+1 攻击力，+0.5 幸运值").withStyle(ChatFormatting.GRAY),
            Component.literal("§7按 V 记录当前状态，再次按 V 进行回溯，冷却 180 秒").withStyle(ChatFormatting.GRAY),

            Component.literal("§d【给予命运之诗】").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
            Component.literal("§7[岁月] 濒死时回复 70% 生命值，对半径 7 格内敌人造成 2 倍当前回复量的虚空伤害").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[门径] 禁用半径 10 格内的传送事件；按 K 沿视线传送到 8~16 格外（濒死时自动传至 16 格内安全位置）").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[律法] 生物试图伤害你时，损失其 5% 最大生命值并禁止回血").withStyle(ChatFormatting.GRAY),

            Component.literal("§b【给予支柱之诗】").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            Component.literal("§7[大地] 2 秒内至多受到一次伤害且不超过最大生命值的 20%，免疫大地伤害").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[天空] 飞行时获得 60% 伤害减免，其他情况 30%，免疫天空伤害").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[海洋] 水中获得生命恢复 IV，其他情况生命恢复 II，免疫海洋伤害").withStyle(ChatFormatting.GRAY),

            Component.literal("§a【给予创生之诗】").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            Component.literal("§7[负世] 免疫负面效果，无视秒杀").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[理性] 长按 Shift+L 使手中物品获得可获得的最高级附魔，冷却 10 秒").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[浪漫] 移速提升 30%（独立乘区），受击后造成 200% 反伤并抹除攻击者心智 2 秒").withStyle(ChatFormatting.GRAY),

            Component.literal("§4【给予灾厄之诗】").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
            Component.literal("§7[纷争] 攻击削减敌人 8% 最大生命值，并施加凋零、缓慢").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[死亡] 每击杀一个生物获得 1% 独立全伤，上限 88%").withStyle(ChatFormatting.GRAY),
            Component.literal("§7[诡计] 你杀死的生物掉落更多掉落物；有 20% 概率造成 2 倍伤害").withStyle(ChatFormatting.GRAY),

            Component.literal("§5【给予真我之诗】").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            Component.literal("§7创造飞行，飞行速度+100%，所有属性 X2，无视禁疗，88% 概率免疫死亡").withStyle(ChatFormatting.GRAY),
            Component.literal("§7当你触发本饰品的任意保命效果时，获得 60 秒无敌").withStyle(ChatFormatting.GRAY),
            Component.literal("§7按 Z 键暂停时间 20 秒，冷却 300 秒").withStyle(ChatFormatting.GRAY),

            Component.literal("§e【隐藏效果】").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            Component.literal("§7获得饱食 II、抗性提升 II、夜视 II、迅捷 II、急迫 II、发光 II、力量 II、幸运 V、潮涌能量 II、村庄英雄 V、抗火、跳跃提升 II。").withStyle(ChatFormatting.GRAY)
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