package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 时停（凝固时间）的共用实现。
 *
 * <h2>修掉的四个问题</h2>
 * <ol>
 *   <li><b>重力标记泄漏</b>：旧代码对每个被冻结的实体调用 {@code setNoGravity(true)}，
 *       结束时却从不还原 —— 效果一过，全场生物永久悬浮。现在会记录每个实体原来的重力状态，
 *       解除时按原值还原。</li>
 *   <li><b>用墙上时钟计时</b>：旧代码用 {@code System.currentTimeMillis()}，
 *       暂停游戏、改变时间流速都会算错。现在统一用 {@link net.minecraft.world.level.Level#getGameTime()}。</li>
 *   <li><b>只冻生物</b>：物品、箭矢、掉落物照常飞。现在冻结范围内的一切非玩家实体。</li>
 *   <li><b>会冻住其他玩家</b>：现在跳过所有 Player。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TimeStopController {

    /** 结束时的游戏刻。 */
    private static final Map<UUID, Long> END_TICK = new HashMap<>();
    /** 本效果的作用半径。 */
    private static final Map<UUID, Double> RADIUS = new HashMap<>();
    /** 本效果冻结的实体，以及它们原来的重力状态。 */
    private static final Map<UUID, Set<Entity>> FROZEN = new HashMap<>();
    private static final Map<UUID, Map<Entity, Boolean>> PREVIOUS_GRAVITY = new HashMap<>();

    private TimeStopController() {
    }

    /**
     * 开始一次时停。
     *
     * @param player        施术者
     * @param durationTicks 持续多少刻
     * @param radius        冻结半径
     */
    public static void activate(Player player, int durationTicks, double radius) {
        if (player.level().isClientSide()) {
            return;
        }
        UUID id = player.getUUID();
        // 如果已经停着，先解除旧的（避免重力状态越叠越乱）
        restore(player);
        END_TICK.put(id, player.level().getGameTime() + durationTicks);
        RADIUS.put(id, radius);
    }

    public static boolean isActive(Player player) {
        Long end = END_TICK.get(player.getUUID());
        return end != null && player.level().getGameTime() < end;
    }

    /** 剩余刻数；未激活返回 0。 */
    public static long remainingTicks(Player player) {
        Long end = END_TICK.get(player.getUUID());
        if (end == null) return 0L;
        return Math.max(0L, end - player.level().getGameTime());
    }

    /** 提前结束并还原所有被冻结的实体。 */
    public static void restore(Player player) {
        UUID id = player.getUUID();
        END_TICK.remove(id);
        RADIUS.remove(id);

        Set<Entity> frozen = FROZEN.remove(id);
        Map<Entity, Boolean> previous = PREVIOUS_GRAVITY.remove(id);
        if (frozen == null) {
            return;
        }
        for (Entity entity : frozen) {
            boolean wasNoGravity = previous != null && Boolean.TRUE.equals(previous.get(entity));
            if (!wasNoGravity) {
                entity.setNoGravity(false);
            }
        }
    }

    /** 该实体此刻是否被某个时停冻住。 */
    public static boolean isFrozen(Entity entity) {
        for (Set<Entity> set : FROZEN.values()) {
            if (set.contains(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家下线 / 换维度 / 重生时把冻结还原。
     *
     * <p>2026-11-10 修：这些路径原来什么都不做，施术者一退出，被冻住的生物就永远带着
     * {@code noGravity} 挂在天上（重启后还在，因为这个标记会存进实体存档）。
     * 现在三个出口都还原一次；服务器关闭时清空静态表，避免下次启动拿脏数据去还原。</p>
     */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        restore(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        restore(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        restore(event.getOriginal());
        restore(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        END_TICK.clear();
        RADIUS.clear();
        FROZEN.clear();
        PREVIOUS_GRAVITY.clear();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        UUID id = player.getUUID();
        Long end = END_TICK.get(id);
        if (end == null) return;

        if (player.level().getGameTime() >= end) {
            restore(player);
            player.displayClientMessage(Component.literal("§b时间重新流动。"), true);
            return;
        }

        double radius = RADIUS.getOrDefault(id, 64.0D);
        Set<Entity> frozen = FROZEN.computeIfAbsent(id, k -> new HashSet<>());
        Map<Entity, Boolean> previous = PREVIOUS_GRAVITY.computeIfAbsent(id, k -> new IdentityHashMap<>());

        // 复制一份再遍历：冻结过程中实体可能被移除
        for (Entity entity : new java.util.ArrayList<>(player.level().getEntitiesOfClass(
                Entity.class, player.getBoundingBox().inflate(radius)))) {
            if (entity == player || entity instanceof Player) continue;

            if (frozen.add(entity)) {
                // 第一次冻结：记下原来的重力状态，供解除时还原
                previous.put(entity, entity.isNoGravity());
            }
            entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
            entity.setNoGravity(true);
            entity.setPos(entity.getX(), entity.getY(), entity.getZ());
            entity.hurtMarked = true; // 让客户端接受这次速度归零
        }
    }
}
