package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FateEpicHandler {

    private static final Random RANDOM = new Random();

    // 1. 拦截致命伤害，触发免死与传送
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Fate_Active")) return;

        float remainingHealth = player.getHealth() - event.getAmount();
        if (remainingHealth > 0) return;

        event.setCanceled(true);
        player.getPersistentData().putBoolean("Fate_Active", false);

        // 清除所有负面状态
        player.getActiveEffects().removeIf(effect -> !effect.getEffect().isBeneficial());

        // 传送至8格内的随机安全位置
        BlockPos safePos = findSafePosition(player);
        if (safePos != null) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
            }
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d【因果逆转】命运改写，你被转移到了安全之地。"), true);
    }

    // 2. 每一 tick 检查：判定结束与祝福过期
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // ---- 命运之诗：30 秒判定 ----
        if (player.getPersistentData().getBoolean("Fate_Active")) {
            long endTime = player.getPersistentData().getLong("Fate_EndTime");
            if (System.currentTimeMillis() >= endTime) {
                player.getPersistentData().putBoolean("Fate_Active", false);
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 2, true, false, false));
                player.getPersistentData().putBoolean("FateBlessing", true);
                player.getPersistentData().putLong("FateBlessingEnd", System.currentTimeMillis() + 60000);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a【因果逆转】规避了命运，获得幸运 III 与命运祝福。"), true);
            }
        }

        // ---- 🚨 独立检查：祝福是否过期（无论 Fate_Active 是否存在） ----
        if (player.getPersistentData().getBoolean("FateBlessing")) {
            if (System.currentTimeMillis() > player.getPersistentData().getLong("FateBlessingEnd")) {
                player.getPersistentData().putBoolean("FateBlessing", false);
                player.getPersistentData().remove("FateBlessingEnd");
            }
        }
    }

    // 辅助方法：寻找8格内的安全位置
    private static BlockPos findSafePosition(Player player) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        int radius = 8;

        for (int attempt = 0; attempt < 50; attempt++) {
            int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dy = RANDOM.nextInt(5) - 2;

            BlockPos target = center.offset(dx, dy, dz);

            if (level.getBlockState(target.below()).isSolid() &&
                    level.getBlockState(target).isAir() &&
                    level.getBlockState(target.above()).isAir() &&
                    !level.getBlockState(target).is(Blocks.WATER) &&
                    !level.getBlockState(target).is(Blocks.LAVA)) {
                return target;
            }
        }
        return null;
    }
}