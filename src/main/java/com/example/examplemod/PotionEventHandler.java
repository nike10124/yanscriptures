package com.example.examplemod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PotionEventHandler {

    // 监听：玩家从酿造台取出药水
    @SubscribeEvent
    public static void onPotionBrewed(PlayerBrewedPotionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ItemStack stack = event.getStack();
        if (stack.getItem() == Items.POTION) {
            PotionProgressTracker.unlockBrewed(player, getPotionType(stack));
        }
    }

    // 监听：玩家喝完药水
    @SubscribeEvent
    public static void onPotionDrunk(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        ItemStack stack = event.getItem();
        if (stack.getItem() == Items.POTION) {
            PotionProgressTracker.unlockDrunk(player, getPotionType(stack));
        }
    }

    // 辅助方法：从药水物品中提取确切的药水种类
    private static String getPotionType(ItemStack stack) {
        return ForgeRegistries.POTIONS.getKey(PotionUtils.getPotion(stack)).toString();
    }
}