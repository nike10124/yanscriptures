package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PillarPoemHandler {

    // 工具方法：检查是否装备《如我所书》
    private static boolean hasBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .isPresent();
    }

    // ==================== [大地] 2秒限伤 + [天空] 飞行减伤 ====================
    // ==================== [大地] 2秒限伤 + [天空] 飞行减伤 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        long gameTime = player.level().getGameTime();
        long immuneUntil = player.getPersistentData().getLong("Pillar_ImmuneUntil");

        // 在免疫窗口内：直接取消伤害
        if (gameTime < immuneUntil) {
            event.setCanceled(true);
            return;
        }

        // 开启新的 2 秒免疫窗口
        player.getPersistentData().putLong("Pillar_ImmuneUntil", gameTime + 40);
        // 手动把原版无敌帧设为 20，配合 PlayerTick 维持，彻底挡掉后续伤害
        player.invulnerableTime = 20;

        // ==================== [大地]：单次伤害不超过最大生命值的 20% ====================
        float maxHealth = player.getMaxHealth();
        float maxDamage = maxHealth * 0.2f;
        if (event.getAmount() > maxDamage) {
            event.setAmount(maxDamage);
        }

        // ==================== [天空]：飞行时 60% 减伤，其他情况 30% ====================
        if (player.getAbilities().flying) {
            event.setAmount(event.getAmount() * 0.4f);
        } else {
            event.setAmount(event.getAmount() * 0.7f);
        }
    }

    // ==================== 每 tick 维持无敌帧 + [海洋] 生命恢复 ====================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        long gameTime = player.level().getGameTime();
        long immuneUntil = player.getPersistentData().getLong("Pillar_ImmuneUntil");

        // 🚨 核心修复：在免疫窗口内，每 tick 强制维持无敌帧
        if (gameTime < immuneUntil) {
            player.invulnerableTime = 20;
        }

        // [海洋] 生命恢复
        if (player.tickCount % 20 == 0) {
            if (player.isInWater()) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 3, true, false));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, true, false));
            }
        }
    }
}