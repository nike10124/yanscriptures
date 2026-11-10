package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 《埃涅阿斯纪》—— 我歌唱战争，也歌唱一个人。
 *
 * <p>作者定稿（2026-11-06）：
 * 「非 boss 级的亡灵生物对你将保持中立，并且会主动攻击，攻击了你的非亡灵生物，
 * 瞬间伤害、中毒和凋零将会恢复你的生命值，瞬间治疗、生命回复将会降低你的生命，
 * 饥饿效果会回复你的饱和度，和饥饿值，饱和效果会降低你的饥饿值和饱和度」。
 *
 * <p>拆成两半实现：
 * <ul>
 *   <li>「亡灵中立 + 替你反击」看原版 {@link MobType#UNDEAD}，不点任何模组的类名——
 *       僵尸、骷髅、幻翼、以及别的模组里声明成亡灵的生物都吃得上。</li>
 *   <li>「治疗/伤害反转」只碰玩家自己身上的药水效果与被无主的魔法伤害打中的那一下。</li>
 * </ul>
 *
 * <p>boss 判定**只看 {@code forge:bosses} 标签**（作者 2026-11-06 明确：整合包里绝大多数生物
 * 生命上限都在 150 以下，用血量判定等于把「排除 boss」这条写废了）。
 *
 * <p>2026-11-07 作者报「替你反击没效果、反转有问题」→ 每个分支都加了日志，
 * 只在状态变化/节流窗口内打印，方便直接看 `latest.log` 判断是哪一环没走到。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AeneidHandler {

    private static final Logger LOG = LogUtils.getLogger();

    /** 亡灵的「守护」范围：与作者写的「周围」对齐，沿用 16 格这一档。 */
    private static final double AURA_RADIUS = 16.0D;

    /** 被非亡灵打中之后，亡灵会盯着这个攻击者多久。 */
    private static final long MARK_TICKS = 200L;

    /** 每隔多少刻重扫一次周围的亡灵（10 刻 = 半秒，够跟手也不浪费）。 */
    private static final int SWEEP_INTERVAL = 10;

    /**
     * 一级瞬间伤害/瞬间治疗的原版数值。
     *
     * <p>这不是一个数：原版瞬间伤害是 {@code 6 << amplifier}，瞬间治疗是 {@code 4 << amplifier}
     * （{@code InstantenousMobEffect} 里写死），曾经用同一个 6.0 抵消治疗，导致「治疗了 4 点、
     * 只扣了 6 点」看起来像在回血——作者 2026-11-07 报的就是这个。
     */
    private static final float INSTANT_DAMAGE_AMOUNT = 6.0F;

    private static final float INSTANT_HEAL_AMOUNT = 4.0F;

    /** 饥饿/饱和反转时一次挪动的食物与饱和度点数。 */
    private static final int FOOD_STEP = 4;

    /** 同一玩家同一类日志的节流间隔（毫秒）。 */
    private static final long LOG_INTERVAL_MS = 2000L;

    /** 亡灵守护时补刀的距离平方（3.5 格）。 */
    private static final double BITE_RANGE_SQR = 12.25D;

    /** 玩家 UUID → 「谁打了我」。 */
    private static record Mark(UUID attacker, long until) {
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    /** 玩家 UUID + 日志键 → 上次打印的时间，防刷屏。 */
    private static final Map<String, Long> LOG_AT = new HashMap<>();

    /** 玩家 UUID → 「{@link MobEffectEvent.Applicable} 刚刚处理过瞬时效果」的 tickCount。 */
    private static final Map<UUID, Integer> APPLICABLE_AT = new HashMap<>();

    /** 我们自己造成的伤害不要再被反转一遍。 */
    private static boolean inverting;

    /** 玩家 UUID → 最近一次「开始喝/用治疗药水」的 tickCount（窗口见 {@link #recentPotion}）。 */
    private static final Map<UUID, Integer> POTION_AT = new HashMap<>();

    /**
     * 被佩戴者亲手打过的亡灵 → 允许它还手的截止游戏刻。
     *
     * <p>作者 2026-11-07：「非boss亡灵生物的中立效果实际上是友善，就算攻击也不会还手」
     * ——「中立」应该是「不主动咬你」，而不是「打完也不还手」。所以只要玩家打了它，
     * 就把这只亡灵从「中立保护」里摘出去一段时间（{@link #RETALIATE_TICKS}），
     * 让它能正常锁定、正常反击；过了这段时间再恢复中立。
     */
    private static final Map<UUID, Long> RETALIATE = new HashMap<>();

    /** 玩家打完亡灵后，允许它反击多久（刻）：15 秒。 */
    private static final long RETALIATE_TICKS = 300L;

    static {
        SessionCleanup.register(AeneidHandler::cleanupPlayer);
    }

    private AeneidHandler() {
    }

    // ------------------------------------------------------------------
    // 一、亡灵中立：不让亡灵把佩戴者当目标
    // ------------------------------------------------------------------

    /**
     * 亡灵换目标时，如果新目标就是佩戴者，直接清空。
     *
     * <p>{@link LivingChangeTargetEvent} 在 1.20.1-47 没有「取消」开关，
     * 只能 {@code setNewTarget(null)}——效果一样：它这一 tick 不会把你记成目标。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player)) {
            return;
        }
        if (!wears(player)) {
            return;
        }
        LivingEntity mob = event.getEntity();
        if (mob.getMobType() != MobType.UNDEAD || isBoss(mob)) {
            return;
        }
        if (mob instanceof Mob angry && retaliating(angry, mob.level().getGameTime())) {
            return;
        }
        event.setNewTarget(null);
        log(player, "neutral",
                "《埃涅阿斯纪》：{} 想把你当目标 → 已清空（亡灵中立）", mob.getName().getString());
    }

    /**
     * 玩家亲手打了亡灵 → 这只亡灵在 {@link #RETALIATE_TICKS} 内不再受「中立」保护。
     *
     * <p>作者 2026-11-07：「就算攻击也不会还手」——中立指的是不主动攻击，不是打完还不还手。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerHitsUndead(LivingHurtEvent event) {
        if (inverting || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !wears(player)) {
            return;
        }
        if (mob.getMobType() != MobType.UNDEAD || isBoss(mob)) {
            return;
        }
        RETALIATE.put(mob.getUUID(), player.level().getGameTime() + RETALIATE_TICKS);
    }

    /** 这只亡灵是不是「刚被打过、允许还手」。过期顺手清掉。 */
    private static boolean retaliating(Mob mob, long now) {
        Long until = RETALIATE.get(mob.getUUID());
        if (until == null) {
            return false;
        }
        if (until < now) {
            RETALIATE.remove(mob.getUUID());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 二、伤害反转：无主的魔法/凋零伤害 → 回血；药水效果本身也被改写
    // ------------------------------------------------------------------

    /**
     * 中毒、凋零、瞬间伤害打上来的伤害改成回血。
     *
     * <p>判据是「没有实体来源的魔法/凋零伤害」——中毒与凋零在 1.20.1 里就是
     * {@code magic} / {@code wither} 两个伤害类型、且没有攻击者实体。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || inverting) {
            return;
        }
        if (!wears(player)) {
            return;
        }
        DamageSource source = event.getSource();
        if (reverses(player, source)) {
            float amount = event.getAmount();
            event.setCanceled(true);
            // 记一笔「这一下已经在伤害事件里反转过」：紧接着到来的效果派发（瞬间伤害药水会先
            // hurt 再 addEffect）不能再走 Added 里的兜底补偿，否则一次药水会被反转两遍。
            APPLICABLE_AT.put(player.getUUID(), player.tickCount);
            healQuietly(player, amount);
            log(player, "invert",
                    "《埃涅阿斯纪》：{} 伤害 {} → 改为回复生命（当前 {}）",
                    source.getMsgId(), String.format("%.1f", amount),
                    String.format("%.1f", player.getHealth()));
            return;
        }
        logSuspicious(player, source, event.getAmount());
        // 「替你反击」：被非亡灵打了，记下攻击者，交给下面的 tick 派活。
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player
                && attacker.getMobType() != MobType.UNDEAD && !isBoss(attacker)) {
            MARKS.put(player.getUUID(), new Mark(attacker.getUUID(),
                    player.level().getGameTime() + MARK_TICKS));
            log(player, "mark",
                    "《埃涅阿斯纪》：被 {} 打中 → 记下攻击者，10 秒内指挥附近亡灵反击",
                    attacker.getName().getString());
        }
    }

    private static boolean reverses(Player player, DamageSource source) {
        boolean noEntity = !(source.getEntity() instanceof LivingEntity)
                && !(source.getDirectEntity() instanceof LivingEntity);
        // 喷溅药水：伤害源上挂的是「扔药水的那个人」和「那瓶药水」。按「扔的人是活体」一刀切会把
        // 玩家自己扔的瞬间伤害药水漏掉（作者 2026-11-07 实测「瞬间伤害照旧生效」就是这个原因），
        // 所以只要直接来源是老版的 ThrownPotion 就一律算作药水伤害，不认扔的人是谁。
        boolean potion = source.getDirectEntity() instanceof ThrownPotion;
        if (potion) {
            // 药水打出来的伤害，不管伤害类型叫什么（thrown / magic / indirect_magic），
            // 一律当成「瞬间伤害 → 回血」处理。作者 2026-11-07：直接改成等量回血即可。
            return true;
        }
        if (!noEntity) {
            return false;
        }
        String id = source.getMsgId();
        return player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER)
                || "magic".equals(id) || "wither".equals(id);
    }

    /**
     * 没被反转、但看起来像药水/瞬时伤害的那一下，打一行日志。
     *
     * <p>作者 2026-11-07 报「瞬间伤害药水依旧能杀死我」，需要先知道那一下的
     * 伤害类型名与来源实体到底是什么，否则只能猜。
     */
    private static void logSuspicious(Player player, DamageSource source, float amount) {
        String id = source.getMsgId();
        boolean potionish = source.getDirectEntity() instanceof ThrownPotion
                || id.contains("magic") || id.contains("thrown") || id.contains("indirect");
        if (!potionish) {
            return;
        }
        log(player, "miss", "《埃涅阿斯纪》[未反转] {} 伤害 {}（直接来源 {}，来源 {}，生命 {}）",
                id, String.format("%.1f", amount),
                source.getDirectEntity() == null ? "无" : source.getDirectEntity().getType().toString(),
                source.getEntity() == null ? "无" : source.getEntity().getType().toString(),
                String.format("%.1f", player.getHealth()));
    }

    /**
     * 药水效果本身反转：瞬间伤害→回血、瞬间治疗→掉血、饥饿→补给、饱和→削减。
     *
     * <p>用 {@code DENY} 拦掉原效果再手动做反向的事，比让它先生效再撤销干净。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!wears(player)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        // 用注册名判断而不是字段名：不同映射版本里「瞬间伤害」的字段叫过 HARM / DAMAGE，
        // 注册名 instant_damage 是一直不变的。
        String key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).getPath();
        int tier = 1 << instance.getAmplifier();
        switch (key) {
            case "instant_damage" -> {
                event.setResult(Event.Result.DENY);
                APPLICABLE_AT.put(player.getUUID(), player.tickCount);
                healQuietly(player, INSTANT_DAMAGE_AMOUNT * tier);
                log(player, "instant", "《埃涅阿斯纪》：瞬间伤害 → 回复 {} 生命",
                        String.format("%.1f", INSTANT_DAMAGE_AMOUNT * tier));
            }
            case "instant_health" -> {
                event.setResult(Event.Result.DENY);
                APPLICABLE_AT.put(player.getUUID(), player.tickCount);
                loseHealth(player, INSTANT_HEAL_AMOUNT * tier);
                log(player, "instant", "《埃涅阿斯纪》：瞬间治疗 → 造成 {} 伤害",
                        String.format("%.1f", INSTANT_HEAL_AMOUNT * tier));
            }
            case "hunger" -> {
                event.setResult(Event.Result.DENY);
                player.getFoodData().eat(FOOD_STEP, 1.0F);
                log(player, "food", "《埃涅阿斯纪》：饥饿 → 补给 {} 点食物与饱和度", FOOD_STEP);
            }
            case "saturation" -> {
                event.setResult(Event.Result.DENY);
                FoodData food = player.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - FOOD_STEP));
                food.setSaturation(Math.max(0.0F, food.getSaturationLevel() - FOOD_STEP));
                log(player, "food", "《埃涅阿斯纪》：饱和 → 扣除 {} 点食物与饱和度", FOOD_STEP);
            }
            default -> {
            }
        }
    }

    /**
     * 瞬时效果的兜底路径。
     *
     * <p>实测（作者 2026-11-07 的 `latest.log`）：喝瞬间治疗药水时
     * {@link MobEffectEvent.Applicable} 根本没有被派发——瞬时效果走的是
     * {@code InstantenousMobEffect#applyInstantenousEffect}，Forge 只在「有持续时间」的分支上
     * 发 Applicable。所以效果已经生效之后，在 {@link MobEffectEvent.Added} 里补一次反向操作：
     * <ul>
     *   <li>瞬间治疗：原版已经回了 {@code 4 << amplifier}，这里再扣同样多——合计净减一份，
     *       等于「治疗是掉血」。</li>
     *   <li>瞬间伤害：原版已经扣了 {@code 6 << amplifier}，这里回两份，净增一份。</li>
     * </ul>
     * 三种情况会跳过：不是玩家、没戴这本书、以及 {@link #onEffectApplicable} 在同一 tick
     * 已经拦下（那时效果没生效，不能再补）。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || !wears(player)) {
            return;
        }
        compensateInstant(player, event.getEffectInstance());
    }

    /**
     * 第二条兜底：喝药水走的是 {@code PotionItem#finishUsingItem}，瞬时效果在这一步就结算完了，
     * 连 {@code MobEffectEvent.Added} 都不一定发。这里在「喝完」之后再补一次反向。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFinishUsing(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || !wears(player)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getItem();
        if (stack.getItem() instanceof net.minecraft.world.item.PotionItem) {
            for (MobEffectInstance instance : net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack)) {
                compensateInstant(player, instance);
            }
        }
    }

    /**
     * 瞬时效果的补偿。已经由 {@link #onEffectApplicable} 处理过的（同一 tick）直接跳过，
     * 免得一条路径走完另一条又补一遍。
     */
    private static void compensateInstant(Player player, MobEffectInstance instance) {
        Integer handled = APPLICABLE_AT.get(player.getUUID());
        if (handled != null && Math.abs(handled - player.tickCount) <= 2) {
            return;
        }
        String key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).getPath();
        int tier = 1 << instance.getAmplifier();
        switch (key) {
            case "instant_damage" -> {
                APPLICABLE_AT.put(player.getUUID(), player.tickCount);
                healQuietly(player, 2.0F * INSTANT_DAMAGE_AMOUNT * tier);
                log(player, "instant-fallback", "《埃涅阿斯纪》：瞬间伤害（兜底）→ 回复 {} 生命",
                        String.format("%.1f", 2.0F * INSTANT_DAMAGE_AMOUNT * tier));
            }
            case "instant_health" -> {
                APPLICABLE_AT.put(player.getUUID(), player.tickCount);
                loseHealth(player, 2.0F * INSTANT_HEAL_AMOUNT * tier);
                log(player, "instant-fallback", "《埃涅阿斯纪》：瞬间治疗（兜底）→ 造成 {} 伤害",
                        String.format("%.1f", 2.0F * INSTANT_HEAL_AMOUNT * tier));
            }
            default -> {
            }
        }
    }

    /**
     * 一切「回血」在这里被截住：生命回复与瞬间治疗都走 {@code LivingEntity#heal()}，
     * Forge 在这一步发 {@link LivingHealEvent}。拦掉并把等量伤害加回去，正好等价于
     * 「治疗反而掉血」，而且不管药水是喝的还是喷溅的、有没有走药水物品那条路。
     *
     * <p>只认两种来源，避免把吃东西自然回血也一起反了：
     * <ul>
     *   <li>身上有生命回复效果 —— 它每跳回 1 点；</li>
     *   <li>单次回复量 ≥ 3 —— 瞬间治疗是 {@code 4 << 等级}，自然回复与金苹果都只有 1~2。</li>
     * </ul>
     * 2026-11-07 作者实测：只靠 `Applicable` / `Added` / 药水用尽事件都抓不到瞬间效果
     * （日志里一行都没有），只有 heal 这一层是必经之路。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || inverting) {
            return;
        }
        // 调试探针（2026-11-07 作者连续三次报「瞬间治疗照旧生效」而日志里一行都没有）：
        // 无论反不反转都打一行，用来确认「事件有没有来」和「书到底戴没戴」。
        logProbe(player, event.getAmount());
        if (!wears(player)) {
            return;
        }
        float amount = event.getAmount();
        boolean regeneration = player.hasEffect(MobEffects.REGENERATION);
        // 「瞬间治疗」的原版数值是 4 << 等级，一次就跳 4~8 点；自然回血、生命回复每跳、
        // 金苹果的持续回复都只有 1 点左右。没有药水窗口时（喷溅药水抓不到「使用物品」），
        // 用这个量级当判据——作者 2026-11-07 认可「瞬间治疗 → 扣血」优先于「怕误伤」。
        boolean instant = amount >= 3.5F || recentPotion(player);
        if (!regeneration && !instant) {
            // 既不是生命回复、也不是瞬间治疗 —— 吃东西回血不该被反转。
            return;
        }
        event.setCanceled(true);
        if (regeneration && !instant) {
            // 生命回复的每一跳只取消回血，扣血交给 tick 的 drainRegeneration（按原版节奏、满血也扣），
            // 否则同一次回血会被扣两遍。
            return;
        }
        if (instant) {
            // 一瓶药水只反转第一次回血，别把同一瓶里的其它效果也算进来。
            POTION_AT.remove(player.getUUID());
        }
        hurtByMagic(player, amount);
        log(player, "heal", "《埃涅阿斯纪》：治疗 {} 点 → 改为等量魔法伤害（当前 {}）",
                String.format("%.1f", amount), String.format("%.1f", player.getHealth()));
    }

    private static void logProbe(Player player, float amount) {
        String id = player.getUUID() + "|heal-probe";
        long now = System.currentTimeMillis();
        Long last = LOG_AT.get(id);
        if (last != null && now - last < LOG_INTERVAL_MS) {
            return;
        }
        LOG_AT.put(id, now);
        LOG.info("《埃涅阿斯纪》[调试] 收到治疗事件 {} 点（佩戴={}，生命回复={}，药水窗口={}）",
                String.format("%.1f", amount), wears(player),
                player.hasEffect(MobEffects.REGENERATION), recentPotion(player));
    }

    /**
     * 玩家刚喝/刚扔过药水的窗口期。
     *
     * <p>为什么不用 {@code MobEffectEvent.Applicable/Added}：实测瞬时效果那两条事件都不发
     * （作者 2026-11-07 的日志里一行都没有）。药水「使用」这一层反而是稳定的——
     * 喝药水从 {@code Start} 到生效约 32 刻，所以窗口取 60 刻。
     */
    private static boolean recentPotion(Player player) {
        Integer at = POTION_AT.get(player.getUUID());
        return at != null && player.tickCount - at <= 60;
    }

    @SubscribeEvent
    public static void onPotionUse(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && wears(player)
                && event.getItem().getItem() instanceof net.minecraft.world.item.PotionItem) {
            POTION_AT.put(player.getUUID(), player.tickCount);
        }
    }

    /**
     * 用「等量魔法伤害」代替直接改血量。
     *
     * <p>作者 2026-11-07 的提议：瞬间伤害那侧已经是「取消 → 等量回血」，瞬间治疗这侧干脆反过来
     * 「取消 → 等量魔法伤害」。好处是伤害走真正的伤害管线：会出飘字、会触发死亡、会被别的模组
     * 的减伤看见；比直接 {@code setHealth} 更「像一个伤害源」。
     *
     * <p>两个必须处理的细节：
     * <ul>
     *   <li>{@code inverting} 置位——否则这 4 点魔法伤害会再次进 {@link #onHurt}，
     *       被「无主 magic 伤害 → 回血」原路反转回去，等于没打。</li>
     *   <li>清零无敌帧——刚喝完药水时玩家往往还剩几刻无敌帧，不清零这笔伤害会被直接吞掉
     *       （之前用「1 点魔法伤害抵消生命回复」就是这么失效的）。</li>
     * </ul>
     */
    private static void hurtByMagic(Player player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        inverting = true;
        int savedInvulnerable = player.invulnerableTime;
        try {
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().magic(), amount);
        } finally {
            player.invulnerableTime = savedInvulnerable;
            inverting = false;
        }
    }

    private static void healQuietly(Player player, float amount) {
        inverting = true;
        try {
            player.heal(amount);
        } finally {
            inverting = false;
        }
    }

    /**
     * 直接扣血，不走 {@code hurt()}。
     *
     * <p>原来用「1 点魔法伤害」去抵消生命回复，作者实测血线纹丝不动还在涨：玩家的无敌帧、
     * 别的模组的减伤/免疫都会把这一点击穿掉。生命值本来就是一个数字，直接改它最可靠。
     */
    private static void loseHealth(Player player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        inverting = true;
        try {
            float left = player.getHealth() - amount;
            if (left <= 0.0F) {
                left = 0.0F;
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
            player.setHealth(left);
        } finally {
            inverting = false;
        }
    }

    // ------------------------------------------------------------------
    // 三、每 tick：生命回复反转 + 亡灵守护
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || !wears(player)) {
            MARKS.remove(player.getUUID());
            return;
        }
        // 生命回复的反转在这里按「原版回血节奏」主动扣，不看当前血量；heal 事件那层只负责把回血截掉。
        drainRegeneration(player);
        // 有「打了我的人」时每 tick 都要重设目标：原版亡灵的目标筛选目标不接受蜘蛛、苦力怕这类
        // 非玩家生物，`setTarget()` 会在下一 tick 被 `canContinueToUse()` 丢掉。这里既每 tick 重设，
        // 也主动寻路 + 近身补刀，保证「替我反击」看得见。
        boolean guarding = MARKS.containsKey(player.getUUID());
        if (guarding || player.tickCount % SWEEP_INTERVAL == 0) {
            sweepGuard(player);
        }
    }

    /**
     * 生命回复的反转。
     *
     * <p>作者 2026-11-07：「在某一次修改之前，生命回复的反转不需要自己扣血啊」。原因是原版
     * {@code MobEffect#applyEffectTick} 只在「生命不满」时才调 {@code heal()}——满血时
     * {@code LivingHealEvent} 根本不发，只靠截 heal 事件就变成「必须先挨一刀才开始反转」。
     * 这里按原版节奏（间隔 {@code 50 >> 等级} 刻、每次 1 点）主动扣血，与当前血量无关；
     * heal 事件那一层只负责把回血截掉，避免同一次回血被扣两遍。
     */
    private static void drainRegeneration(Player player) {
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null) {
            return;
        }
        int interval = Math.max(1, 50 >> regen.getAmplifier());
        if (player.tickCount % interval != 0) {
            return;
        }
        /*
         * 扣血保底 1 点：作者 2026-11-07 戴着这本书喝生命回复，血被自己一格一格扣到 1 然后死了
         * （latest.log 22:15:51「Dev被杀死了」）。生命回复的反转是「把治疗变成扣血」，
         * 不该让玩家在没被打的情况下被自己的饰品慢慢弄死，所以这里永远留 1 点。
         * 瞬时治疗的反转仍走 hurtByMagic()，那是真伤害，该能致死。
         */
        if (player.getHealth() <= 1.0F) {
            return;
        }
        loseHealth(player, Math.min(1.0F, player.getHealth() - 1.0F));
        log(player, "regen", "《埃涅阿斯纪》：生命回复 → 扣 1 点（当前 {}）",
                String.format("%.1f", player.getHealth()));
    }

    /** 有「打了我的非亡灵」时，把 16 格内没 boss 的亡灵都派去咬它；没目标时顺手清掉盯着我的。 */
    private static void sweepGuard(Player player) {
        LivingEntity prey = markedPrey(player);
        int redirected = 0;
        int cleared = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AURA_RADIUS))) {
            if (mob.getMobType() != MobType.UNDEAD || isBoss(mob)) {
                continue;
            }
            if (retaliating(mob, player.level().getGameTime())) {
                // 刚被我打过：允许它咬我，别派活也别清目标。
                continue;
            }
            if (prey != null) {
                if (mob.getTarget() != prey) {
                    mob.setTarget(prey);
                    redirected++;
                }
                // 每 10 刻推一次寻路（每 tick 推会不停重置路径，反而走不动）。
                if (player.tickCount % 10 == 0) {
                    mob.getNavigation().moveTo(prey, 1.1D);
                }
                // 近身就补一刀：不依赖 AI 的攻击目标，即使它因为目标筛选把目标甩掉了也照样掉血。
                if (player.tickCount % 20 == 0 && mob.distanceToSqr(prey) <= BITE_RANGE_SQR) {
                    float bite = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    prey.hurt(mob.damageSources().mobAttack(mob), Math.max(1.0F, bite));
                }
            } else if (mob.getTarget() == player) {
                mob.setTarget(null);
                cleared++;
            }
        }
        if (redirected > 0) {
            log(player, "guard", "《埃涅阿斯纪》：{} 只亡灵被派去攻击 {}",
                    redirected, prey.getName().getString());
        }
        if (cleared > 0) {
            log(player, "guard", "《埃涅阿斯纪》：{} 只亡灵放弃攻击你", cleared);
        }
    }

    private static LivingEntity markedPrey(Player player) {
        Mark mark = MARKS.get(player.getUUID());
        if (mark == null) {
            return null;
        }
        if (player.level().getGameTime() > mark.until()) {
            MARKS.remove(player.getUUID());
            return null;
        }
        if (player.level() instanceof ServerLevel server
                && server.getEntity(mark.attacker()) instanceof LivingEntity prey
                && prey.isAlive() && prey != player) {
            return prey;
        }
        MARKS.remove(player.getUUID());
        return null;
    }

    private static boolean wears(Player player) {
        return !player.level().isClientSide() && WornCache.wears(player, ClassicBook.AENEID);
    }

    /**
     * boss 判定只看 {@code forge:bosses} 标签（作者 2026-11-06 明确）。
     * 别的模组只要把自己的 boss 加进这个标签就会被正确排除。
     */
    private static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES);
    }

    private static void log(Player player, String key, String format, Object... args) {
        String id = player.getUUID() + "|" + key;
        long now = System.currentTimeMillis();
        Long last = LOG_AT.get(id);
        if (last != null && now - last < LOG_INTERVAL_MS) {
            return;
        }
        LOG_AT.put(id, now);
        LOG.info(format, args);
    }

    private static void cleanupPlayer(UUID playerId) {
        MARKS.remove(playerId);
        APPLICABLE_AT.remove(playerId);
        POTION_AT.remove(playerId);
        LOG_AT.keySet().removeIf(key -> key.startsWith(playerId + "|"));
    }
}
