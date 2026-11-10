package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

public class BookOfEmeraldTabletItem extends Item implements ICurioItem {
    public BookOfEmeraldTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) { return true; }

    // ==================== 属性面板加成 ====================
    // [2026-11-01 再改] 与《原理》一致：这里返回当前真实数值，让 Curios 面板能显示；
    // 数值的持续对账仍由 ProgressAttributeHandler 负责（UUID 完全一致，不会叠加）。
    public static final UUID E_BASE_HEALTH   = UUID.nameUUIDFromBytes("emerald_tablet_base_health".getBytes());
    public static final UUID E_GROWTH_HEALTH = UUID.nameUUIDFromBytes("emerald_tablet_growth_health".getBytes());
    public static final UUID E_BASE_ATTACK   = UUID.nameUUIDFromBytes("emerald_tablet_base_attack".getBytes());
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        if (!(slotContext.entity() instanceof Player player)) return map;
        int unlocked;
        if (player.level().isClientSide) {
            // 客户端用物品 NBT 上的镜像值（handler 每 10 tick 回写）
            unlocked = (stack.hasTag() && stack.getTag().contains("UnlockedCount"))
                    ? stack.getTag().getInt("UnlockedCount") : 0;
        } else {
            unlocked = PotionProgressTracker.getTotalUnlockedCount(player);
        }
        return build(map, unlocked);
    }

    private static Multimap<Attribute, AttributeModifier> build(Multimap<Attribute, AttributeModifier> map,
                                                               int unlocked) {
        map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(E_BASE_ATTACK, "emerald_tablet_base_attack",
                Config.tabletAttackBonus, AttributeModifier.Operation.ADDITION));
        // 最大生命**没有基础值**：只有「每解锁一种药水 +2」这一段成长。
        // E_BASE_HEALTH 只作为历史遗留 UUID 保留，由 handler 负责清理旧存档里挂上去的那一条。
        if (unlocked > 0) {
            map.put(Attributes.MAX_HEALTH, new AttributeModifier(E_GROWTH_HEALTH, "emerald_tablet_growth_health",
                    unlocked * 2.0D, AttributeModifier.Operation.ADDITION));
        }
        return map;
    }


    // 新的工具提示方法就放在这里！（在 getAttributeModifiers 的花括号结束之后）
    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        int unlockedCount = 0;
        // 从物品的 NBT 标签里读取进度
        if (stack.hasTag() && stack.getTag().contains("UnlockedCount")) {
            unlockedCount = stack.getTag().getInt("UnlockedCount");
        }

        // 物品名已经写明书名，这里不再重复标题
        // 机制说明：灰色
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.emerald_tablet.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));

        // 当前进度：紫色
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.emerald_tablet.progress", unlockedCount)
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));

        // 当前加成：基础攻击力 + 成长生命值（绿色）
        float healthBonus = unlockedCount * 2.0f;
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.emerald_tablet.bonus",
                        fmt(Config.tabletAttackBonus), fmt(healthBonus))
                .withStyle(net.minecraft.ChatFormatting.GREEN));

        // 成长部分：魔法伤害百分比 + 固定保底（青色）
        float magicPercent = EmeraldTabletMagic.percentFor(unlockedCount) * 100.0f;
        float magicFlat = EmeraldTabletMagic.flatFor(unlockedCount);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.emerald_tablet.magic",
                        fmt(magicPercent), fmt(magicFlat))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** 数值排版：整数就显示整数，否则保留一位小数。避免 tooltip 里出现 3.0000001。 */
    private static String fmt(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }}