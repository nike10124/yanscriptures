package com.example.examplemod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《三十六计》—— 三十六策，走为上策。
 *
 * <p>作者定稿（2026-11-06）：
 * <ul>
 *   <li>生命低于 30% 时自动触发「走为上」：6 秒隐身 + 移动速度 ×2，冷却 30 秒；</li>
 *   <li>触发时清空自身仇恨（周围生物 {@code setTarget(null)}），不影响其他玩家；</li>
 *   <li>佩戴时降低怪物的检测范围（与隐身独立生效）。</li>
 * </ul>
 *
 * <p>「降低检测范围」为什么不改玩家自己的 {@code Attributes.FOLLOW_RANGE}：
 * 那个属性是**生物**拿来索敌的，玩家的那份没人读。所以做法是给附近生物临时挂一个
 * {@code FOLLOW_RANGE} 负修饰符（UUID 按生物 UUID 生成，摘书/走出范围/超时都会撤掉），
 * 而不是去 mixin 索敌代码。
 *
 * <p>「真实隐身」（作者 2026-11-07 要求，参照铁魔法的同名法术）：原版隐身只是让玩家
 * 在渲染与索敌判定里不可见，怪一旦已经锁定你、或者在被攻击时立刻重新锁定，
 * 那 6 秒等于白给。这里补两层——{@link LivingChangeTargetEvent} 里把「新目标是
 * 隐身中的佩戴者」直接改成 null（这个事件不可取消，改目标等价于不让它锁上），
 * 以及隐身窗口内每 5 刻再扫一次把已经锁上的清掉。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThirtySixStrategiesHandler {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        DETECT_SEEN.remove(id);
        DETECT_OWNER.remove(id);
        DETECT_MOB.remove(id);
    }

    /** 触发阈值：生命比例。 */
    private static final double LOW_HEALTH_RATIO = 0.30D;
    /** 隐身时长：6 秒。 */
    private static final int INVISIBLE_TICKS = 120;
    /** 冷却：30 秒。 */
    private static final int COOLDOWN_TICKS = 600;
    /** 移动速度 ×2 = MULTIPLY_BASE +100%。 */
    private static final double SPEED_BONUS = 1.0D;
    /** 清仇恨 / 降低检测的作用半径。 */
    private static final double AURA_RADIUS = 24.0D;
    /** 检测范围 −35%（MULTIPLY_BASE，作用在生物身上）。 */
    private static final double DETECT_FACTOR = -0.35D;
    /** 多久没再看到某个生物，就撤掉它的检测范围修饰符。 */
    private static final long SEEN_TIMEOUT = 100L;

    private static final String TAG_UNTIL = "YanStrategiesUntil";
    private static final String TAG_COOLDOWN = "YanStrategiesCooldown";

    private static final UUID ID_SPEED = BookAttr.id("strategies_speed");

    /** 生物 UUID → 最后被看见的 gameTime。 */
    private static final Map<UUID, Long> DETECT_SEEN = new HashMap<>();
    /** 生物 UUID → 施加者，摘书时只撤自己加的。 */
    private static final Map<UUID, UUID> DETECT_OWNER = new HashMap<>();
    /** 生物 UUID → 实体本身，超时撤修饰符时要用（只活在 100 刻，不会长期占内存）。 */
    private static final Map<UUID, Mob> DETECT_MOB = new HashMap<>();

    private ThirtySixStrategiesHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }

        boolean worn = WornCache.wears(player, ClassicBook.THIRTY_SIX_STRATEGIES);
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        boolean active = worn && data.getLong(TAG_UNTIL) > now;

        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, active ? SPEED_BONUS : 0.0D);

        if (worn && !active && data.getLong(TAG_COOLDOWN) <= now && healthRatio(player) <= LOW_HEALTH_RATIO) {
            trigger(player, data, now);
        }

        if (player.tickCount % 20 == 0) {
            if (worn) {
                sweepDetection(player);
            } else {
                releaseDetection(player);
            }
            sweepStale(now);
        }

        // 隐身窗口内持续压制索敌：怪被打了会立刻重新 setTarget，光靠触发那一下清仇恨不够
        if (active && player.tickCount % 5 == 0) {
            dropAggro(player);
        }
    }

    /**
     * 真实隐身：不让任何生物把「隐身中的佩戴者」设成目标。
     *
     * <p>{@code LivingChangeTargetEvent} 在 Forge 里没有取消开关（javap 只有
     * {@code getNewTarget/setNewTarget/getTargetType/getOriginalTarget}），
     * 所以这里把新目标改成 {@code null}，效果等价于「锁不上」。
     * 只在《三十六计》自己的隐身窗口里生效，别的来源的隐身不受影响。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getLong(TAG_UNTIL) <= player.level().getGameTime()) {
            return;
        }
        if (!player.hasEffect(MobEffects.INVISIBILITY) || !WornCache.wears(player, ClassicBook.THIRTY_SIX_STRATEGIES)) {
            return;
        }
        event.setNewTarget(null);
    }

    private static void trigger(Player player, CompoundTag data, long now) {
        data.putLong(TAG_UNTIL, now + INVISIBLE_TICKS);
        data.putLong(TAG_COOLDOWN, now + COOLDOWN_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVISIBLE_TICKS, 0, false, false, true));
        dropAggro(player);
        player.displayClientMessage(Component.translatable("message.yanscriptures.strategies.trigger"), true);
    }

    /** 把「盯着我」的生物的目标清掉；别的玩家的仇恨不动。 */
    private static void dropAggro(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AURA_RADIUS))) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }

    private static void sweepDetection(Player player) {
        long now = player.level().getGameTime();
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AURA_RADIUS))) {
            BookAttr.set(mob, Attributes.FOLLOW_RANGE, detectId(mob), DETECT_FACTOR);
            DETECT_SEEN.put(mob.getUUID(), now);
            DETECT_OWNER.put(mob.getUUID(), player.getUUID());
            DETECT_MOB.put(mob.getUUID(), mob);
        }
    }

    private static void releaseDetection(Player player) {
        UUID self = player.getUUID();
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AURA_RADIUS))) {
            if (self.equals(DETECT_OWNER.get(mob.getUUID()))) {
                BookAttr.set(mob, Attributes.FOLLOW_RANGE, detectId(mob), 0.0D);
                DETECT_SEEN.remove(mob.getUUID());
                DETECT_OWNER.remove(mob.getUUID());
                DETECT_MOB.remove(mob.getUUID());
            }
        }
    }

    /** 生物走远了也得把修饰符撤掉，否则整张图会被慢慢污染。 */
    private static void sweepStale(long now) {
        Iterator<Map.Entry<UUID, Long>> it = DETECT_SEEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() > SEEN_TIMEOUT) {
                Mob mob = DETECT_MOB.remove(entry.getKey());
                if (mob != null && mob.isAlive()) {
                    BookAttr.set(mob, Attributes.FOLLOW_RANGE, detectId(mob), 0.0D);
                }
                DETECT_OWNER.remove(entry.getKey());
                it.remove();
            }
        }
    }

    private static UUID detectId(LivingEntity entity) {
        return BookAttr.id("strategies_detect_" + entity.getUUID());
    }

    private static double healthRatio(Player player) {
        float max = player.getMaxHealth();
        return max <= 0.0F ? 0.0D : player.getHealth() / max;
    }
}
