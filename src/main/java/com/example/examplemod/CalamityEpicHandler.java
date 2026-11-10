package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CalamityEpicHandler {

    // 1. 伤害处理：叠加印记、增伤、以及自身易伤
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // A. 玩家攻击时，给目标叠加印记
        if (event.getSource().getEntity() instanceof Player player && !player.level().isClientSide) {
            if (player.getPersistentData().getBoolean("Calamity_Active")) {
                LivingEntity target = event.getEntity();
                CompoundTag data = target.getPersistentData();
                int mark = data.getInt("Calamity_Mark");
                if (mark < 5) {
                    data.putInt("Calamity_Mark", mark + 1);
                }
            }
        }

        // B. 带有印记的目标受到伤害时，增加伤害
        LivingEntity target = event.getEntity();
        if (!target.level().isClientSide) {
            int mark = target.getPersistentData().getInt("Calamity_Mark");
            if (mark > 0) {
                // 每层增加8%
                event.setAmount(event.getAmount() * (1.0f + mark * 0.08f));
            }
        }

        // C. 玩家处于灾厄状态时，自身受到的伤害增加15%
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            if (player.getPersistentData().getBoolean("Calamity_Active")) {
                event.setAmount(event.getAmount() * 1.15f);
            }
        }
    }

    // 2. 击杀带有印记的敌人：掉落额外经验并回血
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 直接获取实体，不需要 instanceof 检查
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        int mark = target.getPersistentData().getInt("Calamity_Mark");
        if (mark > 0 && event.getSource().getEntity() instanceof Player player) {
            // 掉落额外经验球
            for (int i = 0; i < mark; i++) {
                target.level().addFreshEntity(new ExperienceOrb(
                        target.level(), target.getX(), target.getY(), target.getZ(), 1));
            }
            // 每层印记恢复2点生命值
            player.heal(mark * 2.0f);
        }
    }

    // 3. 每秒检测：15秒后结束状态
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Calamity_Active")) return;

        if (System.currentTimeMillis() >= player.getPersistentData().getLong("Calamity_EndTime")) {
            player.getPersistentData().putBoolean("Calamity_Active", false);
        }
    }
}