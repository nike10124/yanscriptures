package com.example.examplemod;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EgoEpicHandler {

    // 固定 UUID 防止属性无限叠加
    private static final UUID ATTACK_UUID = UUID.nameUUIDFromBytes("ego_attack".getBytes());
    private static final UUID ARMOR_UUID = UUID.nameUUIDFromBytes("ego_armor".getBytes());
    private static final UUID SPEED_UUID = UUID.nameUUIDFromBytes("ego_speed".getBytes());
    private static final UUID HEALTH_UUID = UUID.nameUUIDFromBytes("ego_health".getBytes());

    /** 飞行来源名：真我觉醒期间申请，结束时只撤这一份。 */
    private static final String EGO_SOURCE = "ego_epic";

    // 1. 每一秒（20 tick）检测一次，处理饥饿值与时长
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Ego_Active")) return;

        long now = System.currentTimeMillis();
        long endTime = player.getPersistentData().getLong("Ego_EndTime");

        // 检查是否结束
        if (now >= endTime) {
            endEgoState(player);
            return;
        }

        // 如果玩家当前 tick 是 20 的倍数，扣 1 点饥饿值
        if (player.tickCount % 20 == 0) {
            int foodLevel = player.getFoodData().getFoodLevel();
            if (foodLevel <= 0) {
                endEgoState(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c【真我觉醒】饥饿耗尽，效果结束。"), true);
                return;
            }
            player.getFoodData().setFoodLevel(foodLevel - 1);
        }

        // 赋予飞行（只声明本来源；其它饰品给的飞行不受影响）
        FlightPermissions.set(player, EGO_SOURCE, true);

        // 赋予属性翻倍（若尚未赋予）
        applyAttributeModifiers(player);
    }

    // 2. 击杀怪物时延长2秒
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        if (!player.getPersistentData().getBoolean("Ego_Active")) return;

        long now = System.currentTimeMillis();
        long startTime = player.getPersistentData().getLong("Ego_StartTime");
        long currentEnd = player.getPersistentData().getLong("Ego_EndTime");

        // 上限40秒
        if ((currentEnd - startTime) < 40000) {
            player.getPersistentData().putLong("Ego_EndTime", currentEnd + 2000);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d【真我觉醒】击杀敌人，时间延长 2 秒！"), true);
        }
    }

    // 辅助方法：应用属性修饰符
    private static void applyAttributeModifiers(Player player) {
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var armor = player.getAttribute(Attributes.ARMOR);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var health = player.getAttribute(Attributes.MAX_HEALTH);

        // 使用 getModifier(UUID) == null 来判断是否已经添加过
        if (attack != null && attack.getModifier(ATTACK_UUID) == null) {
            double baseAttack = attack.getBaseValue();
            attack.addTransientModifier(new AttributeModifier(ATTACK_UUID, "ego_attack", baseAttack, AttributeModifier.Operation.ADDITION));
        }
        if (armor != null && armor.getModifier(ARMOR_UUID) == null) {
            double baseArmor = armor.getBaseValue();
            armor.addTransientModifier(new AttributeModifier(ARMOR_UUID, "ego_armor", baseArmor, AttributeModifier.Operation.ADDITION));
        }
        if (speed != null && speed.getModifier(SPEED_UUID) == null) {
            double baseSpeed = speed.getBaseValue();
            speed.addTransientModifier(new AttributeModifier(SPEED_UUID, "ego_speed", baseSpeed, AttributeModifier.Operation.ADDITION));
        }
        if (health != null && health.getModifier(HEALTH_UUID) == null) {
            double baseHealth = health.getBaseValue();
            health.addTransientModifier(new AttributeModifier(HEALTH_UUID, "ego_health", baseHealth, AttributeModifier.Operation.ADDITION));
        }
    }

    // 辅助方法：结束状态，恢复所有属性
    private static void endEgoState(Player player) {
        player.getPersistentData().putBoolean("Ego_Active", false);

        // 移除属性修饰符
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var armor = player.getAttribute(Attributes.ARMOR);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var health = player.getAttribute(Attributes.MAX_HEALTH);

        if (attack != null) attack.removeModifier(ATTACK_UUID);
        if (armor != null) armor.removeModifier(ARMOR_UUID);
        if (speed != null) speed.removeModifier(SPEED_UUID);
        if (health != null) health.removeModifier(HEALTH_UUID);

        // 收回飞行能力（只撤本来源；创造模式与其它饰品给的飞行不碰）
        FlightPermissions.set(player, EGO_SOURCE, false);
    }
}