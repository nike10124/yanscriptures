package com.example.examplemod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 《战争论》—— 战争是政治的延续。
 *
 * <p>作者定稿（2026-11-06）：
 * <ul>
 *   <li>受到伤害时积累「士气」（伤害值的 50%，作者 2026-11-07 从 10% 上调），
 *       上限 = 生命上限的 20%；</li>
 *   <li>满值时下一次攻击必定暴击，并附加一次等于当前士气值的真实伤害
 *       （无视护甲与伤害减免）；</li>
 *   <li>触发后的 5 秒内，造成伤害的 20% 也会转成士气；</li>
 *   <li>脱战 10 秒后士气清空。</li>
 * </ul>
 *
 * <p>真实伤害走自定义伤害类型 {@code yanscriptures:true_strike}，它在
 * {@code data/minecraft/tags/damage_type/} 里同时进了 {@code bypasses_armor}、
 * {@code bypasses_enchantments}、{@code bypasses_resistance} 三个原版标签，
 * 不需要 mixin，也不会和别家的减伤事件打架。
 *
 * <p>「必定暴击」按作者要求走装备系统的暴击属性：有
 * {@code l2damagetracker:crit_damage} 就按它给倍率（至少 ×1.5），没有就退回原版暴击的 ×1.5。
 *
 * <p>真实伤害那一笔必须先把目标的 {@code invulnerableTime} 清零再打：主伤害在同一次
 * {@code hurt()} 里已经给它上了 20 刻无敌帧，不处理的话第二笔会被直接吃掉
 * （作者 2026-11-07 报「额外一次真实伤害没有生效」就是这里）。打完把无敌帧还原成
 * 主伤害留下的值，避免我们额外延长防御时间。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OnWarHandler {

    private static final double MORALE_FROM_TAKEN = 0.50D;
    private static final double MORALE_CAP_RATIO = 0.20D;
    private static final long OUT_OF_COMBAT = 200L;
    private static final double VANILLA_CRIT_BONUS = 0.5D;

    private static final String TAG_MORALE = "YanOnWarMorale";
    /** 仅用于日志/排查：上次触发的时间，不再参与「冷却」判断（作者 2026-11-07 报「好像有内置 CD」）。 */
    private static final String TAG_TRIGGERED = "YanOnWarTriggered";
    private static final String TAG_COMBAT = "YanOnWarCombat";

    /**
     * 正在打「真实伤害」那一笔的保险丝。
     *
     * <p>{@link #trueStrike} 会在 {@link LivingHurtEvent} 里再调一次 {@code hurt()}，
     * 那一刻会重入本类的 {@link #onHurt}。如果此时士气还没清零，就会「满士气 → 再触发 →
     * 再打一笔 → 还是满士气」无限递归，最后 StackOverflowError 直接把服务器 tick 打崩
     * （2026-11-07 的崩溃就是这条：真实的异常被开发环境里 log4j 的 LinkageError 盖掉了，
     * 只看到 EventBus.handleException，看不到 StackOverflowError）。
     */
    private static boolean striking;

    private OnWarHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (striking) {
            // 我们自己打出的那笔真实伤害，不再累计士气、也不再触发（防自激）
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            if (!WornCache.wears(victim, ClassicBook.ON_WAR)) {
                return;
            }
            addMorale(victim, event.getAmount() * MORALE_FROM_TAKEN);
            stampCombat(victim);
            syncMorale(victim);
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == event.getEntity()) {
            return;
        }
        if (!WornCache.wears(attacker, ClassicBook.ON_WAR)) {
            return;
        }
        settle(attacker, event);
        stampCombat(attacker);
    }

    private static void settle(Player attacker, LivingHurtEvent event) {
        CompoundTag data = attacker.getPersistentData();
        long now = attacker.level().getGameTime();

        double morale = data.getDouble(TAG_MORALE);
        double cap = attacker.getMaxHealth() * MORALE_CAP_RATIO;
        if (morale + 1.0E-4D < cap || cap <= 0.0D) {
            return;
        }

        event.setAmount((float) (event.getAmount() * critMultiplier(attacker)));
        // 先把士气清零、把「刚触发」的窗口写进去，再打那一笔：
        // trueStrike 会重入 onHurt，顺序反了就是无限递归（见 striking 的注释）
        data.putDouble(TAG_MORALE, 0.0D);
        data.putLong(TAG_TRIGGERED, now);
        striking = true;
        try {
            trueStrike(attacker, event.getEntity(), (float) morale);
        } finally {
            striking = false;
        }
        syncMorale(attacker);
    }

    /** 把当前士气与上限发给客户端右上角的 HUD。 */
    private static void syncMorale(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        double cap = player.getMaxHealth() * MORALE_CAP_RATIO;
        float current = (float) player.getPersistentData().getDouble(TAG_MORALE);
        NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                new NetworkHandler.MoralePacket(current, (float) cap));
    }

    private static void trueStrike(Player attacker, LivingEntity target, float amount) {
        if (!(attacker.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return;
        }
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                        new ResourceLocation(ExampleMod.MODID, "true_strike")));
        // 主伤害刚给目标上了 20 刻无敌帧，不清零这一笔会被直接吞掉
        int savedInvulnerable = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurt(new DamageSource(type, attacker), amount);
        } finally {
            // 还原成主伤害留下的值：我们只负责「让这一笔落地」，不额外延长防御时间
            target.invulnerableTime = savedInvulnerable;
        }
    }

    private static double critMultiplier(Player player) {
        Attribute crit = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_damage"));
        double bonus = crit == null ? 0.0D : player.getAttributeValue(crit);
        return 1.0D + Math.max(VANILLA_CRIT_BONUS, bonus);
    }

    private static void addMorale(Player player, double amount) {
        CompoundTag data = player.getPersistentData();
        double cap = player.getMaxHealth() * MORALE_CAP_RATIO;
        double next = Math.min(cap, data.getDouble(TAG_MORALE) + amount);
        data.putDouble(TAG_MORALE, next);
    }

    private static void stampCombat(Player player) {
        player.getPersistentData().putLong(TAG_COMBAT, player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!WornCache.wears(player, ClassicBook.ON_WAR)) {
            data.putDouble(TAG_MORALE, 0.0D);
            syncMorale(player);
            return;
        }
        long now = player.level().getGameTime();
        if (data.getLong(TAG_COMBAT) > 0L && now - data.getLong(TAG_COMBAT) > OUT_OF_COMBAT) {
            data.putDouble(TAG_MORALE, 0.0D);
        }
        syncMorale(player);
    }
}
