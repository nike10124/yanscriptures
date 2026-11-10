package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 九本名著的获取条件。
 *
 * <p>和十张残卷不同，名著没有「必须先违逆某条诫命」的门槛：条件一旦成立，
 * {@link MilestoneTracker#markReady} 会在下一次投递循环里直接把书发到手上。
 *
 * <p>每条都只用一个能一眼讲清的动作来判定，并且只用语义（光照、高度、坐骑、
 * 伤害类型数量……），不写死任何其他模组的 ID —— 这样玩家看提示就知道该做什么，
 * 换整合包也不会失效。
 *
 * <p>进度全部存在玩家的持久化数据里，键前缀 {@code Cm}。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicMilestones {

    /**
     * 九本书「获取条件」的总开关。
     *
     * <p>2026-11-11 作者定：书籍只在「写出史诗」与「拿到寂灭之笺」时给成就，
     * 低稀有度的书不再显示进度提示，所以这九本名著的判定重新停用
     * （代码保留，将来若要恢复，把 {@code ENABLED} 改回 {@code true}
     * 并在 {@code MilestoneTracker.ALL} 里把九个 id 加回去即可）。
     */
    public static final boolean ENABLED = false;

    private ClassicMilestones() {
    }

    // ==================== 持久化键 ====================

    /** 道德经：生命值 ≤ 1 的起始时刻。 */
    private static final String DAO_START = "CmDaoStart";
    /** 理想国：暗处击杀数。 */
    private static final String REPUBLIC_KILLS = "CmRepKills";
    /** 查拉图斯特拉：是否已从深坑出发（受伤即清除）。 */
    private static final String ZARA_CLIMB = "CmZarClimb";
    /** 神曲：下界 / 末地击杀数。 */
    private static final String COMEDY_NETHER = "CmDcNether";
    private static final String COMEDY_END = "CmDcEnd";
    /** 堂吉诃德：骑乘击杀数。 */
    private static final String QUIXOTE_KILLS = "CmQuixKills";
    /** 百年孤独：独处击杀数。 */
    private static final String LONELY_KILLS = "CmLonelyKills";
    /** 几何原本：静止的起点（时刻 + 坐标），受伤即作废。 */
    private static final String EUCLID_TIME = "CmEucTime";
    private static final String EUCLID_POS = "CmEucPos";
    /** 物种起源：见过的伤害类型（逗号分隔）。 */
    private static final String ORIGIN_TYPES = "CmOrigin";

    // ==================== 阈值 ====================

    /** 道德经：以 1 点生命值撑满 10 秒。 */
    private static final float DAO_HEALTH = 1.0F;
    private static final long DAO_TICKS = 200L;
    /** 理想国：在光照 ≤ 3 处击杀 10 个。 */
    private static final int REPUBLIC_BRIGHTNESS = 3;
    private static final int REPUBLIC_NEEDED = 10;
    /** 查拉图斯特拉：从 y ≤ -32 不受伤害地爬到 y ≥ 128。 */
    private static final double ZARA_BOTTOM = -32.0;
    private static final double ZARA_TOP = 128.0;
    /** 神曲：下界、末地各 10 杀。 */
    private static final int COMEDY_NEEDED = 10;
    /** 堂吉诃德：骑乘击杀 10 个。 */
    private static final int QUIXOTE_NEEDED = 10;
    /** 百年孤独：32 格内无其他玩家时击杀 50 个。 */
    private static final double LONELY_RADIUS = 32.0;
    private static final int LONELY_NEEDED = 50;
    /** 几何原本：原地不动 60 秒（每 20 tick 采样一次）。 */
    private static final long EUCLID_TICKS = 1200L;
    private static final int EUCLID_SAMPLE = 20;
    private static final double EUCLID_DRIFT = 0.6;
    /** 物种起源：被 5 种伤害类型打过还活着。 */
    private static final int ORIGIN_NEEDED = 5;
    /** 九章算术：单次承受最大生命一半以上的伤害而没死。 */
    private static final float NINE_RATIO = 0.5F;

    // ==================== 每 tick 判定 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ENABLED) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player) || player.isSpectator()) return;

        CompoundTag tag = player.getPersistentData();
        long now = MilestoneTracker.clock(player);

        checkDaoDeJing(player, tag, now);
        checkZarathustra(player, tag);
        if (player.tickCount % EUCLID_SAMPLE == 0) {
            checkEuclid(player, tag, now);
        }
    }

    /** 道德经：生命值压到 1 点还撑住 10 秒 ——「柔弱胜刚强」。 */
    static void checkDaoDeJing(ServerPlayer player, CompoundTag tag, long now) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.DAO_DE_JING)) return;

        boolean fragile = player.getHealth() <= DAO_HEALTH && !player.isDeadOrDying();
        if (!fragile) {
            tag.putLong(DAO_START, 0L);
            return;
        }
        long start = tag.getLong(DAO_START);
        if (start <= 0L) {
            tag.putLong(DAO_START, now);
            return;
        }
        if (now - start >= DAO_TICKS) {
            tag.putLong(DAO_START, 0L);
            MilestoneTracker.markReady(player, MilestoneTracker.DAO_DE_JING);
        }
    }

    /** 查拉图斯特拉：从深坑出发，一路不受伤害地爬到 128 格以上 ——「上升之路」。 */
    static void checkZarathustra(ServerPlayer player, CompoundTag tag) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.ZARATHUSTRA)) return;

        if (player.getY() <= ZARA_BOTTOM) {
            tag.putBoolean(ZARA_CLIMB, true);
            return;
        }
        if (tag.getBoolean(ZARA_CLIMB) && player.getY() >= ZARA_TOP) {
            tag.putBoolean(ZARA_CLIMB, false);
            MilestoneTracker.markReady(player, MilestoneTracker.ZARATHUSTRA);
        }
    }

    /** 几何原本：原地站满一分钟，像在纸上描第一个圆。 */
    static void checkEuclid(ServerPlayer player, CompoundTag tag, long now) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.EUCLID)) return;

        BlockPos anchor = null;
        if (tag.contains(EUCLID_POS)) {
            anchor = BlockPos.of(tag.getLong(EUCLID_POS));
        }
        long start = tag.getLong(EUCLID_TIME);

        boolean drifted = anchor == null
                || start <= 0L
                || player.blockPosition().distSqr(anchor) > EUCLID_DRIFT * EUCLID_DRIFT;

        if (drifted) {
            tag.putLong(EUCLID_POS, player.blockPosition().asLong());
            tag.putLong(EUCLID_TIME, now);
            return;
        }
        if (now - start >= EUCLID_TICKS) {
            tag.putLong(EUCLID_TIME, 0L);
            tag.remove(EUCLID_POS);
            MilestoneTracker.markReady(player, MilestoneTracker.EUCLID);
        }
    }

    // ==================== 受伤判定 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!ENABLED) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData();

        // 查拉图斯特拉：受了伤就得从头再爬
        tag.putBoolean(ZARA_CLIMB, false);
        // 几何原本：站桩期间挨打，重新计时
        tag.putLong(EUCLID_TIME, 0L);
        tag.remove(EUCLID_POS);

        float amount = event.getAmount();
        // 只统计「这一下打不死他」的情况，否则死亡瞬间也会满足条件
        float remain = player.getHealth() + player.getAbsorptionAmount() - amount;
        if (remain <= 0.0F) return;

        collectDamageType(player, tag, event);
        checkNineChapters(player, tag, amount);
    }

    /** 物种起源：把挨过的伤害类型一种一种记下来，凑满五种。 */
    static void collectDamageType(ServerPlayer player, CompoundTag tag, LivingHurtEvent event) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.ORIGIN)) return;

        String id;
        try {
            id = event.getSource().typeHolder().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("");
        } catch (RuntimeException e) {
            return;
        }
        if (id.isEmpty()) return;

        List<String> seen = new ArrayList<>();
        String raw = tag.getString(ORIGIN_TYPES);
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                if (!part.isBlank()) seen.add(part);
            }
        }
        if (seen.contains(id)) return;

        seen.add(id);
        tag.putString(ORIGIN_TYPES, String.join(",", seen));
        if (seen.size() >= ORIGIN_NEEDED) {
            MilestoneTracker.markReady(player, MilestoneTracker.ORIGIN);
        }
    }

    /** 九章算术：一次承受过半最大生命的伤害而没有死 ——「今有术」。 */
    static void checkNineChapters(ServerPlayer player, CompoundTag tag, float amount) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.NINE_CHAPTERS)) return;
        if (amount >= player.getMaxHealth() * NINE_RATIO) {
            MilestoneTracker.markReady(player, MilestoneTracker.NINE_CHAPTERS);
        }
    }

    // ==================== 击杀判定 ====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!ENABLED) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return;

        CompoundTag tag = player.getPersistentData();

        countDarknessKill(player, tag);
        countDimensionKill(player, tag);
        countMountKill(player, tag);
        countLonelyKill(player, tag);
    }

    /** 理想国：在光照 3 以下的黑暗里杀怪 —— 洞穴里只见影子的人。 */
    private static void countDarknessKill(ServerPlayer player, CompoundTag tag) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.REPUBLIC)) return;
        int brightness = player.level().getMaxLocalRawBrightness(player.blockPosition());
        if (brightness > REPUBLIC_BRIGHTNESS) return;

        int kills = tag.getInt(REPUBLIC_KILLS) + 1;
        tag.putInt(REPUBLIC_KILLS, kills);
        checkRepublicDone(player, tag);
    }

    /** 击杀数够没够（抽出来是为了让自检能单独验证阈值判断）。 */
    static void checkRepublicDone(ServerPlayer player, CompoundTag tag) {
        if (tag.getInt(REPUBLIC_KILLS) >= REPUBLIC_NEEDED) {
            MilestoneTracker.markReady(player, MilestoneTracker.REPUBLIC);
        }
    }

    /** 神曲：下界与末地各杀满十个 —— 地狱、炼狱、天堂。 */
    private static void countDimensionKill(ServerPlayer player, CompoundTag tag) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.DIVINE_COMEDY)) return;

        String key;
        if (player.level().dimension().equals(Level.NETHER)) {
            key = COMEDY_NETHER;
        } else if (player.level().dimension().equals(Level.END)) {
            key = COMEDY_END;
        } else {
            return;
        }
        int kills = tag.getInt(key) + 1;
        tag.putInt(key, kills);
        checkComedyDone(player, tag);
    }

    /** 下界与末地各自满十（同样是给自检留的入口）。 */
    static void checkComedyDone(ServerPlayer player, CompoundTag tag) {
        if (tag.getInt(COMEDY_NETHER) >= COMEDY_NEEDED && tag.getInt(COMEDY_END) >= COMEDY_NEEDED) {
            MilestoneTracker.markReady(player, MilestoneTracker.DIVINE_COMEDY);
        }
    }

    /** 堂吉诃德：坐在坐骑上把怪物当成巨人。 */
    static void countMountKill(ServerPlayer player, CompoundTag tag) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.DON_QUIXOTE)) return;
        if (player.getVehicle() == null) return;

        int kills = tag.getInt(QUIXOTE_KILLS) + 1;
        tag.putInt(QUIXOTE_KILLS, kills);
        if (kills >= QUIXOTE_NEEDED) {
            MilestoneTracker.markReady(player, MilestoneTracker.DON_QUIXOTE);
        }
    }

    /** 百年孤独：方圆 32 格只有自己一个人时杀怪。 */
    static void countLonelyKill(ServerPlayer player, CompoundTag tag) {
        if (tag.getBoolean(MilestoneTracker.DONE + MilestoneTracker.HUNDRED_YEARS)) return;
        if (player.getServer() == null) return;

        double limit = LONELY_RADIUS * LONELY_RADIUS;
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (other.level() != player.level()) continue;
            if (other.distanceToSqr(player) < limit) return;
        }

        int kills = tag.getInt(LONELY_KILLS) + 1;
        tag.putInt(LONELY_KILLS, kills);
        if (kills >= LONELY_NEEDED) {
            MilestoneTracker.markReady(player, MilestoneTracker.HUNDRED_YEARS);
        }
    }

    /** 当前进度（用于指令展示；未列出的条目返回空串）。 */
    public static String progress(Player player, String id) {
        CompoundTag tag = player.getPersistentData();
        return switch (id) {
            case MilestoneTracker.REPUBLIC -> tag.getInt(REPUBLIC_KILLS) + "/" + REPUBLIC_NEEDED;
            case MilestoneTracker.DIVINE_COMEDY ->
                    tag.getInt(COMEDY_NETHER) + "/" + COMEDY_NEEDED + " 下界 · "
                            + tag.getInt(COMEDY_END) + "/" + COMEDY_NEEDED + " 末地";
            case MilestoneTracker.DON_QUIXOTE -> tag.getInt(QUIXOTE_KILLS) + "/" + QUIXOTE_NEEDED;
            case MilestoneTracker.HUNDRED_YEARS -> tag.getInt(LONELY_KILLS) + "/" + LONELY_NEEDED;
            case MilestoneTracker.ORIGIN -> {
                String raw = tag.getString(ORIGIN_TYPES);
                int count = raw.isEmpty() ? 0 : raw.split(",").length;
                yield count + "/" + ORIGIN_NEEDED;
            }
            default -> "";
        };
    }
}
