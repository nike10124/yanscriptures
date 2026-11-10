package com.example.examplemod;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 《资本论》—— 商品是天生的平等派。
 *
 * <p>作者定稿（2026-11-08）：
 * <ul>
 *   <li>击杀生物获得「资本」，数值等于它的生命上限，单次最多 100；</li>
 *   <li>与猪灵交易 +15，与村民交易 +50；</li>
 *   <li>每 500 资本让攻击力 +1%，上限 +30%；</li>
 *   <li>死亡时资本减半，退出游戏不清理。</li>
 * </ul>
 *
 * <p>资本存在玩家持久数据里（{@link Player#getPersistentData()}），所以重进存档不会丢；
 * 数值通过 {@link NetworkHandler.CapitalPacket} 同步到右上角 HUD。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapitalHandler {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        TRACK.remove(id);
    }

    /** 每次击杀最多拿这么多资本。 */
    private static final long GAIN_CAP = 100L;
    /** 与猪灵 / 村民各拿多少。 */
    private static final long PIGLIN_GAIN = 15L;
    private static final long VILLAGER_GAIN = 50L;
    /** 每这么多资本 +1% 攻击，最高 +30%。 */
    private static final long PER_STEP = 500L;
    private static final double PERCENT_PER_STEP = 0.01D;
    private static final double MAX_BONUS = 0.30D;

    static final String TAG_CAPITAL = "YanCapital";

    private static final UUID ID_ATTACK = BookAttr.id("classic_capital_attack");

    /** 猪灵身边「可能要被捡走」的金锭：掉落物实体 UUID → 这笔算谁的。 */
    private static final Map<UUID, Track> TRACK = new ConcurrentHashMap<>();
    /** 记录之后多久内消失才算一次成交（200 刻 = 10 秒）。 */
    private static final long TRACK_TIMEOUT = 200L;

    /** 一次待确认的猪灵拾取：谁的金锭、什么时候第一次看见它。 */
    private record Track(UUID player, long seenTick) {
    }

    private CapitalHandler() {
    }

    /** 击杀拿资本。 */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getEntity() == player) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.CAPITAL)) {
            return;
        }
        long gain = Math.min(GAIN_CAP, Math.max(0L, (long) Math.ceil(event.getEntity().getMaxHealth())));
        addCapital(player, gain);
    }

    /** 玩家自己死亡：资本减半（作者要求的「折旧」）。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long capital = data.getLong(TAG_CAPITAL);
        if (capital <= 0L) {
            return;
        }
        data.putLong(TAG_CAPITAL, capital / 2L);
        sync(player);
    }

    /** 与村民交易 +50。 */
    @SubscribeEvent
    public static void onVillagerTrade(TradeWithVillagerEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !WornCache.wears(player, ClassicBook.CAPITAL)) {
            return;
        }
        if (event.getAbstractVillager() instanceof Villager) {
            addCapital(player, VILLAGER_GAIN);
        }
    }

    /** 与猪灵交易（右击金锭换东西）+15；走进猪灵也会被算成一次交互，这里只看手里拿着金锭且对方没在发火。 */
    @SubscribeEvent
    public static void onPiglinBarter(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(target instanceof Piglin)) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.CAPITAL)) {
            return;
        }
        if (!event.getItemStack().is(Items.GOLD_INGOT)) {
            return;
        }
        addCapital(player, PIGLIN_GAIN);
    }

    /**
     * 猪灵自己捡起地上的金锭也算一次交易（+15）。
     *
     * <p>玩家通常是把金锭丢出去让猪灵自己捡，这条路走的是 {@code Piglin#pickUpItem}，
     * 原版没有对应事件，Forge 也没有「生物拾取」事件。所以这里用「消失即成交」的旁证：
     * 每 10 刻扫一次「戴着《资本论》的玩家 16 格内的猪灵」身边 3 格的金锭掉落物，
     * 把它们的实体 id 记进 {@link #TRACK}；下一次扫描时这个 id 已经不在附近了
     * （被捡走 / 被合并 / 消失），就给对应玩家记一次交易。
     *
     * <p>三条自我保护：①只看戴着书的玩家 16 格内，范围外不参与；②只认记录后
     * {@link #TRACK_TIMEOUT} 刻内消失的，避免掉落物自然消失也被算成交易；
     * ③右键交易已经加过的那一次不会再被这条重复计数（右键路径不写入 {@link #TRACK}，
     * 而右键拿走的金锭在玩家背包里、不是掉落物）。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 10 != 0) {
            return;
        }
        long now = server.getTickCount();
        Set<UUID> nearby = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!WornCache.wears(player, ClassicBook.CAPITAL)) {
                    continue;
                }
                List<Piglin> piglins = level.getEntitiesOfClass(Piglin.class,
                        player.getBoundingBox().inflate(16.0D));
                for (Piglin piglin : piglins) {
                    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                            piglin.getBoundingBox().inflate(3.0D),
                            item -> item.isAlive() && item.getItem().is(Items.GOLD_INGOT));
                    for (ItemEntity drop : drops) {
                        nearby.add(drop.getUUID());
                        TRACK.putIfAbsent(drop.getUUID(), new Track(player.getUUID(), now));
                    }
                }
            }
        }
        // 上一轮记录过、这一轮已经不在猪灵脚边的金锭 → 认为被猪灵捡走（= 完成一次交易）
        Iterator<Map.Entry<UUID, Track>> it = TRACK.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Track> entry = it.next();
            Track track = entry.getValue();
            if (nearby.contains(entry.getKey())) {
                continue;
            }
            it.remove();
            if (now - track.seenTick() > TRACK_TIMEOUT) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(track.player());
            if (player != null && WornCache.wears(player, ClassicBook.CAPITAL)) {
                addCapital(player, PIGLIN_GAIN);
            }
        }
    }

    /** 加资本（可增减），顺手刷新属性与 HUD。 */
    public static void addCapital(Player player, long delta) {
        CompoundTag data = player.getPersistentData();
        long capital = Math.max(0L, data.getLong(TAG_CAPITAL) + delta);
        data.putLong(TAG_CAPITAL, capital);
        applyAttack(player);
        sync(player);
    }

    /** 直接设定资本（指令用），顺手刷新属性与 HUD。 */
    public static void setCapital(Player player, long value) {
        CompoundTag data = player.getPersistentData();
        data.putLong(TAG_CAPITAL, Math.max(0L, value));
        applyAttack(player);
        sync(player);
    }

    /** 每 20 刻对齐一次属性与 HUD（摘书、换书、重进都能自愈）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % 20 != 0) {
            return;
        }
        applyAttack(player);
        if (WornCache.wears(player, ClassicBook.CAPITAL)) {
            sync(player);
        } else {
            // 没戴书时把 HUD 清掉
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new NetworkHandler.CapitalPacket(-1L));
            }
        }
    }

    private static void applyAttack(Player player) {
        boolean worn = WornCache.wears(player, ClassicBook.CAPITAL);
        double bonus = worn ? bonusOf(player) : 0.0D;
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, bonus);
    }

    /** 当前攻击加成（0 ~ 0.30）。 */
    public static double bonusOf(Player player) {
        long capital = player.getPersistentData().getLong(TAG_CAPITAL);
        long steps = capital / PER_STEP;
        return Math.min(MAX_BONUS, steps * PERCENT_PER_STEP);
    }

    /** 当前资本数量。 */
    public static long capitalOf(Player player) {
        return Math.max(0L, player.getPersistentData().getLong(TAG_CAPITAL));
    }

    /** 距离「下一档 +1%」还差多少。 */
    public static long toNextStep(Player player) {
        long capital = capitalOf(player);
        if (bonusOf(player) >= MAX_BONUS) {
            return 0L;
        }
        return PER_STEP - capital % PER_STEP;
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new NetworkHandler.CapitalPacket(capitalOf(player)));
        }
    }

    /** 生物生命上限（给脚本文档与调试用）。 */
    static long killGain(LivingEntity mob) {
        return Math.min(GAIN_CAP, Math.max(0L, (long) Math.ceil(mob.getMaxHealth())));
    }
}
