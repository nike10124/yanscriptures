package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlankPageItem extends Item implements ICurioItem {
    public BlankPageItem(Properties properties) { super(properties); }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) { return true; }

    // 读取历史记录（用于属性计算和防重复）
    public static List<String> getHistoryEnchantments(ItemStack stack) {
        return getList(stack, "HistoryEnchantments");
    }

    // 读取当前可用记录（用于消耗）
    public static List<String> getAvailableEnchantments(ItemStack stack) {
        return getList(stack, "AvailableEnchantments");
    }

    // 读取列表的通用方法
    private static List<String> getList(ItemStack stack, String key) {
        List<String> list = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains(key)) {
            ListTag tagList = stack.getTag().getList(key, 8);
            for (int i = 0; i < tagList.size(); i++) {
                list.add(tagList.getString(i));
            }
        }
        return list;
    }

    // 记录新附魔（防重复）
    public static void addEnchantment(ItemStack stack, String enchantmentId) {
        List<String> history = getHistoryEnchantments(stack);
        if (history.contains(enchantmentId)) return; // 已记录过，忽略
        history.add(enchantmentId);
        saveList(stack, "HistoryEnchantments", history);

        List<String> available = getAvailableEnchantments(stack);
        available.add(enchantmentId);
        saveList(stack, "AvailableEnchantments", available);
    }

    // 消耗附魔
    public static void consumeEnchantments(ItemStack stack, int count) {
        List<String> available = getAvailableEnchantments(stack);
        for (int i = 0; i < count && !available.isEmpty(); i++) {
            available.remove(0);
        }
        saveList(stack, "AvailableEnchantments", available);
    }

    private static void saveList(ItemStack stack, String key, List<String> list) {
        ListTag tagList = new ListTag();
        for (String s : list) tagList.add(StringTag.valueOf(s));
        stack.getOrCreateTag().put(key, tagList);
    }

    // 获取属性加成（基于历史记录数量，永久保留）
    public static int getUnlockedCount(ItemStack stack) {
        return getHistoryEnchantments(stack).size();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        // 核心修改：改为基于“可用附魔”数量来计算
        // [修复 P0-3 2026-10-21] 加硬上限：原版 1.20.1 共 93 种附魔，任何超过 93 的数量都只可能是
        // 被伪造的网络包或存档篡改，直接钳制，避免属性被无限叠加。
        // 原代码：int count = getAvailableEnchantments(stack).size();
        int count = Math.min(getAvailableEnchantments(stack).size(), 93);

        // 基础属性：移动速度 +10%
        UUID speedUuid = UUID.nameUUIDFromBytes("blank_page_speed".getBytes());
        map.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(speedUuid, "blank_page_speed", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));

        // 附魔成长属性（基于可用附魔）
        if (count > 0) {
            UUID healthUuid = UUID.nameUUIDFromBytes("blank_page_health".getBytes());
            UUID attackUuid = UUID.nameUUIDFromBytes("blank_page_attack".getBytes());
            UUID luckUuid = UUID.nameUUIDFromBytes("blank_page_luck".getBytes());

            map.put(Attributes.MAX_HEALTH, new AttributeModifier(healthUuid, "blank_page_health", count * 0.5, AttributeModifier.Operation.ADDITION));
            map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(attackUuid, "blank_page_attack", count * 0.5, AttributeModifier.Operation.ADDITION));
            map.put(Attributes.LUCK, new AttributeModifier(luckUuid, "blank_page_luck", count * 0.25, AttributeModifier.Operation.ADDITION));
        }

        return map;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof net.minecraft.world.entity.player.Player player && !player.level().isClientSide) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.JUMP, 21, 1, true, false
            ));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        int historyCount = getHistoryEnchantments(stack).size();
        int availableCount = getAvailableEnchantments(stack).size();
        float healthBonus = availableCount * 0.5f;
        float attackBonus = availableCount * 0.5f;
        float luckBonus = availableCount * 0.25f;

        // 物品名已经写明书名，这里不再重复标题
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.how_to_record")
                .withStyle(net.minecraft.ChatFormatting.BLUE));

        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.progress", String.format("%03d", historyCount))
                .withStyle(net.minecraft.ChatFormatting.GREEN));

        // 🚨 新增：显示可用附魔数量（青色）
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.available", String.format("%d", availableCount))
                .withStyle(net.minecraft.ChatFormatting.AQUA));

        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.bonus",
                        String.format("%.1f", healthBonus), String.format("%.1f", attackBonus), String.format("%.1f", luckBonus))
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));

        // 按键名走 keybind，改键后提示会跟着变
        String keyName = ExampleMod.BLANK_PAGE_KEY.getTranslatedKeyMessage().getString();
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.state_hint", keyName, keyName)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));

        // 与 BlankPageKeyHandler 里按 V 记录所需的数量保持一致（10 种）
        if (availableCount < 10) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.blank_page.not_enough")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }
}