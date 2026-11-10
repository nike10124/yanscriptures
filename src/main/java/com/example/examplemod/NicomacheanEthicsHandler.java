package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 《尼各马可伦理学》nicomachean_ethics。
 *
 * <p>作者定稿（2026-11-08）：「在存在仇恨并且进行攻击时，5 秒没有受到伤害，移动速度提高 15%，
 * 10 秒没有受到伤害，攻击力提高 15%，15 秒没有受到伤害，暴击率提高 15%、暴击伤害提高 30%，
 * 30 秒没有受到伤害，造成的伤害提高 50%，60 秒没有受到伤害，造成的每一次攻击附加一次伤害与当前攻击相同
 * 的小范围伤害。时间长时，每一级效果都会得到保留，受到伤害或者脱战时全部清零。
 * 你攻击的目标必须要对你抱有敌意。」
 *
 * <p>补充口径（作者同日回答）：脱战 = 10 秒；附伤半径 = 1 格的圆。
 *
 * <p>这些加成都依赖「连续多久没挨打」，属于条件型，按项目约定留在 Handler 里用
 * {@link BookAttr} 挂瞬时修饰符；物品侧不给读者看。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NicomacheanEthicsHandler {

    private static final Logger LOG = LogUtils.getLogger();

    /** 逐级门槛（秒）。 */
    private static final int SPEED_SECONDS = 5;
    private static final int ATTACK_SECONDS = 10;
    private static final int CRIT_SECONDS = 15;
    private static final int DAMAGE_SECONDS = 30;
    private static final int EXTRA_SECONDS = 60;

    private static final double SPEED_BONUS = 0.15D;
    private static final double ATTACK_BONUS = 0.15D;
    private static final double CRIT_RATE_BONUS = 0.15D;
    private static final double CRIT_DAMAGE_BONUS = 0.30D;
    private static final double DAMAGE_BONUS = 0.50D;

    /** 脱战判定：这么久没有交战就清零。 */
    private static final int OUT_OF_COMBAT_TICKS = 200;
    /** 附伤半径（作者：1 格的圆）。 */
    private static final double EXTRA_RADIUS = 1.0D;

    /** 玩家 → 已经连续多少刻「有仇恨且没挨打」。 */
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    /** 玩家 → 最近一次交战的游戏刻。 */
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    /** 玩家 → 16 格内是否有敌意生物（每 10 刻刷新一次，省得每刻扫世界）。 */
    private static final Map<UUID, Boolean> HOSTILE_NEARBY = new HashMap<>();

    private static final UUID ID_SPEED = BookAttr.id("classic_nicomachean_speed");
    private static final UUID ID_ATTACK = BookAttr.id("classic_nicomachean_attack");
    private static final UUID ID_CRIT_RATE = BookAttr.id("classic_nicomachean_crit");
    private static final UUID ID_CRIT_DAMAGE = BookAttr.id("classic_nicomachean_crit_damage");

    /** 附伤再打一下时的保险丝，防止自己触发自己。 */
    private static boolean strikingExtra;

    private NicomacheanEthicsHandler() {
    }

    static {
        SessionCleanup.register(NicomacheanEthicsHandler::cleanupPlayer);
    }

    public static void cleanupPlayer(UUID id) {
        TIMER.remove(id);
        LAST_COMBAT.remove(id);
        HOSTILE_NEARBY.remove(id);
    }

    /** 已连续多少秒处于「有仇恨且没受伤」的状态（给物品介绍用）。 */
    public static int secondsOf(Player player) {
        return TIMER.getOrDefault(player.getUUID(), 0) / 20;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        UUID id = player.getUUID();
        if (!WornCache.wears(player, ClassicBook.NICOMACHEAN_ETHICS)) {
            if (TIMER.remove(id) != null) {
                clearAll(player);
            }
            LAST_COMBAT.remove(id);
            HOSTILE_NEARBY.remove(id);
            return;
        }

        if (player.tickCount % 10 == 0) {
            HOSTILE_NEARBY.put(id, hasHostileNearby(player));
        }

        long now = player.level().getGameTime();
        Long lastCombat = LAST_COMBAT.get(id);
        boolean inCombat = Boolean.TRUE.equals(HOSTILE_NEARBY.get(id))
                || (lastCombat != null && now - lastCombat <= OUT_OF_COMBAT_TICKS);

        if (!inCombat) {
            // 脱战：整条计时清零，加成一起撤掉。
            if (TIMER.remove(id) != null) {
                clearAll(player);
            }
            return;
        }

        int timer = TIMER.merge(id, 1, Integer::sum);
        if (timer % 20 == 0) {
            applyBonuses(player, timer / 20);
        }
    }

    /** 挨打就把计时清零并撤掉全部加成。 */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.NICOMACHEAN_ETHICS)) {
            return;
        }
        UUID id = player.getUUID();
        if (TIMER.remove(id) == null) {
            return;
        }
        clearAll(player);
    }

    /** 打出伤害：维持「交战」状态、30 秒的增伤、60 秒的附伤。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoingHurt(LivingHurtEvent event) {
        if (strikingExtra) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.NICOMACHEAN_ETHICS)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 作者：「你攻击的目标必须要对你抱有敌意」——只认敌意生物，打和平生物不算交战。
        if (!(victim instanceof Enemy)) {
            return;
        }
        LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());

        int seconds = TIMER.getOrDefault(player.getUUID(), 0) / 20;
        if (seconds >= DAMAGE_SECONDS && event.getAmount() > 0.0F) {
            event.setAmount(event.getAmount() * (1.0F + (float) DAMAGE_BONUS));
        }
        if (seconds >= EXTRA_SECONDS) {
            strikeAround(player, victim, event.getAmount());
        }
    }

    /** 60 秒档：对目标周围 1 格内的其它生物再打一次「与当前攻击相同」的伤害。 */
    private static void strikeAround(Player player, LivingEntity victim, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        strikingExtra = true;
        try {
            for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class,
                    victim.getBoundingBox().inflate(EXTRA_RADIUS),
                    entity -> entity != player && entity != victim && entity.isAlive()
                            && !entity.isSpectator())) {
                int saved = other.invulnerableTime;
                try {
                    other.invulnerableTime = 0;
                    other.hurt(player.damageSources().playerAttack(player), amount);
                } finally {
                    other.invulnerableTime = saved;
                }
            }
        } finally {
            strikingExtra = false;
        }
    }

    private static void applyBonuses(Player player, int seconds) {
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED,
                seconds >= SPEED_SECONDS ? SPEED_BONUS : 0.0D);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK,
                seconds >= ATTACK_SECONDS ? ATTACK_BONUS : 0.0D);

        Attribute critRate = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_rate"));
        Attribute critDamage = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_damage"));
        if (critRate != null) {
            BookAttr.set(player, critRate, ID_CRIT_RATE, seconds >= CRIT_SECONDS ? CRIT_RATE_BONUS : 0.0D);
        }
        if (critDamage != null) {
            BookAttr.set(player, critDamage, ID_CRIT_DAMAGE, seconds >= CRIT_SECONDS ? CRIT_DAMAGE_BONUS : 0.0D);
        }
    }

    private static void clearAll(Player player) {
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, 0.0D);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, 0.0D);
        Attribute critRate = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_rate"));
        Attribute critDamage = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_damage"));
        if (critRate != null) {
            BookAttr.set(player, critRate, ID_CRIT_RATE, 0.0D);
        }
        if (critDamage != null) {
            BookAttr.set(player, critDamage, ID_CRIT_DAMAGE, 0.0D);
        }
    }

    private static boolean hasHostileNearby(Player player) {
        for (var mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(16.0D),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            if (mob.getTarget() == player) {
                return true;
            }
        }
        return false;
    }
}
