package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 限伤开关 —— 试炼期间临时抬高各 Boss 的单次伤害上限。
 *
 * <h2>为什么需要它</h2>
 * 这些 Boss 全部写死了 20～25 点的单次伤害上限。四波合计约 6200 血，
 * 不抬高上限就要打 300 次以上（每次还要等 0.75 秒无敌帧），战场会变成纯粹的耐久消耗。
 * 抬到 200 之后整场约 2 分钟，难度落在「躲技能」而不是「砍血条」。
 *
 * <h2>做法</h2>
 * 反射取出对方的 {@code ForgeConfigSpec.ConfigValue<Double>} 字段，临时 {@code set} 一个新值，
 * 结束时还原。只动<b>配置值</b>，不碰任何实体或逻辑类。
 *
 * <p>字段名必须含 {@code cap} 或 {@code limit} 才接受，防止误伤其它数值。
 */
public final class DamageCapControl {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 字段名语义提示：满足其一才认为是「单次伤害上限」。 */
    private static final String[] CAP_HINTS = {"cap", "limit"};

    /**
     * 需要临时抬高的配置项。
     * 这是整套代码里唯一需要写出其它模组类名的位置 —— 因为限伤是各家自写的逻辑，
     * 没有通用接口可以枚举。取不到就跳过，不影响其它功能。
     */
    private static final String[][] CAP_CONFIGS = {
            {"com.Polarice3.Goety.config.AttributesConfig", "ApostleDamageCap"},
            {"com.Polarice3.Goety.config.AttributesConfig", "EnderKeeperDamageCap"},
            {"com.Polarice3.Goety.config.AttributesConfig", "VizierDamageCap"},
            {"com.Polarice3.Goety.config.AttributesConfig", "RedstoneMonstrosityDamageCap"},
            {"z1gned.goetyrevelation.config.ModConfig", "APOLLYON_HURT_LIMIT"},
            {"com.mega.revelationfix.common.config.EntityConfig", "maximumDamagePerHit"},
    };

    private static final List<Object[]> SAVED = new ArrayList<>();
    private static boolean raised;
    /** 同时有几个功能在用抬高（试炼 / 如我所书 R4）。只有降到 0 才还原。 */
    private static int holders;
    /** 当前生效的抬高值：多个调用方取最大值。 */
    private static double activeCap;

    private DamageCapControl() {
    }

    /**
     * 抬高限伤，按「占用者」计数。
     *
     * <p>2026-11-10 修：原来这里和 {@code BookOfMeCombatHandler} 各有一套 raise/restore，
     * 同时触发时后调用的那套会把数值改掉，先结束的那套又原样还原 —— 抬到 1e9 的一场战斗里
     * 可能被试炼的还原打回 20，反之亦然。现在统一由这里按计数管理。</p>
     */
    public static void raise(double newCap) {
        holders++;
        // 已经抬过、且新值不更高：什么都不用动（但仍占着一个名额）
        if (raised && newCap <= activeCap) {
            return;
        }
        boolean wasRaised = raised;
        applyCap(newCap);
        if (!wasRaised) {
            LOGGER.info("[限伤] 已抬至 {} ，命中 {} 项", newCap, SAVED.size());
        }
    }

    /** 释放一个占用者；归零时才真正还原。 */
    public static void release() {
        if (holders <= 0) {
            return;
        }
        holders--;
        if (holders == 0) {
            restore();
        }
    }

    private static void applyCap(double newCap) {
        // 重新采样：先把旧的还回去，避免把「抬过的值」当成原值保存
        if (raised) {
            restoreValues();
        }
        SAVED.clear();
        activeCap = newCap;

        for (String[] entry : CAP_CONFIGS) {
            Field field = lookup(entry[0], entry[1]);
            if (field == null) {
                continue;
            }
            try {
                if (ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                    ForgeConfigSpec.ConfigValue<?> config = (ForgeConfigSpec.ConfigValue<?>) field.get(null);
                    SAVED.add(new Object[]{0, config, config.get()});
                    setConfig(config, newCap);
                } else if (field.getType() == double.class) {
                    // 有些模组用的是普通 static double（不走 ForgeConfigSpec）
                    SAVED.add(new Object[]{1, field, field.getDouble(null)});
                    field.setDouble(null, newCap);
                } else if (field.getType() == float.class) {
                    SAVED.add(new Object[]{2, field, field.getFloat(null)});
                    field.setFloat(null, (float) newCap);
                }
            } catch (Exception ignored) {
                // 单项失败不影响其它配置
            }
        }

        raised = !SAVED.isEmpty();
        if (!raised) {
            LOGGER.info("[限伤] 没有匹配到任何可抬高的配置项");
        }
    }

    /** 还原所有被抬高的限伤。 */
    public static void restore() {
        if (!raised) {
            return;
        }
        restoreValues();
        LOGGER.info("[限伤] 已还原（{} 项）", SAVED.size());
        SAVED.clear();
        activeCap = 0.0D;
    }

    /** 只把值写回去，保留 SAVED 供日志统计。 */
    private static void restoreValues() {
        if (!raised) {
            return;
        }
        raised = false;
        for (Object[] entry : SAVED) {
            try {
                int kind = (Integer) entry[0];
                Object target = entry[1];
                Object old = entry[2];
                if (kind == 0) {
                    setConfig((ForgeConfigSpec.ConfigValue<?>) target, old);
                } else if (kind == 1) {
                    ((Field) target).setDouble(null, (Double) old);
                } else if (kind == 2) {
                    ((Field) target).setFloat(null, (Float) old);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isRaised() {
        return raised;
    }

    // ==================== 反射工具 ====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setConfig(ForgeConfigSpec.ConfigValue<?> config, Object value) {
        ((ForgeConfigSpec.ConfigValue) config).set(value);
    }

    private static Field lookup(String className, String fieldName) {
        if (!containsHint(fieldName)) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? field : null;
        } catch (Throwable ignored) {
            // 模组未安装 / 字段改名：跳过
            return null;
        }
    }

    private static boolean containsHint(String fieldName) {
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String hint : CAP_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
