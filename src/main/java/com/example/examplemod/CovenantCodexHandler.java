package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 圣约·原典 · 被动层。
 *
 * <p>覆盖十二权柄中不依赖按键的那几条：
 * <ul>
 *   <li><b>万邦止戈</b> —— 生物不再把佩戴者视作目标</li>
 *   <li><b>不可近身</b> —— 免疫负面效果、伤害由特殊生命吸收、777% 反伤、无敌帧 7 秒、创造飞行</li>
 *   <li><b>万军之磐石</b> —— 777 点特殊生命 + 777 点护甲/幸运/攻击</li>
 *   <li><b>复活在我</b> —— 永久图腾：死亡事件直接取消并满血复起</li>
 * </ul>
 *
 * <h2>为什么用「特殊生命」而不是直接取消伤害</h2>
 * 直接取消伤害会让「777」这个数字毫无意义，也无法表现「护盾被磨掉」的过程。
 * 所以伤害照常结算，只是**从特殊生命池里扣**，扣到 0 后不再伤及本体；池子会缓慢回复。
 *
 * <h2>为什么用事件而不是每 tick 巡检</h2>
 * 事件是权威的：死亡、受伤、锁定目标都只有一条路径。巡检只能兜底，不能替代。
 * 本类只在需要连续状态的场合（飞行、属性、特殊生命回复）才用 tick。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CovenantCodexHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ==================== 数值 ====================

    /** 特殊生命的上限。 */
    public static final int SPECIAL_HEALTH_MAX = 777;
    /** 反伤倍率：777%。 */
    private static final float REFLECT_MULTIPLIER = 7.77F;
    /** 无来源伤害时，反伤波及的半径。 */
    private static final double REFLECT_RADIUS = 16.0D;
    /** 无敌帧：7 秒。 */
    private static final int INVULNERABLE_TICKS = 140;
    /** 特殊生命的回复：每 20 tick 回复 1 点。 */
    private static final int REGEN_INTERVAL = 20;
    /** 受伤后多久开始回复特殊生命（3 秒）。 */
    private static final long REGEN_DELAY_MS = 3000L;

    /** 固定 UUID，保证重复添加不会叠加。 */
    private static final String TAG_SPECIAL_HP = "CovenantSpecialHp";
    private static final String TAG_LAST_HURT = "CovenantLastHurtMs";
    /** F 键的整体飞行开关（true = 禁用飞行）。 */
    public static final String TAG_FLY_DENIED = "CovenantFlyDenied";

    /**
     * 创造飞行的速度。
     *
     * <p>原版创造飞行是 {@code 0.05}；这里提高到 {@code 0.25}（约 5 倍）。
     * 只影响「创造飞行」，鞘翅滑翔不受影响（见 tick 里的 isFallFlying 分流）。
     */
    private static final float COVENANT_FLY_SPEED = 0.25F;
    /** 戴书之前玩家自己的飞行速度，摘书时还回去（作者 2026-11-09）。 */
    private static final String TAG_PREV_FLY_SPEED = "CovenantPrevFlySpeed";
    /** 原版默认飞行速度。 */
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    /** 摘书时把飞行速度还原成戴书前的值。 */
    private static void restoreFlySpeed(Player player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        float current = player.getAbilities().getFlyingSpeed();
        if (data.contains(TAG_PREV_FLY_SPEED)) {
            player.getAbilities().setFlyingSpeed(data.getFloat(TAG_PREV_FLY_SPEED));
            player.onUpdateAbilities();
            data.remove(TAG_PREV_FLY_SPEED);
            return;
        }
        // 老存档 / 旧版本留下的：没记过原值，但速度还停在我们设的 0.25 上，直接回原版默认。
        if (Math.abs(current - COVENANT_FLY_SPEED) < 1.0E-4F) {
            player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
            player.onUpdateAbilities();
        }
    }
    // 云柱火柱（脱困）的判定与状态已统一到 {@link EscapeController} —— 两个毕业饰品共用一份，
    // 不再在本类里保留第二套参数，避免「一个能触发、一个不能」的分裂。

    private static final UUID UUID_GROWTH_ATTACK = UUID.fromString("8f3f7a52-1d4e-4c8a-9b71-2c6d5e0a4b13");
    private static final UUID UUID_GROWTH_HEALTH = UUID.fromString("6a1d9c44-2b7f-4e53-8d0a-7f5c3b9e2a61");

    // 固定数值加成（护甲/幸运/攻击/韧性 777、免击退、移速、攻速、交互距离、攻血 +77%、可选暴击）
    // 的 UUID 与数值已随逻辑一起搬到 ScriptureOfCovenantItem，这里不再重复定义。

    /** 受到伤害的最终系数：降低 77%，只承担 23%。 */
    private static final float DAMAGE_TAKEN_SCALE = 0.23F;

    /**
     * 反伤递归保险。
     *
     * <p>反伤是直接对攻击者调用 {@code hurt(...)}：如果对方也戴着圣约，那一笔会再次进入
     * {@link #absorbDamage}，再反伤回来，每层 ×7.77，几十层就是 StackOverflowError 崩服
     * （作者 2026-11-10 让外部审查发现，2026-11-10 修）。</p>
     *
     * <p>所以反伤期间打上标记：标记期内收到的伤害**不再吸收、也不再反伤**，直接从圣约的
     * 结算里放行。伤害本身仍然是「神圣震怒」，本来就设计成无视护甲与无敌帧。</p>
     */
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    /** 造成伤害的独立乘区：+77%。 */
    private static final float DAMAGE_DEALT_SCALE = 1.77F;

    /** 每名玩家上一 tick 的生命值（用于侦测绕过事件层的直接改血）。 */
    private static final java.util.Map<UUID, Float> LAST_HEALTH = new java.util.HashMap<>();
    /**
     * 「被玩家打过、允许仇恨」的窗口。
     * 用 WeakHashMap 避免长期持有实体引用。
     */
    private static final java.util.Map<Mob, Long> AGGRO_UNTIL =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** 还手窗口时长：10 秒（持续输出时会不断续期）。 */
    private static final long AGGRO_WINDOW_MS = 10_000L;

    /** 神圣震怒的伤害类型键（数据包定义，见 data/yanscriptures/damage_type/holy_wrath.json）。 */
    private static final ResourceKey<DamageType> HOLY_WRATH =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("yanscriptures", "holy_wrath"));

    private CovenantCodexHandler() {
    }

    // ==================== 佩戴判定 ====================

    /** 玩家是否佩戴《圣约·原典》。 */
    public static boolean wearsCovenant(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                .filter(result -> !result.stack().isEmpty())
                .isPresent();
    }

    // ==================== 万邦止戈：生物不再锁定佩戴者 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        // 我们自己在清目标时会产生一次重入派发，那一层必须放行，否则无限递归
        if (TargetGuard.isClearing()) {
            return;
        }
        if (!(event.getNewTarget() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        try {
            if (!wearsCovenant(player)) {
                return;
            }
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 目标拦截判定失败: " + t);
            return;
        }

        // 任何生物都不再把佩戴者当作目标 —— 除非它刚被玩家打过（允许还手）
        if (event.getEntity() instanceof Mob mob && allowsAggro(mob)) {
            return;
        }

        event.setCanceled(true);
        if (event.getEntity() instanceof Mob mob) {
            TargetGuard.clearTarget(mob);
        }
    }

    // ==================== 万邦止戈：但被主动攻击时要还手 ====================

    /**
     * 玩家戴着圣约主动攻击某个生物时，把它的目标设回玩家，并给它一个「可以仇恨玩家」的窗口。
     *
     * <p>原因：有些 Boss 的受伤判定要求「它正在锁定攻击者」，否则打不掉血
     * （实测在亚波伦的「万众归一」阶段就是这样）。
     *
     * <p>第一个版本只设置了一次目标，结果<b>被我们自己的「万邦止戈」立刻抹掉</b>——
     * 它下一次想锁定玩家时，又被拦截器取消了。所以现在除了设目标，还要登记一个
     * {@link #AGGRO_UNTIL} 窗口：窗口内的目标变更放行。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerStrikesMob(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        try {
            AGGRO_UNTIL.put(mob, System.currentTimeMillis() + AGGRO_WINDOW_MS);
            TargetGuard.setTargetBypassingGuard(mob, player);
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 设置还手目标失败: " + t);
        }
    }

    /** 该生物是否处在「被玩家打过、允许仇恨」的窗口内。 */
    private static boolean allowsAggro(Mob mob) {
        Long until = AGGRO_UNTIL.get(mob);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            AGGRO_UNTIL.remove(mob);
            return false;
        }
        return true;
    }

    // ==================== 不可近身：负面效果免疫 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;

        if (!event.getEffectInstance().getEffect().isBeneficial()) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ==================== 不可近身 + 万军之磐石：受伤结算 ====================

    /**
     * 在 {@link net.minecraftforge.event.entity.living.LivingAttackEvent} 就拦下伤害。
     *
     * <p><b>为什么不用 LivingHurtEvent</b>：伤害要经过 {@code ForgeHooks.onLivingHurt}
     * 才会派发 {@code LivingHurtEvent}，而那条路径上有别的模组给 {@code ForgeHooks}
     * 打的 Mixin——一旦它们在里面抛异常，而 Forge 的事件总线在记录异常时又会撞上
     * log4j 的 loader 冲突，就会从一次普通错误升级成整局崩溃。
     * 在更早的 Attack 阶段拦下，整条 {@code onLivingHurt} 都不会被执行，也就绕开了它。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        try {
            if (!wearsCovenant(player)) {
                return;
            }
            // 基本伤害类型（火、落、溺水、冻、雷、爆炸）完全免疫：
            // 不扣特殊生命、不记受伤、也不给脱困判定留下「被打」的假证据
            if (isBasicDamage(event.getSource())) {
                event.setCanceled(true);
                return;
            }
            absorbDamage(player, event.getAmount(), event.getSource());
            event.setCanceled(true);
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 攻击拦截失败: " + t);
        }
    }

    /** 兜底：若某条路径绕过了 LivingAttackEvent，仍在 Hurt 阶段兜住。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 绝不能把异常抛回事件总线：Forge 的事件总线在记录异常时会撞上
        // log4j 的 loader 冲突（LinkageError: MessageSupplier），把一次小错误放大成整局崩溃。
        try {
            if (!wearsCovenant(player)) {
                return;
            }
            float amount = event.getAmount();
            if (amount <= 0.0F) {
                return;
            }
            if (isBasicDamage(event.getSource())) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                return;
            }
            absorbDamage(player, amount, event.getSource());
            event.setCanceled(true);
            event.setAmount(0.0F);
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 受伤结算失败: " + t);
        }
    }

    /** 伤害结算的核心：扣特殊生命、拉长无敌帧、777% 反伤。 */
    private static void absorbDamage(Player player, float amount, DamageSource source) {
        // 正在反伤中（对方也戴圣约时会回调进来）：直接放行，避免 777% 反伤无限递归崩服。
        if (REFLECTING.get()) {
            return;
        }
        // ① 伤害由特殊生命吸收，本体不掉血。
        //    「受到伤害降低 77%」在这里体现：只从特殊生命里扣 23%。
        CompoundTag data = player.getPersistentData();
        int pool = data.contains(TAG_SPECIAL_HP) ? data.getInt(TAG_SPECIAL_HP) : SPECIAL_HEALTH_MAX;
        int cost = (int) Math.ceil(amount * DAMAGE_TAKEN_SCALE);
        pool = Math.max(0, pool - Math.max(1, cost));
        data.putInt(TAG_SPECIAL_HP, pool);
        data.putLong(TAG_LAST_HURT, System.currentTimeMillis());
        syncSpecialHp(player, pool);

        // ② 无敌帧拉长到 7 秒
        player.invulnerableTime = Math.max(player.invulnerableTime, INVULNERABLE_TICKS);

        // ③ 777% 反伤，走「神圣震怒」——无视护甲/抗性/无敌帧
        boolean wasReflecting = REFLECTING.get();
        REFLECTING.set(true);
        try {
            if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
                // 正在死亡的实体不再反伤：对它调用 hurt 会在它的死亡演出里重入
                if (!attacker.isDeadOrDying() && attacker.isAlive()) {
                    attacker.hurt(holyWrath(player), amount * REFLECT_MULTIPLIER);
                }
            } else {
                // 无来源（环境伤害、效果伤害等）：反给周围所有敌人，每个都吃满 777%
                reflectToNearbyEnemies(player, amount * REFLECT_MULTIPLIER);
            }
        } finally {
            REFLECTING.set(wasReflecting);
        }
    }

    /**
     * 是否属于「基本伤害类型」：火焰、掉落、溺水、冰冻、雷击、爆炸、岩浆。
     *
     * <p>这些是环境与自然现象，按权柄描述应当<b>完全免疫</b>——
     * 既不扣特殊生命，也不留燃烧/冻伤等痕迹，更不记为「挨打」。
     * 之前只做了「吸收进特殊生命」，结果玩家照样着火、特殊生命照样掉，
     * 还被脱困判定当成「被按住」的证据。
     */
    private static boolean isBasicDamage(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
    }

    /** 把反伤倾泻到半径 {@link #REFLECT_RADIUS} 内所有敌对生物身上。 */
    private static void reflectToNearbyEnemies(Player player, float damage) {
        var enemies = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(REFLECT_RADIUS),
                entity -> entity != player
                        && entity.isAlive()
                        && entity instanceof net.minecraft.world.entity.monster.Enemy);
        for (LivingEntity enemy : enemies) {
            enemy.hurt(holyWrath(player), damage);
        }
    }

    // ==================== DPS 统计（用于测试，不参与战斗判定） ====================
    /** 伤害记录：每次命中记 (毫秒时间, 伤害)。 */
    private static final java.util.Map<UUID, java.util.ArrayDeque<long[]>> DAMAGE_LOG = new java.util.HashMap<>();
    /** DPS 统计窗口（毫秒）。 */
    private static final long DPS_WINDOW_MS = 5000L;

    /**
     * 累计输出伤害。
     *
     * <p>挂在 LOWEST：此时暴击、减伤、限伤都已经算完，记录的是<b>真正落到目标身上的数字</b>。
     * 数值写进饰品 NBT（{@code CovenantDps}），由 Curios 同步给客户端显示。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealDamageForDps(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (event.getEntity() == player) return;
        // 注意：不跳过 isCanceled —— 训练假人的伤害被取消（打不死），
        // 但那些数字正是要统计的 DPS。

        try {
            java.util.ArrayDeque<long[]> log =
                    DAMAGE_LOG.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
            long now = System.currentTimeMillis();
            // 伤害转成整数保存（0.01 精度足够）
            log.addLast(new long[]{now, (long) (event.getAmount() * 100.0F)});
        } catch (Throwable ignored) {
        }
    }

    /** 每 10 刻把 DPS 与特殊生命推给客户端。 */
    private static void pushHudStats(Player player) {
        java.util.ArrayDeque<long[]> log = DAMAGE_LOG.get(player.getUUID());
        long now = System.currentTimeMillis();
        long total = 0L;
        if (log != null) {
            while (!log.isEmpty() && now - log.peekFirst()[0] > DPS_WINDOW_MS) {
                log.pollFirst();
            }
            for (long[] entry : log) {
                total += entry[1];
            }
        }
        int dps = (int) (total / 100.0F / (DPS_WINDOW_MS / 1000.0F));
        int hp = player.getPersistentData().getInt(TAG_SPECIAL_HP);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new NetworkHandler.CovenantStatsPacket(hp, dps));
        }
    }

    // ==================== 造成伤害 +77%（独立乘区） ====================
    /**
     * 佩戴者造成的伤害再乘 1.77。
     *
     * <p>挂在 LOWEST：此时暴击、其它模组的增减伤都已经算完，
     * 所以这是一个<b>独立乘区</b>——不会被任何加减法稀释。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealDamageBoost(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (event.getEntity() == player) return;
        event.setAmount(event.getAmount() * DAMAGE_DEALT_SCALE);
    }

    // ==================== 初次佩戴：立约登神演出 ====================
    /** 已经播过入场演出的玩家。 */
    private static final java.util.Set<UUID> INTRODUCED = new java.util.HashSet<>();

    private static void playIntro(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        // 标题：大字短语；完整句子放副标题（标题字号固定，太长会被裁掉）
        serverPlayer.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(12, 90, 24));
        serverPlayer.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal("§7我是欧米伽；是初，是终。")));
        serverPlayer.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal("§6§l我是阿尔法")));

        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.TOTEM_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.7F);

        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    60, 1.2D, 1.6D, 1.2D, 0.12D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                    player.getX(), player.getY() + 1.2D, player.getZ(), 1, 0, 0, 0, 0);
        }
        LOGGER.info("[圣约·原典] 初戴演出已播放");
    }

    /** 常驻光环：每 10 刻在一圈金色微光，让佩戴者始终“有神性”。 */
    private static void tickAura(Player player) {
        if (player.tickCount % 10 != 0) return;
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

        double angle = player.tickCount * 0.12;
        double radius = 0.7D;
        for (int i = 0; i < 4; i++) {
            double a = angle + i * (Math.PI / 2.0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX() + Math.cos(a) * radius,
                    player.getY() + 0.25D + 0.15D * Math.sin(a * 2),
                    player.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    /** 玩家下线时清掉与他相关的静态缓存（否则会随会话无限增长）。 */
    public static void cleanupPlayer(UUID id) {
        LAST_HEALTH.remove(id);
        INTRODUCED.remove(id);
        DAMAGE_LOG.remove(id);
    }

    /** 供其它处理器查询是否佩戴（例如「五饼二鱼」的掉落判定）。 */
    public static boolean wearsCovenantPublic(Player player) {
        return wearsCovenant(player);
    }

    /** 把特殊生命同步进饰品 NBT：Curios 会把槽内物品的 NBT 同步给客户端，HUD 靠它显示。 */
    private static void syncSpecialHp(Player player, int pool) {
        try {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                    .ifPresent(result -> {
                        CompoundTag tag = result.stack().getOrCreateTag();
                        if (tag.getInt("CovenantHp") != pool) {
                            tag.putInt("CovenantHp", pool);
                        }
                    });
        } catch (Throwable ignored) {
            // 同步失败不影响逻辑
        }
    }

    /** 构造「神圣震怒」伤害源：无视护甲、抗性、附魔、无敌帧与限伤。找不到就退回魔法伤害。 */
    public static DamageSource holyWrath(LivingEntity source) {
        try {
            var holder = source.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolder(HOLY_WRATH);
            if (holder.isPresent()) {
                return new DamageSource(holder.get(), source);
            }
            System.err.println("[圣约·原典] 伤害类型 yanscriptures:holy_wrath 未注册，回退到魔法伤害");
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 构造神圣震怒失败: " + t);
        }
        return source.damageSources().magic();
    }

    // ==================== 申命之赐：七级恩典与赐福 / 降灾 ====================

    /** 恩典等级（amplifier + 1）。 */
    private static final int BLESSING_AMPLIFIER = 6;
    /** 每次刷新给的效果时长（刻），比刷新间隔长，避免闪烁。 */
    private static final int BLESSING_DURATION = 240;
    /** 刷新间隔（刻）。 */
    private static final int BLESSING_INTERVAL = 20;

    /** 给佩戴者维持「一切有益效果，七级」。 */
    private static final java.util.Set<String> LOGGED_EFFECTS = new java.util.HashSet<>();

    /**
     * 会在脚底冒火 / 灼烧皮肤一类「看着有害」的效果关键词。
     *
     * <p>某些模组会把带火焰表现的增益效果标成 BENEFICIAL，视觉上像在自燃。
     * 这里按<b>效果 ID 的语义关键词</b>排除（不是点名某个模组），
     * 命中即跳过，避免视觉与判定上的混淆。
     */
    private static final String[] FIRE_KEYWORDS = {"fire", "flame", "burn", "ignite", "blaze", "ember"};

    private static boolean looksFiery(net.minecraft.world.effect.MobEffect effect) {
        String id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                .getKey(effect).toString().toLowerCase(java.util.Locale.ROOT);
        for (String keyword : FIRE_KEYWORDS) {
            if (id.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void applyBlessings(Player player) {
        boolean firstLog = LOGGED_EFFECTS.isEmpty();
        java.util.List<String> applied = new java.util.ArrayList<>();

        for (net.minecraft.world.effect.MobEffect effect
                : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT) {
            if (effect.getCategory() != net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                continue;
            }
            if (effect.isInstantenous()) {
                continue; // 瞬间效果反复施加会变成持续回血/伤害，不合适
            }
            // 只保留原版效果。
            // 其它模组把「正面效果」标得很随意，有些会冒火、灼烧、甚至改血量，
            // 全给会让画面和判定都乱掉，所以这里只认 minecraft 命名空间。
            net.minecraft.resources.ResourceLocation key =
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || !"minecraft".equals(key.getNamespace())) {
                continue;
            }
            // 隐身、缓降这类同样不给（无论敌我）——它们让目标「看不见」或「飘着」，失去可读性
            if (EXCLUDED_EFFECT_IDS.contains(key.toString())) {
                continue;
            }
            if (looksFiery(effect)) {
                continue; // 会冒火/灼烧的增益，排除（原版里目前没有，留作保险）
            }
            applied.add(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getKey(effect).toString());

            net.minecraft.world.effect.MobEffectInstance current = player.getEffect(effect);
            if (current != null
                    && current.getAmplifier() >= BLESSING_AMPLIFIER
                    && current.getDuration() > BLESSING_DURATION / 2) {
                continue;
            }
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effect, BLESSING_DURATION, BLESSING_AMPLIFIER, false, false, false));
        }

        // 首次施加时把「实际给了哪些效果」记进日志，方便排查是哪一个在冒火
        if (firstLog && !applied.isEmpty()) {
            LOGGED_EFFECTS.addAll(applied);
            LOGGER.info("[圣约·原典] 申命之赐 授予 {} 种有益效果：{}",
                    applied.size(), applied);
        }
    }

    /**
     * 右键生物：按住 Shift 赐福（全部有益效果七级），按住 Alt 降灾（全部负面效果七级）。
     *
     * <p>用「右键实体」事件而不是自建按键 —— 玩家本来就有的交互方式，不需要额外键位。
     */
    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        Player player = event.getEntity();
        if (player.level().isClientSide() || !wearsCovenant(player)) return;

        // 坐标判定由客户端同步过来：Shift 用 sneak 标志，Alt 只能看手持输入
        boolean bless = player.isShiftKeyDown();
        boolean curse = !bless && isAltDown(player);
        if (!bless && !curse) {
            return;
        }

        try {
            if (bless) {
                applyAllEffects(target, true);
                player.displayClientMessage(Component.literal("§e申命之赐：「你要谨守，就必存活。」"), true);
            } else {
                applyAllEffects(target, false);
                player.displayClientMessage(Component.literal("§4申命之赐：「咒诅也必追随你，直到你灭亡。」"), true);
            }
            event.setCanceled(true);
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 申命之赐失败: " + t);
        }
    }

    /** Alt 键状态：Player 没有公开接口，只能从输入事件状态判断。 */
    private static boolean isAltDown(Player player) {
        return ALT_DOWN.getOrDefault(player.getUUID(), false);
    }

    /** 客户端在按键状态变化时写入。 */
    public static void setAltDown(Player player, boolean down) {
        ALT_DOWN.put(player.getUUID(), down);
    }

    private static final java.util.Map<UUID, Boolean> ALT_DOWN = new java.util.HashMap<>();

    /**
     * 赐福 / 降灾时应当排除的效果。
     *
     * <p>要排除的不是「所有模组效果」，而是<b>会把战斗场面搞乱的那几种机制</b>：
     * <ul>
     *   <li>{@code evil_eye}（邪恶之眼）—— 持续在受影响者周围生成怪物。给敌人挂上等于当场刷怪海。</li>
     *   <li>{@code launch}（发射）—— 把生物垂直抛飞。一群怪在天上乱飞，别说打架，连目标都看不清。</li>
     *   <li>传送类（{@code ender_flux} 等）—— 目标被随机挪走，攻击直接落空。</li>
     *   <li>召唤类 —— 同上，凭空增加实体。</li>
     * </ul>
     * 判断依据是<b>效果 ID 的语义关键词</b>，不是点名某个模组，
     * 因此其它模组里同名的机制也会一并排除。
     *
     * <p>注意：这里<b>不再</b>按命名空间一刀切。之前把整个 {@code goety:} 都排掉，
     * 连铁皮、灵魂护甲这类正常增益也一起没了 —— 那是过度矫正。
     */
    private static final String[] DISRUPTIVE_KEYWORDS = {
            "evil_eye", "launch", "propel", "thrust", "yeet",   // 生成怪物 / 抛飞
            "summon", "spawn",                                   // 召唤
            "ender_flux", "teleport", "blink",                   // 随机传送
            "brew"                                               // 药酿本体
    };

    /** 明确不想给的效果（无论敌我）：漂浮、隐身、缓降。 */
    private static final java.util.Set<String> EXCLUDED_EFFECT_IDS = java.util.Set.of(
            "minecraft:levitation",     // 漂浮：目标飘起来，打不到也看不清
            "minecraft:invisibility",   // 隐身：看不见目标，可读性为零
            "minecraft:slow_falling"    // 缓降：与漂浮同类，拖慢节奏
    );

    private static boolean excludedFromBestowal(net.minecraft.world.effect.MobEffect effect) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if (key == null) {
            return true;
        }
        String id = key.toString().toLowerCase(java.util.Locale.ROOT);
        if (EXCLUDED_EFFECT_IDS.contains(id)) {
            return true;
        }
        for (String keyword : DISRUPTIVE_KEYWORDS) {
            if (id.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给目标施加全部有益或全部负面效果，均为七级。
     */
    private static final int CURSE_AMPLIFIER = 6;
    private static final int CURSE_AMPLIFIER_DOOM = 76;

    private static void applyAllEffects(LivingEntity target, boolean beneficial) {
        var category = beneficial
                ? net.minecraft.world.effect.MobEffectCategory.BENEFICIAL
                : net.minecraft.world.effect.MobEffectCategory.HARMFUL;

        for (net.minecraft.world.effect.MobEffect effect
                : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT) {
            if (effect.getCategory() != category) {
                continue;
            }
            if (effect.isInstantenous()) {
                continue;
            }
            if (excludedFromBestowal(effect)) {
                continue;
            }
            int amplifier = beneficial ? BLESSING_AMPLIFIER : CURSE_AMPLIFIER;
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effect, 200, amplifier, false, true, true));
        }
    }

    // ==================== 万军之约：击杀成长 ====================

    private static final String TAG_KILLS = "CovenantKills";
    private static final int GROWTH_CAP = 777;

    /**
     * 击杀成长：每击杀一个生物，永久 +1 攻击、+1 生命上限。
     * 上限 777 次，避免无限膨胀。
     */
    @SubscribeEvent
    public static void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (event.getEntity() == player) return;

        CompoundTag data = player.getPersistentData();
        int kills = data.getInt(TAG_KILLS);
        if (kills >= GROWTH_CAP) {
            return;
        }
        data.putInt(TAG_KILLS, kills + 1);

        // 属性加成用固定 UUID，每次只 +1，由 tick 维护（见 applyGrowth）
        if (kills == 0) {
            player.displayClientMessage(Component.literal("§6万军之约：你的力量开始增长。"), true);
        }
    }

    /** 由 tick 调用：把击杀数换算成属性加成。 */
    private static void applyGrowth(Player player, boolean worn) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (attack == null || health == null) {
            return;
        }

        int kills = worn ? player.getPersistentData().getInt(TAG_KILLS) : 0;

        attack.removeModifier(UUID_GROWTH_ATTACK);
        health.removeModifier(UUID_GROWTH_HEALTH);
        if (kills <= 0) {
            return;
        }
        attack.addTransientModifier(new AttributeModifier(
                UUID_GROWTH_ATTACK, "covenant_growth_attack", kills, AttributeModifier.Operation.ADDITION));
        health.addTransientModifier(new AttributeModifier(
                UUID_GROWTH_HEALTH, "covenant_growth_health", kills, AttributeModifier.Operation.ADDITION));
    }

    // ==================== 暴击：77% 概率，777% 伤害 ====================
    private static final float CRIT_CHANCE = 0.77F;
    private static final float CRIT_MULTIPLIER = 7.77F;

    /**
     * 圣约佩戴者造成伤害时的暴击判定。
     *
     * <p>77% 概率触发，触发时伤害乘以 7.77（即 777% 暴击伤害）。
     * 暴击走 {@link EventPriority#HIGHEST}，这样后续的减伤、限伤仍然能照常生效。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDealDamageCrit(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (event.getEntity() == player) return;

        try {
            if (player.getRandom().nextFloat() < CRIT_CHANCE) {
                event.setAmount(event.getAmount() * CRIT_MULTIPLIER);
                DpsMeter.markCrit(player);
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            event.getEntity().getX(),
                            event.getEntity().getY() + event.getEntity().getBbHeight() * 0.5D,
                            event.getEntity().getZ(),
                            8, 0.3D, 0.3D, 0.3D, 0.2D);
                    // 暴击音效：给玩家一个明确的听觉反馈
                    serverLevel.playSound(null, event.getEntity().blockPosition(),
                            net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 暴击判定失败: " + t);
        }
    }

    // ==================== 不死之约：生命永不枯竭 ====================
    /** 每次回复间隔（刻）。 */
    private static final int IMMORTAL_HEAL_INTERVAL = 20;
    /** 每次回复的最大生命百分比。 */
    private static final float IMMORTAL_HEAL_RATIO = 0.07F;

    /**
     * 不死之约 —— 比《如我所书》更彻底的保命：
     * <ul>
     *   <li>生命值永远不会低于 1（伤害事件与直接改血两条路都堵住）</li>
     *   <li>每秒回复最大生命的 7%，无需进食</li>
     * </ul>
     * 与「复活在我」的区别：复活在我负责「已经死了」的情况，
     * 不死之约负责「根本死不成」——血量被钉在 1 之上，再持续补回来。
     */
    private static void tickImmortality(Player player) {
        if (player.tickCount % IMMORTAL_HEAL_INTERVAL != 0) {
            return;
        }
        float max = player.getMaxHealth();
        if (player.getHealth() >= max) {
            return;
        }
        float heal = Math.max(1.0F, max * IMMORTAL_HEAL_RATIO);
        player.setHealth(Math.min(max, player.getHealth() + heal));
        LAST_HEALTH.put(player.getUUID(), player.getHealth());
        syncSpecialHp(player, player.getPersistentData().getInt(TAG_SPECIAL_HP));
    }

    // ==================== 圣所：为同伴分担伤害 ====================
    /** 分担半径。 */
    private static final double SANCTUARY_RADIUS = 16.0D;
    /** 分担比例：由原典佩戴者吃下 77%。 */
    private static final float SANCTUARY_SHARE = 0.77F;

    /**
     * 圣所 —— 同伴受到的伤害由佩戴者承担 77%。
     *
     * <p>佩戴者本来就是完全无敌的，所以这份「分担」实际上等于替队友免伤。
     * 若范围内有多名佩戴者，取最近的那一位。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAllyHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.level().isClientSide() || wearsCovenant(victim)) return;

        try {
            Player wearer = findNearbyCovenantWearer(victim);
            if (wearer == null) {
                return;
            }
            float original = event.getAmount();
            if (original <= 0.0F) {
                return;
            }
            // 佩戴者吃下 77%，受击者只承担剩余部分
            absorbDamage(wearer, original * SANCTUARY_SHARE, event.getSource());
            event.setAmount(original * (1.0F - SANCTUARY_SHARE));
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 圣所分担失败: " + t);
        }
    }

    @javax.annotation.Nullable
    private static Player findNearbyCovenantWearer(Player victim) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : victim.level().players()) {
            if (candidate == victim) continue;
            if (!wearsCovenant(candidate)) continue;
            double distance = candidate.distanceToSqr(victim);
            if (distance <= SANCTUARY_RADIUS * SANCTUARY_RADIUS && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ==================== 不可近身：禁疗免疫 ====================
    /**
     * 免疫一切「禁止治疗」。
     *
     * <p>禁疗在 Minecraft 里没有统一的标记，各家实现方式不同：
     * 有的是给一个负面效果（已被负面效果免疫覆盖），有的则是在治疗事件上直接取消。
     * 这里补的是后者：<b>在 LOWEST 优先级把被取消的治疗重新放行</b> ——
     * LOWEST 最后执行，此时才能看到所有已经 cancel 过的结果。
     *
     * <p><b>2026-11-10 补</b>：还要声明 {@code receiveCanceled = true} ——
     * Forge 只把已取消的事件送给这类监听器，否则这里的 {@code isCanceled()} 恒为 false，
     * 「免疫禁疗」等于没写（外部审查发现）。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;
        if (event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    // ==================== 复活在我：永久图腾 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !wearsCovenant(player)) return;

        try {
            // 取消死亡并满血复起，等同于一次不会失效的不死图腾
            // 作者 2026-11-10 定：毕业饰品连 /kill 与虚空都要扛得住，所以这里不再区分伤害类型。
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.getPersistentData().putInt(TAG_SPECIAL_HP, SPECIAL_HEALTH_MAX);

            // 清除一切负面效果（图腾的常规行为）
            List<MobEffect> harmful = new ArrayList<>();
            player.getActiveEffects().forEach(instance -> {
                if (!instance.getEffect().isBeneficial()) {
                    harmful.add(instance.getEffect());
                }
            });
            for (MobEffect effect : harmful) {
                player.removeEffect(effect);
            }

            player.displayClientMessage(
                    Component.literal("§f§l复活在我，生命也在我。"), true);
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TOTEM_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
        } catch (Throwable t) {
            System.err.println("[圣约·原典] 复活处理失败: " + t);
        }
    }

    // ==================== 每 tick：飞行、属性、特殊生命回复 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        try {
            boolean worn = wearsCovenant(player);
            // 固定数值加成已搬到 ScriptureOfCovenantItem.getAttributeModifiers（Curios 负责挂/摘 + 提示）：
            // 这里只剩「击杀成长」这种随状态变化的量。
            applyGrowth(player, worn);
            if (worn && player.tickCount % BLESSING_INTERVAL == 0) {
                applyBlessings(player);
            }

            if (!worn) {
                // 摘书时也要撤掉否决战：否则「戴过并关了 F」的人摘掉书之后仍然飞不了
                // （作者 2026-11-08 报的「禁飞会作用于全局」）。
                FlightPermissions.veto(player, FlightPermissions.COVENANT, false);
                FlightPermissions.set(player, FlightPermissions.COVENANT, false);
                restoreFlySpeed(player);
                return;
            }

            // 创造飞行
            //
            // F 键可以整体禁用飞行。禁用状态记在 persistent data 里，
            // 所以「关掉之后双击空格又能飞」不会再发生 —— 只要开关是关的，
            // 每 tick 都会把 mayfly / flying 一起按死。
            boolean flightDenied = player.getPersistentData().getBoolean(TAG_FLY_DENIED);
            // 飞行走「多方申请 + 一票否决」：F 键关掉时是「否决」，不是「本来源不申请」，
            // 这样同时戴着《如我所书》也照样飞不起来（作者 2026-11-08 的要求）。
            FlightPermissions.veto(player, FlightPermissions.COVENANT, flightDenied);
            FlightPermissions.set(player, FlightPermissions.COVENANT, !flightDenied);
            if (!flightDenied) {
                if (!player.isFallFlying()
                        && player.getAbilities().getFlyingSpeed() < COVENANT_FLY_SPEED) {
                    // 记住改之前的原值：摘书时要还回去（作者 2026-11-09 报「取下圣约后飞行速度异常」）。
                    if (!player.getPersistentData().contains(TAG_PREV_FLY_SPEED)) {
                        float current = player.getAbilities().getFlyingSpeed();
                        // 已经不记得原值、速度却正好是 0.25 的旧档，就别把 0.25 记成「原值」。
                        if (Math.abs(current - COVENANT_FLY_SPEED) > 1.0E-4F) {
                            player.getPersistentData().putFloat(TAG_PREV_FLY_SPEED, current);
                        }
                    }
                    player.getAbilities().setFlyingSpeed(COVENANT_FLY_SPEED);
                    player.onUpdateAbilities();
                }
            }

            // 飞行排查用：正常游玩时不该刷屏，需要时把 debug.log 打开就有
            if (player.tickCount % 200 == 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[圣约·原典] 飞行状态：mayfly={} flying={} speed={} fallFlying={}",
                        player.getAbilities().mayfly,
                        player.getAbilities().flying,
                        player.getAbilities().getFlyingSpeed(),
                        player.isFallFlying());
            }

            // 基本伤害免疫的「痕迹清理」：即便伤害被拦下，着火/冻伤这类状态仍会保留，
            // 每 tick 直接抹掉，避免视角里一直冒火。
            if (player.isOnFire() || player.getRemainingFireTicks() > 0) {
                player.clearFire();
            }
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }

            // 特殊生命恢复
            CompoundTag data = player.getPersistentData();
            if (!data.contains(TAG_SPECIAL_HP)) {
                data.putInt(TAG_SPECIAL_HP, SPECIAL_HEALTH_MAX);
            }
            int pool = data.getInt(TAG_SPECIAL_HP);
            if (pool < SPECIAL_HEALTH_MAX
                    && System.currentTimeMillis() - data.getLong(TAG_LAST_HURT) > REGEN_DELAY_MS
                    && player.tickCount % REGEN_INTERVAL == 0) {
                data.putInt(TAG_SPECIAL_HP, Math.min(SPECIAL_HEALTH_MAX, pool + 1));
                syncSpecialHp(player, data.getInt(TAG_SPECIAL_HP));
            }

            // ==================== 防改血 ====================
            // 有的机制（例如亚波伦的结界）会绕过伤害管道直接改血量，事件层完全看不到。
            // 这里的做法是：每 tick 记住血量，只要发现比上一 tick 低，就原样拉回。
            // 由于「正常受伤」会先被 absorbDamage 拦下、血量根本不会掉，
            // 所以任何真实的下降都意味着有人在绕过我们 —— 直接抹平即可。
            float health = player.getHealth();
            Float previous = LAST_HEALTH.get(player.getUUID());
            // 生命上限本身被改动时（例如七级效果的增删），血量会被系统重新夹取，
            // 那不是「有人在偷偷扣血」，不该拉回 —— 否则会与上限互相较劲、反复刷日志。
            if (previous != null && previous > player.getMaxHealth() + 0.01F) {
                previous = null;
            }
            if (previous != null && health < previous - 0.01F) {
                float lost = previous - health;
                player.setHealth(previous);
                health = previous;
                // 把这次「被绕过」记成受伤证据，让脱困判定能感知到「正在被结界消耗」
                EscapeController.markHurt(player);
                // 有的机制会持续消耗（例如亚波伦的结界每 6 秒扣一次），
                // 这条只在 debug.log 里留痕，正常日志不参与刷屏。
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[圣约·原典] 拦截直接改血：-{} （{} → {}）",
                            lost, health + lost, health);
                }
            }
            if (health <= 0.0F) {
                player.setHealth(player.getMaxHealth());
                health = player.getMaxHealth();
            }
            LAST_HEALTH.put(player.getUUID(), health);

            // 不死之约：每秒回复最大生命的 7%
            tickImmortality(player);

            // 初戴演出 + 常驻光环
            if (INTRODUCED.add(player.getUUID())) {
                playIntro(player);
            }
            tickAura(player);

            // HUD 数据（特殊生命 + DPS），每 10 刻推送一次
            if (player.tickCount % 10 == 0) {
                pushHudStats(player);
            }
        } catch (Throwable t) {
            System.err.println("[圣约·原典] tick 处理失败: " + t);
        }
    }

    // 「万军之磐石」的固定数值加成（护甲/幸运/攻击/韧性 777、免击退、移速、攻速、交互距离、
    // 攻血 +77%、可选暴击）已经整体搬到 ScriptureOfCovenantItem.getAttributeModifiers(...)：
    // 写在物品上 Curios 才会显示「当装备在书籍栏：」那一段，并由 Curios 负责挂上与卸下时摘掉。
    // 这里不再保留第二套 apply/applyOptional helper，避免两处同时挂修饰符。

    // ==================== 对外查询 ====================

    /** 当前特殊生命值。 */
    public static int getSpecialHealth(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(TAG_SPECIAL_HP) ? data.getInt(TAG_SPECIAL_HP) : SPECIAL_HEALTH_MAX;
    }

    /** 供未来界面/指令使用：检查某物品是否为《圣约·原典》。 */
    public static boolean isCovenant(ItemStack stack) {
        return stack.is(ExampleMod.COVENANT_CODEX.get());
    }
}
