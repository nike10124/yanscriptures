package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 「真我的史诗」的临时获取方式（作者 2026-11-12 定）。
 *
 * <p>条件：已经拥有另外四块史诗石板，并且击败了亚波伦。
 *
 * <p><b>为什么要延迟发放</b>：亚波伦的死亡要播「尾杀」——生命值归零只是开始，
 * 之后还有一段结界/演出，期间实体仍在世界里。如果在 {@link LivingDeathEvent}
 * 里直接发，玩家会看到「刚把他打空血就结算」，与设计不符。
 * 所以这里只记一笔待结算，等实体真正从世界里消失（死亡动画播完被移除）
 * 才发放，并留一个超时兜底，避免实体卡住导致永远拿不到。
 *
 * <p>判定亚波伦不靠注册名：读实体存档里的 {@code isApollyon} 标记
 * （与启示试炼那边同一份依据），这样别的模组改名也不会失效。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ApollyonEpicRewardHandler {

    /** 等实体彻底消失的上限，防止实体卡住不发奖（20 秒）。 */
    private static final long MAX_WAIT_TICKS = 400L;

    /** 记录「曾经获得过」哪几块史诗，写在玩家存档里（作者 2026-11-12：不要求背包里带着）。 */
    private static final String TAG_GOT_EPICS = "YanGotEpics";

    /** 「真我的史诗」已经发过一次的标记（防止把石板存箱子后再打一次又发一份）。 */
    private static final String TAG_EGO_GOT = "YanGotEgo";

    /** 每 20 tick 扫一次背包/饰品栏，把「拿到过」的史诗记下来。 */
    private static final int SCAN_INTERVAL = 20;

    /**
     * 四块史诗各自的进度 id（不带命名空间）。
     *
     * <p>进度是原版存档的一部分，退出重进、死亡重生都不会丢；
     * 之前只用 {@code getPersistentData()} 记，作者反馈重进后记录就没了。
     */
    private static final String[] EPIC_ADVANCEMENTS = {
            "epic_fate", "epic_creation", "epic_pillar", "epic_calamity"
    };

    /** 击杀者为空时（例如毒/火焰结算），按这个半径找附近的玩家。 */
    private static final double CREDIT_RADIUS = 48.0D;

    private record Pending(UUID playerId, UUID bossId,
                           ResourceKey<Level> dimension, long deadline) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    static {
        SessionCleanup.register(ApollyonEpicRewardHandler::cleanupPlayer);
    }

    private ApollyonEpicRewardHandler() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead.level() instanceof ServerLevel level)) {
            return;
        }

        // 先判标记：绝大多数死亡事件在这里就结束了，不产生任何开销
        CompoundTag tag = new CompoundTag();
        dead.saveWithoutId(tag);
        if (!tag.getBoolean("isApollyon")) {
            return;
        }

        // 作者 2026-11-12 补充：「亚波伦」指的是**下界**亚波伦。
        // 同一个实体在主世界也会死一次，那一次不算，必须死在下界才算数。
        if (!level.dimension().equals(Level.NETHER)) {
            return;
        }

        Player killer = event.getSource().getEntity() instanceof Player sourcePlayer
                ? sourcePlayer : null;
        if (killer == null) {
            killer = level.getNearestPlayer(dead.getX(), dead.getY(), dead.getZ(), CREDIT_RADIUS, false);
        }
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }

        PENDING.put(player.getUUID(), new Pending(player.getUUID(), dead.getUUID(),
                level.dimension(), level.getGameTime() + MAX_WAIT_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Pending pending = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player == null) {
                iterator.remove();
                continue;
            }

            ServerLevel level = server.getLevel(pending.dimension());
            Entity boss = level == null ? null : level.getEntity(pending.bossId());
            boolean gone = boss == null || boss.isRemoved();

            if (!gone) {
                if (level != null && level.getGameTime() >= pending.deadline()) {
                    // 超时兜底：实体卡住也要让人拿到
                    iterator.remove();
                    settle(player);
                }
                continue;
            }

            iterator.remove();
            settle(player);
        }
    }

    private static void settle(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(TAG_EGO_GOT)) {
            return;
        }
        if (has(player, ExampleMod.EPIC_EGO.get())) {
            player.getPersistentData().putBoolean(TAG_EGO_GOT, true);
            return;
        }

        if (!hasOtherFour(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.yanscriptures.epic.ego_missing").withStyle(ChatFormatting.GRAY), false);
            return;
        }

        ItemStack ego = new ItemStack(ExampleMod.EPIC_EGO.get());
        if (!player.getInventory().add(ego)) {
            player.drop(ego, false);
        }
        player.getPersistentData().putBoolean(TAG_EGO_GOT, true);
        player.displayClientMessage(Component.translatable(
                "message.yanscriptures.epic.ego_granted").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        levelSound(player);
    }

    private static void levelSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /**
     * 另外四块史诗：命运、创生、支柱、灾厄。
     *
     * <p>判定的是「**曾经获得过**」，不是此刻拿在手上——作者 2026-11-12 明确：
     * 「是获取过其他四个史诗，不要求背包里有」。所以打扫描把见过的那几块记进
     * 玩家存档，这里只读存档。
     */
    private static boolean hasOtherFour(Player player) {
        // 首选进度（作者 2026-11-12：退出重进后存档标记会丢，进度不会）：
        // 四块史诗各有一条 inventory_changed 进度，拿到过就永久做完。
        if (player instanceof ServerPlayer serverPlayer && allFourAdvancements(serverPlayer)) {
            return true;
        }
        CompoundTag got = player.getPersistentData().getCompound(TAG_GOT_EPICS);
        return got.getBoolean("fate") && got.getBoolean("creation")
                && got.getBoolean("pillar") && got.getBoolean("calamity");
    }

    /** 四条史诗进度是否都已完成。 */
    private static boolean allFourAdvancements(ServerPlayer player) {
        var server = player.server;
        if (server == null) {
            return false;
        }
        for (String name : EPIC_ADVANCEMENTS) {
            var advancement = server.getAdvancements()
                    .getAdvancement(new net.minecraft.resources.ResourceLocation(ExampleMod.MODID, name));
            if (advancement == null) {
                return false;
            }
            if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                return false;
            }
        }
        return true;
    }

    /** 每 20 tick 扫一次，把「拿到过」的史诗记进玩家存档。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }

        CompoundTag got = player.getPersistentData().getCompound(TAG_GOT_EPICS);
        boolean changed = false;
        changed |= mark(got, "fate", player, ExampleMod.EPIC_FATE.get());
        changed |= mark(got, "creation", player, ExampleMod.EPIC_CREATION.get());
        changed |= mark(got, "pillar", player, ExampleMod.EPIC_PILLAR.get());
        changed |= mark(got, "calamity", player, ExampleMod.EPIC_CALAMITY.get());
        if (changed) {
            player.getPersistentData().put(TAG_GOT_EPICS, got);
        }
    }

    private static boolean mark(CompoundTag got, String key, Player player,
                                net.minecraft.world.item.Item item) {
        if (got.getBoolean(key) || !has(player, item)) {
            return false;
        }
        got.putBoolean(key, true);
        return true;
    }

    private static boolean has(Player player, net.minecraft.world.item.Item item) {
        // 主背包 + 副手 + 快捷栏
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                return true;
            }
        }

        // 饰品栏（史诗是 Curio，戴上后就离开背包了）
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) {
            return false;
        }
        var handler = curios.get().getEquippedCurios();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static void cleanupPlayer(UUID playerId) {
        PENDING.remove(playerId);
    }
}
