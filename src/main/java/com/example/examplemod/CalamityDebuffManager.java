package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CalamityDebuffManager {

    // 固定 UUID，防止修饰符无限叠加
    private static final UUID ATK_UUID = UUID.nameUUIDFromBytes("calamity_atk".getBytes());
    private static final UUID ARMOR_UUID = UUID.nameUUIDFromBytes("calamity_armor".getBytes());
    private static final UUID HEALTH_UUID = UUID.nameUUIDFromBytes("calamity_health".getBytes());

    // 记录被削弱的生物 → 过期时间
    private static final Map<UUID, Long> DEBUFF_MAP = new HashMap<>();

    /**
     * 施加灾厄削弱（攻击 -8%、护甲 -8%、最大生命 -8%）
     * 由攻击事件调用。
     */
    public static void applyDebuff(LivingEntity target) {
        // 属性修饰符：MULTIPLY_BASE 是原版的「乘法乘区」（多条之间相加再乘），
        // 与文本「攻击力 -8%」的口径一致；独立乘区 MULTIPLY_TOTAL 只留给材料档位。
        var atk = target.getAttribute(Attributes.ATTACK_DAMAGE);
        var armor = target.getAttribute(Attributes.ARMOR);
        var health = target.getAttribute(Attributes.MAX_HEALTH);

        if (atk != null) {
            atk.removeModifier(ATK_UUID); // 先删旧，防止叠加
            atk.addTransientModifier(new AttributeModifier(ATK_UUID, "calamity_atk", -0.08, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (armor != null) {
            armor.removeModifier(ARMOR_UUID);
            armor.addTransientModifier(new AttributeModifier(ARMOR_UUID, "calamity_armor", -0.08, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (health != null) {
            health.removeModifier(HEALTH_UUID);
            health.addTransientModifier(new AttributeModifier(HEALTH_UUID, "calamity_health", -0.08, AttributeModifier.Operation.MULTIPLY_BASE));
            // 🚨 关键：如果当前生命值高于新上限，必须 clamp 下来
            if (target.getHealth() > target.getMaxHealth()) {
                target.setHealth(target.getMaxHealth());
            }
        }

        // 记录 999 秒过期
        DEBUFF_MAP.put(target.getUUID(), System.currentTimeMillis() + 999000);
    }

    /**
     * 每秒扫描一次，移除已经过期的修饰符。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        DEBUFF_MAP.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;

            // 需要找到这个实体。1.20.1 中可以通过服务器实体列表查
            // 这里通过每个玩家所在的服务器遍历实体
            // 简化：用 ServerLevel 查找 UUID
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return true;

            for (var level : server.getAllLevels()) {
                var entity = level.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    var atk = living.getAttribute(Attributes.ATTACK_DAMAGE);
                    var armor = living.getAttribute(Attributes.ARMOR);
                    var health = living.getAttribute(Attributes.MAX_HEALTH);

                    if (atk != null) atk.removeModifier(ATK_UUID);
                    if (armor != null) armor.removeModifier(ARMOR_UUID);
                    if (health != null) health.removeModifier(HEALTH_UUID);
                    return true;
                }
            }
            return true;
        });
    }
}