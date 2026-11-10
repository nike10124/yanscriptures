package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 两个毕业饰品的**共用脱困逻辑**。
 *
 * <h2>为什么单独成类</h2>
 * 之前两个饰品各写各的，结果一个能触发一个不能。现在共用这一份：
 * 只按「戴了哪个」决定半径与冷却，判定完全一致。
 *
 * <h2>判定（三条全中）</h2>
 * <ol>
 *   <li>最近 3 秒内确实挨过打</li>
 *   <li>连续两次采样（间隔 10 tick）位移都过小 —— 也就是约 1 秒内没动</li>
 *   <li>沿视线方向找得到落点</li>
 * </ol>
 *
 * <h2>两个曾经踩过的坑</h2>
 * <ul>
 *   <li><b>受伤时间戳不能只听 {@code LivingHurtEvent}</b>：圣约·原典在
 *       {@code LivingAttackEvent} 就取消了伤害，{@code LivingHurtEvent} 根本不会派发，
 *       于是「最近受过伤」永远为假 —— 脱困一次都不会触发。现在两个事件都记时间。</li>
 *   <li><b>采样基线不能每 tick 更新</b>：那样每次比对只隔 1 tick，任何微小位移都会被算成「动了」。
 *       现在基线只在采样点（每 10 tick）更新，比对的是真正的一段时间。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EscapeController {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ==================== 参数 ====================

    /** 受伤后多久内才认为「可能正被按住」。 */
    private static final long HURT_WINDOW_MS = 3000L;
    /** 采样间隔（tick）。 */
    private static final int SAMPLE_INTERVAL = 10;
    /** 需要连续几次采样都没动（2 次 ≈ 1 秒）。 */
    private static final int STUCK_SAMPLES = 2;
    /** 判定「没动」的阈值（格²，5 厘米）。 */
    private static final double STUCK_THRESHOLD = 0.0025D;
    /** 戴圣约时的「没动」阈值（格²，0.5 格）——结界会小幅拽动玩家，阈值必须放宽。 */
    private static final double COVENANT_STILL_THRESHOLD = 0.25D;
    /** 兜底条件：连续静止这么久且附近有敌人，也视为被按住（5 秒）。只对《如我所书》生效。 */
    private static final long STILL_WITH_ENEMY_MS = 5000L;
    /** 兜底条件触发的冷却（6 秒，避免站着不动被反复传送）。 */
    private static final int STILL_TRIGGER_COOLDOWN = 120;

    /** 《如我所书》。 */
    private static final double BOOK_MIN = 8.0D;
    private static final double BOOK_MAX = 16.0D;
    private static final int BOOK_COOLDOWN = 20;

    /** 《圣约·原典》。 */
    private static final double COVENANT_MIN = 8.0D;
    private static final double COVENANT_MAX = 24.0D;
    private static final int COVENANT_COOLDOWN = 40;

    // ==================== 状态 ====================

    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    /** 上一次采样点时的坐标。 */
    private static final Map<UUID, double[]> SAMPLE_POS = new HashMap<>();
    private static final Map<UUID, Integer> SAMPLES = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    /** 从什么时候开始「纹丝不动」。 */
    private static final Map<UUID, Long> STILL_SINCE = new HashMap<>();
    /** 上一 tick 的生命值，用来把「掉血」当作受伤证据。 */
    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

    /**
     * 玩家下线：把按 UUID 记的采样与冷却全部丢掉。
     *
     * <p>这些表原来只增不减，服务器跑几天就会攒下一堆已离线玩家的条目；
     * 更要紧的是「重新进服」会继承上一轮的残血基线与静止计时。
     */
    public static void cleanupPlayer(UUID id) {
        LAST_HURT.remove(id);
        SAMPLE_POS.remove(id);
        SAMPLES.remove(id);
        COOLDOWN.remove(id);
        STILL_SINCE.remove(id);
        LAST_HEALTH.remove(id);
    }

    private EscapeController() {
    }

    // ==================== 佩戴判定 ====================

    private static boolean wears(Player player, ItemStack target) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(target.getItem()))
                .filter(result -> !result.stack().isEmpty())
                .isPresent();
    }

    private static boolean wearsBook(Player player) {
        return wears(player, new ItemStack(ExampleMod.BOOK_OF_ME.get()));
    }

    private static boolean wearsCovenant(Player player) {
        return wears(player, new ItemStack(ExampleMod.COVENANT_CODEX.get()));
    }

    private static boolean wearsEither(Player player) {
        return wearsBook(player) || wearsCovenant(player);
    }

    // ==================== 受伤时间戳：两个事件都记 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        stamp(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        stamp(event.getEntity());
    }

    private static void stamp(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!wearsEither(player)) return;
        LAST_HURT.put(player.getUUID(), System.currentTimeMillis());
    }

    /** 被外部（例如圣约的防改血层）记为「刚挨过打」。 */
    public static void markHurt(Player player) {
        LAST_HURT.put(player.getUUID(), System.currentTimeMillis());
    }

    /** 附近是否有敌对生物（用于「结界不造成伤害」时的兜底判定）。 */
    private static boolean hasNearbyEnemy(Player player) {
        return !player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(16.0D),
                entity -> entity != player
                        && entity.isAlive()
                        && entity instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
    }

    // ==================== 对外诊断接口 ====================

    /** 强制脱困一次（供 `/yanbook escape` 使用），返回是否成功。 */
    public static boolean forceEscape(Player player) {
        boolean covenant = wearsCovenant(player);
        boolean book = covenant || wearsBook(player);
        double min = covenant ? COVENANT_MIN : BOOK_MIN;
        double max = covenant ? COVENANT_MAX : BOOK_MAX;

        boolean ok = TeleportEscape.escape(player, min, max);
        if (ok) {
            SAMPLE_POS.put(player.getUUID(),
                    new double[]{player.getX(), player.getY(), player.getZ()});
            LOGGER.info("[脱困] 指令强制传送 → ({}, {}, {})",
                    (int) player.getX(), (int) player.getY(), (int) player.getZ());
        } else {
            LOGGER.info("[脱困] 指令强制传送失败：{} 格内找不到落点", max);
        }
        return ok;
    }

    /** 返回一行诊断信息，用于 `/yanbook escape info`。 */
    public static String debugInfo(Player player) {
        UUID id = player.getUUID();
        boolean covenant = wearsCovenant(player);
        boolean book = covenant || wearsBook(player);

        long hurtAgo = LAST_HURT.containsKey(id)
                ? System.currentTimeMillis() - LAST_HURT.get(id) : -1L;

        double[] baseline = SAMPLE_POS.get(id);
        double moved = baseline == null ? -1.0D
                : Math.sqrt(Math.pow(player.getX() - baseline[0], 2) + Math.pow(player.getZ() - baseline[2], 2));

        return String.format(
                "佩戴：圣约=%s 书=%s | 受伤距今=%s ms | 采样位移=%s | 连续静止=%d | 冷却=%d | 阈值=%s",
                covenant, book,
                hurtAgo < 0 ? "从未" : hurtAgo,
                moved < 0 ? "无基线" : String.format("%.4f", moved),
                SAMPLES.getOrDefault(id, 0),
                COOLDOWN.getOrDefault(id, 0),
                STUCK_THRESHOLD);
    }

    // ==================== 主循环 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        UUID id = player.getUUID();
        boolean book = wearsCovenant(player) || wearsBook(player);
        if (!book) {
            LAST_HURT.remove(id);
            SAMPLE_POS.remove(id);
            SAMPLES.remove(id);
            COOLDOWN.remove(id);
            LAST_HEALTH.remove(id);
            STILL_SINCE.remove(id);
            return;
        }

        // 生命值下降也算「挨打」：结界类机制常常直接改血、不派发伤害事件。
        // 现在只用于调试信息的显示，不再驱动任何自动传送。
        float health = player.getHealth();
        Float previousHealth = LAST_HEALTH.put(id, health);
        if (previousHealth != null && health < previousHealth - 0.01F) {
            LAST_HURT.put(id, System.currentTimeMillis());
        }

        // ==================== 自动脱困已整体关闭（2026-11-08 作者要求） ====================
        // 两本毕业饰品的脱困统一改成「按 K 才传送」（与《圣约·原典》一致）。
        // 原来《如我所书》还有一条「长时间站着不动 + 16 格内有敌人」的兜底自动传送，
        // 正常游玩里太容易满足（挂机、看背包、被结界按住都会被判成同一个状态），
        // 动不动就把人传走。现在两条自动路径都不再执行，只剩 K 键与 `/ys escape`。
        SAMPLES.remove(id);
        STILL_SINCE.remove(id);
    }
}
