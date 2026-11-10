package com.example.examplemod;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《罪与罚》—— 罪越深，力气越大，也越容易挨打。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>每次击杀 +1 层「罪」，上限 10 层，死亡或摘下清空；</li>
 *   <li>每层：攻击力 +2%（乘法乘区）、受到伤害 +2%；</li>
 *   <li>满 10 层：你造成的攻击无敌帧 −0.2 秒，但受到的最终伤害再 ×1.2；</li>
 *   <li>彩蛋（不写进物品文本）：满 10 层时，攻击有 1‰ 概率召来雷击，
 *       对落点周围（包括自己）造成「面板攻击力 × 666」的物理与魔法伤害。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrimeAndPunishmentHandler {

    /** 层数存在玩家持久数据里（死亡会清，但不写进物品，换书不带走）。 */
    static final String TAG_LAYERS = "YanCrimeLayers";

    private static final int MAX_LAYERS = 10;
    private static final double PER_LAYER_ATTACK = 0.02D;
    private static final double PER_LAYER_INCOMING = 0.02D;
    /** 满层之后「最终伤害 ×1.2」——这是作者改过的口径：不与「受到伤害」属性同乘区。 */
    private static final double TEN_LAYER_FINAL_MULTIPLIER = 1.2D;
    /**
     * 无敌帧缩短量（0.2 秒 = 4 刻）。
     *
     * <p>不是「清掉」：原版一次命中给对手 10 刻（0.5 秒）无敌，满层只是把它砍到 6 刻，
     * 所以连击更快、但不是刀刀都吃满。
     */
    private static final int INVULNERABLE_CUT = 4;
    /**
     * 彩蛋触发概率 1‰ —— **每次造成伤害**掷一次（入口是 {@link #onOutgoingHurt} 的
     * {@code LivingHurtEvent}），不是击杀时掷。作者确认过这个口径。
     */
    private static final float EASTER_EGG_CHANCE = 0.001F;
    private static final double EASTER_EGG_MULTIPLIER = 666.0D;
    private static final double EASTER_EGG_RADIUS = 3.5D;

    /** 召雷进行中：雷击产生的伤害会再触发一次 {@code LivingHurtEvent}，用它掐掉递归。 */
    private static boolean rolling;

    private CrimeAndPunishmentHandler() {
    }

    private static int layers(Player player) {
        return player.getPersistentData().getInt(TAG_LAYERS);
    }

    private static void setLayers(Player player, int value) {
        player.getPersistentData().putInt(TAG_LAYERS, Math.max(0, Math.min(MAX_LAYERS, value)));
    }

    /** 把层数同步给客户端 HUD（左下角「罪孽 N / 10」，0 层自动隐藏）。 */
    private static void syncLayers(Player player, int value) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> server),
                    new NetworkHandler.CrimeLayerPacket(value));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.CRIME_AND_PUNISHMENT)) {
            return;
        }
        setLayers(player, layers(player) + 1);
        syncLayers(player, layers(player));
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
        boolean worn = WornCache.wears(player, ClassicBook.CRIME_AND_PUNISHMENT);
        if (!worn) {
            setLayers(player, 0);
        }
        int layers = layers(player);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, BookAttr.id("crime_attack"), layers * PER_LAYER_ATTACK);
        // 每 20 刻同步一次，客户端 HUD 靠它显示层数（死亡/换书归零也会被这条带过去）
        syncLayers(player, layers);
    }

    /** 打出去的那一下顺手掷一次彩蛋（1‰）。 */
    @SubscribeEvent
    public static void onOutgoingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker.level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == attacker || !target.isAlive()) {
            return;
        }
        rollEasterEgg(attacker, target);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(victim, ClassicBook.CRIME_AND_PUNISHMENT)) {
            return;
        }
        int layers = layers(victim);
        if (layers <= 0) {
            return;
        }
        double multiplier = 1.0D + layers * PER_LAYER_INCOMING;
        if (layers >= MAX_LAYERS) {
            multiplier *= TEN_LAYER_FINAL_MULTIPLIER;
        }
        event.setAmount((float) (event.getAmount() * multiplier));
    }

    /** 满层之后，你打出去的那一下让对手的无敌帧缩短。 */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (layers(player) < MAX_LAYERS || !WornCache.wears(player, ClassicBook.CRIME_AND_PUNISHMENT)) {
            return;
        }
        LivingEntity target = event.getEntity();
        target.invulnerableTime = Math.max(1, target.invulnerableTime - INVULNERABLE_CUT);
    }

    /** 彩蛋：满层**每次命中**的 1‰ 召雷（含自己与落点 3.5 格内的所有活物）。 */
    public static void rollEasterEgg(Player player, LivingEntity target) {
        if (rolling) {
            // 雷击本身也会走一遍 LivingHurtEvent，不拦会掷出「雷中雷」
            return;
        }
        if (layers(player) < MAX_LAYERS || !WornCache.wears(player, ClassicBook.CRIME_AND_PUNISHMENT)) {
            return;
        }
        if (player.getRandom().nextFloat() >= EASTER_EGG_CHANCE) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        rolling = true;
        try {
            double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE) * EASTER_EGG_MULTIPLIER;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setCause(player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
                level.addFreshEntity(bolt);
            }
            AABB box = target.getBoundingBox().inflate(EASTER_EGG_RADIUS);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box);
            for (LivingEntity victim : victims) {
                // 物理与魔法各一段，每段都是「面板攻击力 × 666」
                victim.hurt(level.damageSources().playerAttack(player), (float) damage);
                victim.hurt(level.damageSources().indirectMagic(player, player), (float) damage);
            }
        } finally {
            rolling = false;
        }
    }
}
