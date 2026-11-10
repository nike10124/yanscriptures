package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Random;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CalamityPoemHandler {

    private static final Random RANDOM = new Random();

    private static boolean hasBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .isPresent();
    }

    // ==================== [纷争] + [死亡] + [诡计]：攻击时触发 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        // 只处理玩家造成的伤害
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        LivingEntity target = event.getEntity();

        // ==================== [纷争]：削减敌人 8% 攻击/护甲/最大生命值，并施加凋零、缓慢 ====================
        CalamityDebuffManager.applyDebuff(target);
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, true, false));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
        // ==================== [死亡]：独立全伤加成，每击杀 1 个生物 +1%，上限 88% ====================
        int killCount = player.getPersistentData().getInt("BookOfMe_KillCount");
        float bonus = Math.min(killCount, 88) * 0.01f;
        if (bonus > 0) {
            event.setAmount(event.getAmount() * (1.0f + bonus));
        }

        // ==================== [诡计]：20% 概率造成 2 倍伤害 ====================
        if (RANDOM.nextFloat() < 0.20f) {
            event.setAmount(event.getAmount() * 2.0f);
        }
    }

    // ==================== [死亡]：击杀计数 ====================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (ClassicBooksHandler.isRepublicShadow(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        int killCount = player.getPersistentData().getInt("BookOfMe_KillCount");
        if (killCount < 88) {
            player.getPersistentData().putInt("BookOfMe_KillCount", killCount + 1);
        }
    }

    // ==================== [诡计]：击杀生物时必定掉落所有可掉落物 ====================
    // 简化实现：给每个掉落物额外复制一份，相当于 100% 抢夺 III 的效果
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (ClassicBooksHandler.isRepublicShadow(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        // 复制所有掉落物
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                ItemStack extra = stack.copy();
                ItemEntity extraEntity = new ItemEntity(
                        player.level(),
                        drop.getX(), drop.getY(), drop.getZ(),
                        extra);
                player.level().addFreshEntity(extraEntity);
            }
        }
    }
}