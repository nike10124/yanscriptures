package com.example.examplemod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 《纯粹理性批判》—— 头顶的星空，与心中的道德律。
 *
 * <p>作者定稿（2026-11-08）：
 * <ul>
 *   <li>背包里每有一种附魔（按格计算：一格内不重复，不同格之间可重复；附魔书不算），
 *       造成伤害 +0.5%、生命上限 +1%；</li>
 *   <li>若背包里所有物品都带附魔（空格不限，但带东西的格子要超过 21 格），获得创造飞行；</li>
 *   <li>你从未主动攻击过的和平/中立生物对你造成的伤害 -20%。</li>
 * </ul>
 *
 * <p>「按格计算」的实现：每个格子取它身上<b>不同附魔的个数</b>；同一本附魔有两三个不同附魔
 * 就算两三点，两格各有一本「锋利」算两点，符合作者「不同格可以重复计算、一格内只算一个」的口径。
 * 附魔书（{@link EnchantedBookItem}）整格跳过。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CritiqueOfPureReasonHandler {

    /** 每点附魔的增伤 / 增血。 */
    private static final double DAMAGE_PER_POINT = 0.005D;
    private static final double HEALTH_PER_POINT = 0.01D;

    /** 触发创造飞行需要的「带东西的格子」下限。 */
    private static final int FLIGHT_MIN_SLOTS = 21;

    /** 被这本书保护的目标：玩家 UUID → 他亲手打过（或打过）的生物 UUID。 */
    private static final Map<UUID, Set<UUID>> ENGAGED = new HashMap<>();

    private static final UUID ID_DAMAGE = BookAttr.id("classic_critique_damage");
    private static final UUID ID_HEALTH = BookAttr.id("classic_critique_health");

    /** 最近一次扫描算出的「飞行条件是否达成」（每 20 刻更新一次）。 */
    private static final Map<UUID, Boolean> FLIGHT_OK = new HashMap<>();
    /** 由我们授予了创造飞行的玩家，收手时只收回这些人的。 */
    private static final Set<UUID> GRANTED = new HashSet<>();

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static int lastPoints = -1;
    private static int lastHealthPoints = -1;

    static {
        SessionCleanup.register(CritiqueOfPureReasonHandler::cleanupPlayer);
    }

    private CritiqueOfPureReasonHandler() {
    }

    /** 每 20 刻重算一次附魔点球（背包变化不需要每刻扫描）；飞行状态每刻维护。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        boolean worn = WornCache.wears(player, ClassicBook.CRITIQUE_OF_PURE_REASON);
        if (player.tickCount % 20 == 0) {
            int points = worn ? enchantPoints(player) : 0;
            BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_DAMAGE, points * DAMAGE_PER_POINT);
            BookAttr.set(player, Attributes.MAX_HEALTH, ID_HEALTH, points * HEALTH_PER_POINT);
            lastPoints = points;
            lastHealthPoints = player.getMaxHealth() > 0 ? (int) Math.round(player.getMaxHealth()) : 0;
            FLIGHT_OK.put(player.getUUID(), worn && allEnchanted(player));
        }
        if (player.tickCount % 20 == 0 && !worn) {
            ENGAGED.remove(player.getUUID());
        }
        // 每刻都维护一次：别的模组 / 原版把 abilities 同步回去时，下一 tick 就能修回来。
        applyFlight(player, worn && Boolean.TRUE.equals(FLIGHT_OK.get(player.getUUID())));
    }

    /**
     * 飞行：满足条件时申请一份，条件消失时只撤回自己那一份。
     *
     * <p>2026-11-08 改：不再往客户端发自定义包去「钉」{@code mayfly}。
     * 那样做在「同时戴两本会飞的书」时会互相打架（客户端被本包按成 false、
     * 服务端却被另一本书设成 true，于是怎么看都是「不能飞」）。
     * 现在只走服务端 {@code FlightPermissions} + 原版 abilities 同步这一条路。
     */
    private static void applyFlight(Player player, boolean allowed) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        UUID id = player.getUUID();
        if (allowed) {
            FlightPermissions.set(player, FlightPermissions.CRITIQUE, true);
            if (GRANTED.add(id)) {
                LOG.info("《纯粹理性批判》：{} 达成飞行条件，已授予创造飞行（mayfly={}）",
                        player.getName().getString(), player.getAbilities().mayfly);
            }
            return;
        }
        if (GRANTED.remove(id)) {
            FlightPermissions.set(player, FlightPermissions.CRITIQUE, false);
        }
    }

    /** 记录玩家打过谁（打成没打成不重要，只要他出过手）。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == player) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.CRITIQUE_OF_PURE_REASON)) {
            return;
        }
        ENGAGED.computeIfAbsent(player.getUUID(), key -> new HashSet<>()).add(target.getUUID());
    }

    /** 和平/中立生物打你：你没惹过它才减伤 20%。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.CRITIQUE_OF_PURE_REASON)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        // 2026-11-08 作者：去掉「只对和平与中立生物」的限制——只要没被你主动打过，谁都吃这一档减伤。
        if (!(source instanceof Mob mob)) {
            return;
        }
        Set<UUID> engaged = ENGAGED.get(player.getUUID());
        if (engaged != null && engaged.contains(mob.getUUID())) {
            return;
        }
        event.setAmount(event.getAmount() * 0.80F);
    }

    /** 背包（快捷栏 + 物品栏 + 装备栏）里的附魔点球。 */
    public static int enchantPoints(Player player) {
        Inventory inventory = player.getInventory();
        int points = 0;
        for (int slot = 0; slot < 40; slot++) {
            points += slotPoints(inventory.getItem(slot));
        }
        return points;
    }

    /** 单个格子的附魔点数；附魔书不算。 */
    private static int slotPoints(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof EnchantedBookItem) {
            return 0;
        }
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        return enchantments.size();
    }

    /** 条件：带东西的格子超过 21 格，并且每一格都带附魔。 */
    public static boolean allEnchanted(Player player) {
        Inventory inventory = player.getInventory();
        int occupied = 0;
        for (int slot = 0; slot < 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            occupied++;
            if (slotPoints(stack) <= 0) {
                return false;
            }
        }
        return occupied > FLIGHT_MIN_SLOTS;
    }

    /** 给 tooltip 用：当前点球。 */
    public static int pointsOf(Player player) {
        return enchantPoints(player);
    }

    /**
     * 给 tooltip 用的飞行条件进度：{@code [已带附魔的格子数, 没带附魔的格子数]}。
     *
     * <p>条件是「带东西的格子多于 21 格」并且「所有带东西的格子都有附魔」，
     * 空格子不算。返回两个数是为了让玩家一眼看出差在哪：
     * 第一个数不够就是格子太少，第二个数不为 0 就是还有格子没附魔。
     */
    public static int[] flightProgress(Player player) {
        Inventory inventory = player.getInventory();
        int enchanted = 0;
        int plain = 0;
        for (int slot = 0; slot < 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (slotPoints(stack) > 0) {
                enchanted++;
            } else {
                plain++;
            }
        }
        return new int[]{enchanted, plain};
    }

    /**
     * `/ys debug flight` 用：把飞行相关的每个变量摊开，判断是「我们自己撤的」还是「外面清的」。
     *
     * <p>关键看两组：{@code mayfly} 与 {@code flying}。若 mayfly=true 而客户端仍然飞不起来，
     * 就是 abilities 包没到客户端；若 mayfly 在飞行途中被改回 false，而 FLIGHT_OK 一直是 true，
     * 那就是别的模组在改服务端状态。
     */
    public static String debugFlight(Player player) {
        UUID id = player.getUUID();
        int[] progress = flightProgress(player);
        boolean worn = WornCache.wears(player, ClassicBook.CRITIQUE_OF_PURE_REASON);
        return "§6===== 《纯粹理性批判》飞行诊断 =====\n"
                + "§7佩戴：" + (worn ? "§a是" : "§c否") + "\n"
                + "§7附魔格数：" + progress[0] + "（需 > " + FLIGHT_MIN_SLOTS + "）"
                + "　没附魔格数：" + progress[1] + "\n"
                + "§7条件缓存 FLIGHT_OK：" + FLIGHT_OK.get(id) + "\n"
                + "§7服务端 mayfly：" + player.getAbilities().mayfly
                + "　flying：" + player.getAbilities().flying + "\n"
                + "§7是否由本模组授予过：" + GRANTED.contains(id) + "\n"
                + "§7在飞行：" + player.isFallFlying() + "　离地：" + !player.onGround();
    }

    private static void cleanupPlayer(UUID playerId) {
        ENGAGED.remove(playerId);
        FLIGHT_OK.remove(playerId);
        GRANTED.remove(playerId);
    }
}
