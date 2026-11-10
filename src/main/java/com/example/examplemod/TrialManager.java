package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「启示试炼」—— 安息仪式开启的四波连续 Boss 战。
 *
 * <h2>流程</h2>
 * <pre>
 * 安息仪式（data/yanscriptures/recipes/rite_of_revelation.json）
 *   → 生成 TrialSummonerEntity → 调用 {@link #start}
 *   → 第 1 波：红石巨兽 + 末影守望者 + 灾厄宰相
 *   → 30 秒间隔
 *   → 第 2 波：使徒 ×2 + 凋灵 ×4
 *   → 30 秒间隔
 *   → 第 3 波：使徒 ×4 + 寒冰隐士
 *   → 30 秒间隔
 *   → 第 4 波：亚波伦 + 使徒 ×2 + 寒冰隐士
 *   → 通关：发放带 {@code TrialCleared:1b} 的启示残卷
 * </pre>
 *
 * <h2>规则</h2>
 * <ul>
 *   <li><b>限伤抬到 200</b>：试炼期间临时抬高各方 Boss 的单次伤害上限，结束后恢复。</li>
 *   <li><b>敌人互不伤害</b>：只拦「试炼生物 ↔ 试炼生物」，玩家的召唤物照常生效。</li>
 *   <li><b>失败保留残卷</b>：玩家死亡则清场，并补回一张普通启示残卷，可以重新交素材再来。</li>
 * </ul>
 *
 * <h2>为什么不引用 Goety 的类</h2>
 * Goety 是 runtimeOnly，编译期不可见。所以实体一律按注册名取（{@link ForgeRegistries#ENTITY_TYPES}），
 * 模组没装时对应波次会跳过而不是崩溃。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrialManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 波次间隔：30 秒。 */
    private static final int WAVE_GAP_TICKS = 30 * 20;
    /** 开场过渡：10 秒。让第一段经文浮出来，再放第一波。 */
    private static final int INTRO_TICKS = 10 * 20;
    /** 字幕换行：每行停留时长由调用方指定；重发间隔固定 2 秒（续命用）。 */
    private static final int SUBTITLE_REFRESH_TICKS = 40;
    /** 开场四行的停留时长：4 × 50 = 200 tick，正好铺满 10 秒的过渡。 */
    private static final int SUBTITLE_HOLD_INTRO = 50;
    /** 波次之间：每行 8 秒，两行共 16 秒（间隔 30 秒，留出空白）。 */
    private static final int SUBTITLE_HOLD_GAP = 160;
    /** 亚波伦出场：每行 6 秒。 */
    private static final int SUBTITLE_HOLD_DRAMATIC = 120;
    /** 通关收尾：每行 4 秒，节奏收得更紧，像余音一样快速掠过。 */
    private static final int SUBTITLE_HOLD_FINALE = 80;
    /** 五段经文各自的行数（逐行轮播，需要提前知道行数）。 */
    private static final int[] VERSE_LINE_COUNT = {2, 2, 2, 2, 4};
    /** 试炼期间的单次伤害上限。 */
    private static final double TRIAL_DAMAGE_CAP = 200.0D;
    /** 生成半径。 */
    private static final int SPAWN_RADIUS = 7;

    /** 波次里的一个单位。{@code spawner} 为 true 表示它是「生成器」而不是战斗单位。 */
    private record Unit(String id, boolean spawner) {
    }

    private static Unit u(String id) {
        return new Unit(id, false);
    }

    /** 生成器：落地后自己把真正的 Boss 放出来然后消失，不能当成战斗单位计数。 */
    private static Unit spawner(String id) {
        return new Unit(id, true);
    }

    /** 每一波的实体清单。 */
    private static final List<List<Unit>> WAVES = List.of(
            List.of(u("goety:hostile_redstone_monstrosity"), u("goety:ender_keeper"), u("goety:vizier")),
            List.of(u("goety:apostle"), u("goety:apostle"),
                    u("minecraft:wither"), u("minecraft:wither"),
                    u("minecraft:wither"), u("minecraft:wither")),
            List.of(u("goety:apostle"), u("goety:apostle"), u("goety:apostle"), u("goety:apostle"),
                    u("revelationfix:ice_hermit")),
            // 亚波伦由启示录的生成器实体延后放出。生成器放完 Boss 就会自毁，
            // 所以它不能进 mobs 集合，否则它一消失就会误判「本波已清空」。
            List.of(spawner("goety_revelation:summon_apollyon"),
                    u("goety:apostle"), u("goety:apostle"),
                    u("revelationfix:ice_hermit"))
    );

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TrialManager() {
    }

    // ==================== 会话 ====================

    private static final class Session {
        final UUID playerId;
        final ResourceLocation dimension;
        final BlockPos center;
        final Set<UUID> mobs = new HashSet<>();
        int wave = -1;
        int timer = 0;
        boolean fighting;

        /** 是否还在等「延迟出场的 Boss」（亚波伦由生成器实体延后放置）。 */
        boolean awaitingBoss;
        /** 等待已持续的 tick 数，超时兜底。 */
        int awaitTicks;
        /** 延迟出场的 Boss 实体 UUID；未出现时为 null。 */
        UUID bossId;

        /**
         * 当前要显示的字幕行。逐行发送 —— 动作栏对换行支持很差。
         * 每行<b>只显示一次</b>，但停留时间较长（见 {@link #subtitleHoldTicks}）。
         */
        java.util.List<Component> subtitle = java.util.List.of();
        int subtitleIndex;
        /** 当前这一行还要停留多久。 */
        int subtitleHoldTicks;
        /** 每行统一停留时长。 */
        int subtitleHoldPerLine;
        /** 距离下次重发同一行还有多久（动作栏会自动淡出，需要续命）。 */
        int subtitleRefresh;

        /** 通关后的收尾阶段：让最后一段经文播完再结束会话。 */
        boolean closing;
        int closingTicks;

        Session(UUID playerId, ResourceLocation dimension, BlockPos center) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.center = center;
        }
    }

    /** 等待延迟 Boss 出场的上限：60 秒。超时就当作没出现，避免整场卡死。 */
    private static final int AWAIT_BOSS_TIMEOUT = 60 * 20;
    /** 使徒可选头衔数量（Goety 的 title.goety.0 ~ title.goety.11，共 12 个）。 */
    private static final int TITLE_COUNT = 12;
    /** 查找延迟 Boss 的半径。 */
    private static final double BOSS_SEARCH_RADIUS = 48.0D;

    // ==================== 开场 ====================

    /** 由 {@link TrialSummonerEntity} 调用。 */
    /** 玩家是否正在试炼里（命令用来避免「重复开启还报成功」）。 */
    public static boolean isRunning(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void start(ServerLevel level, Player player, BlockPos center) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return; // 已经在试炼里，忽略重复触发
        }

        Session session = new Session(player.getUUID(), level.dimension().location(), center);
        SESSIONS.put(player.getUUID(), session);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            YanEventTrigger.INSTANCE.trigger(serverPlayer, "trial_start");
        }
        DamageCapControl.raise(TRIAL_DAMAGE_CAP);
        suppressMobGriefing(level);

        // 开场：第一段经文 + 两条规则提示，全部走动作栏字幕轮播；
        // 同时把这第一组内容也留一份在聊天栏，方便玩家回看（后续经文只走字幕）。
        session.timer = INTRO_TICKS;

        java.util.List<Component> intro = new java.util.ArrayList<>(verseLines(1));
        Component grace1 = Component.translatable("message.yanscriptures.trial.grace.1")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
        Component grace2 = Component.translatable("message.yanscriptures.trial.grace.2")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
        intro.add(grace1);
        intro.add(grace2);
        setSubtitle(session, player, intro, SUBTITLE_HOLD_INTRO);

        for (Component line : intro) {
            player.displayClientMessage(line, false);
        }
    }

    /**
     * 设置字幕（动作栏，物品栏正上方）。
     * <p><b>每行只显示一次</b>，但停留 {@code holdPerLine} 这么久（见 {@link #tickSubtitle}）。
     * <p>为什么逐行：动作栏对 {@code \n} 支持很差，多行会挤在一起或溢出。
     */
    private static void setSubtitle(Session session, Player player,
                                    java.util.List<Component> lines, int holdPerLine) {
        session.subtitle = lines;
        session.subtitleIndex = 0;
        session.subtitleHoldPerLine = holdPerLine;
        session.subtitleHoldTicks = holdPerLine;
        session.subtitleRefresh = SUBTITLE_REFRESH_TICKS;
        if (!lines.isEmpty()) {
            player.displayClientMessage(lines.get(0), true);
        }
    }

    /**
     * 维持字幕：同一行每 2 秒重发一次（动作栏约 3 秒自动淡出，不续命就看不见），
     * 停留时间到了才换下一行；最后一行播完就停，不再循环。
     */
    private static void tickSubtitle(Session session, Player player) {
        if (session.subtitle.isEmpty()) {
            return;
        }
        if (--session.subtitleRefresh > 0) {
            return;
        }
        session.subtitleRefresh = SUBTITLE_REFRESH_TICKS;
        player.displayClientMessage(session.subtitle.get(session.subtitleIndex), true);

        // 注意：这里每 {@link #SUBTITLE_REFRESH_TICKS} 刻才走到一次，
        // 所以剩余时长必须按同样的步长递减。
        // 旧代码写的是 `--subtitleHoldTicks`（每次只减 1），而刷新间隔是 40 刻，
        // 于是一行实际要显示 40 倍的时间 —— 波次之间的经文永远停在第一句。
        session.subtitleHoldTicks -= SUBTITLE_REFRESH_TICKS;
        if (session.subtitleHoldTicks <= 0) {
            if (session.subtitleIndex + 1 < session.subtitle.size()) {
                session.subtitleIndex++;
                session.subtitleHoldTicks = session.subtitleHoldPerLine;
                player.displayClientMessage(session.subtitle.get(session.subtitleIndex), true);
            } else {
                // 最后一行播完就收工，交给客户端自然淡出
                session.subtitle = java.util.List.of();
            }
        }
    }

    /** 收起字幕（开打时调用）。 */
    private static void clearSubtitle(Session session, Player player) {
        session.subtitle = java.util.List.of();
        player.displayClientMessage(Component.empty(), true);
    }

    /** 某一段经文的各行（1..5）。 */
    private static java.util.List<Component> verseLines(int index) {
        int count = VERSE_LINE_COUNT[index - 1];
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            lines.add(Component.translatable("message.yanscriptures.trial.verse." + index + ".l" + i));
        }
        return lines;
    }

    // ==================== 生物破坏开关 ====================
    // 凋灵的爆炸、使徒的法术都会拆方块；仪式场地里的暗黑祭坛和基座经不起折腾。
    // 试炼期间把 mobGriefing 关掉，结束后按原值还原（原本就是 false 也照样还原）。

    private static Boolean savedMobGriefing;

    private static void suppressMobGriefing(ServerLevel level) {
        if (savedMobGriefing != null) {
            return;
        }
        var rules = level.getGameRules();
        savedMobGriefing = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        rules.getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(false, level.getServer());
        LOGGER.info("[启示试炼] 生物破坏已关闭（原值 {}）", savedMobGriefing);
    }

    private static void restoreMobGriefing(ServerLevel level) {
        if (savedMobGriefing == null) {
            return;
        }
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                .set(savedMobGriefing, level.getServer());
        LOGGER.info("[启示试炼] 生物破坏已还原为 {}", savedMobGriefing);
        savedMobGriefing = null;
    }

    // ==================== 每 tick 推进 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (SESSIONS.isEmpty()) {
            return;
        }

        List<UUID> done = new ArrayList<>();

        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            ServerLevel level = levelOf(event, session);
            Player player = level == null ? null : level.getServer().getPlayerList().getPlayer(session.playerId);

            // 玩家死亡 / 掉线 → 失败
            if (player == null || !player.isAlive()) {
                fail(event, session, player);
                done.add(entry.getKey());
                continue;
            }

            // 维持动作栏上的经文字幕（动作栏会自动淡出，需持续重发）
            tickSubtitle(session, player);

            // 通关后的收尾：只播字幕，播完才真正结束会话
            if (session.closing) {
                if (--session.closingTicks <= 0) {
                    done.add(entry.getKey());
                }
                continue;
            }

            if (session.fighting) {
                session.mobs.removeIf(id -> {
                    Entity entity = level.getEntity(id);
                    return entity == null || !entity.isAlive();
                });

                // 延迟出场的 Boss（亚波伦）：生成器放完 Boss 会自毁，
                // 所以要独立追踪真正的 Boss 本体，而不是只数 mobs。
                if (session.awaitingBoss) {
                    session.awaitTicks++;
                    Entity boss = findDelayedBoss(level, session);
                    if (boss != null) {
                        session.bossId = boss.getUUID();
                        session.awaitingBoss = false;
                        LOGGER.info("[启示试炼] 延迟 Boss 已出现: {}", boss.getName().getString());

                        // 出场演出：用「标题 + 副标题」而不是动作栏 —— 两句同时出现、居中大字。
                        // 注意标题的字号是固定的，文字太长会被屏幕宽度裁掉（实测第一句显示不全），
                        // 所以大标题只放短语，完整句子交给副标题（字号更小）。
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 90, 20));
                            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                    Component.translatable("message.yanscriptures.trial.apollyon.l2")));
                            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                    Component.literal("§4§l那掌死权的")));
                        }
                        level.playSound(null, player.blockPosition(),
                                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.55F);
                    } else if (session.awaitTicks > AWAIT_BOSS_TIMEOUT) {
                        session.awaitingBoss = false;
                        LOGGER.warn("[启示试炼] 等待延迟 Boss 超时，按已清空处理");
                    }
                }

                boolean bossAlive = false;
                if (session.bossId != null) {
                    Entity boss = level.getEntity(session.bossId);
                    bossAlive = boss != null && boss.isAlive();
                }

                if (session.mobs.isEmpty() && !session.awaitingBoss && !bossAlive) {
                    session.fighting = false;

                    // [修复] 最后一波清完要立刻结算，不能还等 30 秒间隔 ——
                    // 玩家在这 30 秒里退出/死亡就再也拿不到奖励了。
                    if (session.wave + 1 >= WAVES.size()) {
                        LOGGER.info("[启示试炼] 第 {} 波已清空，试炼通过", session.wave + 1);
                        succeed(level, player, session);
                        // 不在这里移除会话：succeed 会把会话切到「收尾」状态，
                        // 让最后一段经文播完再由主循环移除。
                        continue;
                    }

                    session.timer = WAVE_GAP_TICKS;
                    LOGGER.info("[启示试炼] 第 {} 波已清空，30 秒后下一波", session.wave + 1);
                    // 波次之间浮现一段经文：第 1 波后 = 第 2 段，第 2 波后 = 第 3 段，第 3 波后 = 第 4 段
                    setSubtitle(session, player, verseLines(session.wave + 2), SUBTITLE_HOLD_GAP);
                }
                continue;
            }

            if (session.timer > 0) {
                session.timer--;
                continue;
            }

            int next = session.wave + 1;
            if (next >= WAVES.size()) {
                succeed(level, player, session);
                done.add(entry.getKey());
            } else {
                spawnWave(level, session, next);
                clearSubtitle(session, player); // 开打时收起字幕，别挡视线
            }
        }

        for (UUID id : done) {
            SESSIONS.remove(id);
        }
        if (SESSIONS.isEmpty()) {
            DamageCapControl.release();
            restoreMobGriefing(event.getServer().overworld());
        }
    }

    private static ServerLevel levelOf(TickEvent.ServerTickEvent event, Session session) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().location().equals(session.dimension)) {
                return level;
            }
        }
        return null;
    }

    // ==================== 生成与结束 ====================

    private static void spawnWave(ServerLevel level, Session session, int waveIndex) {
        session.wave = waveIndex;
        session.mobs.clear();
        session.fighting = true;
        session.awaitingBoss = false;
        session.awaitTicks = 0;
        session.bossId = null;

        List<Unit> ids = WAVES.get(waveIndex);
        int count = ids.size();

        for (int i = 0; i < count; i++) {
            Unit unit = ids.get(i);
            Entity entity = createById(level, session, unit, i, count);
            if (entity == null) {
                LOGGER.warn("[启示试炼] 实体 {} 不存在，本波跳过该单位", unit.id());
                continue;
            }
            // 生成器不进 mobs：它放完 Boss 就会自毁，计入会导致提前判定清场
            if (unit.spawner()) {
                session.awaitingBoss = true;
            } else {
                session.mobs.add(entity.getUUID());
            }
        }

        if (session.awaitingBoss) {
            LOGGER.info("[启示试炼] 第 {} 波：等待延迟 Boss 出场", waveIndex + 1);
        }

        if (session.mobs.isEmpty() && !session.awaitingBoss) {
            session.fighting = false;
            session.timer = WAVE_GAP_TICKS;
        }
    }

    /**
     * 找延迟出场的亚波伦：在试炼中心附近搜使徒，按 NBT 的 {@code isApollyon} 区分。
     * 返回 null 表示还没出现。
     */
    private static Entity findDelayedBoss(ServerLevel level, Session session) {
        var box = new net.minecraft.world.phys.AABB(session.center).inflate(BOSS_SEARCH_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            return key != null && "goety:apostle".equals(key.toString());
        });

        for (LivingEntity candidate : candidates) {
            CompoundTag tag = new CompoundTag();
            candidate.saveWithoutId(tag);
            if (tag.getBoolean("isApollyon")) {
                return candidate;
            }
        }
        return null;
    }

    private static Entity createById(ServerLevel level, Session session, Unit unit, int index, int count) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(unit.id()));
        if (type == null) {
            return null;
        }

        // 环绕中心点均匀摆放，避免所有 Boss 叠在一格
        double angle = 2 * Math.PI * index / Math.max(1, count);
        double x = session.center.getX() + 0.5 + Math.cos(angle) * SPAWN_RADIUS;
        double z = session.center.getZ() + 0.5 + Math.sin(angle) * SPAWN_RADIUS;
        double y = session.center.getY();

        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        applyRandomTitle(entity);
        return entity;
    }

    /**
     * 给 Boss 随机分配一个头衔。
     *
     * <p>Goety 的 Boss 有一个公开的 {@code int titleNumber} 字段，并配套
     * {@code getTitleNumber / setTitleNumber / addTitleEffect}；头衔效果由 {@code addTitleEffect} 施加。
     * 这里按<b>字段名</b>反射，不写死任何类名 —— 任何实现这套约定的 Boss 都会自动获得随机头衔。
     *
     * <p>找不到该字段的实体（普通小怪、凋灵等）直接跳过。
     */
    private static void applyRandomTitle(Entity entity) {
        try {
            java.lang.reflect.Field field = entity.getClass().getField("titleNumber");
            if (field.getType() != int.class) {
                return;
            }
            int title = entity.level().random.nextInt(TITLE_COUNT);
            field.setInt(entity, title);

            // 让头衔效果立即生效（若该方法不存在则忽略）
            try {
                entity.getClass().getMethod("addTitleEffect").invoke(entity);
            } catch (NoSuchMethodException ignored) {
                // 没有这个方法的实体不需要头衔效果
            }
        } catch (Exception ignored) {
            // 没有 titleNumber 字段：不是带称号的 Boss
        }
    }

    /** 通关额外奖励。<b>只看注册名</b>，模组没装就跳过。 */
    private static final String[] CLEAR_REWARDS = {
            // 寂灭之星：原版只有 2.5%（穿教主套 6.66%）的凋灵掉落率，
            // 而第二次仪式需要它，所以直接改由试炼结算发放。
            "goety_revelation:quietus_star"
    };

    private static void succeed(ServerLevel level, Player player, Session session) {
        ItemStack scroll = new ItemStack(ExampleMod.SCROLL_REVELATION.get());
        CompoundTag tag = scroll.getOrCreateTag();
        tag.putBoolean("TrialCleared", true);

        if (!player.getInventory().add(scroll)) {
            player.drop(scroll, false);
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            YanEventTrigger.INSTANCE.trigger(serverPlayer, "trial_clear");
        }

        for (String rewardId : CLEAR_REWARDS) {
            giveById(player, rewardId, 1);
        }

        // 终末的宣告：新天新地。收尾阶段让字幕播完再结束会话 ——
        // 如果立刻移除会话，tickSubtitle 就没人调，只有第一行会闪一下就没了。
        int hold = SUBTITLE_HOLD_FINALE;
        setSubtitle(session, player, verseLines(5), hold);
        session.closing = true;
        session.closingTicks = VERSE_LINE_COUNT[4] * hold + 60;
        cleanUp(level, session);
    }

    /** 按注册名发放物品。 */
    private static void giveById(Player player, String id, int count) {
        var loc = new ResourceLocation(id);
        var item = ForgeRegistries.ITEMS.getValue(loc);

        // 关键：1.20.1 的物品注册表是「带默认值」的注册表（DefaultedRegistry，默认 minecraft:air）。
        // 查不到的 id 不会返回 null，而是返回空气 —— 这就是聊天栏出现「获得 空气」的原因。
        // 所以必须把 AIR 和 containsKey 一起判掉。
        if (item == null || item == net.minecraft.world.item.Items.AIR
                || !ForgeRegistries.ITEMS.containsKey(loc)) {
            var candidates = ForgeRegistries.ITEMS.getKeys().stream()
                    .filter(key -> key.getPath().contains("quietus"))
                    .map(Object::toString)
                    .sorted()
                    .toList();
            LOGGER.warn("[启示试炼] 奖励物品 {} 无法解析；已注册的相似物品: {}", id, candidates);
            return;
        }

        ItemStack stack = new ItemStack(item, count);
        // 先取名字再入包：Inventory.add 成功后会把这个 stack 的数量清零，
        // 之后 getHoverName() 就会退化成「空气」，这正是聊天栏那条诡异提示的来源。
        Component displayName = stack.getHoverName();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(
                Component.literal("§d获得 §f").append(displayName), false);
    }

    private static void fail(TickEvent.ServerTickEvent event, Session session, Player player) {
        ServerLevel level = levelOf(event, session);
        if (level != null) {
            cleanUp(level, session);
            // 失败保留残卷：仪式消耗掉的那张补回来，玩家可以重新交素材再来
            if (player != null) {
                ItemStack scroll = new ItemStack(ExampleMod.SCROLL_REVELATION.get());
                if (!player.getInventory().add(scroll)) {
                    player.drop(scroll, false);
                }
                player.displayClientMessage(
                        Component.literal("§c那审判没有越过你。残卷仍在，可以重来。"), false);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    YanEventTrigger.INSTANCE.trigger(serverPlayer, "trial_fail");
                }
            }
        }
        LOGGER.info("[启示试炼] 会话结束（失败）");
    }

    private static void cleanUp(ServerLevel level, Session session) {
        for (UUID id : session.mobs) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
        }
        session.mobs.clear();
    }

    // ==================== 敌人互不伤害 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTrialHurt(LivingHurtEvent event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        // LivingHurtEvent 的实体本来就一定是 LivingEntity，不需要 instanceof 模式匹配
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }

        // 只拦「试炼生物打试炼生物」，玩家的召唤物不受影响
        for (Session session : SESSIONS.values()) {
            if (session.mobs.contains(victim.getUUID()) && session.mobs.contains(attacker.getUUID())) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                return;
            }
        }
    }

    /** 只读查询：该实体是否属于某场进行中的试炼。 */
    public static boolean isTrialMob(Entity entity) {
        for (Session session : SESSIONS.values()) {
            if (session.mobs.contains(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 立即终止指定玩家的试炼：清场、还原限伤与生物破坏。
     * 供 {@code /yanbook trial stop} 与异常清理使用。返回是否真的终止了一场试炼。
     */
    public static boolean stop(ServerLevel level, Player player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return false;
        }
        cleanUp(level, session);
        if (SESSIONS.isEmpty()) {
            DamageCapControl.release();
            restoreMobGriefing(level);
        }
        player.displayClientMessage(Component.literal("§7【启示试炼】已终止。"), false);
        return true;
    }
}
