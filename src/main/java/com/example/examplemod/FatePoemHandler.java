package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Random;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FatePoemHandler {

    private static final Random RANDOM = new Random();

    // ==================== 工具方法：检查是否装备《如我所书》 ====================
    private static boolean hasBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .isPresent();
    }

    // ==================== [岁月] + [门径]：濒死触发 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        // ==================== [律法]：攻击者损失 5% 最大生命值并禁止回血 ====================
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            // 损失 5% 最大生命值
            float damage = attacker.getMaxHealth() * 0.05f;
            attacker.hurt(player.damageSources().magic(), damage);
            // 施加禁疗标记（持续 5 秒）
            attacker.getPersistentData().putLong("BookOfMe_NoHeal_End", System.currentTimeMillis() + 5000);
        }

        // ==================== [岁月] + [门径]：濒死触发 ====================
        float remainingHealth = player.getHealth() - event.getAmount();
        if (remainingHealth > 0) return;

        // 检查濒死冷却（防止连续触发，30 秒冷却）
        long now = System.currentTimeMillis();
        long lastTrigger = player.getPersistentData().getLong("FatePoem_LastTrigger");
        if (now - lastTrigger < 30000) return;

        player.getPersistentData().putLong("FatePoem_LastTrigger", now);

        // ==================== [修复 P0-5 2026-10-21] ====================
        // 原实现只 setHealth，却没有取消这次致命伤害，LivingHurtEvent 结束后原版照常扣血，
        // 于是玩家依然会死 —— [岁月] 的数值全部白给。
        // 这里显式取消本次伤害，才真正实现"濒死回溯"。
        event.setCanceled(true);

        // [岁月] 回复 70% 生命值
        // 原代码：player.setHealth(Math.min(player.getMaxHealth(), healAmount));
        // 问题：如果玩家当前血量高于 70%，会被"回溯"成更低的 70%；改为取两者较大值。
        float healAmount = player.getMaxHealth() * 0.7f;
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(player.getHealth(), healAmount)));

        // [岁月] 对半径 7 格内敌人造成 2 倍回复量的虚空伤害
        player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(7)).forEach(entity -> {
            if (entity == player) return;
            entity.hurt(player.damageSources().magic(), healAmount * 2.0f);
        });

        // [门径] 传送至 16 格内的安全位置
        BlockPos safePos = findSafePosition(player, 16);
        if (safePos != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
        }

        // 清除所有负面效果
        player.getActiveEffects().removeIf(effect -> !effect.getEffect().isBeneficial());

        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d【命运之诗】岁月回溯，门径重启。"), true);
    }

    // ==================== [律法] 的禁疗逻辑 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        // 🚨 直接获取实体，不需要多余的类型转换
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        // ... 后面的逻辑保持不变

        long noHealEnd = entity.getPersistentData().getLong("BookOfMe_NoHeal_End");
        if (noHealEnd > 0 && System.currentTimeMillis() < noHealEnd) {
            event.setCanceled(true);
        } else if (noHealEnd > 0) {
            entity.getPersistentData().remove("BookOfMe_NoHeal_End");
        }
    }

    // ==================== [门径] 禁用传送事件 ====================
    // 监听玩家传送事件（末影珍珠、末影人传送等）
    @SubscribeEvent
    public static void onPlayerTeleport(net.minecraftforge.event.entity.EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        // 检查是否有其他玩家在 10 格内装备了《如我所书》
        boolean blocked = player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(10))
                .stream()
                .anyMatch(other -> other != player && hasBookOfMe(other));

        if (blocked) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d【门径】传送被附近的神性力量禁止。"), true);
        }
    }

    // ==================== 辅助方法：寻找安全位置 ====================
    private static BlockPos findSafePosition(Player player, int radius) {
        Level level = player.level();
        BlockPos center = player.blockPosition();

        for (int attempt = 0; attempt < 100; attempt++) {
            int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dy = RANDOM.nextInt(7) - 3;

            BlockPos target = center.offset(dx, dy, dz);

            if (level.getBlockState(target.below()).isSolid() &&
                    level.getBlockState(target).isAir() &&
                    level.getBlockState(target.above()).isAir() &&
                    !level.getBlockState(target).is(Blocks.WATER) &&
                    !level.getBlockState(target).is(Blocks.LAVA) &&
                    !level.getBlockState(target).is(Blocks.FIRE) &&
                    !level.getBlockState(target).is(Blocks.CACTUS)) {
                return target;
            }
        }
        return null;
    }
}