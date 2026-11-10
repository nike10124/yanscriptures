package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「七碗倾倒」—— 圣约·原典的主动毁灭权柄（J 键）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>半径 16 格内所有敌人，<b>每一刻</b>都被结算一次伤害</li>
 *   <li><b>每一种伤害类型各结算一次</b>：神圣震怒、近战、魔法、凋零、溺水、冰冻、
 *       雷击、着火、岩浆、摔落、虚空、仙人掌…… 目的是让「任何免疫某一类伤害」
 *       的单位也无法躲过审判</li>
 *   <li>单次基础伤害 777，另加 <b>面板攻击力 × 7.77</b>；
 *       暴击由通用暴击判定（77% 概率 × 7.77）叠加</li>
 *   <li>持续 7 秒，冷却 30 秒</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JudgementController {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        END_TICK.remove(id);
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int DURATION_TICKS = 140;      // 7 秒
    private static final double RADIUS = 16.0D;

    /** 单次基础伤害。 */
    private static final float BASE_DAMAGE = 777.0F;
    /** 面板攻击力倍率。 */
    private static final float ATTACK_SCALE = 7.77F;

    /** 结束时的游戏刻。 */
    private static final Map<UUID, Long> END_TICK = new HashMap<>();

    private JudgementController() {
    }

    /** 由按键包调用：开始一次七碗倾倒。 */
    public static void activate(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long cd = player.getPersistentData().getLong("Covenant_Judgement_CD");
        if (now < cd) {
            long remaining = (cd - now) / 1000 + 1;
            player.displayClientMessage(
                    Component.literal("§c【七碗倾倒】冷却中，剩余 " + remaining + " 秒。"), true);
            return;
        }
        player.getPersistentData().putLong("Covenant_Judgement_CD", now + 30_000L);
        END_TICK.put(player.getUUID(), player.level().getGameTime() + DURATION_TICKS);

        player.displayClientMessage(Component.literal("§4七碗倾倒，地上的国成了我主的国。"), true);
        LOGGER.info("[圣约·原典] 七碗倾倒 启动");
    }

    public static boolean isActive(Player player) {
        Long end = END_TICK.get(player.getUUID());
        return end != null && player.level().getGameTime() < end;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        UUID id = player.getUUID();
        Long end = END_TICK.get(id);
        if (end == null) return;

        long gameTime = player.level().getGameTime();
        if (gameTime >= end) {
            END_TICK.remove(id);
            player.displayClientMessage(Component.literal("§7七碗已尽，怒息止歇。"), true);
            return;
        }

        // 每刻结算一次（不再是每秒）
        AABB area = player.getBoundingBox().inflate(RADIUS);
        float attack = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = BASE_DAMAGE + Math.max(0.0F, attack) * ATTACK_SCALE;
        // 2026-11-10（外部审查）：这一句原来写在按目标循环里，每 tick 每个目标都要重建一遍
        // DamageSource 列表；它只依赖施法者，提到循环外即可，零风险。
        List<DamageSource> sources = damageSources(player);

        for (Entity entity : new ArrayList<>(player.level().getEntities(player, area))) {
            if (entity == player || entity instanceof Player) continue;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) continue;

            // 钉住：清速度
            target.setDeltaMovement(0.0D, 0.0D, 0.0D);

            float before = target.getHealth();

            // 每一种伤害类型各来一次
            for (DamageSource source : sources) {
                target.hurt(source, damage);
                if (!target.isAlive()) {
                    break;
                }
            }

            // 训练假人只吃伤害数字，不参与强制扣血
            if (TrainingDummy.isDummy(target)) {
                continue;
            }

            // 所有类型都被无视时兜底：直接扣血
            if (target.isAlive() && target.getHealth() >= before - 0.01F) {
                float next = Math.max(0.0F, before - damage);
                target.setHealth(next);
                if (next <= 0.0F) {
                    target.die(CovenantCodexHandler.holyWrath(player));
                }
                LOGGER.info("[圣约·原典] 七碗倾倒 越过免伤：{} 剩余 {}", target.getName().getString(), next);
            }
        }
    }

    /** 审判使用的伤害类型集合：每刻对每个目标各结算一次。 */
    private static List<DamageSource> damageSources(Player player) {
        var sources = player.level().damageSources();
        List<DamageSource> list = new ArrayList<>(12);
        // 自定义类型：无视护甲/抗性/附魔/无敌帧
        list.add(CovenantCodexHandler.holyWrath(player));
        list.add(sources.playerAttack(player));
        list.add(sources.magic());
        list.add(sources.wither());
        list.add(sources.drown());
        list.add(sources.freeze());
        list.add(sources.lightningBolt());
        list.add(sources.onFire());
        list.add(sources.lava());
        list.add(sources.fall());
        list.add(sources.generic());
        list.add(sources.cactus());
        return list;
    }
}
