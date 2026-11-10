package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /ys milestone selftest} 的实现：逐条伪造前置条件，
 * 跑**真实**的监听器，再看「就绪」标记有没有被写下来。
 *
 * <p>和直接 {@code grant} 的区别：grant 跳过了判定逻辑，selftest 走的是判定本身。
 * 所以它能抓到「条件写错 / 阈值写错 / 监听器没挂上 / 时间基准反了」这类问题，
 * 而这些恰恰是 untested 代码最常见的坏法。
 *
 * <p>三条铁律：
 * <ol>
 *   <li>**不污染存档**：每条测试前后都完整快照并回滚 {@code Ms*} 开头的持久化键；</li>
 *   <li>**不发东西**：测试期间 {@link MilestoneTracker#suppressDelivery} 置真，
 *       只写「就绪」不投递，否则戴诅咒的玩家跑一次自检就被塞了一堆残卷；</li>
 *   <li>**不伪造监听器**：事件是手工 new 的，但 {@code onDeath} 等是我们自己的静态方法，
 *       不往 Forge 总线上 post——否则会惊动其他模组的监听器（伪造死亡事件很容易炸）。</li>
 * </ol>
 */
public final class MilestoneSelfTest {

    private MilestoneSelfTest() {
    }

    public enum Status {
        /** 监听器真的把「就绪」写下来了。 */
        PASS,
        /** 前置条件造好了，但监听器没反应——这才是真 bug。 */
        FAIL,
        /** 前置条件没法在当前环境下伪造（等真实等级、真实背包状态等）。 */
        SKIP
    }

    public record Result(String id, String label, Status status, String detail) {
    }

    /** 测试体主动觉得“造不出前置”时抛这个。 */
    private static final class Skip extends RuntimeException {
        Skip(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    private interface Body {
        void run(ServerPlayer player, ServerLevel level);
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(MilestoneTracker.SHEMA, "壹·示马");
        LABELS.put(MilestoneTracker.SILENCE, "贰·无声");
        LABELS.put(MilestoneTracker.THORNS, "叁·荆棘");
        LABELS.put(MilestoneTracker.SABBATH, "肆·歇工");
        LABELS.put(MilestoneTracker.REBELLION, "伍·反叛");
        LABELS.put(MilestoneTracker.BLOOD, "陆·血声");
        LABELS.put(MilestoneTracker.HARP, "柒·挂琴");
        LABELS.put(MilestoneTracker.SHINAR, "捌·示拿");
        LABELS.put(MilestoneTracker.TONGUE, "玖·火舌");
        LABELS.put(MilestoneTracker.VANITY, "拾·虚荣");
        LABELS.put(MilestoneTracker.FATE, "命运");
        LABELS.put(MilestoneTracker.CREATION, "创生");
        LABELS.put(MilestoneTracker.PILLAR, "磐柱");
        LABELS.put(MilestoneTracker.CALAMITY, "灾厄");

        // 第一批九本名著
    }

    public static String labelOf(String id) {
        return LABELS.getOrDefault(id, id);
    }

    // ==================== 入口 ====================

    public static List<Result> run(ServerPlayer player) {
        List<Result> results = new ArrayList<>();
        boolean previousSuppress = MilestoneTracker.suppressDelivery;
        MilestoneTracker.suppressDelivery = true;
        try {
            ServerLevel level = player.serverLevel();

            run(results, player, level, MilestoneTracker.SHEMA,
                    "造成一次击杀：目标最大生命 222，且饰品栏通过豁免表", (p, l) -> {
                        if (!MilestoneTracker.shemaCurioOk(p)) {
                            throw new Skip("§7需先戴一件本模组饰品（且没有未豁免的其他饰品）");
                        }
                        killNow(p, l, mob(l, EntityType.ZOMBIE, 222.0D), players(l, p));
                    });

            run(results, player, level, MilestoneTracker.SILENCE,
                    "受到一次 ≥ 最大生命 120% 的伤害，并活过 5 秒", (p, l) -> {
                        float damage = p.getMaxHealth()
                                * MilestoneTuning.SILENCE_DAMAGE_RATIO + 1.0F;
                        MilestoneTracker.onHurt(new LivingHurtEvent(
                                p, l.damageSources().magic(), damage));
                        // 把「存活判定」的截止时间拨到过去，等于替玩家活过这 5 秒
                        p.getPersistentData().putLong(MilestoneTracker.SILENCE_UNTIL,
                                MilestoneTracker.clock(p) - 1L);
                        MilestoneTracker.runPeriodicChecks(p);
                    });

            run(results, player, level, MilestoneTracker.THORNS,
                    "经验等级 ≥ 100", (p, l) -> {
                        int original = p.experienceLevel;
                        try {
                            p.experienceLevel = Math.max(original,
                                    MilestoneTuning.THORNS_MIN_LEVEL);
                            MilestoneTracker.runPeriodicChecks(p);
                        } finally {
                            p.experienceLevel = original;
                        }
                    });

            run(results, player, level, MilestoneTracker.SABBATH,
                    "连续 3 天没有破坏方块", (p, l) -> {
                        p.getPersistentData().putLong(MilestoneTracker.LAST_BREAK,
                                MilestoneTracker.clock(p)
                                        - (long) MilestoneTuning.SABBATH_DAYS
                                        * MilestoneTuning.TICKS_PER_DAY - 1L);
                        MilestoneTracker.runPeriodicChecks(p);
                    });

            run(results, player, level, MilestoneTracker.REBELLION,
                    "在 8 格内治愈一名僵尸村民", (p, l) -> {
                        ZombieVillager zombie = mob(l, EntityType.ZOMBIE_VILLAGER, 20.0D);
                        zombie.moveTo(p.getX(), p.getY(), p.getZ());
                        Villager outcome = mob(l, EntityType.VILLAGER, 20.0D);
                        MilestoneTracker.onConversion(
                                new LivingConversionEvent.Post(zombie, outcome));
                    });

            run(results, player, level, MilestoneTracker.BLOOD,
                    "累计 1000 击杀、其中 5 个首领", (p, l) -> {
                        CompoundTag tag = p.getPersistentData();
                        tag.putInt(MilestoneTracker.KILLS,
                                MilestoneTuning.BLOOD_MIN_KILLS - 1);
                        tag.putInt(MilestoneTracker.BOSS_KILLS,
                                MilestoneTuning.BLOOD_MIN_BOSS_KILLS - 1);
                        killNow(p, l, mob(l, EntityType.WITHER, 300.0D), players(l, p));                        MilestoneTracker.runPeriodicChecks(p);
                    });

            run(results, player, level, MilestoneTracker.HARP,
                    "同一只宠物活过 7 天", (p, l) -> {
                        Wolf wolf = mob(l, EntityType.WOLF, 20.0D);
                        if (!l.addFreshEntity(wolf)) {
                            throw new Skip("§7实体加入世界失败（区块未加载？）");
                        }
                        try {
                            CompoundTag pet = new CompoundTag();
                            pet.putString("Id", wolf.getUUID().toString());
                            pet.putLong("Born", MilestoneTracker.clock(p)
                                    - (long) MilestoneTuning.HARP_DAYS
                                    * MilestoneTuning.TICKS_PER_DAY - 1L);
                            ListTag list = new ListTag();
                            list.add(pet);
                            p.getPersistentData().put(MilestoneTracker.PETS, list);
                            MilestoneTracker.runPeriodicChecks(p);
                        } finally {
                            wolf.discard();
                        }
                    });

            run(results, player, level, MilestoneTracker.SHINAR,
                    "击杀监守者", (p, l) -> {
                        killNow(p, l, mob(l, EntityType.WARDEN, 500.0D), players(l, p));
                    });

            run(results, player, level, MilestoneTracker.TONGUE,
                    "同一次爆炸连杀 7 名敌人", (p, l) -> {
                        DamageSource explosion = l.damageSources().explosion(p, p);
                        for (int i = 0; i < MilestoneTuning.TONGUE_MIN_KILLS; i++) {
                            killNow(p, l, mob(l, EntityType.ZOMBIE, 20.0D), explosion);
                        }
                    });

            run(results, player, level, MilestoneTracker.VANITY,
                    "背包 / 护甲 / 副手全空时击杀最大生命 300 的敌人", (p, l) -> {
                        if (!inventoryEmpty(p)) {
                            throw new Skip("§7请先清空背包、护甲与副手");
                        }
                        killNow(p, l, mob(l, EntityType.ZOMBIE, 300.0D), players(l, p));
                    });

            run(results, player, level, MilestoneTracker.FATE,
                    "血量低于 10% 时被一次 > 50% 最大生命的治疗拉起", (p, l) -> {
                        float original = p.getHealth();
                        try {
                            p.setHealth(Math.max(0.5F, p.getMaxHealth() * 0.05F));
                            MilestoneTracker.onHeal(new LivingHealEvent(
                                    p, p.getMaxHealth()
                                    * MilestoneTuning.FATE_HEAL_RATIO + 1.0F));
                        } finally {
                            p.setHealth(original);
                        }
                    });

            run(results, player, level, MilestoneTracker.CREATION,
                    "一秒内累计治疗 ≥ 100", (p, l) -> {
                        float half = MilestoneTuning.CREATION_HEAL_PER_SECOND / 2.0F + 1.0F;
                        MilestoneTracker.onHeal(new LivingHealEvent(p, half));
                        MilestoneTracker.onHeal(new LivingHealEvent(p, half));
                        MilestoneTracker.runPeriodicChecks(p);
                    });

            run(results, player, level, MilestoneTracker.PILLAR,
                    "一场战斗内承受 ≥ 496 伤害并活下来", (p, l) -> {
                        MilestoneTracker.onHurt(new LivingHurtEvent(
                                p, l.damageSources().magic(),
                                MilestoneTuning.PILLAR_MIN_DAMAGE_TAKEN + 1.0F));
                        // 把战斗窗口拨到过去，等于替玩家打完了这一场
                        p.getPersistentData().putLong(MilestoneTracker.COMBAT_UNTIL,
                                MilestoneTracker.clock(p) - 1L);
                        MilestoneTracker.runPeriodicChecks(p);
                    });

            run(results, player, level, MilestoneTracker.CALAMITY,
                    "一场战斗内击杀 ≥ 50 名敌人", (p, l) -> {
                        DamageSource source = players(l, p);
                        for (int i = 0; i < MilestoneTuning.CALAMITY_MIN_KILLS; i++) {
                            killNow(p, l, mob(l, EntityType.ZOMBIE, 20.0D), source);
                        }
                        p.getPersistentData().putLong(MilestoneTracker.COMBAT_UNTIL,
                                MilestoneTracker.clock(p) - 1L);
                        MilestoneTracker.runPeriodicChecks(p);
                    });

        } finally {
            MilestoneTracker.suppressDelivery = previousSuppress;
        }
        return results;
    }

    // ==================== 单条测试的执行壳 ====================

    private static void run(List<Result> results, ServerPlayer player, ServerLevel level,
                            String id, String scenario, Body body) {
        String label = labelOf(id);
        Map<String, Tag> snapshot = snapshot(player);
        clearMark(player, id);
        Result result;
        try {
            body.run(player, level);
            boolean ready = MilestoneTracker.isReady(player, id);
            result = ready
                    ? new Result(id, label, Status.PASS, "§7监听器已写入就绪（" + scenario + "）")
                    : new Result(id, label, Status.FAIL,
                            "§c条件伪造完成，但没有记录就绪（" + scenario + "）");
        } catch (Skip skip) {
            result = new Result(id, label, Status.SKIP, skip.getMessage());
        } catch (Exception e) {
            result = new Result(id, label, Status.FAIL,
                    "§c抛异常：" + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            restore(player, snapshot);
        }
        results.add(result);
    }

    // ==================== 工具 ====================

    private static DamageSource players(ServerLevel level, ServerPlayer player) {
        return level.damageSources().playerAttack(player);
    }

    /**
     * 直接调用我们自己的死亡监听器，不往 Forge 总线 post。
     *
     * <p>伪造的死亡事件如果广播出去，其他模组（Goety、RevelationFix 等）的监听器
     * 会拿到一个从没真正死过的实体，动辄 NPE 或重复结算掉落。
     */
    private static void killNow(ServerPlayer player, ServerLevel level,
                             LivingEntity victim, DamageSource source) {
        MilestoneTracker.onDeath(new LivingDeathEvent(victim, source));
    }

    private static <T extends LivingEntity> T mob(ServerLevel level, EntityType<T> type,
                                                 double maxHealth) {
        T entity = type.create(level);
        if (entity == null) {
            throw new IllegalStateException("创建实体失败：" + type);
        }
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
        entity.setHealth(entity.getMaxHealth());
        return entity;
    }

    /** 与 {@link MilestoneTracker} 里那份判定同义：物品栏、护甲、副手必须全空。 */
    private static boolean inventoryEmpty(ServerPlayer player) {
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty()) return false;
        }
        for (var stack : player.getInventory().armor) {
            if (!stack.isEmpty()) return false;
        }
        for (var stack : player.getInventory().offhand) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    private static void clearMark(ServerPlayer player, String id) {
        CompoundTag tag = player.getPersistentData();
        tag.remove(MilestoneTracker.READY + id);
        tag.remove(MilestoneTracker.DONE + id);
    }

    private static Map<String, Tag> snapshot(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        Map<String, Tag> copy = new LinkedHashMap<>();
        for (String key : new ArrayList<>(tag.getAllKeys())) {
            if (key.startsWith("Ms")) {
                copy.put(key, tag.get(key).copy());
            }
        }
        return copy;
    }

    private static void restore(ServerPlayer player, Map<String, Tag> snapshot) {
        CompoundTag tag = player.getPersistentData();
        for (String key : new ArrayList<>(tag.getAllKeys())) {
            if (key.startsWith("Ms")) {
                tag.remove(key);
            }
        }
        snapshot.forEach(tag::put);
    }

    /** 供指令输出用：按十诫顺序排好的结果。 */
    public static List<Result> sorted(List<Result> results) {
        List<Result> copy = new ArrayList<>(results);
        copy.sort(Comparator.comparingInt(r -> MilestoneTracker.ALL.indexOf(r.id())));
        return copy;
    }
}
