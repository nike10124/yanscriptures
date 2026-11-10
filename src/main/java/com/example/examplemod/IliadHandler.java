package com.example.examplemod;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《伊利亚特》—— 阿喀琉斯的愤怒。
 *
 * <p>作者定稿（2026-11-06）：
 * <ul>
 *   <li>单挑（周围 8 格内只有这一个对手在盯着你）：攻击力 +15%、受到伤害 −10%，
 *       并且你和对手都不会被「削弱型」debuff 影响；</li>
 *   <li>被两个及以上敌对生物同时锁定：攻击力 −10%，但每次攻击给敌人挂随机负面效果 II（8 秒），
 *       横扫半径扩大。</li>
 * </ul>
 *
 * <p><b>「削弱型 debuff」怎么判</b>（作者问过会不会漏）：
 * 先看这条效果自带的属性修饰符是否碰到生命上限 / 护甲 / 移动速度 / 攻击力；
 * 再补一份显式清单，因为失明、反胃、漂浮、黑暗这类效果<b>没有属性修饰符</b>，
 * 却实实在在地影响战斗（失明既不能疾跑也不能暴击）。
 * 会漏的情况只有一种：别家模组自定义的负面效果既不带属性、也不在清单里——没有公共接口能
 * 判断它「算不算削弱」，所以只能漏，不能瞎猜。
 * 毒、凋零虽然没有属性修饰符，但会实实在在地掉血，按作者 2026-11-07 的要求一并清掉。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IliadHandler {

    private static final double DUEL_RADIUS = 8.0D;
    private static final double DUEL_ATTACK = 0.15D;
    private static final double DUEL_TAKEN = 0.10D;
    private static final double SIEGE_ATTACK = -0.10D;
    private static final int SIEGE_DEBUFF_TICKS = 160;
    private static final double SWEEP_RADIUS = 2.5D;
    private static final double SWEEP_RATIO = 0.5D;

    private static final UUID ID_ATTACK = BookAttr.id("iliad_attack");

    /** 会削弱战斗、但没有属性修饰符的原版效果（毒与凋零按作者 2026-11-07 要求一并列入）。 */
    private static final List<MobEffect> NEUTRALISE = List.of(
            MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.LEVITATION, MobEffects.DARKNESS,
            MobEffects.POISON, MobEffects.WITHER);

    /** 围攻时随机挂的负面效果池（都是 II 级）。 */
    private static final List<MobEffect> SIEGE_POOL = List.of(
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
            MobEffects.BLINDNESS, MobEffects.DIG_SLOWDOWN, MobEffects.HUNGER, MobEffects.CONFUSION);

    private static boolean sweeping;

    private IliadHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 10 != 0) {
            return;
        }
        boolean worn = WornCache.wears(player, ClassicBook.ILIAD);
        int lockers = worn ? lockers(player) : 0;
        double attack = 0.0D;
        if (lockers == 1) {
            attack = DUEL_ATTACK;
        } else if (lockers >= 2) {
            attack = SIEGE_ATTACK;
        }
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, attack);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || sweeping) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            if (WornCache.wears(victim, ClassicBook.ILIAD) && lockers(victim) == 1) {
                event.setAmount((float) (event.getAmount() * (1.0D - DUEL_TAKEN)));
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == event.getEntity()) {
            return;
        }
        if (!WornCache.wears(attacker, ClassicBook.ILIAD) || lockers(attacker) < 2) {
            return;
        }
        LivingEntity victim = event.getEntity();
        MobEffect effect = SIEGE_POOL.get(attacker.getRandom().nextInt(SIEGE_POOL.size()));
        victim.addEffect(new MobEffectInstance(effect, SIEGE_DEBUFF_TICKS, 1, false, true, true));
        sweep(attacker, victim, event.getAmount());
    }

    /** 围攻时的「横扫扩大」：半径 2.5 格，跳过主目标与玩家自己。 */
    private static void sweep(Player attacker, LivingEntity main, float amount) {
        sweeping = true;
        try {
            for (LivingEntity other : attacker.level().getEntitiesOfClass(LivingEntity.class,
                    attacker.getBoundingBox().inflate(SWEEP_RADIUS))) {
                if (other == attacker || other == main || !other.isAlive()) {
                    continue;
                }
                if (attacker.distanceToSqr(other) <= 1.0D) {
                    continue;
                }
                other.hurt(attacker.damageSources().playerAttack(attacker), (float) (amount * SWEEP_RATIO));
            }
        } finally {
            sweeping = false;
        }
    }

    /** 以玩家为目标的生物数量（8 格内）。 */
    private static int lockers(Player player) {
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DUEL_RADIUS))) {
            if (mob.getTarget() == player) {
                count++;
            }
        }
        return count;
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect().isBeneficial() || !neutralised(instance)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (target instanceof Player player) {
            if (WornCache.wears(player, ClassicBook.ILIAD) && lockers(player) == 1) {
                event.setResult(Event.Result.DENY);
            }
            return;
        }
        // 对手：只有「唯一在盯着某个伊利亚特佩戴者」的生物才享受公平决斗的待遇。
        for (Player player : target.level().players()) {
            if (!WornCache.wears(player, ClassicBook.ILIAD) || lockers(player) != 1) {
                continue;
            }
            if (soleLocker(player) == target) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    private static LivingEntity soleLocker(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DUEL_RADIUS))) {
            if (mob.getTarget() == player) {
                return mob;
            }
        }
        return null;
    }

    /** 属性判定 + 显式清单。 */
    private static boolean neutralised(MobEffectInstance instance) {
        if (NEUTRALISE.contains(instance.getEffect())) {
            return true;
        }
        for (Attribute attribute : instance.getEffect().getAttributeModifiers().keySet()) {
            if (attribute == Attributes.MAX_HEALTH || attribute == Attributes.ARMOR
                    || attribute == Attributes.MOVEMENT_SPEED || attribute == Attributes.ATTACK_DAMAGE
                    || attribute == Attributes.ATTACK_SPEED) {
                return true;
            }
        }
        return false;
    }
}
