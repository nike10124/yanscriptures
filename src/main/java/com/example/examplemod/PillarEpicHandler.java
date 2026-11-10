package com.example.examplemod;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PillarEpicHandler {

    // 1. 拦截伤害：减伤与反伤
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // 检查是否在“地脉锚定”状态
        if (!player.getPersistentData().getBoolean("Pillar_Active")) return;

        // 计算减伤后的伤害（50%减伤）
        float originalDamage = event.getAmount();
        float reducedDamage = originalDamage * 0.5f;
        event.setAmount(reducedDamage);

        // 反伤逻辑：对攻击者造成实际受到伤害的50%
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            // 使用魔法伤害，无视护甲
            livingAttacker.hurt(player.damageSources().magic(), reducedDamage * 1.0f);
        }
    }

    // 2. 每秒检测：10秒后结束状态
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Pillar_Active")) return;

        if (System.currentTimeMillis() >= player.getPersistentData().getLong("Pillar_EndTime")) {
            player.getPersistentData().putBoolean("Pillar_Active", false);
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        }
    }
}