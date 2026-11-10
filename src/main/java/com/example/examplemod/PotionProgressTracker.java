package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public class PotionProgressTracker {
    public static void unlockBrewed(Player player, String potionId) {
        player.getPersistentData().putBoolean("brewed_" + potionId, true);
        updateBookNBT(player);
    }

    public static void unlockDrunk(Player player, String potionId) {
        player.getPersistentData().putBoolean("drunk_" + potionId, true);
        updateBookNBT(player);
    }

    public static int getTotalUnlockedCount(Player player) {
        CompoundTag data = player.getPersistentData();
        int count = 0;
        for (String key : data.getAllKeys()) {
            if (key.startsWith("brewed_") || key.startsWith("drunk_")) {
                if (data.getBoolean(key)) count++;
            }
        }
        return count;
    }

    // 核心：把进度写进所有《翠玉录》的 NBT 标签里，以便同步到客户端显示
    private static void updateBookNBT(Player player) {
        int count = getTotalUnlockedCount(player);

        // 1. 扫描背包
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == ExampleMod.EMERALD_TABLET.get()) {
                stack.getOrCreateTag().putInt("UnlockedCount", count);
            }
        }

        // 2. 扫描饰品栏 (Curios)
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.findCurios(ExampleMod.EMERALD_TABLET.get()).forEach(slotResult -> {
                slotResult.stack().getOrCreateTag().putInt("UnlockedCount", count);
            });
        });
    }
}