package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 《天体运行论》：太阳、月亮、地球三颗球围着玩家转（作者 2026-11-07 定稿）。
 *
 * <p>作者明确「最好不用粒子特效」，所以三颗球是**实体**（{@link CelestialOrbEntity}），
 * 由这里每刻摆到轨道上，客户端渲染成球面贴图。
 *
 * <p>轨道：等边三角形的三个顶点，半径 1.5 格、绕玩家水平旋转，周期 10 秒（200 刻）。
 *
 * <p>接触规则（全部按作者定稿）：
 * <ul>
 *   <li><b>地球</b>碰到怪物 → 给玩家一个随机正面效果 15 秒，重复触发就升一级（上限 IV）。</li>
 *   <li><b>月亮</b>碰到敌人 → 20% 攻击力伤害 + 随机三级负面效果 15 秒；越接近深夜等级越高（最高 V），正午最低（I）。</li>
 *   <li><b>太阳</b>碰到敌人 → 150% 攻击力伤害；正午升到 250%，深夜降到 50%。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeRevolutionibusHandler {

    private static final Logger LOG = LogUtils.getLogger();

    /** 一圈转多久（刻）：5 秒（作者 2026-11-07 从 10 秒改快）。 */
    private static final int PERIOD_TICKS = 100;

    /** 轨道半径（格）：太阳半径 0.8 格，轨道太近会直接糊住视野，所以从 1.5 放到 2.0。 */
    private static final double ORBIT_RADIUS = 2.0D;

    /** 轨道离玩家脚底多高（格），大概在胸口位置。 */
    private static final double ORBIT_HEIGHT = 1.15D;

    /** 效果持续（刻）：15 秒。 */
    private static final int EFFECT_TICKS = 15 * 20;

    /** 同一颗球对同一只怪的最短间隔（刻），否则每刻都判定一次会连成一条伤害线。 */
    private static final long CONTACT_COOLDOWN = 10L;

    /** 球的碰撞半径（格）：作者反馈「判定太严格」，从 0.75 放宽到 1.2，并按生物碰撞箱算距离。 */
    private static final double CONTACT_RADIUS = 1.2D;

    private static final int SUN_DAMAGE_PERCENT = 150;
    private static final int SUN_NOON_PERCENT = 250;
    private static final int SUN_NIGHT_PERCENT = 50;
    private static final double MOON_DAMAGE_PERCENT = 0.20D;

    /** 地面地球给玩家的正面效果池。 */
    private static final MobEffect[] BENEFICIAL = {
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.REGENERATION,
            MobEffects.ABSORPTION,
            MobEffects.JUMP,
            MobEffects.DIG_SPEED,
            MobEffects.SATURATION,
    };

    /** 月亮给敌人的负面效果池。 */
    private static final MobEffect[] HARMFUL = {
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.CONFUSION,
    };

    /** 每个玩家当前的三颗球（索引 = 种类）。 */
    private static final Map<UUID, CelestialOrbEntity[]> ORBS = new HashMap<>();

    /** 「某玩家某颗球刚碰过某只怪」的时间戳，键 = 玩家|种类|生物。 */
    private static final Map<String, Long> CONTACT = new HashMap<>();

    static {
        SessionCleanup.register(DeRevolutionibusHandler::cleanup);
    }

    private DeRevolutionibusHandler() {
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
        if (!WornCache.wears(player, ClassicBook.DE_REVOLUTIONIBUS)) {
            discard(player, ORBS.remove(id));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        CelestialOrbEntity[] orbs = ORBS.get(id);
        if (orbs == null || !stillValid(orbs, level)) {
            discard(player, orbs);
            orbs = new CelestialOrbEntity[3];
            for (int kind = 0; kind < 3; kind++) {
                orbs[kind] = spawn(level, player, kind);
            }
            ORBS.put(id, orbs);
            LOG.info("《天体运行论》：为 {} 生成三颗天体", player.getGameProfile().getName());
        }

        double base = (level.getGameTime() % PERIOD_TICKS) / (double) PERIOD_TICKS * Math.PI * 2.0D;
        double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        long now = level.getGameTime();
        for (int kind = 0; kind < 3; kind++) {
            CelestialOrbEntity orb = orbs[kind];
            double angle = base + kind * (Math.PI * 2.0D / 3.0D);
            double x = player.getX() + Math.cos(angle) * ORBIT_RADIUS;
            double z = player.getZ() + Math.sin(angle) * ORBIT_RADIUS;
            orb.setPos(x, player.getY() + ORBIT_HEIGHT, z);
            touch(level, player, orb, kind, attack, now);
        }
    }

    /** 某颗球这一 tick 是否碰到了怪物。 */
    private static void touch(ServerLevel level, Player player, CelestialOrbEntity orb, int kind,
                              double attack, long now) {
        // 用「球心到生物碰撞箱的距离」判定：只看球心点会把高大的怪（铁傀儡、凋灵）判丢。
        Vec3 center = orb.position();
        AABB search = new AABB(center, center).inflate(CONTACT_RADIUS + 1.0D);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, search)) {
            if (!mob.isAlive() || mob.isSpectator()) {
                continue;
            }
            if (mob.getBoundingBox().inflate(CONTACT_RADIUS).distanceToSqr(center) > 0.0D) {
                continue;
            }
            String key = player.getUUID() + "|" + kind + "|" + mob.getUUID();
            Long last = CONTACT.get(key);
            if (last != null && now - last < CONTACT_COOLDOWN) {
                continue;
            }
            CONTACT.put(key, now);
            if (kind == CelestialOrbEntity.KIND_EARTH) {
                grantBeneficial(player);
            } else if (kind == CelestialOrbEntity.KIND_MOON) {
                applyMoon(level, player, mob, attack);
            } else {
                applySun(level, player, mob, attack);
            }
        }
        if (CONTACT.size() > 256) {
            CONTACT.clear();
        }
    }

    /** 太阳：正午 250%、黄昏/黎明 150%、深夜 50%，中间按余弦插值。 */
    private static void applySun(ServerLevel level, Player player, Monster mob, double attack) {
        double phase = dayPhase(level);
        double percent = (SUN_NOON_PERCENT + SUN_NIGHT_PERCENT) / 2.0D
                + (SUN_NOON_PERCENT - SUN_NIGHT_PERCENT) / 2.0D * phase;
        float damage = (float) (attack * percent / 100.0D);
        mob.invulnerableTime = 0;
        mob.hurt(player.damageSources().playerAttack(player), damage);
    }

    /** 月亮：20% 攻击力 + 随机负面效果，等级随夜色加深（正午 I → 深夜 V）。 */
    private static void applyMoon(ServerLevel level, Player player, Monster mob, double attack) {
        mob.invulnerableTime = 0;
        mob.hurt(player.damageSources().indirectMagic(player, player), (float) (attack * MOON_DAMAGE_PERCENT));
        double phase = dayPhase(level);
        int extra = (int) Math.round(3.0D - 2.0D * phase);
        if (extra < 1) {
            extra = 1;
        }
        if (extra > 5) {
            extra = 5;
        }
        MobEffect effect = HARMFUL[mob.getRandom().nextInt(HARMFUL.length)];
        mob.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, extra - 1, false, true));
    }

    /** 地球：给玩家一个随机正面效果；已经有同名效果就升一级（上限 IV）。 */
    private static void grantBeneficial(Player player) {
        MobEffect effect = BENEFICIAL[player.getRandom().nextInt(BENEFICIAL.length)];
        MobEffectInstance existing = player.getEffect(effect);
        int amplifier = existing == null ? 0 : Math.min(3, existing.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, false, true));
    }

    /**
     * 昼夜相位：正午 = +1、午夜 = -1。
     *
     * <p>原版 {@code dayTime % 24000 == 6000} 是正午，18000 是午夜。
     */
    private static double dayPhase(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return Math.cos((time - 6000.0D) * (Math.PI * 2.0D) / 24000.0D);
    }

    private static CelestialOrbEntity spawn(ServerLevel level, Player player, int kind) {
        CelestialOrbEntity orb = ExampleMod.CELESTIAL_ORB.get().create(level);
        if (orb == null) {
            throw new IllegalStateException("《天体运行论》：实体类型未注册");
        }
        orb.setKind(kind);
        orb.moveTo(player.getX(), player.getY() + ORBIT_HEIGHT, player.getZ(), 0.0F, 0.0F);
        if (!level.addFreshEntity(orb)) {
            LOG.warn("《天体运行论》：天体实体没能加入世界（{}）", level.dimension().location());
        }
        return orb;
    }

    private static boolean stillValid(CelestialOrbEntity[] orbs, ServerLevel level) {
        for (CelestialOrbEntity orb : orbs) {
            if (orb == null || !orb.isAlive() || orb.level() != level) {
                return false;
            }
        }
        return true;
    }

    private static void discard(Player player, CelestialOrbEntity[] orbs) {
        if (orbs == null) {
            return;
        }
        for (CelestialOrbEntity orb : orbs) {
            if (orb != null) {
                orb.discard();
            }
        }
    }

    /** 玩家下线：把三颗球一起撤掉。 */
    public static void cleanup(UUID playerId) {
        CelestialOrbEntity[] orbs = ORBS.remove(playerId);
        if (orbs != null) {
            for (CelestialOrbEntity orb : orbs) {
                if (orb != null) {
                    orb.discard();
                }
            }
        }
    }
}
