package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《易经》—— 六十秒一变卦，戴着的这六十秒里只吃当前这一卦。
 *
 * <p>作者定稿（2026-11-01）六卦：
 * <ul>
 *   <li>乾：攻击 +10%，且每次攻击 50% 概率视为攻击两次（多的一次不受无敌帧影响，可触发武器特效）；</li>
 *   <li>坤：受伤 −10%，免疫击退，50% 反伤；</li>
 *   <li>震：移速 +15%，疾跑时获得「移动速度加成值 ×200%」的增伤；</li>
 *   <li>坎：水下呼吸 + 游泳速度 +30%，在液体中受伤 −50%、造成伤害 +30%；</li>
 *   <li>离：攻击点燃 3 秒，免疫火焰；被点燃的怪物每受一次燃烧伤害获得随机负面效果 5 秒（重复则升一级）；对燃烧敌人伤害 +15%；</li>
 *   <li>艮：站定不动时受伤 −80%。</li>
 * </ul>
 *
 * <p>卦象存在玩家持久数据里，换卦时把上一卦留下的三个修饰符全部清掉再重新挂，
 * 所以永远不会出现「乾 + 坤同时生效」。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IChingHandler {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        LIT_BY_US.remove(id);
    }

    static final String TAG_HEXAGRAM = "YanIChingHexagram";
    static final String TAG_NEXT_CHANGE = "YanIChingNextChange";

    public static final int QIAN = 0;
    public static final int KUN = 1;
    public static final int ZHEN = 2;
    public static final int KAN = 3;
    public static final int LI = 4;
    public static final int GEN = 5;
    public static final String[] NAMES = {"qian", "kun", "zhen", "kan", "li", "gen"};

    private static final int CYCLE_TICKS = 1200;
    private static final double QIAN_ATTACK = 0.10D;
    private static final double ZHEN_SPEED = 0.15D;
    private static final double KAN_SWIM = 0.30D;

    private static final UUID ID_ATTACK = BookAttr.id("iching_attack");
    private static final UUID ID_SPEED = BookAttr.id("iching_speed");
    private static final UUID ID_SWIM = BookAttr.id("iching_swim");
    private static final UUID ID_KNOCKBACK = BookAttr.id("iching_knockback");

    /**
     * 震卦的增伤系数：<b>最终移速属性值</b> × 200% 就是伤害加成（作者 2026-11-01 定案，选项 B）。
     *
     * <p>「移动的内部数值」= {@code getAttributeValue(MOVEMENT_SPEED)} 算完之后的实际移速，
     * 不是修饰符本身：基础 0.1，戴上《空白的书页》（+10%，乘法乘区）之后是 0.1 × 1.10 = 0.11，
     * 于是 0.11 × 200% = <b>+22% 伤害</b>；几本一起戴到 0.17 就是 +34%。
     * 注意别再用「修饰符之和」那种读法（0.10 直接当 20%）——那是把内部值当成相对基础值的倍数，
     * 只有 {@code MULTIPLY_TOTAL} 才那么算。也别自行把系数调成 1.0（作者试过又让改回来了）。
     */
    private static final double ZHEN_DAMAGE_FACTOR = 2.0D;

    /** 被《离》点燃的生物 → 点燃时刻，超过 200 刻就不再多管。 */
    private static final Map<UUID, Long> LIT_BY_US = new HashMap<>();

    /**
     * 正在补「第二次攻击」的保险丝。
     *
     * <p>{@code target.hurt(...)} 会再触发一次 {@link LivingHurtEvent}，如果不拦住，
     * 第二次攻击又会掷一次 50%、再补一次，递归下去能把服务器栈打穿。
     */
    private static boolean rollingExtraHit;

    /** 当前这次 `hurt()` 是不是《易经》补出来的那一笔（排查工具用）。 */
    static boolean isExtraHit() {
        return rollingExtraHit;
    }

    private static final MobEffect[] BAD_EFFECTS = {
            MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.WITHER
    };

    private IChingHandler() {
    }

    public static int hexagramOf(Player player) {
        return Math.floorMod(player.getPersistentData().getInt(TAG_HEXAGRAM), 6);
    }

    /**
     * 直接把卦象定成某一个（指令入口，{@code /ys iching <卦>}）。
     *
     * <p>顺带把下一个自动起卦的时间往后推一整轮，否则刚定完就被随机卦盖掉了。
     */
    public static void setHexagram(Player player, int hex) {
        int value = Math.floorMod(hex, NAMES.length);
        CompoundTag data = player.getPersistentData();
        data.putInt(TAG_HEXAGRAM, value);
        data.putLong(TAG_NEXT_CHANGE, player.level().getGameTime() + CYCLE_TICKS);
        applyHexagram(player, value);
        player.displayClientMessage(Component.translatable("message.yanscriptures.iching.set",
                Component.translatable("gui.yanscriptures.iching." + NAMES[value])), true);
    }

    /** 指令自动补全用：六个卦名 + random。 */
    public static final String[] COMMAND_NAMES =
            {"qian", "kun", "zhen", "kan", "li", "gen", "random"};

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.I_CHING)) {
            clearAll(player);
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        long next = data.getLong(TAG_NEXT_CHANGE);
        if (next == 0L || now >= next) {
            int newHex = player.getRandom().nextInt(6);
            data.putInt(TAG_HEXAGRAM, newHex);
            data.putLong(TAG_NEXT_CHANGE, now + CYCLE_TICKS);
            player.displayClientMessage(Component.translatable(
                    "message.yanscriptures.iching.change",
                    Component.translatable("gui.yanscriptures.iching." + NAMES[newHex])), true);
        }
        applyHexagram(player, hexagramOf(player));
    }

    private static void applyHexagram(Player player, int hex) {
        BookAttr.clear(player, Attributes.ATTACK_DAMAGE, ID_ATTACK);
        BookAttr.clear(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
        BookAttr.clear(player, ForgeMod.SWIM_SPEED.get(), ID_SWIM);
        BookAttr.clear(player, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK);

        switch (hex) {
            case QIAN -> BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, QIAN_ATTACK);
            case KUN -> BookAttr.set(player, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK, 1.0D,
                    AttributeModifier.Operation.ADDITION);
            case ZHEN -> BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, ZHEN_SPEED);
            case KAN -> {
                BookAttr.set(player, ForgeMod.SWIM_SPEED.get(), ID_SWIM, KAN_SWIM);
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false, true));
            }
            default -> {
            }
        }
    }

    private static void clearAll(Player player) {
        BookAttr.clear(player, Attributes.ATTACK_DAMAGE, ID_ATTACK);
        BookAttr.clear(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
        BookAttr.clear(player, ForgeMod.SWIM_SPEED.get(), ID_SWIM);
        BookAttr.clear(player, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK);
    }

    /**
     * 玩家当前的最终移动速度属性值 —— 震卦的「移动的内部数值」。
     *
     * <p>原版 {@code AttributeMap} 算完的结果：{@code (0.1 + 加法) × (1 + 乘法乘区之和)}，
     * 所以 +10% 移速返回 0.11，而不是 0.10。
     */
    private static double finalSpeed(Player player) {
        return player.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    private static boolean stationary(Player player) {
        return player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4D && player.onGround();
    }

    @SubscribeEvent
    public static void onOutgoing(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker.level().isClientSide()) {
            return;
        }
        if (rollingExtraHit) {
            return;
        }
        if (!WornCache.wears(attacker, ClassicBook.I_CHING)) {
            return;
        }
        LivingEntity target = event.getEntity();
        int hex = hexagramOf(attacker);
        double factor = 1.0D;
        if (hex == QIAN) {
            factor *= 1.0D + QIAN_ATTACK;
            if (attacker.getRandom().nextFloat() < 0.5F) {
                // 「视为攻击两次」：多出来的那一次不受无敌帧影响，所以先归零再补一次伤害。
                float extra = event.getAmount() * (float) (1.0D + QIAN_ATTACK);
                rollingExtraHit = true;
                try {
                    target.invulnerableTime = 0;
                    target.hurt(attacker.damageSources().playerAttack(attacker), extra);
                } finally {
                    rollingExtraHit = false;
                }
            }
        } else if (hex == ZHEN && attacker.isSprinting()) {
            factor *= 1.0D + finalSpeed(attacker) * ZHEN_DAMAGE_FACTOR;
        } else if (hex == KAN && attacker.isInWaterOrBubble()) {
            factor *= 1.30D;
        } else if (hex == LI) {
            target.setSecondsOnFire(3);
            LIT_BY_US.put(target.getUUID(), attacker.level().getGameTime());
            if (target.isOnFire()) {
                factor *= 1.15D;
            }
        }
        if (factor != 1.0D) {
            event.setAmount((float) (event.getAmount() * factor));
        }
    }

    @SubscribeEvent
    public static void onIncoming(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.I_CHING)) {
            return;
        }
        int hex = hexagramOf(player);
        if (hex == KUN) {
            event.setAmount((float) (event.getAmount() * 0.90D));
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity living && player.getRandom().nextFloat() < 0.5F) {
                // 反伤本身也会走一遍 LivingHurtEvent，同样用保险丝拦住，免得被自己的增伤再算一次。
                rollingExtraHit = true;
                try {
                    living.hurt(player.damageSources().thorns(player), event.getAmount() * 0.5F);
                } finally {
                    rollingExtraHit = false;
                }
            }
        } else if (hex == KAN && player.isInWaterOrBubble()) {
            event.setAmount((float) (event.getAmount() * 0.50D));
        } else if (hex == GEN && stationary(player)) {
            event.setAmount((float) (event.getAmount() * 0.20D));
        }
    }

    /** 离卦：被我们点燃的东西每烧一次就随机吃一个负面效果（重复则升一级）。 */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        Long lit = LIT_BY_US.get(entity.getUUID());
        if (lit == null) {
            return;
        }
        long now = entity.level().getGameTime();
        if (now - lit > 200L) {
            LIT_BY_US.remove(entity.getUUID());
            return;
        }
        MobEffect effect = BAD_EFFECTS[entity.getRandom().nextInt(BAD_EFFECTS.length)];
        MobEffectInstance existing = entity.getEffect(effect);
        int amplifier = existing == null ? 0 : Math.min(4, existing.getAmplifier() + 1);
        entity.addEffect(new MobEffectInstance(effect, 100, amplifier, false, true, true));
    }

    /** 离卦的免疫火焰：只有戴着离卦时才拦火伤。 */
    @SubscribeEvent
    public static void onFireDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        if (WornCache.wears(player, ClassicBook.I_CHING) && hexagramOf(player) == LI) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }
}
