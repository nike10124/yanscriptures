package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《孙子兵法》—— 兵者，诡道也。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>从背后命中（双方朝向一致）必定暴击，且该次伤害再 +30%；</li>
 *   <li>对还没把你当目标的生物出手，伤害 +15%；</li>
 *   <li>按住 Shift 获得隐身 II。</li>
 * </ul>
 *
 * <p>「必定暴击」在原版里没有变量可设 —— 玩家的暴击只存在于
 * {@code Player#attack} 的跳跃判定里，事件里改不到。所以这里按原版暴击
 * 的倍率 1.5 直接乘伤害，并补上暴击粒子和音效，看起来就是暴击。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArtOfWarHandler {

    /** 原版跳劈暴击倍率。 */
    private static final double CRIT_MULTIPLIER = 1.5D;
    /** 背刺的额外加成。 */
    private static final double BACKSTAB_BONUS = 0.30D;
    /** 对「没把你当目标」的生物的加成。 */
    private static final double AMBUSH_BONUS = 0.15D;
    /** 背刺判定阈值：双方朝向点积 > 0.5 即夹角小于 60°（同向 = 你在它背后）。 */
    private static final double BACKSTAB_DOT = 0.5D;
    private static final int INVIS_DURATION = 40;

    /** 记录「这个玩家的隐身是我们给的」，避免撤掉别的模组/药水给的隐身。 */
    private static final Set<UUID> GRANTED_INVISIBILITY = new HashSet<>();

    private ArtOfWarHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == player || target.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.ART_OF_WAR)) {
            return;
        }
        double multiplier = 1.0D;
        if (target.getLookAngle().dot(player.getLookAngle()) > BACKSTAB_DOT) {
            multiplier *= CRIT_MULTIPLIER * (1.0D + BACKSTAB_BONUS);
            playCrit(target);
        } else if (!(target instanceof Mob mob) || mob.getTarget() != player) {
            multiplier *= 1.0D + AMBUSH_BONUS;
        }
        if (multiplier != 1.0D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    private static void playCrit(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                12, 0.3D, 0.3D, 0.3D, 0.1D);
        server.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        boolean want = WornCache.wears(player, ClassicBook.ART_OF_WAR) && player.isShiftKeyDown();
        if (want) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVIS_DURATION, 1, false, false, false));
            GRANTED_INVISIBILITY.add(player.getUUID());
            return;
        }
        if (GRANTED_INVISIBILITY.remove(player.getUUID())) {
            MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
            if (current != null && current.getAmplifier() == 1) {
                player.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    /** 玩家退出时清掉记录（{@link SessionCleanup} 也可以在这里挂，但只有一个 Set，直接清）。 */
    public static void forget(UUID playerId) {
        GRANTED_INVISIBILITY.remove(playerId);
    }
}
