package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 全局 DPS 统计。
 *
 * <h2>为什么和饰品解耦</h2>
 * DPS 是用来测伤害的工具，不该因为「没戴某个饰品」就消失。
 * 所以这里对所有玩家一律统计，再按玩家推给客户端，客户端那边独立渲染。
 *
 * <h2>统计口径</h2>
 * 记录 {@link LivingHurtEvent} 的最终金额（LOWEST，暴击、减伤都已算完），
 * 用 5 秒滑动窗口换算成「每秒伤害」。只有自己打出的伤害算数，打自己不算。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DpsMeter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 统计窗口（毫秒）。 */
    private static final long WINDOW_MS = 5000L;
    /** 推送间隔（刻）。 */
    private static final int PUSH_INTERVAL = 20;

    /** 玩家 → (时间戳, 伤害×100)。 */
    private static final Map<UUID, ArrayDeque<long[]>> LOG = new HashMap<>();
    /** 玩家 → 窗口内的命中次数 / 暴击次数。 */
    private static final Map<UUID, ArrayDeque<Long>> HITS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> CRITS = new HashMap<>();
    private static int tickCounter = 0;

    private DpsMeter() {
    }

    /** 玩家下线时清掉记账。 */
    public static void cleanupPlayer(java.util.UUID id) {
        LOG.remove(id);
        HITS.remove(id);
        CRITS.remove(id);
    }

    /** 由暴击判定调用：记一次暴击。 */
    public static void markCrit(Player player) {
        CRITS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>())
                .addLast(System.currentTimeMillis());
    }

    /**
     * 累计伤害。
     *
     * <p><b>必须带 {@code receiveCanceled = true}</b>：Forge 默认不会把「已经被取消的事件」
     * 继续派发给后面的监听器。训练假人的伤害在 HIGHEST 就被取消了（所以打不死），
     * 而本方法在 LOWEST —— 不加这个开关就永远收不到假人的伤害，
     * 表现就是「打真怪有 DPS，打假人没有」。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (event.getEntity() == player) return;

        long now = System.currentTimeMillis();
        LOG.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>())
                .addLast(new long[]{now, (long) (event.getAmount() * 100.0F)});
        HITS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()).addLast(now);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < PUSH_INTERVAL) return;
        tickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, ArrayDeque<long[]>>> it = LOG.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ArrayDeque<long[]> deque = entry.getValue();

            // 清掉窗口外的记录
            while (!deque.isEmpty() && now - deque.peekFirst()[0] > WINDOW_MS) {
                deque.pollFirst();
            }

            long total = 0L;
            for (long[] item : deque) {
                total += item[1];
            }
            int dps = (int) (total / 100.0F / (WINDOW_MS / 1000.0F));

            // 命中/暴击统计（同一窗口）
            int hits = countRecent(HITS.get(entry.getKey()), now);
            int crits = countRecent(CRITS.get(entry.getKey()), now);

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                if (deque.isEmpty()) {
                    it.remove();
                }
                continue;
            }
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new NetworkHandler.DpsPacket(dps, hits, crits));
        }
    }

    /** 统计窗口内还剩多少条记录。 */
    private static int countRecent(ArrayDeque<Long> deque, long now) {
        if (deque == null) {
            return 0;
        }
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
            deque.pollFirst();
        }
        return deque.size();
    }
}
