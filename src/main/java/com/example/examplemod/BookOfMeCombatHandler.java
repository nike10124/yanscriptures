package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 《如我所书》的战斗规则：负面效果免疫、无敌帧穿透、生物伤害免疫、限伤穿透。
 *
 * <h2>设计原则：不指名道姓</h2>
 * 规则本身<b>不引用任何其它模组的类、注册名或字段名</b>，只依赖原版语义与通用反射：
 *
 * <table border="1">
 *   <caption>四条规则</caption>
 *   <tr><th>规则</th><th>实现依据</th></tr>
 *   <tr><td>R1 负面效果免疫</td>
 *       <td>{@link MobEffect#isBeneficial()} —— 原版语义，不看具体是哪个效果</td></tr>
 *   <tr><td>R2 无敌帧穿透</td>
 *       <td>扫描目标类里名字含 <code>invul / iframe / cooldown / hurtcool</code> 的
 *           int 字段与同步数据，连同原版 {@code invulnerableTime} 一起清零</td></tr>
 *   <tr><td>R3 生物伤害免疫</td>
 *       <td>对「任何生物来源」的伤害一律取消 —— 2026-10-23 起不再按 {@link Monster} 判断
 *           （尾杀武器的伤害来源不是 Monster，按基类判断会漏）。
 *           注意：这条不是「只免疫怪物」，而是除 {@code genericKill} 与虚空外全部免疫，
 *           与本表早期的描述不同，见 {@code docs/模组内容总览.md} §3.5。</td></tr>
 *   <tr><td>R4 限伤穿透</td>
 *       <td>临时抬高「字段名含 cap / limit 的数值配置项」，离开战斗后恢复</td></tr>
 * </table>
 *
 * <h2>R4 是唯一的例外</h2>
 * 限伤是各家自己写的逻辑，没有通用接口。相对通用的做法是按<b>配置键的语义</b>匹配，
 * 但枚举所有已加载配置在 Forge 上没有稳定 API，所以仍需知道两个配置类的全名。
 * 这属于「配置值层面」的兼容，不触碰对方的实体或逻辑类；拿不到就静默跳过，
 * 其余三条规则照常工作。
 *
 * <h2>为什么在每 tick 做</h2>
 * 有模组把限制写在 {@code LivingEntity#hurt} 的最开头（早于任何 Forge 事件），
 * 在事件里清无敌帧永远慢一步。所以无敌帧清理必须主动做，不能等事件。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookOfMeCombatHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean ENABLED = true;
    private static final boolean DEBUG = false;
    /** 探针：只在战斗期间输出关键数值，用来定位"不能动"的真实来源。定位后改 false。 */
    private static final boolean PROBE = false;
    private static long lastHurtMillis;

    /** 抬高后的限伤值。 */
    private static final double RAISED_CAP = 1.0E9D;
    /** 无敌帧相关字段名（小写包含匹配）。 */
    private static final String[] INVUL_HINTS = {"invul", "iframe", "cooldown", "hurtcool"};
    /** 限伤配置的字段名提示（小写包含匹配）。 */
    private static final String[] CAP_HINTS = {"cap", "limit"};

    private static final long CAP_HOLD_MS = 400L;
    private static long lastCombatMillis;
    /** 脱困用：每名玩家上一次的坐标。 */
    private static final Map<java.util.UUID, double[]> LAST_POS = new HashMap<>();

    /** 见类注释 R4：整套代码里唯一需要类名的位置。 */
    private static final String[][] CAP_CONFIGS = {
            {"com.Polarice3.Goety.config.AttributesConfig", "ApostleDamageCap"},
            {"z1gned.goetyrevelation.config.ModConfig", "APOLLYON_HURT_LIMIT"},
    };

    private static final Map<String, Field[]> FIELD_CACHE = new HashMap<>();
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> CAP_CACHE = new HashMap<>();
    private static final List<Object[]> RAISED = new ArrayList<>();
    private static boolean capsRaised;
    private static boolean lookupLogged;

    private BookOfMeCombatHandler() {
    }

    // ==================== R1：负面效果免疫 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ENABLED) return;
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!wearsBookOfMe(player)) return;
        if (player.tickCount % 2 != 0) return;

        // 语义判断：非增益即清除。减速、定身、凋零、中毒、饥饿、致盲……一视同仁。
        List<MobEffect> harmful = new ArrayList<>();
        player.getActiveEffects().forEach(instance -> {
            if (!instance.getEffect().isBeneficial()) {
                harmful.add(instance.getEffect());
            }
        });
        for (MobEffect effect : harmful) {
            player.removeEffect(effect);
        }

        // [2026-10-23] 兜底：如果伤害绕过了所有事件（例如直接 setHealth），
        // 每 5 tick 把血量补满。这是唯一不依赖伤害管道的做法。
        if (player.tickCount % 5 == 0 && player.isAlive() && player.getHealth() > 0.0F
                && player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    // ==================== R5：击退覆盖 ====================
    // 原版 LivingEntity#hurt 末尾会置 hurtMarked = true，服务端随即把速度包同步给客户端，
    // 客户端会用它覆盖玩家的移动输入。伤害一旦每 tick 都落，表现就是"明明没减速却动不了"。
    // 这里每 tick 清掉这个标记，不让速度同步盖掉玩家自己的移动。

    @SubscribeEvent
    public static void clearHurtMarked(TickEvent.PlayerTickEvent event) {
        if (!ENABLED) return;
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!wearsBookOfMe(player)) return;

        player.hurtMarked = false;

        // 脱困逻辑已统一到 {@link EscapeController}（两个毕业饰品共用一份），
        // 这里不再维护第二套实现，避免「一个能触发、一个不能」的分裂。

        if (PROBE && player.tickCount % 20 == 0
                && System.currentTimeMillis() - lastHurtMillis < 3000L) {
            LOGGER.info("[YanProbe] move delta={} hurtMarked={} speed={} effects={}",
                    player.getDeltaMovement(),
                    player.hurtMarked,
                    player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                    player.getActiveEffects().size());
        }
    }

    // ==================== R2 + R4：攻击时穿透 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDealDamage(LivingHurtEvent event) {
        if (!ENABLED) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!wearsBookOfMe(player)) return;

        raiseCaps();
        lastCombatMillis = System.currentTimeMillis();

        clearInvulnerability(event.getEntity());

        if (DEBUG) {
            LOGGER.info("[YanBypass] 命中 {} 伤害={}",
                    event.getEntity().getName().getString(), event.getAmount());
        }
    }

    @SubscribeEvent
    public static void restoreCapsWhenIdle(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!capsRaised) return;
        if (System.currentTimeMillis() - lastCombatMillis <= CAP_HOLD_MS) return;
        restoreCaps();
    }

    // ==================== R3：伤害免疫 ====================

    /**
     * 通用防护：<b>正在死亡的生物不应再造成伤害</b>。
     *
     * <p>有些模组会在 Boss 的死亡演出里对玩家下手（从「垂死实体的 tick」里发起一次 hurt），
     * 而那条调用链上往往还挂着它们自己的 Mixin。一旦 Mixin 在那时抛异常，
     * Forge 的事件总线记录异常时又会撞上 log4j 的 loader 冲突，把一次普通错误
     * 升级成整局崩溃。把这类「死者出手」挡在事件入口，可以整条绕开。
     *
     * <p>这条与是否佩戴饰品无关，属于全局战斗卫生。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelDamageFromDyingSource(LivingHurtEvent event) {
        if (!ENABLED) return;
        Entity source = event.getSource().getEntity();
        if (source instanceof LivingEntity dying && dying.isDeadOrDying()) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    /**
     * 时停期间，被冻结的生物不能造成伤害。
     *
     * <p>冻结只是把速度和位置按住，但很多 Boss 的攻击靠「技能/动画」驱动，
     * 不依赖位移——所以时间停下之后它们照样能挥出伤害。
     * 这里在伤害入口直接取消：<b>被冻住的东西，打不到任何人</b>。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelDamageFromFrozenSource(LivingHurtEvent event) {
        Entity source = event.getSource().getEntity();
        if (source != null && TimeStopController.isFrozen(source)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!ENABLED) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!wearsBookOfMe(player)) return;

        // [2026-10-23] 原来按「来源是 Monster」判断，实测没生效 —— 说明尾杀的伤害
        // 来源实体是空的（例如瞬间伤害效果走 damageSources().magic()，没有造成者）。
        // 改成不看来源：戴书玩家一律免伤。
        lastHurtMillis = System.currentTimeMillis();

        event.setCanceled(true);
        event.setAmount(0.0F);
        if (PROBE) {
            var src = event.getSource();
            LOGGER.info("[YanProbe] hurt src={} direct={} causing={} amount={}",
                    src.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?"),
                    src.getDirectEntity() == null ? "null" : src.getDirectEntity().getType(),
                    src.getEntity() == null ? "null" : src.getEntity().getType(),
                    event.getAmount());
        }
    }

    /** 最后一道：LivingDamageEvent 是血量真正变化前的最后一个事件。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!ENABLED) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!wearsBookOfMe(player)) return;

        event.setCanceled(true);
        event.setAmount(0.0F);
    }

    // ==================== R2 实现 ====================

    private static void clearInvulnerability(LivingEntity target) {
        target.invulnerableTime = 0;

        for (Field field : fieldsOf(target.getClass())) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!matches(name, INVUL_HINTS)) continue;

            try {
                if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setInt(target, 0);
                } else if (Modifier.isStatic(field.getModifiers())
                        && EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    Object accessor = field.get(null);
                    if (!(accessor instanceof EntityDataAccessor<?> dataAccessor)) continue;
                    Object current = target.getEntityData().get((EntityDataAccessor<Object>) dataAccessor);
                    if (current instanceof Integer) {
                        //noinspection unchecked
                        target.getEntityData().set((EntityDataAccessor<Integer>) dataAccessor, 0);
                    } else if (current instanceof Boolean) {
                        //noinspection unchecked
                        target.getEntityData().set((EntityDataAccessor<Boolean>) dataAccessor, false);
                    }
                }
            } catch (Exception ignored) {
                // 单个字段失败不影响其它字段
            }
        }
    }

    private static Field[] fieldsOf(Class<?> clazz) {
        String key = clazz.getName();
        Field[] cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        List<Field> fields = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                } catch (Exception ignored) {
                    continue;
                }
                fields.add(field);
            }
            c = c.getSuperclass();
        }

        Field[] result = fields.toArray(new Field[0]);
        FIELD_CACHE.put(key, result);
        return result;
    }

    private static boolean matches(String name, String[] hints) {
        for (String hint : hints) {
            if (name.contains(hint)) return true;
        }
        return false;
    }

    // ==================== R4 实现 ====================

    private static void raiseCaps() {
        if (capsRaised) return;
        // 2026-11-10：统一交给 DamageCapControl（按占用者计数），
        // 免得和启示试炼的 raise/restore 互相把对方的数值改掉。
        DamageCapControl.raise(RAISED_CAP);
        capsRaised = true;
        if (!lookupLogged) {
            lookupLogged = true;
            LOGGER.info("[YanBypass] 限伤穿透已启用（统一由 DamageCapControl 管理）");
        }
    }

    private static void restoreCaps() {
        if (!capsRaised) return;
        capsRaised = false;
        DamageCapControl.release();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setCap(ForgeConfigSpec.ConfigValue<?> config, Object value) {
        ((ForgeConfigSpec.ConfigValue) config).set(value);
    }

    private static ForgeConfigSpec.ConfigValue<?> capConfig(String className, String fieldName) {
        String key = className + '#' + fieldName;
        if (CAP_CACHE.containsKey(key)) return CAP_CACHE.get(key);

        ForgeConfigSpec.ConfigValue<?> result = null;
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            Object value = field.get(null);
            // 只接受「名字语义匹配 cap / limit」的配置项，避免误伤别的数值
            if (value instanceof ForgeConfigSpec.ConfigValue<?> configValue
                    && matches(fieldName.toLowerCase(Locale.ROOT), CAP_HINTS)) {
                result = configValue;
            }
        } catch (Throwable ignored) {
            // 该模组未安装或改了字段名：跳过，不影响其它规则
        }

        CAP_CACHE.put(key, result);
        return result;
    }

    // ==================== 工具 ====================

    private static boolean wearsBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .filter(result -> !result.stack().isEmpty())
                .isPresent();
    }
}
