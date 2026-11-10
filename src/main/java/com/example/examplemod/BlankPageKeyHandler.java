package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BlankPageKeyHandler {

    // 只保留这个静态方法，供 BookOfMeKeyHandler 调用
    public static void useBlankPage(Player player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        long now = System.currentTimeMillis();

        if (tag.getLong("CooldownEnd") > now) {
            long remaining = (tag.getLong("CooldownEnd") - now) / 1000;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("冷却中，剩余 " + remaining + " 秒"), true);
            return;
        }

        if (tag.contains("SavedHealth")) {
            // ==================== 回溯 ====================
            // 🚨 先清除所有效果，避免自然恢复/生命恢复与回溯产生冲突
            player.getActiveEffects().clear();

            // 🚨 限制生命值范围，防止因上限变化导致的溢出或负值
            float savedHealth = tag.getFloat("SavedHealth");
            float clampedHealth = Math.max(0.0f, Math.min(savedHealth, player.getMaxHealth()));
            player.setHealth(clampedHealth);

            // 恢复饥饿值
            player.getFoodData().setFoodLevel(tag.getInt("SavedFood"));

            // 恢复药水效果
            if (tag.contains("SavedEffects")) {
                ListTag list = tag.getList("SavedEffects", 10);
                for (int i = 0; i < list.size(); i++) {
                    player.addEffect(MobEffectInstance.load(list.getCompound(i)));
                }
            }

            // 🚨 新增：记录血量保护标记，持续 5 秒
            // 这能防止原版自然恢复在同步期间把血量覆盖掉
            player.getPersistentData().putFloat("BookOfMe_ProtectedHealth", clampedHealth);
            player.getPersistentData().putLong("BookOfMe_ProtectEnd", now + 5000);

            tag.putLong("CooldownEnd", now + 15 * 60 * 1000); // 15分钟冷却
            tag.remove("SavedHealth");
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("状态已恢复！"), true);
        } else {
            // ==================== 记录 ====================
            int availableCount = BlankPageItem.getAvailableEnchantments(stack).size();
            if (availableCount < 10) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("可用附魔不足 10 种，无法记录！"), true);
                return;
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("消耗 10 种附魔，当前属性加成将降低！"), true);
            BlankPageItem.consumeEnchantments(stack, 10);
            tag.putFloat("SavedHealth", player.getHealth());
            tag.putInt("SavedFood", player.getFoodData().getFoodLevel());
            ListTag list = new ListTag();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                list.add(effect.save(new CompoundTag()));
            }
            tag.put("SavedEffects", list);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("状态已记录！"), true);
        }
    }
}
