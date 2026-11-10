package com.example.examplemod;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 《吉尔伽美什史诗》—— 天地乖离开辟之星（Enuma Elish）。
 *
 * <p>作者定稿（2026-11-06）：
 * 「每一级村庄英雄为你提升 15% 生命值和 15% 攻击力，手持剑类武器时 Shift+R，
 * 可释放一次 Enuma Elish，对路径上的所有敌人造成一次 1000% 攻击力的伤害
 * （普通物理伤害来源是玩家，如果受到伤害者有限伤，则对他改为造成限伤乘以十的伤害），冷却时间 600 秒」。
 *
 * <p>2026-11-07 作者补充：「按住按键之后持续五秒钟，才能开始释放」——长按由客户端
 * {@code client/GilgameshClient} 计时，满 5 秒才发 {@code EnumaCastPacket}。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GilgameshHandler {

    private static final Logger LOG = LogUtils.getLogger();

    /** 每级村庄英雄给的加成（作者定稿：15% 生命 + 15% 攻击）。 */
    private static final double HERO_BONUS_PER_LEVEL = 0.15D;

    /** 10000% 攻击力（作者 2026-11-07 从 1000% 上调）。 */
    private static final double DAMAGE_MULTIPLIER = 100.0D;

    /** 限伤目标的补偿倍率：按「实际能打掉的血量 × 10」补一笔（作者定稿原文）。 */
    private static final double LIMIT_MULTIPLIER = 10.0D;

    /** 剑气在世界上「留痕」的时长（刻）：够红环从面前推满整条剑气。 */
    private static final int BEAM_TICKS = 45;

    /** 释放瞬间那圈冲击波的半径（格）。 */
    private static final double RELEASE_RING_RADIUS = 4.0D;

    /** 推进中的红环：每刻沿视线前进多少格。 */
    private static final double TRAVEL_RING_SPEED = 2.6D;

    /** 相邻两道红环之间隔多少格。 */
    private static final double TRAVEL_RING_SPACING = 6.0D;

    /** 红环刚离开视野时的半径（格）。 */
    private static final double TRAVEL_RING_BASE_RADIUS = 1.6D;

    /** 每前进 1 格，半径涨多少格（越远越大）。作者 2026-11-07 要求「降低环扩大的幅度」。 */
    private static final double TRAVEL_RING_GROW = 0.25D;

    /** 一道红环由多少个粒子点组成（会随半径变大而加密）。 */
    private static final int TRAVEL_RING_POINTS = 32;

    /** 环最前面多少格之内不画——避免一堆粒子堵在脸前挡住视线。 */
    private static final double TRAVEL_RING_START = 2.5D;

    /** 偏红的尘埃（粒子表里没有红色烟雾，只能自己调颜色）。 */
    private static final net.minecraft.core.particles.DustParticleOptions RED_DUST =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.85F, 0.06F, 0.06F), 2.6F);

    /** 暗红的尘埃，给裂缝边缘用。 */
    private static final net.minecraft.core.particles.DustParticleOptions DARK_RED_DUST =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45F, 0.02F, 0.02F), 3.2F);

    /** 剑气长度（格）——作者 2026-11-07 再次要求「最远距离增大」，64 → 96。 */
    private static final double BEAM_LENGTH = 96.0D;

    /** 剑气半径（格）——判定用，作者要求「攻击半径也高一点」。 */
    private static final double BEAM_RADIUS = 5.0D;

    /** 一次释放最多改多少格方块：防低配机卡顿。 */
    private static final int CARVE_LIMIT = 70000;

    /** 每刻最多改多少格：作者 2026-11-07 实测「性能好像跟得上」，从 1600 提到 3000。 */
    private static final int CARVE_PER_TICK = 3000;

    /** 破坏从中多远开始：0 = 连眼前那一面墙一起凿（粒子仍在 {@link #TRAVEL_RING_START} 之后才画）。 */
    private static final double CARVE_START = 0.0D;

    /** 排队中的地形破坏任务（先算出坐标、再分刻执行）。 */
    private static final java.util.Deque<CarveTask> CARVE_TASKS = new java.util.ArrayDeque<>();

    /** 冷却（刻）：600 秒。 */
    private static final long COOLDOWN_TICKS = 600L * 20L;

    private static final String TAG_COOLDOWN = "YanEnumaCooldown";

    private static final UUID ID_HEALTH = BookAttr.id("classic_gilgamesh_hero_health");
    private static final UUID ID_ATTACK = BookAttr.id("classic_gilgamesh_hero_attack");

    private GilgameshHandler() {
    }

    // ------------------------------------------------------------------
    // 一、村庄英雄：每级 +15% 生命上限 / +15% 攻击力
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.GILGAMESH)) {
            BookAttr.set(player, Attributes.MAX_HEALTH, ID_HEALTH, 0.0D);
            BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, 0.0D);
            return;
        }
        MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        int level = hero == null ? 0 : hero.getAmplifier() + 1;
        double bonus = HERO_BONUS_PER_LEVEL * level;
        BookAttr.set(player, Attributes.MAX_HEALTH, ID_HEALTH, bonus);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, bonus);
    }

    // ------------------------------------------------------------------
    // 二、Enuma Elish
    // ------------------------------------------------------------------

    /**
     * 释放一次 Enuma Elish。由 {@code EnumaCastPacket} 在服务端调用。
     *
     * <p>五秒长按由客户端保证，这里只做服务端能验证的事：有没有佩戴、手里是不是剑、冷却好没好。
     */
    public static void cast(ServerPlayer player) {
        if (!WornCache.wears(player, ClassicBook.GILGAMESH)) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("message.yanscriptures.enuma.no_book"), true);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ItemTags.SWORDS) && !(held.getItem() instanceof net.minecraft.world.item.SwordItem)) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("message.yanscriptures.enuma.no_sword"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long ready = player.getPersistentData().getLong(TAG_COOLDOWN);
        if (now < ready) {
            long left = (ready - now + 19L) / 20L;
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("message.yanscriptures.enuma.cooldown", left), true);
            return;
        }
        player.getPersistentData().putLong(TAG_COOLDOWN, now + COOLDOWN_TICKS);

        // ---- 路径判定 ----
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (attack * DAMAGE_MULTIPLIER);

        Set<UUID> hit = new LinkedHashSet<>();
        // 从 1 格起就算：贴着玩家站的目标也要吃到，不能留「脚下安全区」。
        for (double step = 1.0D; step <= BEAM_LENGTH; step += 2.0D) {
            // 判定范围跟着红环一起变宽：看到多大就打到多大（作者 2026-11-07 要求两者重合）。
            double radius = coneRadius(step);
            Vec3 point = eye.add(dir.scale(step));
            AABB box = new AABB(point.x - radius, point.y - radius, point.z - radius,
                    point.x + radius, point.y + radius, point.z + radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (target == player || !target.isAlive() || target.isSpectator()) {
                    continue;
                }
                if (target instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
                    continue;
                }
                if (hit.add(target.getUUID())) {
                    strike(player, target, damage);
                }
            }
        }

        // ---- 破坏地形（不掉落，纯演出）----
        int carved = carve(level, eye, dir);

        // ---- 演出 ----
        playBeam(level, player, eye, dir);
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("message.yanscriptures.enuma.cast", hit.size()), true);
        LOG.info("《吉尔伽美什史诗》：Enuma Elish 命中 {} 个目标，单笔伤害 {}，排队凿开 {} 格方块",
                hit.size(), damage, carved);
    }

    /** 剑气在某个距离上的半径：与同距离的红环半径完全一致，所以「看到的范围」就是「打到的范围」。 */
    private static double coneRadius(double along) {
        return TRAVEL_RING_BASE_RADIUS + along * TRAVEL_RING_GROW;
    }

    /**
     * 沿剑气把地形凿开：石头、木头、树叶、草、泥土、沙、水、岩浆都算。
     *
     * <p>作者 2026-11-07：不能只凿「环」，环内部那块空白也要凿掉；而且不能有残留。
     * 早先按 1.5 格网格在圆盘里撒点，会漏掉格子——表现出来就是「被竖着切了一刀」的竖条残留。
     * 现在改成**逐格判定**：把整个锥体的包围盒按整数坐标扫一遍，每个方块中心到视线的垂距
     * 与 {@link #coneRadius(double)} 比较，命中就凿，所以不会再有采样缝隙。
     *
     * <p>整个锥体几万格，同一刻全改完必然卡（还会触发大量区块重发）。因此这里只**算出坐标**、
     * 按「离玩家由近到远」排好序挂进 {@link #CARVE_TASKS}，由 {@link #onServerTick} 每刻改
     * {@link #CARVE_PER_TICK} 格，表现为「剑气扫过、石头随之消失」。总量有 {@link #CARVE_LIMIT} 兜底。
     */
    private static int carve(ServerLevel level, Vec3 eye, Vec3 dir) {
        double maxRadius = coneRadius(BEAM_LENGTH);
        Vec3 end = eye.add(dir.scale(BEAM_LENGTH));
        int minX = (int) Math.floor(Math.min(eye.x, end.x) - maxRadius);
        int maxX = (int) Math.ceil(Math.max(eye.x, end.x) + maxRadius);
        int minY = (int) Math.floor(Math.min(eye.y, end.y) - maxRadius);
        int maxY = (int) Math.ceil(Math.max(eye.y, end.y) + maxRadius);
        int minZ = (int) Math.floor(Math.min(eye.z, end.z) - maxRadius);
        int maxZ = (int) Math.ceil(Math.max(eye.z, end.z) + maxRadius);
        java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        scan:
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (positions.size() >= CARVE_LIMIT) {
                        break scan;
                    }
                    Vec3 offset = new Vec3(x + 0.5D, y + 0.5D, z + 0.5D).subtract(eye);
                    double along = offset.dot(dir);
                    if (along < CARVE_START || along > BEAM_LENGTH) {
                        continue;
                    }
                    if (offset.subtract(dir.scale(along)).length() > coneRadius(along)) {
                        continue;
                    }
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (!breakable(level.getBlockState(pos))) {
                        continue;
                    }
                    positions.add(pos);
                }
            }
        }
        if (positions.isEmpty()) {
            return 0;
        }
        // 由近到远凿：先塌眼前，再塌远处，看起来像剑气一路推出去。
        positions.sort(java.util.Comparator.comparingDouble(
                pos -> pos.distToCenterSqr(eye.x, eye.y, eye.z)));
        CARVE_TASKS.add(new CarveTask(level, positions));
        return positions.size();
    }

    /** 一批待凿的坐标：每刻从 cursor 往后推 {@link #CARVE_PER_TICK} 格。 */
    private static final class CarveTask {
        private final ServerLevel level;
        private final java.util.List<net.minecraft.core.BlockPos> positions;
        private int cursor;

        private CarveTask(ServerLevel level, java.util.List<net.minecraft.core.BlockPos> positions) {
            this.level = level;
            this.positions = positions;
        }
    }

    /** 把排队的地形按每刻的预算凿掉。 */
    private static void drainCarve() {
        int budget = CARVE_PER_TICK;
        while (budget > 0 && !CARVE_TASKS.isEmpty()) {
            CarveTask task = CARVE_TASKS.peek();
            int done = 0;
            while (done < budget && task.cursor < task.positions.size()) {
                net.minecraft.core.BlockPos pos = task.positions.get(task.cursor++);
                done++;
                if (!breakable(task.level.getBlockState(pos))) {
                    continue;
                }
                task.level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
            budget -= done;
            if (task.cursor >= task.positions.size()) {
                CARVE_TASKS.poll();
            }
            if (done == 0) {
                break;
            }
        }
    }

    /** 哪些方块允许被剑气抹掉：石头、木头、树叶、草、泥土、沙、雪、水、岩浆。 */
    private static boolean breakable(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        net.minecraft.world.level.block.Block block = state.getBlock();
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                || state.is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.LEAVES)
                || state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.SAND)
                || state.is(net.minecraft.tags.BlockTags.SNOW)
                || state.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)
                || block instanceof net.minecraft.world.level.block.BushBlock
                || block instanceof net.minecraft.world.level.block.LiquidBlock;
    }

    /**
     * 对单个目标结算 1000% 攻击力。
     *
     * <p>「如果受到伤害者有限伤，则对他改为造成限伤乘以十的伤害」——限伤值没有接口可读，
     * 所以用一个探针：先按 1000% 打一次，量出**实际掉了多少血**。如果掉血明显少于应掉的血量
     * （不足一半），说明这一击被上限砍过，那个「实际掉血量」就是它的有效上限，再补一笔
     * 「上限 × 10」的伤害。
     */
    private static void strike(ServerPlayer player, LivingEntity target, float damage) {
        float before = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().playerAttack(player), damage);
        float lost = before - target.getHealth();
        if (lost > 0.0F && lost < damage * 0.5F && target.isAlive()) {
            // 被限伤了：按「限伤 × 10」再补一笔。
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().playerAttack(player), (float) (lost * LIMIT_MULTIPLIER));
            LOG.info("《吉尔伽美什史诗》：{} 疑似有限伤（应掉 {}，实掉 {}）→ 补 {} 点",
                    target.getName().getString(), damage, lost, lost * LIMIT_MULTIPLIER);
        }
    }

    // ------------------------------------------------------------------
    // 剑气演出：释放瞬间登记一条「留痕」，之后每刻重绘，持续 1 秒
    // ------------------------------------------------------------------

    /** 一条还在持续的剑气：起点、方向、开始与结束时刻。 */
    private record Beam(ServerLevel level, Vec3 eye, Vec3 dir, long startTick, long endTick) {
    }

    private static final java.util.List<Beam> BEAMS = new java.util.ArrayList<>();

    /**
     * 登记一条剑气并画出第一帧。
     *
     * <p>只画一帧不行——粒子寿命不到一秒、瞬间炸开的一团在游戏里几乎看不见，
     * 作者 2026-11-07 反馈「完全没有看到发射的特效」就是这个问题。
     * 现在剑气在 {@link #BEAM_TICKS} 刻内每刻重绘，看起来才是一道留在空中的裂痕。
     */
    private static void playBeam(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 dir) {
        long now = level.getGameTime();
        // 起手不放在脸前：作者 2026-11-07 反馈「触发的瞬间大量粒子堆在角色面前挡住特效」。
        // 闪光与起爆点都前移到视线前方 5~7 格，脸前只留很薄的一层。
        Vec3 flash = eye.add(dir.scale(5.0D));
        Vec3 burst = eye.add(dir.scale(7.0D));
        level.sendParticles(ParticleTypes.FLASH, flash.x, flash.y, flash.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, burst.x, burst.y, burst.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 起手那一道环：垂直于视线、半径 3 格，放在前方 3 格处，不糊脸。
        Vec3 helper = Math.abs(dir.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();
        Vec3 center = eye.add(dir.scale(3.0D));
        for (int i = 0; i < 48; i++) {
            double angle = i * (Math.PI * 2.0D / 48.0D);
            Vec3 point = center.add(u.scale(Math.cos(angle) * RELEASE_RING_RADIUS)).add(v.scale(Math.sin(angle) * RELEASE_RING_RADIUS));
            level.sendParticles(RED_DUST, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.playSound(null, burst.x, burst.y, burst.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.5F);

        Beam beam = new Beam(level, eye, dir, now, now + BEAM_TICKS);
        BEAMS.add(beam);
        emitBeam(beam, now);
    }

    /** 每刻把所有还在持续的剑气重画一遍，并按预算推进地形破坏。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!BEAMS.isEmpty()) {
            BEAMS.removeIf(beam -> {
                long now = beam.level().getGameTime();
                if (now > beam.endTick()) {
                    return true;
                }
                emitBeam(beam, now);
                return false;
            });
        }
        drainCarve();
    }

    /**
     * 沿视线往前推的红环：从面前出现，一路推进、一路变大——作者 2026-11-07 要的「不断前进并且变大的环」。
     *
     * <p>同一刻会有约 {@code BEAM_LENGTH / TRAVEL_RING_SPACING} 道环同时存在于不同距离上，
     * 每道环的半径 = 起始半径 + 距离 × {@link #TRAVEL_RING_GROW}，看起来就像一圈圈红浪往前扩。
     * 环面垂直于视线：视线与任意垂向量叉积得到正交基 u、v，再在 u-v 平面取圆周。
     */
    private static void emitTravelRings(Beam beam, long now) {
        double age = now - beam.startTick();
        Vec3 dir = beam.dir();
        Vec3 helper = Math.abs(dir.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();
        int ringCount = (int) Math.ceil(BEAM_LENGTH / TRAVEL_RING_SPACING) + 2;
        for (int ring = 0; ring < ringCount; ring++) {
            double along = age * TRAVEL_RING_SPEED - ring * TRAVEL_RING_SPACING;
            if (along < TRAVEL_RING_START || along > BEAM_LENGTH) {
                continue;
            }
            double radius = TRAVEL_RING_BASE_RADIUS + along * TRAVEL_RING_GROW;
            Vec3 center = beam.eye().add(dir.scale(along));
            // 半径越大、周长越长，取样点也要跟着加密，否则会变成「一圈点阵」。
            int points = Math.max(TRAVEL_RING_POINTS, Math.min(64, (int) Math.round(radius * 3.2D)));
            for (int i = 0; i < points; i++) {
                double angle = i * (Math.PI * 2.0D / points);
                Vec3 point = center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
                beam.level().sendParticles(RED_DUST, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
                if (i % 4 == 0) {
                    beam.level().sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                            1, 0.02D, 0.02D, 0.02D, 0.0D);
                }
                if (i % 8 == 0) {
                    beam.level().sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z,
                            1, 0.02D, 0.02D, 0.02D, 0.05D);
                }
            }
        }
    }

    /** 龙息为骨、传送门粒子为缝；越到末尾越散，最后化成浓烟。 */
    private static void emitBeam(Beam beam, long now) {
        ServerLevel level = beam.level();
        Vec3 eye = beam.eye();
        Vec3 dir = beam.dir();
        double life = Math.max(0.0D, (beam.endTick() - now) / (double) BEAM_TICKS);
        for (double step = TRAVEL_RING_START; step <= BEAM_LENGTH; step += 0.75D) {
            Vec3 point = eye.add(dir.scale(step));
            level.sendParticles(ParticleTypes.DRAGON_BREATH, point.x, point.y, point.z,
                    2, 0.10D, 0.10D, 0.10D, 0.01D);
            level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z,
                    2, 0.20D, 0.20D, 0.20D, 0.10D + 0.5D * life);
            // 血色基调：中心一道亮红、边缘一层暗红，火光沿裂缝跳动。
            level.sendParticles(RED_DUST, point.x, point.y, point.z, 2, 0.12D, 0.12D, 0.12D, 0.0D);
            if (step % 1.5D < 0.5D) {
                level.sendParticles(DARK_RED_DUST, point.x, point.y, point.z, 2, 0.55D, 0.55D, 0.55D, 0.0D);
            }
            if (step % 3.0D < 0.5D) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z,
                        3, 0.25D, 0.25D, 0.25D, 0.05D);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, point.x, point.y, point.z,
                        3, 0.30D, 0.30D, 0.30D, 0.02D);
            }
            if (step % 6.0D < 0.5D) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z,
                        6, 0.3D, 0.3D, 0.3D, 0.1D);
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                        4, 0.25D, 0.25D, 0.25D, 0.03D);
            }
            if (step % 10.0D < 0.5D) {
                level.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z,
                        1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
        Vec3 end = eye.add(dir.scale(BEAM_LENGTH));
        emitTravelRings(beam, now);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (life > 0.85D) {
            level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 40, 1.8D, 1.2D, 1.8D, 0.02D);
            level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 30, 1.4D, 1.0D, 1.4D, 0.05D);
            level.sendParticles(RED_DUST, end.x, end.y, end.z, 24, 1.6D, 1.2D, 1.6D, 0.0D);
            level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 6, 1.0D, 0.6D, 1.0D, 0.0D);
        }
    }

    /** 供客户端显示冷却读秒用。 */
    public static long remainingCooldown(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0L;
        }
        long ready = player.getPersistentData().getLong(TAG_COOLDOWN);
        return Math.max(0L, ready - level.getGameTime());
    }

    /** 属性句柄（占位，避免 IDE 提示未使用；真正写入由 BookAttr 完成）。 */
    static Attribute healthAttribute() {
        return Attributes.MAX_HEALTH;
    }
}
