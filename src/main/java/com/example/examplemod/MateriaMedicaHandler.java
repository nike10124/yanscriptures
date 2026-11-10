package com.example.examplemod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《本草纲目》—— 会用药的人，药也更认他。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>正面效果时长 ×1.5，负面效果 ×0.5；</li>
 *   <li>喝下的药水、吃下的食物带来的效果，等级 +2（抗性提升只在原等级 1 或 2 时生效）。</li>
 * </ul>
 *
 * <p><b>2026-11-01 两次崩溃的教训（看这里别再踩）</b>：
 * Forge 1.20.1 的 {@code MobEffectEvent.Added} 与 {@code MobEffectEvent.Applicable}
 * <b>两个都不可取消</b>，也都没有 {@code setEffectInstance}（只有 {@code getEffectInstance()}）。
 * 所以「在事件里取消掉、换一份加进去」这条路根本不存在：
 * 调 {@code setCanceled(true)} 会抛
 * {@code UnsupportedOperationException: Attempted to call Event#setCanceled()
 * on a non-cancelable event}，被包成 {@code ReportedException: Ticking player} 直接崩服。
 *
 * <p>现在的做法：在 {@link MobEffectEvent.Added} 里只<b>记账</b>（算出想要的那份实例），
 * 下一个 tick 由 {@link #onPlayerTick} 真正执行
 * {@code removeEffect} + {@code forceAddEffect}。{@code OURS} 是一次性保险丝，
 * 免得自己加回去的那份又被自己算一遍（否则时长会越乘越长）。
 *
 * <p><b>2026-11-01 第二次修（等级 +2 不生效）</b>：吃喝东西时，药水效果是在
 * {@code ItemStack#finishUsingItem} 里加上的，而 {@link LivingEntityUseItemEvent.Finish}
 * 在那之后才触发 —— 也就是说 {@code Added} 事件里 {@code LAST_CONSUME} 还是空的，
 * 「刚吃过」永远判成 false，等级自然不会 +2。修法：{@code Added} 里只算时长，
 * <b>等级留到下一个 tick 再定</b>，那会儿 {@code Finish} 早就把时间记上了。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MateriaMedicaHandler {

    private static final double BENEFICIAL_DURATION = 1.5D;
    private static final double HARMFUL_DURATION = 0.5D;
    private static final int CONSUME_BONUS_AMPLIFIER = 2;
    /** 「刚吃过东西」的有效窗口（刻）。 */
    private static final int CONSUME_WINDOW_TICKS = 2;

    /** 玩家 → 最近一次「喝/吃」发生的 gameTime。 */
    private static final Map<UUID, Long> LAST_CONSUME = new HashMap<>();

    /** 待换上去的一条：已经算好时长的原始实例 + 它是不是正面效果。 */
    private record Pending(MobEffectInstance base, boolean beneficial) {
    }

    /** 玩家 → 下一个 tick 要换上去的效果（可能一次喝出好几个）。 */
    private static final Map<UUID, Deque<Pending>> PENDING = new HashMap<>();

    /** 玩家 → 「下一份是我们自己加回去的，别再算一遍」的一次性标记。 */
    private static final Set<UUID> OURS = new HashSet<>();

    static {
        SessionCleanup.register(MateriaMedicaHandler::cleanup);
    }

    private MateriaMedicaHandler() {
    }

    @SubscribeEvent
    public static void onUseItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.MATERIA_MEDICA)) {
            return;
        }
        if (event.getItem().getItem() instanceof net.minecraft.world.item.PotionItem
                || event.getItem().getItem().isEdible()) {
            LAST_CONSUME.put(player.getUUID(), player.level().getGameTime());
        }
    }

    /** 只记账，不动手（事件不可取消，见类注释）。等级这里不碰，留给 tick。 */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (OURS.remove(player.getUUID())) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.MATERIA_MEDICA)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) {
            return;
        }
        MobEffect effect = instance.getEffect();
        // 瞬间效果（治疗/伤害药水）没有「时长」可言，别碰
        if (effect.isInstantenous()) {
            return;
        }
        boolean beneficial = effect.getCategory() == MobEffectCategory.BENEFICIAL;

        int duration = instance.getDuration();
        if (!instance.isInfiniteDuration()) {
            duration = Math.max(20, (int) (duration * (beneficial ? BENEFICIAL_DURATION : HARMFUL_DURATION)));
        }
        // 时长没变、也不是正面效果 → 没什么可做的
        if (duration == instance.getDuration() && !beneficial) {
            return;
        }
        MobEffectInstance base = new MobEffectInstance(effect, duration, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        PENDING.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>()).add(new Pending(base, beneficial));
    }

    /** 下一个 tick 把记下的那份换上去（这时「刚吃过」才判得准）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        if (!PENDING.containsKey(player.getUUID())) {
            return;
        }
        Deque<Pending> queue = PENDING.get(player.getUUID());
        UUID id = player.getUUID();
        boolean consumed = justConsumed(player);
        Pending pending;
        while (queue != null && (pending = queue.poll()) != null) {
            MobEffectInstance base = pending.base();
            int amplifier = base.getAmplifier();
            if (pending.beneficial() && consumed && canRaiseAmplifier(base.getEffect(), amplifier)) {
                amplifier = Math.min(4, amplifier + CONSUME_BONUS_AMPLIFIER);
            }
            MobEffectInstance want = new MobEffectInstance(base.getEffect(), base.getDuration(), amplifier,
                    base.isAmbient(), base.isVisible(), base.showIcon());
            player.removeEffect(want.getEffect());
            OURS.add(id);
            try {
                player.forceAddEffect(want, null);
            } finally {
                OURS.remove(id);
            }
        }
        PENDING.remove(id);
    }

    private static boolean justConsumed(Player player) {
        Long when = LAST_CONSUME.get(player.getUUID());
        if (when == null) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now - when > CONSUME_WINDOW_TICKS) {
            LAST_CONSUME.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** 抗性提升只允许在原等级 1~2（amplifier 0~1）时被抬高 —— 作者专门要求的例外。 */
    private static boolean canRaiseAmplifier(MobEffect effect, int amplifier) {
        if (effect == MobEffects.DAMAGE_RESISTANCE) {
            return amplifier <= 1;
        }
        return true;
    }

    private static void cleanup(UUID id) {
        LAST_CONSUME.remove(id);
        PENDING.remove(id);
        OURS.remove(id);
    }
}
