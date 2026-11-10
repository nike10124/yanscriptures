package com.example.examplemod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 第二批名著共用的属性小工具。
 *
 * <p>要解决的问题就一个：{@code AttributeInstance.addTransientModifier} 对同一个 UUID
 * 重复添加会抛 {@code IllegalArgumentException: Modifier is already applied!}。
 * 而第二批的几本书都是「条件成立才给、条件失效就撤」的写法，每 tick 都会重复问一遍，
 * 所以统一走这里：<b>缺了才加、数值或运算不同才先删后加、amount 为零就删</b>。
 *
 * <p>只放三件事（加修饰符、删修饰符、把血量压回上限），不放任何玩法逻辑 ——
 * 玩法逻辑各自在自己那本书的文件里（作者要求「一个功能一个文件」）。
 */
public final class BookAttr {

    private static final double EPSILON = 1.0E-7D;

    private BookAttr() {
    }

    /** 给一本书的某项加成取一个稳定的 UUID（同一个 key 永远得到同一个值）。 */
    public static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("yanscriptures:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static void set(LivingEntity entity, Attribute attribute, UUID id, double amount) {
        set(entity, attribute, id, amount, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    public static void set(LivingEntity entity, Attribute attribute, UUID id, double amount,
                           AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (Math.abs(amount) < EPSILON) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null) {
            if (existing.getOperation() == operation && Math.abs(existing.getAmount() - amount) < EPSILON) {
                return;
            }
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, "yanscriptures_book", amount, operation));
    }

    public static void clear(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    /** 生命上限被改小之后，当前血量可能超出上限 —— 原版不会自己压回去。 */
    public static void clampHealth(LivingEntity entity) {
        float max = entity.getMaxHealth();
        if (entity.getHealth() > max) {
            entity.setHealth(max);
        }
    }
}
