package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.UUID;

public class BookOfPeaceItem extends Item implements ICurioItem {
    public BookOfPeaceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }
    // 为物品添加悬浮提示
    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        // 物品名已经写明书名，这里不再重复
        // 第一行：红色攻击说明
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.book_of_peace.attack")
                .withStyle(net.minecraft.ChatFormatting.RED));

        // 第二行：蓝色防御说明
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.book_of_peace.defense")
                .withStyle(net.minecraft.ChatFormatting.BLUE));

        // 材料档位（著书台写出来的才有；工作台合成的那本没有这一行）
        MaterialBonus.appendTooltip(stack, tooltip);
    }
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) { return true; }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        // 我们不需要静态属性了，就返回空表
        return map;
    }
}
