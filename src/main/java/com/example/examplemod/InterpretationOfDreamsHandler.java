package com.example.examplemod;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 《梦的解析》—— 睡着了的人不受这个世界的规矩管。
 *
 * <p>作者重新定稿（2026-11-01 晚），按这条实现：
 * <ol>
 *   <li>Shift + D 入睡，持续 30 秒、冷却 600 秒；</li>
 *   <li>梦里用原版生存的移速，可穿墙、可飞行、不受重力；</li>
 *   <li>周围的生物也「在梦里」：和影子一样，不掉血、不移动、不燃烧、不锁定玩家；</li>
 *   <li>玩家打在它们身上的伤害不落地，只把总和的 30% 记住，梦醒时结算到它们身上；</li>
 *   <li>梦结束时，玩家回到入睡那一刻站着的位置。</li>
 * </ol>
 *
 * <p>为什么不做成真维度：作者问过「写特殊状态还是真维度，你自行判断」，答「可以」。
 * 真维度要造一个 world、要同步地形、要把实体搬进去再搬回来，一步出错就是存档级事故；
 * 「梦游」状态把这些需求全做到了，而且任何一条出问题都只影响那 30 秒。
 * 「地形与现实一致」在这个做法下是<b>自动成立</b>的 —— 人根本没离开现实世界。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InterpretationOfDreamsHandler {

    static final String TAG_UNTIL = "YanDreamUntil";
    static final String TAG_COOLDOWN = "YanDreamCooldown";
    static final String TAG_PENDING = "YanDreamPending";
    static final String TAG_BASE_SPEED = "YanDreamBaseSpeed";
    /** 入睡时站着的位置，梦醒要回到这里。 */
    static final String TAG_START = "YanDreamStart";
    /** 被冻住的生物 uuid 列表（写进玩家数据，崩服也不会留下满世界不动的怪）。 */
    static final String TAG_MOBS = "YanDreamMobs";
    /**
     * 其中「本来是能动的、是被我们关掉 AI 的」那些 uuid。
     * 作者实测（2026-11-01 夜）：训练假人这类本来就没有 AI 的生物，梦醒后被我
     * 一律 {@code setNoAi(false)} 反而开始动了，所以要记下哪些是我们改的、只还原这些。
     */
    static final String TAG_NOAI_FLIPPED = "YanDreamNoAiFlipped";

    private static final int DURATION_TICKS = 600;
    private static final int COOLDOWN_TICKS = 12_000;
    private static final double RADIUS = 32.0D;
    private static final double DAMAGE_CARRY = 0.30D;
    /** 冰冻状态每 5 刻补一次：梦开始之后走进 32 格的生物也要一起「在梦里」。 */
    private static final int FREEZE_INTERVAL = 5;

    private InterpretationOfDreamsHandler() {
    }

    public static boolean dreaming(Player player) {
        long until = player.getPersistentData().getLong(TAG_UNTIL);
        return until > player.level().getGameTime();
    }

    /** 由 {@code NetworkHandler.DreamEnterPacket} 调用。 */
    public static void enter(ServerPlayer player) {
        if (!WornCache.wears(player, ClassicBook.INTERPRETATION_OF_DREAMS)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (dreaming(player)) {
            return;
        }
        long cooldown = data.getLong(TAG_COOLDOWN);
        if (now < cooldown) {
            long left = (cooldown - now) / 20L + 1L;
            player.displayClientMessage(Component.literal("§c【梦境】还要等 " + left + " 秒才能再睡。"), true);
            return;
        }
        data.putLong(TAG_UNTIL, now + DURATION_TICKS);
        data.putLong(TAG_COOLDOWN, now + COOLDOWN_TICKS);
        data.putDouble(TAG_BASE_SPEED, player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        saveStart(player, data);

        player.setInvulnerable(true);
        player.noPhysics = true;
        player.setNoGravity(true);
        sendState(player, true);
        // 原版生存的移速：基础值就是 0.1，戴着别的移速饰品也照样按这个走
        if (player.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1D);
        }
        freezeAround(player, data, new HashSet<>(), new HashSet<>());
        player.displayClientMessage(Component.literal(
                "§5【梦境】世界安静下来了。" + frozenCount(player) + " 只生物也睡着了。"), true);
    }

    private static void saveStart(ServerPlayer player, CompoundTag data) {
        CompoundTag start = new CompoundTag();
        start.putString("dim", player.level().dimension().location().toString());
        start.putDouble("x", player.getX());
        start.putDouble("y", player.getY());
        start.putDouble("z", player.getZ());
        start.putFloat("yaw", player.getYRot());
        start.putFloat("pitch", player.getXRot());
        data.put(TAG_START, start);
    }

    /** 梦的圆心：入睡点；老存档没有记录时退回玩家当前位置。 */
    private static net.minecraft.world.phys.Vec3 dreamCenter(ServerPlayer player, CompoundTag data) {
        if (data.contains(TAG_START, Tag.TAG_COMPOUND)) {
            CompoundTag start = data.getCompound(TAG_START);
            boolean sameDim = player.level().dimension().location().toString().equals(start.getString("dim"));
            if (sameDim) {
                return new net.minecraft.world.phys.Vec3(
                        start.getDouble("x"), start.getDouble("y"), start.getDouble("z"));
            }
        }
        return player.position();
    }

    /** 把 32 格内还没被冻住的生物补进冰冻集合。 */
    private static void freezeAround(ServerPlayer player, CompoundTag data, Set<UUID> frozen,
                                     Set<UUID> flipped) {
        Level level = player.level();
        // 2026-11-10：梦的领域以入睡点为圆心（不是跟着玩家走），否则玩家往前飞 30 格，
        // 冻结圈也跟着往前推 30 格，等于一路推平。
        net.minecraft.world.phys.Vec3 center = dreamCenter(player, data);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
                center.x + RADIUS, center.y + RADIUS, center.z + RADIUS);
        for (Entity entity : level.getEntities(player, box)) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (mob.position().distanceToSqr(center) > RADIUS * RADIUS) {
                continue;
            }
            if (frozen.add(mob.getUUID())) {
                // 只关「本来能动的」的 AI；本来就没 AI 的（训练假人）别碰，否则梦醒后会活过来
                if (!mob.isNoAi()) {
                    mob.setNoAi(true);
                    flipped.add(mob.getUUID());
                }
                mob.setTarget(null);
            }
            // 不燃烧、不被点燃：梦里没有火这回事
            mob.clearFire();
            mob.setRemainingFireTicks(0);
            // 受击动画照放，但别被击退滑走（每 5 刻钉一次速度）
            mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
            // 兜底：有不走事件、直接灌真伤的技能（作者报的「断耀流光」），
            // 血条每 5 刻回满一次，配合下面 onDreamDeath 的死亡拦截，梦里就死不掉了
            if (mob.getHealth() < mob.getMaxHealth()) {
                mob.setHealth(mob.getMaxHealth());
            }
        }
        data.put(TAG_MOBS, uuidList(frozen));
        data.put(TAG_NOAI_FLIPPED, uuidList(flipped));
    }

    private static ListTag uuidList(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        return list;
    }

    /** 从玩家数据里读回被冻住的生物。 */
    private static Set<UUID> frozenIds(Player player) {
        return readIds(player.getPersistentData(), TAG_MOBS);
    }

    /** 从玩家数据里读回「被我们关掉 AI 的」生物。 */
    private static Set<UUID> flippedIds(Player player) {
        return readIds(player.getPersistentData(), TAG_NOAI_FLIPPED);
    }

    private static Set<UUID> readIds(CompoundTag data, String key) {
        Set<UUID> ids = new HashSet<>();
        if (!data.contains(key, Tag.TAG_LIST)) {
            return ids;
        }
        ListTag list = data.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                ids.add(entry.getUUID("id"));
            }
        }
        return ids;
    }

    private static Entity findEntity(Player player, UUID id) {
        if (player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        // 每 2 秒补发一次「你在梦里」，客户端那条包万一丢了也不会卡在撞墙状态
        if (player instanceof ServerPlayer serverPlayer && dreaming(player) && player.tickCount % 40 == 0) {
            sendState(serverPlayer, true);
        }
        if (player.tickCount % FREEZE_INTERVAL != 0) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long until = data.getLong(TAG_UNTIL);
        if (until == 0L) {
            return;
        }
        if (player.level().getGameTime() >= until) {
            wake(player);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            freezeAround(serverPlayer, data, frozenIds(player), flippedIds(player));
        }
    }

    /**
     * 每刻重压一次「无碰撞」。
     *
     * <p>为什么不能用 {@code TickEvent.PlayerTickEvent}：原版 {@code Player#tick()} 的
     * 第一件事就是 {@code this.noPhysics = this.isSpectator();}，而 {@code PlayerTickEvent.END}
     * 在那之后、且晚于本刻移动；下一刻开头又被重置 —— 于是整个梦游期间
     * {@code noPhysics} 在「真正移动的那一刻」永远是 false，穿墙自然无效（作者 m12495 报的就是它）。
     * {@code LivingEvent.LivingTickEvent} 由 {@code ForgeHooks.onLivingTick} 在
     * {@code LivingEntity#tick()} 的第一条指令触发，**晚于** {@code Player#tick()} 的这次重置，
     * 又早于后面的 {@code aiStep()} / {@code travel()}，所以这里设的值能被本刻的移动吃到。</p>
     */
    @SubscribeEvent
    public static void onDreamLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (!dreaming(player)) {
            return;
        }
        // 2026-11-10（作者 m18917）：梦是「以入睡点为圆心、半径 32 的领域」，不是跟着玩家走的雪球。
        // 玩家自己飘出这个圈，就等于离开了梦 —— 直接醒（梦醒会把人送回入睡点），
        // 否则就是一个可以无限拖着走的 32 格冻结领域。
        CompoundTag data = player.getPersistentData();
        if (data.contains(TAG_START, Tag.TAG_COMPOUND)) {
            CompoundTag start = data.getCompound(TAG_START);
            boolean sameDim = player.level().dimension().location().toString().equals(start.getString("dim"));
            if (sameDim) {
                double dx = player.getX() - start.getDouble("x");
                double dy = player.getY() - start.getDouble("y");
                double dz = player.getZ() - start.getDouble("z");
                if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) {
                    player.displayClientMessage(Component.literal("§5【梦境】你飘出了梦的边界，于是醒了。"), true);
                    wake(player);
                    return;
                }
            }
        }
        player.noPhysics = true;
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
    }

    /** 让客户端也把碰撞与飞行改掉（见过「服务端在梦里、客户端还在撞墙」的那种）。 */
    private static void sendState(ServerPlayer player, boolean active) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NetworkHandler.DreamStatePacket(active));
    }

    /** 梦醒：恢复身体、解冻生物、回到入睡的位置、把 30% 的伤害结算到现实。 */
    public static void wake(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(TAG_UNTIL);
        player.setInvulnerable(false);
        player.noPhysics = false;
        player.setNoGravity(false);
        player.getAbilities().flying = false;
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
        }
        player.onUpdateAbilities();
        if (player instanceof ServerPlayer serverPlayer) {
            sendState(serverPlayer, false);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(data.getDouble(TAG_BASE_SPEED));
        }

        Set<UUID> frozen = frozenIds(player);
        Set<UUID> flipped = flippedIds(player);
        data.remove(TAG_MOBS);
        data.remove(TAG_NOAI_FLIPPED);
        for (UUID id : frozen) {
            Entity entity = findEntity(player, id);
            // 只还原「是我们关掉的」；本来就没 AI 的保持原样
            if (entity instanceof Mob mob && flipped.contains(id)) {
                mob.setNoAi(false);
            }
        }

        settlePending(player);
        if (player instanceof ServerPlayer serverPlayer) {
            teleportHome(serverPlayer, data);
        }
        data.remove(TAG_START);
        player.displayClientMessage(Component.literal("§7【梦境】你醒了。"), true);
    }

    /** 回到入睡时站着的位置。 */
    private static void teleportHome(ServerPlayer player, CompoundTag data) {
        if (player.getServer() == null || !data.contains(TAG_START, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag start = data.getCompound(TAG_START);
        ResourceKey<Level> key;
        try {
            key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(start.getString("dim")));
        } catch (RuntimeException e) {
            return;
        }
        ServerLevel target = player.getServer().getLevel(key);
        if (target == null) {
            return;
        }
        player.teleportTo(target, start.getDouble("x"), start.getDouble("y"), start.getDouble("z"),
                start.getFloat("yaw"), start.getFloat("pitch"));
    }

    private static void settlePending(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_PENDING, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = data.getList(TAG_PENDING, Tag.TAG_COMPOUND);
        Map<UUID, Float> totals = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID id = entry.getUUID("id");
            totals.merge(id, entry.getFloat("dmg"), Float::sum);
        }
        data.remove(TAG_PENDING);
        if (player.getServer() == null) {
            return;
        }
        DamageSource source = player.damageSources().indirectMagic(player, player);
        for (Map.Entry<UUID, Float> e : totals.entrySet()) {
            Entity entity = findEntity(player, e.getKey());
            if (entity != null && entity.isAlive()) {
                entity.hurt(source, e.getValue());
            }
        }
    }

    /**
     * 梦里的伤害不落地，但**保留受击动画与音效**（作者 m11415：要有受击动画与音效）。
     *
     * <p>所以这里只做两件事：①玩家在梦里打出的伤害记 30%；②梦里的生物被别人打时直接取消。
     * 真正把血条按住的是 {@link #onDreamFinalDamage(LivingDamageEvent)}——它发生在原版
     * 扣血之前，`setAmount(0)` 之后动画、音效、击退标记都已经放过了，只有血量不减。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDreamDamage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target instanceof Player player && dreaming(player)) {
            // 梦里不会被任何人打到（包括环境）
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }
        if (event.getSource().getEntity() instanceof Player attacker && dreaming(attacker)) {
            if (target == attacker) {
                return;
            }
            // 2026-11-10（外部审查 + 作者 m18864）：梦只覆盖 32 格。以前这里对「梦者打到的
            // 任何目标」都记 30%，包括 32 格外射中的远程目标 —— 那种目标不在梦里、
            // 伤害照常落地，醒来再补 30% 就变成 130%。现在只有真在梦里的目标才记账。
            if (target.distanceToSqr(attacker) > RADIUS * RADIUS || frozenByAnyDreamer(target) == null) {
                return;
            }
            // 玩家在梦里造成的伤害：只记 30%，血条交给 LivingDamageEvent 清零
            CompoundTag data = attacker.getPersistentData();
            ListTag list = data.contains(TAG_PENDING, Tag.TAG_LIST)
                    ? data.getList(TAG_PENDING, Tag.TAG_COMPOUND)
                    : new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", target.getUUID());
            entry.putFloat("dmg", (float) (event.getAmount() * DAMAGE_CARRY));
            list.add(entry);
            data.put(TAG_PENDING, list);
            return;
        }
        // 梦里的生物：别人怎么打都打不动（连事件都不该往下走）
        if (frozenByAnyDreamer(target) != null) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    /** 梦里的生物血条不动，但受击动画/音效/击退标记已经发生过了。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDreamFinalDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (frozenByAnyDreamer(event.getEntity()) != null) {
            event.setAmount(0.0F);
            // 顺手把血补满：有技能绕过事件直接扣血时，这里是最靠近「扣血之前」的一站
            LivingEntity target = event.getEntity();
            if (target.getHealth() < target.getMaxHealth()) {
                target.setHealth(target.getMaxHealth());
            }
        }
    }

    /**
     * 兜底：有的技能（作者报的「断耀流光」）不走 {@code LivingHurtEvent} 的普通路子，
     * 直接按真伤灌下去甚至直接扣死。这里拦死亡并把血补满 —— 梦里死不掉这件事比
     * 「伤害数字不显示」重要得多。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDreamDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (dying.level().isClientSide()) {
            return;
        }
        if (frozenByAnyDreamer(dying) != null) {
            event.setCanceled(true);
            dying.setHealth(dying.getMaxHealth());
            // 有的技能先把血拍成 0 再直接触发死亡：只把血补回来还不够，
            // deathTime 一旦开始数就会在 20 刻后被 remove（表现为「补了血照样死」）。
            dying.deathTime = 0;
        }
    }

    /**
     * 这只生物是否正被某个做梦的玩家冻着。
     *
     * <p>两套判定都要：①在冻结名单里（正常路径）；②就在做梦玩家 32 格内、且是活的
     * {@link Mob}（兜底）。第二套是为了「刚生成、还没来得及进名单」或者
     * 「名单写失败」的生物 —— 作者报的「断曜流光（诡厄巫法启示录）一发就把梦里的生物打死」
     * 就是这类漏网：技能打到的不是名单里的那只。</p>
     */
    private static ServerPlayer frozenByAnyDreamer(Entity entity) {
        if (entity.getServer() == null) {
            return null;
        }
        for (ServerPlayer candidate : entity.getServer().getPlayerList().getPlayers()) {
            if (!dreaming(candidate)) {
                continue;
            }
            if (frozenIds(candidate).contains(entity.getUUID())) {
                return candidate;
            }
            if (entity instanceof Mob mob
                    && mob.isAlive()
                    && mob.level() == candidate.level()
                    && mob.distanceToSqr(candidate) <= RADIUS * RADIUS) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 梦里的生物不接受别人的攻击。
     *
     * <p>{@link LivingAttackEvent} 是伤害链最前面那一道，连
     * 「无视无敌帧（bypasses_invulnerability）」的技能也要先过它 —— 所以第三方的
     * 一次性大招在这里就被挡掉，不用等 {@code LivingDamageEvent} 去清血量。
     * 做梦的玩家自己打仍然放行，那条路保留受击动画与音效。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDreamAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target instanceof Player) {
            return;
        }
        if (frozenByAnyDreamer(target) == null) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player attacker && dreaming(attacker)) {
            return;
        }
        event.setCanceled(true);
    }

    /** 下线/重生时务必收尾，否则飞行、无敌与「回到原处」会跟着存档走。 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && player.getPersistentData().getLong(TAG_UNTIL) != 0L) {
            wake(serverPlayer);
        }
    }

    /** 让调试消息与 `/ys` 能报出还冻着几只。 */
    static int frozenCount(Player player) {
        return frozenIds(player).size();
    }
}
