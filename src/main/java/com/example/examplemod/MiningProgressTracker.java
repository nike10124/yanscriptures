package com.example.examplemod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MiningProgressTracker {

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ItemStack stack = event.getStack();

        // ==================== 1. 挖矿等级判断 ====================
        int currentLevel = getCurrentLevel(player);
        int newLevel = currentLevel;

        if (currentLevel == 0 && stack.is(Items.COBBLESTONE)) {
            newLevel = 1;
        } else if (currentLevel == 1 && stack.is(Items.IRON_INGOT)) {
            newLevel = 2;
        } else if (currentLevel == 2 && stack.is(Items.DIAMOND)) {
            newLevel = 3;
        } else if (currentLevel == 3 && stack.is(Items.NETHERITE_INGOT)) {
            newLevel = 4;
        }

        // 如果挖矿等级提升了，更新玩家数据
        if (newLevel > currentLevel) {
            player.getPersistentData().putInt("principia_level", newLevel);
        }

        // ==================== 2. 进阶等级判断 ====================
        // 🚨 注意：这部分独立判断，不管挖矿等级有没有提升，都要检查！
        int currentAdvanced = getCurrentAdvancedLevel(player);
        int newAdvanced = currentAdvanced;

        if (currentAdvanced == 0 && stack.is(Items.NETHER_STAR)) {
            newAdvanced = 1;
        } else if (currentAdvanced == 1 && stack.is(Items.DRAGON_EGG)) {
            newAdvanced = 2;
        }

        if (newAdvanced > currentAdvanced) {
            player.getPersistentData().putInt("principia_advanced", newAdvanced);
        }

        // ==================== 3. 如果任意一个等级提升了，都刷新《原理》的 NBT ====================
        if (newLevel > currentLevel || newAdvanced > currentAdvanced) {
            updateBookNBT(player, newLevel, newAdvanced);
        }
    }

    // 获取挖矿等级
    public static int getCurrentLevel(Player player) {
        return player.getPersistentData().getInt("principia_level");
    }

    // 🚨 新增：获取进阶等级
    public static int getCurrentAdvancedLevel(Player player) {
        return player.getPersistentData().getInt("principia_advanced");
    }

    // 🚨 修改：updateBookNBT 现在接收两个等级，并分别写入 NBT
    private static void updateBookNBT(Player player, int miningLevel, int advancedLevel) {
        // 1. 扫描背包
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == ExampleMod.PRINCIPIA.get()) {
                stack.getOrCreateTag().putInt("UnlockedCount", miningLevel);
                stack.getOrCreateTag().putInt("AdvancedLevel", advancedLevel);
            }
        }
        // 2. 扫描饰品栏
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
            handler.findCurios(ExampleMod.PRINCIPIA.get()).forEach(slotResult -> {
                slotResult.stack().getOrCreateTag().putInt("UnlockedCount", miningLevel);
                slotResult.stack().getOrCreateTag().putInt("AdvancedLevel", advancedLevel);
            });
        });
    }
}