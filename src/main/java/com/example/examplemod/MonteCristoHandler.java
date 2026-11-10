package com.example.examplemod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《基督山伯爵》—— 人类的一切智慧都包含在这两个词里：等待与希望。
 *
 * <p>作者定稿（2026-11-08）：
 * <ul>
 *   <li>伤害过你的生物进入「复仇名单」，你对其造成的伤害 +20%；</li>
 *   <li>若你曾被它杀死过，这条再加 20%（合计 +40%）；</li>
 *   <li>名单里的生物被红色高亮，并额外飘一行「复仇」；</li>
 *   <li>名单里还有目标时，你的攻击速度 +25%。</li>
 * </ul>
 *
 * <p>红色高亮为什么用计分板队伍：原版 {@code setGlowingTag(true)} 只会画出**白色**描边，
 * 描边颜色取的是实体所在队伍的颜色。所以这里建一个红色队伍，把名单里的生物塞进去；
 * 名单里没有了就再移出来，不会影响别的实体。
 *
 * <p>名单不清空（作者没定过期时间），但生物一旦死亡就从名单里移除。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MonteCristoHandler {

    /** 复仇名单：玩家 UUID → (生物 UUID → 是否杀死过这名玩家)。 */
    private static final Map<UUID, Map<UUID, Boolean>> ENEMIES = new HashMap<>();

    /** 当前已经被我们点亮过的生物：玩家 UUID → 生物 UUID 集合，用来撤掉自己加的发光。 */
    private static final Map<UUID, Set<UUID>> MARKED = new HashMap<>();

    private static final String TEAM_NAME = "yanscriptures_revenge";
    private static final double BONUS = 0.20D;
    private static final double KILLED_BONUS = 0.20D;
    private static final double ATTACK_SPEED_BONUS = 0.25D;

    private static final UUID ID_ATTACK_SPEED = BookAttr.id("classic_monte_cristo_attack_speed");

    static {
        SessionCleanup.register(MonteCristoHandler::cleanupPlayer);
    }

    private MonteCristoHandler() {
    }

    /** 被生物打中时记名字。 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.THE_COUNT_OF_MONTE_CRISTO)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity mob) || mob == player) {
            return;
        }
        ENEMIES.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
                .putIfAbsent(mob.getUUID(), Boolean.FALSE);
        markGlowing(mob);
    }

    /** 被生物打死时，把它升级成「杀过你的那只」。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.THE_COUNT_OF_MONTE_CRISTO)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof LivingEntity mob) || mob == player) {
            return;
        }
        ENEMIES.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
                .put(mob.getUUID(), Boolean.TRUE);
    }

    /** 对名单里的目标造成伤害时加成，并飘一行「复仇」。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || event.getEntity() == player) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.THE_COUNT_OF_MONTE_CRISTO)) {
            return;
        }
        Map<UUID, Boolean> enemies = ENEMIES.get(player.getUUID());
        if (enemies == null) {
            return;
        }
        Boolean killedMe = enemies.get(event.getEntity().getUUID());
        if (killedMe == null) {
            return;
        }
        double bonus = BONUS + (killedMe ? KILLED_BONUS : 0.0D);
        event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
        DamageNumberSender.send(event.getEntity(), event.getAmount(), false, "复仇");
    }

    /** 每 10 刻维护攻击速度加成与红色发光；死亡的生物从名单里清掉。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        boolean worn = WornCache.wears(player, ClassicBook.THE_COUNT_OF_MONTE_CRISTO);
        Map<UUID, Boolean> enemies = worn ? ENEMIES.get(player.getUUID()) : null;
        boolean hasTarget = enemies != null && !enemies.isEmpty();
        BookAttr.set(player, Attributes.ATTACK_SPEED, ID_ATTACK_SPEED,
                hasTarget ? ATTACK_SPEED_BONUS : 0.0D);
        if (!(player instanceof ServerPlayer serverPlayer) || player.tickCount % 10 != 0) {
            return;
        }
        Set<UUID> desired = new HashSet<>();
        if (hasTarget) {
            Iterator<Map.Entry<UUID, Boolean>> it = enemies.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Boolean> entry = it.next();
                Entity entity = serverPlayer.serverLevel().getEntity(entry.getKey());
                if (!(entity instanceof LivingEntity mob) || !mob.isAlive()) {
                    it.remove();
                    continue;
                }
                markGlowing(mob);
                desired.add(mob.getUUID());
            }
        }
        clearStale(serverPlayer, desired);
    }

    /** 自己给生物打上发光标记，再塞进红色队伍——原版描边的颜色取自队伍色。 */
    private static void markGlowing(LivingEntity mob) {
        if (!mob.hasGlowingTag()) {
            mob.setGlowingTag(true);
        }
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setColor(ChatFormatting.RED);
        }
        if (scoreboard.getPlayersTeam(mob.getScoreboardName()) != team) {
            scoreboard.addPlayerToTeam(mob.getScoreboardName(), team);
        }
    }

    /** 不再在名单里的生物：撤掉我们加的发光、退出红色队伍，别的模组加的发光不动。 */
    private static void clearStale(ServerPlayer player, Set<UUID> desired) {
        Set<UUID> previous = MARKED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        if (previous.isEmpty()) {
            previous.addAll(desired);
            return;
        }
        MinecraftServer server = player.getServer();
        Scoreboard scoreboard = server == null ? null : server.getScoreboard();
        for (UUID id : new HashSet<>(previous)) {
            if (desired.contains(id)) {
                continue;
            }
            previous.remove(id);
            Entity entity = player.serverLevel().getEntity(id);
            if (entity instanceof LivingEntity mob) {
                mob.setGlowingTag(false);
                if (scoreboard != null) {
                    scoreboard.removePlayerFromTeam(mob.getScoreboardName());
                }
            }
        }
        previous.addAll(desired);
    }

    private static void cleanupPlayer(UUID playerId) {
        ENEMIES.remove(playerId);
        MARKED.remove(playerId);
    }

    /** 名单里所有生物的 UUID（给调试与文档用）。 */
    public static Set<UUID> targetsOf(Player player) {
        Map<UUID, Boolean> enemies = ENEMIES.get(player.getUUID());
        return enemies == null ? Set.of() : new HashSet<>(enemies.keySet());
    }
}
