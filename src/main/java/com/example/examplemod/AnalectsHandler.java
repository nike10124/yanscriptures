package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 《论语》—— 学而时习之，不亦说乎。
 *
 * <p>作者定稿（2026-11-06）：
 * 「周围 16 格内每有一个玩家或者宠物或仆从，使自身造成伤害提高 5%，
 * 攻击后回复（1 × 其个数 %）的生命值」，上限十个（作者补充）。
 * 2026-11-07 作者更正：「是根据仆从或者宠物玩家的数量恢复自身的生命值，不是友善单位的」
 * ——只数自己的宠物/仆从，其他玩家与友好生物不算。
 *
 * <p>「宠物或仆从」不写别家模组的类名，统一看 {@link OwnableEntity} 的主人是不是自己——
 * 原版猫狗、别的模组实现过主从关系的仆从都算（诡厄巫法的仆从基类也实现了这个原版接口）。
 *
 * <p>为什么用 {@link EventPriority#LOW} 而不是默认优先级：本模组里改伤害的处理器大多
 * 在默认优先级做乘法，挪到 LOW 之后才保证我们是最后一手乘法，不会被别人后来的
 * {@code setAmount()} 覆盖掉（作者 2026-11-07 报「论语无效」，优先级顺序是第一嫌疑）。
 * 顺手加了「同伴数变化时打一行日志」，作者可以直接在 `latest.log` 里确认判定到了几个。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnalectsHandler {

    private static final Logger LOG = LogUtils.getLogger();

    private static final double RADIUS = 16.0D;
    private static final double PER_COMPANION = 0.05D;
    private static final int MAX_COUNT = 10;
    private static final double HEAL_RATIO = 0.01D;

    /** 玩家 UUID → 上一次记日志时的同伴数，避免每次命中都刷屏。 */
    private static final Map<UUID, Integer> LAST_LOGGED = new HashMap<>();

    /**
     * 玩家 UUID → 待结算的回血量。
     *
     * <p>为什么不在 {@link LivingHurtEvent} 里直接 {@code heal()}：那个事件发生在伤害**落地之前**，
     * 先回血、再扣血，玩家看到的只有掉血数字，体感就是「回血没生效」（作者 2026-11-07 报
     * 「根据宠物数量给玩家回血的效果好像没有生效」）。改成攒到下一 tick 再结算，才是真正
     * 「打完之后回一口」。
     */
    private static final Map<UUID, Float> PENDING_HEAL = new HashMap<>();

    static {
        SessionCleanup.register(AnalectsHandler::cleanupPlayer);
    }

    private AnalectsHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoingHurt(LivingHurtEvent event) {
        Player player = attackerOf(event);
        if (player == null || event.getEntity() == player) {
            return;
        }
        if (player.level().isClientSide() || !WornCache.wears(player, ClassicBook.ANALECTS)) {
            return;
        }
        int count = companions(player);
        if (count <= 0) {
            return;
        }
        event.setAmount((float) (event.getAmount() * (1.0D + PER_COMPANION * count)));
        // 回血攒到下一 tick 结算（见 PENDING_HEAL 的说明），这里只记账。
        PENDING_HEAL.merge(player.getUUID(), (float) (player.getMaxHealth() * HEAL_RATIO * count), Float::sum);
        logOnce(player, count);
    }

    /**
     * 下一 tick 把攒下的回血真正加上。
     *
     * <p>用 {@code setHealth} 而不是 {@code heal()}：{@code heal()} 会发 {@link net.minecraftforge.event.entity.living.LivingHealEvent}，
     * 被别的机制（例如《埃涅阿斯纪》反转治疗）拦掉时玩家看不到任何效果；这里是我们自己给的奖励，
     * 直接加、并且不触发治疗事件。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        Float amount = PENDING_HEAL.remove(player.getUUID());
        if (amount == null || amount <= 0.0F || !player.isAlive()) {
            return;
        }
        float max = player.getMaxHealth();
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    /** 近战取 {@code getEntity()}，远程（箭矢之类）退到 {@code getDirectEntity()}。 */
    private static Player attackerOf(LivingHurtEvent event) {
        Entity direct = event.getSource().getEntity();
        if (direct instanceof Player player) {
            return player;
        }
        Entity source = event.getSource().getDirectEntity();
        return source instanceof Player player ? player : null;
    }

    private static void logOnce(Player player, int count) {
        Integer last = LAST_LOGGED.put(player.getUUID(), count);
        if (last != null && last == count) {
            return;
        }
        LOG.info("《论语》：附近同伴 {} 名 → 造成伤害 +{}%，每次命中回复 {}% 生命",
                count, String.format("%.0f", count * PER_COMPANION * 100.0D),
                String.format("%.0f", count * HEAL_RATIO * 100.0D));
    }

    private static void cleanupPlayer(UUID playerId) {
        LAST_LOGGED.remove(playerId);
        PENDING_HEAL.remove(playerId);
    }

    /**
     * 16 格内的同伴数：**只算自己的宠物/仆从**，封顶 10。
     *
     * <p>作者 2026-11-07：「论语的效果是根据仆从或者宠物玩家的数量恢复自身的生命值，不是友善单位的」
     * ——所以其他玩家、村民、友好生物一律不计，只认 {@link OwnableEntity} 的主人是不是自己。
     */
    private static int companions(Player player) {
        int count = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS))) {
            if (entity == player || !entity.isAlive() || entity instanceof Player) {
                continue;
            }
            boolean companion = entity instanceof OwnableEntity ownable
                    && player.getUUID().equals(ownable.getOwnerUUID());
            if (companion && ++count >= MAX_COUNT) {
                return MAX_COUNT;
            }
        }
        return count;
    }
}
