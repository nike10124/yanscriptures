package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreationEpicHandler {

    // 1. 受到伤害时：标记为已受伤，并移除生命回复效果
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Creation_Active")) return;

        // 标记已受伤
        player.getPersistentData().putBoolean("Creation_Hurt", true);

        // 打断所有生命回复效果（无论是 II 还是后续的 VIII）
        player.removeEffect(MobEffects.REGENERATION);
    }

    // 2. 每刻检测：处理血量判定和阶段切换
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Creation_Active")) return;
        long now = System.currentTimeMillis();

        // 检测血量是否低于 15%
        float healthPercent = player.getHealth() / player.getMaxHealth();
        if (healthPercent <= 0.15f) {
            player.getPersistentData().putBoolean("Creation_LowHealth", true);
        }

        // 阶段一：20秒判定
        if (!player.getPersistentData().getBoolean("Creation_Phase1Done") && now >= player.getPersistentData().getLong("Creation_Phase1End")) {
            player.getPersistentData().putBoolean("Creation_Phase1Done", true);

            boolean hurt = player.getPersistentData().getBoolean("Creation_Hurt");
            boolean lowHealth = player.getPersistentData().getBoolean("Creation_LowHealth");

            // 核心判定：无伤 或 曾濒死，则给生命回复 VIII
            if (!hurt || lowHealth) {
                // 10秒 = 200 tick，等级 7 代表生命回复 VIII
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 7, true, false));
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a【创生】生命绽放，获得生命回复 VIII！"), true);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c【创生】未能保持无伤或濒死，生息之环黯淡了。"), true);
            }
        }

        // 阶段二：30秒彻底结束
        if (now >= player.getPersistentData().getLong("Creation_EndTime")) {
            player.getPersistentData().putBoolean("Creation_Active", false);
        }
    }

    // 3. 食用食物时：溢出治疗转化为伤害吸收，并模拟收益翻倍
    @SubscribeEvent
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!player.getPersistentData().getBoolean("Creation_Active")) return;

        ItemStack stack = event.getItem();
        FoodProperties food = stack.getItem().getFoodProperties();

        if (food != null) {
            int nutrition = food.getNutrition();

            // 1. 模拟“收益翻倍”：额外补充一次饱食度和饱和度
            // 原版吃食物后，饥饿值和饱和度已经加过一次了，我们这里再补一次，体感就是“翻倍”
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + nutrition);
            player.getFoodData().setSaturation(player.getFoodData().getSaturationLevel() + (nutrition * 2.0f));

            // 2. 溢出治疗转化为伤害吸收（黄心）
            // 食物营养值越高，给的黄心越多
            int absorption = nutrition * 2;
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.ABSORPTION, 600, absorption / 4, true, false));
        }
    }
}