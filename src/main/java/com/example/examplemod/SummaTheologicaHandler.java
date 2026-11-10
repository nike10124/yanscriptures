package com.example.examplemod;

import java.util.List;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.ForgeMod;
import org.slf4j.Logger;

/**
 * 《神学大全》：父、子、灵三枚印记。
 *
 * <p>作者 2026-11-09 定稿（见 {@code docs/第四批名著_设计稿.md}）：
 * <ul>
 *   <li>［子］受到伤害时获得：单次伤害最多只造成生命上限的 49%，并回复这次伤害的 10%；</li>
 *   <li>［父］造成伤害时获得：每秒对周围敌对生物造成攻击力 10% 的伤害（视作玩家攻击，不消耗无敌帧）；</li>
 *   <li>［灵］获得经验时获得：经验 +50%、额外掉落一份战利品、移动与攻击速度 +10%、触及距离 +0.5；</li>
 *   <li>三种可同时存在、各一层，重置即刷新时间；基础持续 10 秒，同时佩戴《圣约·原典》时用括号里的数值（70 秒等）。</li>
 * </ul>
 *
 * <p>数值还会按「十诫的祝福数量」线性抬高：每一条祝福让基础值提高 5%。
 */
@EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SummaTheologicaHandler {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String TAG_SON = "SummaSon";
    private static final String TAG_FATHER = "SummaFather";
    private static final String TAG_SPIRIT = "SummaSpirit";

    /** 基础值 / 同时佩戴《圣约·原典》时的值。 */
    private static final double SON_HEAL = 0.10D;
    private static final double SON_HEAL_COVENANT = 0.70D;
    private static final double SON_HIT_CAP = 0.49D;
    /** 超过生命上限 700% 的伤害整笔作废（作者 2026-11-09 澄清）。 */
    private static final double SON_CANCEL_RATIO = 7.0D;
    private static final double FATHER_RATIO = 0.10D;
    private static final double FATHER_RATIO_COVENANT = 0.70D;
    private static final double FATHER_RADIUS = 1.5D;
    private static final double FATHER_RADIUS_COVENANT = 10.5D;
    private static final double SPIRIT_XP = 0.50D;
    private static final double SPIRIT_SPEED = 0.10D;
    private static final double SPIRIT_SPEED_COVENANT = 0.70D;
    private static final double SPIRIT_REACH = 0.5D;
    private static final double SPIRIT_REACH_COVENANT = 3.5D;

    private static final int DURATION_TICKS = 200;
    private static final int DURATION_TICKS_COVENANT = 1400;
    /** ［父］每秒结算一次。 */
    private static final int FATHER_INTERVAL = 20;
    /** 每有一条十诫祝福，基础值提高这么多。 */
    private static final double PER_BLESSING = 0.05D;

    private static final UUID ID_SPEED = BookAttr.id("summa_spirit_speed");
    private static final UUID ID_ATTACK_SPEED = BookAttr.id("summa_spirit_attack_speed");
    private static final UUID ID_BLOCK_REACH = BookAttr.id("summa_spirit_block_reach");
    private static final UUID ID_ENTITY_REACH = BookAttr.id("summa_spirit_entity_reach");

    private SummaTheologicaHandler() {
    }

    // ==================== 印记记账 ====================

    private static boolean worn(Player player) {
        return WornCache.wears(player, ClassicBook.SUMMA_THEOLOGICA);
    }

    private static boolean covenant(Player player) {
        return CovenantCodexHandler.wearsCovenant(player);
    }

    /** 十诫祝福每多一条，数值 +5%。 */
    private static double scale(Player player) {
        int blessings = Math.max(0, 10 - ClassicBooksHandler.activeCurses(player));
        return 1.0D + PER_BLESSING * blessings;
    }

    private static long duration(Player player) {
        return covenant(player) ? DURATION_TICKS_COVENANT : DURATION_TICKS;
    }

    private static void refresh(Player player, String key) {
        player.getPersistentData().putLong(key, player.level().getGameTime() + duration(player));
    }

    private static boolean active(Player player, String key) {
        CompoundTag data = player.getPersistentData();
        long until = data.getLong(key);
        if (until <= 0L) {
            return false;
        }
        if (player.level().getGameTime() >= until) {
            data.remove(key);
            return false;
        }
        return true;
    }

    // ==================== ［子］受伤 ====================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!worn(player)) {
            return;
        }
        refresh(player, TAG_SON);
        if (!active(player, TAG_SON)) {
            return;
        }
        // 作者 2026-11-09 澄清：超过生命上限 700% 的伤害直接作废（一点也不掉）；
        // 没超过的话，最多也只打掉生命上限的 49%。
        if (event.getAmount() > player.getMaxHealth() * SON_CANCEL_RATIO) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }
        float cap = (float) (player.getMaxHealth() * SON_HIT_CAP);
        float amount = Math.min(event.getAmount(), cap);
        event.setAmount(amount);
        double ratio = (covenant(player) ? SON_HEAL_COVENANT : SON_HEAL) * scale(player);
        float heal = (float) (amount * ratio);
        if (heal > 0.0F) {
            // 直接改血量：走 heal() 会被《埃涅阿斯纪》那类「反转治疗」截走。
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
        }
    }

    // ==================== ［父］造成伤害 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDealt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (worn(player)) {
            refresh(player, TAG_FATHER);
        }
    }

    // ==================== ［灵］获得经验 ====================

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null || player.level().isClientSide() || !worn(player)) {
            return;
        }
        if (active(player, TAG_SPIRIT)) {
            event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * (1.0D + SPIRIT_XP)));
        }
        refresh(player, TAG_SPIRIT);
    }

    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide() && worn(player)) {
            refresh(player, TAG_SPIRIT);
        }
    }

    /** ［灵］额外掉落：把这一刀掉出来的东西直接翻倍（作者 2026-11-09：不要挑一样，整体 ×2）。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!worn(player) || !active(player, TAG_SPIRIT)) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            int count = stack.getCount();
            if (count <= 0) {
                continue;
            }
            stack.setCount(Math.min(stack.getMaxStackSize() * 2, count * 2));
        }
    }

    // ==================== 每刻：［父］环伤害 + ［灵］属性 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!worn(player)) {
            clearAttributes(player);
            syncState(player, 0);
            return;
        }

        boolean son = active(player, TAG_SON);
        boolean spirit = active(player, TAG_SPIRIT);
        applySpirit(player, spirit);
        syncState(player, (son ? 1 : 0) | (active(player, TAG_FATHER) ? 2 : 0) | (spirit ? 4 : 0));

        if (!active(player, TAG_FATHER)) {
            return;
        }
        if (player.tickCount % FATHER_INTERVAL != 0) {
            return;
        }
        fatherRing(player);
    }

    /** 三枚印记只用于给物品介绍里的三个字上色，变了才发。 */
    private static void syncState(Player player, int bits) {
        Integer last = SYNCED.get(player.getUUID());
        if (last != null && last == bits) {
            return;
        }
        SYNCED.put(player.getUUID(), bits);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> server),
                    new NetworkHandler.SummaStatePacket(bits));
        }
    }

    private static void fatherRing(Player player) {
        double radius = (covenant(player) ? FATHER_RADIUS_COVENANT : FATHER_RADIUS) * scale(player);
        double ratio = (covenant(player) ? FATHER_RATIO_COVENANT : FATHER_RATIO) * scale(player);
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ratio);
        if (damage <= 0.0F) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
            if (!(mob instanceof Enemy) || !mob.isAlive()) {
                continue;
            }
            if (mob.distanceToSqr(player) > radius * radius) {
                continue;
            }
            // 「不计入无敌帧」：打之前清零、打完还原，既保证这一下能落下，也不占用目标的无敌时间。
            int saved = mob.invulnerableTime;
            mob.invulnerableTime = 0;
            try {
                mob.hurt(player.damageSources().playerAttack(player), damage);
            } finally {
                mob.invulnerableTime = saved;
            }
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            int points = 40;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 0.6D, z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
        }
    }

    private static void applySpirit(Player player, boolean active) {
        if (!active) {
            clearAttributes(player);
            return;
        }
        double speedRatio = (covenant(player) ? SPIRIT_SPEED_COVENANT : SPIRIT_SPEED) * scale(player);
        double reach = (covenant(player) ? SPIRIT_REACH_COVENANT : SPIRIT_REACH) * scale(player);
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, speedRatio);
        BookAttr.set(player, Attributes.ATTACK_SPEED, ID_ATTACK_SPEED, speedRatio);
        setReach(player, ForgeMod.BLOCK_REACH.get(), ID_BLOCK_REACH, reach);
        setReach(player, ForgeMod.ENTITY_REACH.get(), ID_ENTITY_REACH, reach);
    }

    private static void setReach(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                 UUID id, double amount) {
        if (attribute == null) {
            return;
        }
        BookAttr.set(player, attribute, id, amount, AttributeModifier.Operation.ADDITION);
    }

    private static void clearAttributes(Player player) {
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, 0.0D);
        BookAttr.set(player, Attributes.ATTACK_SPEED, ID_ATTACK_SPEED, 0.0D);
        setReach(player, ForgeMod.BLOCK_REACH.get(), ID_BLOCK_REACH, 0.0D);
        setReach(player, ForgeMod.ENTITY_REACH.get(), ID_ENTITY_REACH, 0.0D);
    }

    /** 经验加成走事件：调用方需要在发经验前调这里加成的比例。 */
    public static double xpBonus(Player player) {
        if (!worn(player) || !active(player, TAG_SPIRIT)) {
            return 0.0D;
        }
        return SPIRIT_XP;
    }

    static {
        SessionCleanup.register(SummaTheologicaHandler::cleanup);
    }

    /** 上次同步给客户端的三枚状态（bit0 子 / bit1 父 / bit2 灵）。 */
    private static final java.util.Map<UUID, Integer> SYNCED = new java.util.concurrent.ConcurrentHashMap<>();

    public static void cleanup(UUID id) {
        SYNCED.remove(id);
        LOG.debug("《神学大全》：清理玩家 {} 的印记", id);
    }

    /** 调试用：三枚印记剩余秒数。 */
    public static String describe(Player player) {
        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        return "子 " + left(now, data.getLong(TAG_SON))
                + " 秒 / 父 " + left(now, data.getLong(TAG_FATHER))
                + " 秒 / 灵 " + left(now, data.getLong(TAG_SPIRIT)) + " 秒";
    }

    private static long left(long now, long until) {
        return Math.max(0L, (until - now) / 20L);
    }

    /** 掉落表里的物品 id 便于调试（未使用，但保留给后续扩展）。 */
    static String idOf(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** 供事件里判断敌对生物的通用口径。 */
    static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }
}
