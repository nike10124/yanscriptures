package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 九本名著饰品的全部机制（按作者 2026-10-29 定稿的「佩戴效果」实现）。
 *
 * <p>设计约定：一本书一件事；数值集中在常量里；跨模组只走语义判断
 * （生命值倍数、伤害类型标签、维度集合、属性注册表），不写死别人的 ID。
 *
 * <table border="1">
 *   <caption>九本书一览</caption>
 *   <tr><th>书名</th><th>一句话机制</th></tr>
 *   <tr><td>道德经</td><td>空血不死，受击升级生命回复，回满即死（最长 30 秒）；常驻再生 II</td></tr>
 *   <tr><td>理想国</td><td>攻击时留下影子，打影子等于打本体（影子待实现）</td></tr>
 *   <tr><td>查拉图斯特拉</td><td>十诫失效，按祝福/诅咒条数改写攻防血速</td></tr>
 *   <tr><td>神曲</td><td>Shift+T 随机穿越维度，落地给 60 秒增益；移速 +15%</td></tr>
 *   <tr><td>堂吉诃德</td><td>对生命倍数远高于自己的敌人增伤；骑马抗性 III</td></tr>
 *   <tr><td>百年孤独</td><td>独处增伤减伤；背包越空伤害越高，背包越满越看不清物品</td></tr>
 *   <tr><td>几何原本</td><td>五公理挡致命伤，五公设随公理减少；600 秒重置</td></tr>
 *   <tr><td>物种起源</td><td>同类伤害适应、环境减伤、二次击杀过的首领增伤 100%</td></tr>
 *   <tr><td>九章算术</td><td>按斐波那契顺序使用快捷栏物品叠加增伤，乱序则重置并罚 10%</td></tr>
 * </table>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicBooksHandler {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * 玩家下线时清掉按 UUID 记的账（2026-11-10，D 批「状态残留」）。
     *
     * <p>这些表原来只增不减：开服务器时长住离线玩家的数据，
     * 重新进服还可能继承上一轮的「一刀两笔」去重标记。</p>
     */
    public static void cleanupPlayer(java.util.UUID id) {
        DOUBLE_SWING_TICK.remove(id);
        DOUBLE_SETTLED.remove(id);
        BODY_HIT_TARGET.remove(id);
        BODY_HIT_AT.remove(id);
        SHADOW_BY_TARGET.remove(id);
    }

    /** 九本名著的功能总开关。 */
    public static final boolean ENABLED = true;

    // ==================== 《道德经》 ====================
    /** 空血状态最长持续 30 秒。 */
    private static final int DAO_EMPTY_TICKS = 30 * 20;
    private static final String DAO_EMPTY = "DaoEmpty";
    private static final String DAO_START = "DaoStart";
    /** 受击次数 = 额外叠加的再生等级。 */
    private static final String DAO_AMP = "DaoAmp";
    private static final String DAO_DYING = "DaoDying";
    /** 必死判定的重试次数（被外部拦截时每刻补一刀）。 */
    private static final String DAO_RETRY = "DaoRetry";
    private static final int DAO_RETRY_MAX = 60;

    /**
     * 空血 30 秒结束时的「死亡线」：低于它就死。
     *
     * <p>取「5 点」与「上限的 10%」里更大的那个 —— 只有 20 点生命的原版玩家用 5 点这条，
     * 血量上限被模组拉到 100 以上时才轮到百分比那条。之所以要这条线：
     * 有些模组会把生命值固定为 1（无敌饰品），于是「空血」期间永远看不出危险，
     * 30 秒结束时也必须照样死，否则就是无敌。
     */
    private static final float DAO_DEATH_MIN = 5.0F;
    private static final double DAO_DEATH_RATIO = 0.10D;

    /**
     * 空血期间的“血量地板”。
     *
     * <p>真正的 0 血会让客户端判定玩家已死、弹出死亡界面；而服务端这边
     * {@code LivingDeathEvent} 又被取消，于是客户端停在死亡画面、服务端把玩家当成活人，
     * 再点“重生”就会错位成「原地复活、周围怪物不同步、什么都点不动」。
     * 所以屏幕上的“空血”用 0.5 点生命表示：血条看起来是空的，但玩家仍然活着。
     */
    private static final float DAO_HEALTH_FLOOR = 0.5F;

    // ==================== 《理想国》 ====================

    /** 攻击力与生命值各 +10%。 */
    private static void tickRepublic(Player player) {
        // 2026-11-01：+10% 攻/血改成饰品自身的属性修饰符（ClassicBookItem.getAttributeModifiers），
        // 由 Curios 挂到玩家身上并显示在「当装备在书籍栏：」里；这里只负责把超出的血压回上限。
        if (!wears(player, ClassicBook.REPUBLIC)) {
            return;
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 这只生物是不是《理想国》投下的影子。 */
    public static boolean isRepublicShadow(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(SHADOW_TAG);
    }

    /**
     * 在被打的那只生物旁边投下它的影子。
     *
     * <p>影子是这只生物的一份「替身」：同一种实体、关 AI、不发声，站在它旁边不动。
     * 玩家或环境对影子造成的伤害会等量转给本体，影子自己不掉血。
     */
    private static void spawnRepublicShadow(Player player, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (isRepublicShadow(target)) {
            return;
        }
        // 同一本体最多一份影子：查表（O(1)）而不是在 8 格内扫一遍实体
        UUID tracked = SHADOW_BY_TARGET.get(target.getUUID());
        if (tracked != null) {
            var existing = level.getEntity(tracked);
            if (existing != null && existing.isAlive()) {
                return;
            }
            SHADOW_BY_TARGET.remove(target.getUUID());
        }

        net.minecraft.world.entity.Entity created = target.getType().create(level);
        if (!(created instanceof LivingEntity shadow)) {
            return;
        }

        // 站在本体朝玩家那一侧，看起来像本体投出来的
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            len = 1.0D;
        }
        double x = target.getX() + dx / len * 1.1D;
        double z = target.getZ() + dz / len * 1.1D;
        BlockPos spot = BlockPos.containing(x, target.getY(), z);
        if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
            // 旁边站不下就贴着本体生成
            x = target.getX();
            z = target.getZ();
        }
        shadow.moveTo(x, target.getY(), z, target.getYRot(), 0.0F);

        if (shadow instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setTarget(null);
            mob.setCanPickUpLoot(false);
        }
        shadow.setSilent(true);
        shadow.setInvulnerable(false);
        if (shadow.getAttribute(Attributes.MAX_HEALTH) != null) {
            shadow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(target.getMaxHealth());
        }
        shadow.setHealth(shadow.getMaxHealth());

        CompoundTag data = shadow.getPersistentData();
        data.putBoolean(SHADOW_TAG, true);
        data.putUUID(SHADOW_OF, target.getUUID());
        data.putInt(SHADOW_LIFE, SHADOW_LIFETIME);
        level.addFreshEntity(shadow);
        SHADOW_BY_TARGET.put(target.getUUID(), shadow.getUUID());
    }

    /** 影子挨打：只认玩家与环境，伤害等量转给本体，影子本身不掉血。 */
    private static boolean handleShadowHurt(LivingHurtEvent event) {
        LivingEntity shadow = event.getEntity();
        if (!isRepublicShadow(shadow)) {
            return false;
        }
        float amount = event.getAmount();
        event.setCanceled(true);
        event.setAmount(0.0F);
        // 影子自己不吃伤害，但 hurt() 已经把它的无敌帧设成 20 刻了，下一次横扫就打不出
        // LivingHurtEvent —— 于是「一剑同时扫到本体的影子」只剩一下伤害。
        // 影子本来就不掉血，无敌帧对它没有意义，清零即可。
        shadow.invulnerableTime = 0;

        DamageSource source = event.getSource();
        boolean fromPlayer = source.getEntity() instanceof Player;
        boolean environment = !(source.getEntity() instanceof LivingEntity)
                && !(source.getDirectEntity() instanceof LivingEntity);
        if (!fromPlayer && !environment) {
            return true;
        }

        LivingEntity original = ownerOf(shadow);
        if (original != null && original.isAlive()) {
            // 一拳打两下的玩法（作者 m11297）：靠站位让本体也落进这一刀的范围，
            // 就多结算一笔 —— 但**只能多一笔**。
            //
            // 之前的写法是「只要 inSwing 就再转一次」，于是同一刀被记成三笔：
            // 本体自己那笔 + 影子转过来那笔 + 这里硬塞的一笔，玩家看到的就是
            // 「有时候连续掉两次血」。现在改成：影子这一笔照常转，另外那一笔
            // 由「本体是否也在范围里」决定，且同一 tick 只补一次。
            boolean bothInSwing = source.getEntity() instanceof Player attacker && inSwing(attacker, original);
            transferShadowDamage(shadow, original, source, amount);
            if (bothInSwing && markDouble((Player) source.getEntity(), original)
                    && !bodyHitThisTick((Player) source.getEntity(), original)) {
                transferShadowDamage(shadow, original, source, amount);
            }
        }
        return true;
    }

    /** 同一 tick 只允许补一笔「同扫」伤害，防止一刀被记成三笔。 */
    private static final java.util.Map<java.util.UUID, Integer> DOUBLE_SWING_TICK = new java.util.HashMap<>();

    /** 这一笔「同扫」补给的是谁：补过之后，本体自己那笔要在同一 tick 里让掉。 */
    private static final java.util.Map<java.util.UUID, java.util.UUID> DOUBLE_SETTLED = new java.util.HashMap<>();

    /** 本体在这一 tick 有没有被这个玩家本人打过（不管事件先后，用来判断「第二笔要不要补」）。 */
    private static final java.util.Map<java.util.UUID, java.util.UUID> BODY_HIT_TARGET = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> BODY_HIT_AT = new java.util.HashMap<>();

    private static void recordBodyHit(Player player, LivingEntity target) {
        BODY_HIT_TARGET.put(player.getUUID(), target.getUUID());
        BODY_HIT_AT.put(player.getUUID(), player.tickCount);
        if (BODY_HIT_AT.size() > 64) {
            BODY_HIT_TARGET.clear();
            BODY_HIT_AT.clear();
        }
    }

    /**
     * 这一 tick 本体是不是已经被这个玩家自己打过一笔了。
     *
     * <p>顺序无关的关键：横扫可能先打本体、也可能先结算影子。先打本体时，影子那笔转伤
     * 本身已经是「第二笔」，再补就是第三笔 —— 所以补之前先问这一句。</p>
     */
    private static boolean bodyHitThisTick(Player player, LivingEntity original) {
        Integer at = BODY_HIT_AT.get(player.getUUID());
        return at != null && at == player.tickCount
                && original.getUUID().equals(BODY_HIT_TARGET.get(player.getUUID()));
    }

    private static boolean markDouble(Player attacker, LivingEntity original) {
        if (attacker == null) {
            return false;
        }
        int tick = attacker.tickCount;
        Integer last = DOUBLE_SWING_TICK.get(attacker.getUUID());
        if (last != null && last == tick) {
            return false;
        }
        DOUBLE_SWING_TICK.put(attacker.getUUID(), tick);
        DOUBLE_SETTLED.put(attacker.getUUID(), original.getUUID());
        if (DOUBLE_SWING_TICK.size() > 64) {
            DOUBLE_SWING_TICK.clear();
            DOUBLE_SETTLED.clear();
        }
        return true;
    }

    /**
     * 同一 tick 里这一刀是否已经由影子那边补足了第二笔。
     *
     * <p>扫到影子时我们已经硬结算了一笔，紧接着原版横扫还会再打本体一笔 ——
     * 那一笔必须让掉，否则又是三笔。判断只做「同 tick + 同一个本体」，
     * 不误伤同一刀扫到的其它怪。</p>
     */
    private static boolean isDoubleSettled(Player player, LivingEntity target) {
        if (!DOUBLE_SETTLED.containsKey(player.getUUID())) {
            return false;
        }
        Integer tick = DOUBLE_SWING_TICK.get(player.getUUID());
        return tick != null && tick == player.tickCount
                && target.getUUID().equals(DOUBLE_SETTLED.get(player.getUUID()));
    }

    /** 本体是否也在玩家这一刀能扫到的范围里：贴身（1 格内）且大致在面前。 */
    private static boolean inSwing(Player player, LivingEntity original) {
        if (!player.getBoundingBox().inflate(1.25D).intersects(original.getBoundingBox())) {
            return false;
        }
        var look = player.getLookAngle();
        var dir = new net.minecraft.world.phys.Vec3(
                original.getX() - player.getX(), 0.0D, original.getZ() - player.getZ());
        if (dir.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return look.dot(dir.normalize()) > 0.0D;
    }

    /** 把影子接到的一笔伤害转给本体，绕开本体身上的无敌帧。 */
    private static void transferShadowDamage(LivingEntity shadow, LivingEntity original,
                                            DamageSource source, float amount) {
        original.invulnerableTime = 0;
        transferring = true;
        try {
            original.hurt(shadow.damageSources().indirectMagic(shadow, source.getEntity()), amount);
        } finally {
            transferring = false;
            original.invulnerableTime = 0;
        }
    }

    /** 影子自己倒计时，也在本体死亡后自动消散。 */
    @SubscribeEvent
    public static void onShadowTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!ENABLED || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity shadow = event.getEntity();
        if (!isRepublicShadow(shadow)) {
            return;
        }
        CompoundTag data = shadow.getPersistentData();
        int left = data.getInt(SHADOW_LIFE) - 1;
        data.putInt(SHADOW_LIFE, left);
        if (left <= 0) {
            shadow.discard();
            return;
        }
        if (ownerOf(shadow) == null) {
            SHADOW_BY_TARGET.remove(data.getUUID(SHADOW_OF));
            shadow.discard();
        }
    }

    // ==================== 《查拉图斯特拉如是说》 ====================
    /** 「本体 UUID -> 影子 UUID」。只用来去重与清理，不参与任何掉落/计数。 */
    private static final Map<UUID, UUID> SHADOW_BY_TARGET = new HashMap<>();

    /**
     * 影子的本体。
     *
     * <p>{@code ServerLevel#getEntity(UUID)} 是哈希查找，比在 64 格里扫实体便宜得多；
     * 影子与本体永远在同一个维度（本体换维度时影子会自己消散）。
     */
    private static LivingEntity ownerOf(LivingEntity shadow) {
        if (!(shadow.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID id = shadow.getPersistentData().getUUID(SHADOW_OF);
        if (id == null) {
            return null;
        }
        var entity = level.getEntity(id);
        return entity instanceof LivingEntity living && !isRepublicShadow(living) ? living : null;
    }

    /** 每条被禁的诅咒：全属性 -3%。 */
    private static final float ZAR_CURSE = 0.03F;
    /** 每条被禁的祝福：全属性 +4%。 */
    private static final float ZAR_BLESSING = 0.04F;
    private static final UUID ZAR_ATK = UUID.nameUUIDFromBytes("classic_zar_atk".getBytes());
    private static final UUID ZAR_HEALTH = UUID.nameUUIDFromBytes("classic_zar_health".getBytes());
    private static final UUID ZAR_ARMOR = UUID.nameUUIDFromBytes("classic_zar_armor".getBytes());
    private static final UUID ZAR_SPEED = UUID.nameUUIDFromBytes("classic_zar_speed".getBytes());

    // ==================== 《神曲》 ====================
    /** 传送后的增益持续 60 秒。 */
    private static final int COMEDY_BUFF_TICKS = 60 * 20;
    /** 冷却 180 秒。 */
    private static final int COMEDY_COOLDOWN_TICKS = 180 * 20;

    /** 神曲落点取样：在维度出生点周围 ±SPOT_SPREAD 里试 SPOT_ATTEMPTS 次。 */
    private static final int SPOT_SPREAD = 2000;
    /** 本次传送是否是靠兜底平台落下去的（用来给玩家说一句）。 */
    private static boolean landingBuilt = false;
    private static final int SPOT_ATTEMPTS = 24;

    /**
     * 一次传送里允许同步生成的区块数上限。
     *
     * <p>同步生成发生在服务端主线程，一块约 10~100 ms；以前 24 次采样每次都生成，
     * 最坏能卡住主线程两秒以上。现在只在「已加载区块全都找不到落点」时生成一块，
     * 剩下的交给头顶那块兜底落脚石。
     */
    private static final int SPOT_MAX_SYNC_GENERATE = 1;
    private static final String COMEDY_READY_AT = "ComedyReadyAt";
    // 移速 +15% 的修饰符已搬到 ClassicBookItem.getAttributeModifiers（常量 COMEDY_SPEED_PERCENT）。

    // ==================== 《堂吉诃德》 ====================
    private static final float QUIXOTE_LOW_RATIO = 10.0F;
    private static final float QUIXOTE_HIGH_RATIO = 20.0F;
    private static final float QUIXOTE_MIN_BONUS = 0.20F;
    private static final float QUIXOTE_MAX_BONUS = 0.50F;
    private static final int QUIXOTE_RESISTANCE_AMP = 2;

    // ==================== 《理想国》 ====================
    // 攻/血 +10% 的修饰符已搬到 ClassicBookItem.getAttributeModifiers
    // （常量 REPUBLIC_ATK_PERCENT / REPUBLIC_HP_PERCENT）。
    private static final String SHADOW_TAG = "YanRepublicShadow";
    private static final String SHADOW_OF = "YanShadowOf";
    private static final String SHADOW_LIFE = "YanShadowLife";
    private static final int SHADOW_LIFETIME = 200;
    /** 正在把影子的伤害转给本体：这一下不要再乘一次进攻端加成。 */
    private static boolean transferring = false;

    /** 当前这次 `hurt()` 是不是《理想国》影子转过来的那一笔（排查工具用）。 */
    static boolean isTransferring() {
        return transferring;
    }

    // ==================== 《百年孤独》 ====================
    private static final double SOLITUDE_RADIUS = 24.0D;
    private static final float SOLITUDE_BONUS = 0.20F;
    /** 背包接近空时的最大增伤。 */
    private static final float LONELY_MAX_BONUS = 0.40F;
    /** 少于这么多件就是满额增伤。 */
    private static final int LONELY_FEW_ITEMS = 5;
    /** 背包主格 27 + 快捷栏 9。 */
    private static final int LONELY_SLOTS = 36;
    private static final int LONELY_HIDE_ITEMS = LONELY_SLOTS / 2;

    // ==================== 《几何原本》 ====================
    private static final int EUCLID_AXIOMS = 5;
    private static final String EUCLID_LEFT = "EucAxioms";
    private static final String EUCLID_RESET_AT = "EucResetAt";
    /** 第一条公理被消耗后 600 秒重置。 */
    private static final int EUCLID_RESET_TICKS = 600 * 20;
    private static final float EUCLID_INCOMING = 0.10F;
    private static final float EUCLID_OUTGOING = 0.10F;
    private static final float EUCLID_EXP = 0.20F;
    private static final float EUCLID_SINGLE_HIT_CAP = 0.90F;
    private static final UUID EUCLID_BLOCK_REACH = UUID.nameUUIDFromBytes("classic_euc_block_reach".getBytes());
    private static final UUID EUCLID_ENTITY_REACH = UUID.nameUUIDFromBytes("classic_euc_entity_reach".getBytes());
    private static final String EUCLID_BREAK = "EucBreak";

    // ==================== 《物种起源》 ====================
    private static final int ORIGIN_HITS_TO_ADAPT = 10;
    private static final float ORIGIN_REDUCTION = 0.25F;
    private static final float ORIGIN_ENVIRONMENT_REDUCTION = 0.50F;
    /** 二次击杀过的首领，之后对它增伤 100%。 */
    private static final float ORIGIN_BOSS_BONUS = 1.00F;
    private static final int ORIGIN_BOSS_KILLS = 2;
    /** 首领的语义判定：敌对生物且最大生命值不低于这个数。 */
    private static final float ORIGIN_BOSS_MIN_HEALTH = 100.0F;
    private static final String ORIGIN_BOSS_PREFIX = "OriginBoss_";
    private static final String ORIGIN_BOSS_DONE = "OriginBossDone_";

    // ==================== 《九章算术》 ====================
    /** 斐波那契格位：快捷栏第 1、1、2、3、5、8 格（0 基下标）。 */
    private static final int[] NINE_SLOTS = {0, 0, 1, 2, 4, 7};
    /** 每一步给该格物品的增伤。 */
    private static final float[] NINE_VALUES = {0.10F, 0.10F, 0.20F, 0.30F, 0.50F, 0.80F};
    /** 乱序的惩罚。 */
    private static final float NINE_PENALTY = 0.10F;
    private static final String NINE_STEP = "NineStep";
    private static final String NINE_PENALTY_KEY = "NinePenalty";
    private static final String NINE_BONUS_PREFIX = "NineB_";

    private ClassicBooksHandler() {
    }

    // ==================== 事件入口 ====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 影子的伤害单独处理，不再往下走
        if (handleShadowHurt(event)) {
            return;
        }

        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }

        // ===== 防守端 =====
        if (event.getEntity() instanceof Player defender) {
            // 《道德经》空血：先吃掉这一击，再升级再生（“到点真死”那一下不接管）
            if (wears(defender, ClassicBook.DAO_DE_JING) && isEmptyBlood(defender)
                    && !defender.getPersistentData().getBoolean(DAO_DYING)) {
                onEmptyBloodHurt(defender);
                event.setAmount(0.0F);
                event.setCanceled(true);
                return;
            }
            amount = applyIncoming(defender, event.getSource(), amount);

            // 《道德经》：这一击足以致死 → 进入空血，而不是死亡
            if (wears(defender, ClassicBook.DAO_DE_JING)
                    && !isEmptyBlood(defender)
                    && !defender.getPersistentData().getBoolean(DAO_DYING)
                    && amount >= defender.getHealth()) {
                enterEmptyBlood(defender);
                event.setAmount(0.0F);
                event.setCanceled(true);
                return;
            }

            // 《几何原本》：公理抵挡致命伤
            if (wears(defender, ClassicBook.EUCLID) && amount >= defender.getHealth()) {
                if (consumeAxiom(defender)) {
                    defender.setHealth(1.0F);
                    event.setAmount(0.0F);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // ===== 进攻端 =====
        if (event.getSource().getEntity() instanceof Player attacker) {
            amount = applyOutgoing(attacker, event.getEntity(), amount);
        }

        event.setAmount(Math.max(0.0F, amount));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!ENABLED || event.getEntity().level().isClientSide()) {
            return;
        }
        // 《理想国》的影子只是「本体受伤的投影」，它不能被「杀死」：
        // 这里取消死亡事件并直接 discard，于是掉落、经验、成就、击杀统计、
        // 以及其它模组挂在死亡上的结算全都不会发生在影子上。
        if (isRepublicShadow(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().discard();
            return;
        }
        // 《道德经》：空血期间的死亡一律不算
        if (event.getEntity() instanceof Player player
                && wears(player, ClassicBook.DAO_DE_JING)
                && isEmptyBlood(player)) {
            event.setCanceled(true);
            // 不要写回 0：0 血会触发客户端的死亡界面，而服务端又没有真的死
            player.setHealth(Math.max(player.getHealth(), DAO_HEALTH_FLOOR));
            return;
        }
        // 《物种起源》：记下被击败的首领
        if (event.getSource().getEntity() instanceof Player killer
                && wears(killer, ClassicBook.ORIGIN)) {
            recordBossKill(killer, event.getEntity());
        }
    }

    /** 影子没有任何掉落：它不是一个能被「打死」的生物，只是本体的血条副本。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onShadowDrops(LivingDropsEvent event) {
        if (!isRepublicShadow(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!ENABLED || event.getEntity().level().isClientSide()) {
            return;
        }
        if (isRepublicShadow(event.getEntity())) {
            event.setDroppedExperience(0);
            return;
        }
        Player player = event.getAttackingPlayer();
        if (player == null || !wears(player, ClassicBook.EUCLID)) {
            return;
        }
        if (postulates(player) >= 3) {
            event.setDroppedExperience(Math.round(event.getDroppedExperience() * (1.0F + EUCLID_EXP)));
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!ENABLED || event.getEntity() == null) {
            return;
        }
        Player player = event.getEntity();
        // 《百年孤独》：背包过半时，看不清手里拿的是什么
        if (wears(player, ClassicBook.HUNDRED_YEARS) && itemCount(player) > LONELY_HIDE_ITEMS) {
            // 不能抛 clear()：创造模式背包会往这个列表里 add(1, ...)，
            // 列表被清成 0 长度就抛 IndexOutOfBoundsException: Index: 1, Size: 0。
            // 保留行数、把每行换成空串，效果一样但不再越界。
            List<Component> tooltip = event.getToolTip();
            for (int i = 0; i < tooltip.size(); i++) {
                tooltip.set(i, Component.empty());
            }
            while (tooltip.size() < 2) {
                tooltip.add(Component.empty());
            }
        }
    }

    /** 死亡重生后必须清干净：残留的 DaoEmpty / DaoDying 会让新身体一进去就空血。 */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearDaoState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clearDaoState(event.getEntity());
    }

    private static void clearDaoState(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(DAO_EMPTY);
        data.remove(DAO_START);
        data.remove(DAO_AMP);
        data.remove(DAO_DYING);
        data.remove(DAO_RETRY);
        if (player.getHealth() <= 0.0F && player.isAlive()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }

        WornCache.refresh(player);

        tickDao(player);
        tickRepublic(player);
        tickZarathustra(player);
        tickComedy(player);
        tickQuixote(player);
        tickEuclid(player);
        EuclidAttributes.tick(player);
    }

    // ==================== 《道德经》 ====================

    private static boolean isEmptyBlood(Player player) {
        return player.getPersistentData().getBoolean(DAO_EMPTY);
    }

    private static void enterEmptyBlood(Player player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(DAO_EMPTY, true);
        data.putLong(DAO_START, player.level().getGameTime());
        data.putInt(DAO_AMP, 0);
        player.removeEffect(MobEffects.POISON);
        player.setHealth(DAO_HEALTH_FLOOR);
        player.displayClientMessage(Component.literal(
                "§5【道德经】你已空血：回到满血会死；30 秒结束时空血且生命不足 "
                        + Math.round(daoDeathLine(player)) + " 点，也会死。"), true);
    }

    /** 空血期间每挨一下，再生等级 +1。 */
    private static void onEmptyBloodHurt(Player player) {
        CompoundTag data = player.getPersistentData();
        int amp = Math.min(9, data.getInt(DAO_AMP) + 1);
        data.putInt(DAO_AMP, amp);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, amp, true, false, false));
    }

    private static void tickDao(Player player) {
        boolean worn = wears(player, ClassicBook.DAO_DE_JING);
        CompoundTag data = player.getPersistentData();

        // 「必死」还没真的死透（伤害被外部模组拦了）：每刻补一刀，直到死亡生效。
        if (data.getBoolean(DAO_DYING)) {
            forceDeath(player, data);
            return;
        }

        if (!worn) {
            if (data.getBoolean(DAO_EMPTY)) {
                // 书被摘下：别把人留在 0 血
                data.remove(DAO_EMPTY);
                if (player.getHealth() < 1.0F) {
                    player.setHealth(1.0F);
                }
            }
            return;
        }

        if (!data.getBoolean(DAO_EMPTY)) {
            // 常驻再生 II
            MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
            if (current == null || current.getAmplifier() < 1 || current.getDuration() < 60) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, true, false, false));
            }
            return;
        }

        // ===== 空血状态 =====
        if (player.getHealth() < DAO_HEALTH_FLOOR) {
            player.setHealth(DAO_HEALTH_FLOOR);
        }
        long start = data.getLong(DAO_START);
        long now = player.level().getGameTime();
        long elapsed = now - start;

        // 每秒在快捷栏上方刷一次倒计时，让玩家知道还剩几秒、死亡线在哪
        if (elapsed % 20 == 0) {
            int left = (int) Math.max(0L, (DAO_EMPTY_TICKS - elapsed) / 20);
            player.displayClientMessage(Component.literal(
                    "§5【道德经】空血 " + left + " 秒：回到满血会死，结束时生命不足 "
                            + Math.round(daoDeathLine(player)) + " 点也会死。"), true);
        }

        if (player.getHealth() >= player.getMaxHealth()) {
            // 回到满血 → 死（原规则，不动）
            dieFromEmptyBlood(player);
            return;
        }

        if (elapsed >= DAO_EMPTY_TICKS) {
            if (player.getHealth() + 1.0E-4F < daoDeathLine(player)) {
                // 30 秒结束仍在死亡线以下 → 同样死。
                // 这一条专门对付「把生命固定为 1」的无敌饰品：无论血条被钉在哪，都会走到这里。
                dieFromEmptyBlood(player);
            } else {
                // 撑过了死亡线，但没回满 → 活下来，空血解除
                endEmptyBlood(player, data);
            }
        }
    }

    /** 空血 30 秒结束时的死亡线：5 点与上限 10% 取大者。 */
    private static float daoDeathLine(Player player) {
        return (float) Math.max(DAO_DEATH_MIN, player.getMaxHealth() * DAO_DEATH_RATIO);
    }

    /** 撑过 30 秒、且血量高于死亡线：解除空血，人就活下来了。 */
    private static void endEmptyBlood(Player player, CompoundTag data) {
        data.remove(DAO_EMPTY);
        data.remove(DAO_START);
        data.remove(DAO_AMP);
        player.displayClientMessage(Component.literal("§6【道德经】你撑过了 30 秒。"), true);
    }

    private static void dieFromEmptyBlood(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(DAO_EMPTY);
        data.remove(DAO_START);
        data.remove(DAO_AMP);
        // 这一下是「真的死」：打上标记，onLivingHurt 里就不再把它转成新的空血，
        // onLivingDeath 也不会再取消。
        data.putBoolean(DAO_DYING, true);
        data.putInt(DAO_RETRY, 0);
        forceDeath(player, data);
    }

    /**
     * 强制死亡，且不信任伤害管线。
     *
     * <p>顺序是有讲究的：
     * <ol>
     *   <li>先走正常伤害（{@code genericKill} 无视护甲/抗性/无敌帧），这样绝大多数的死亡结算、
     *       统计、成就都还是原版流程；</li>
     *   <li>没死成，说明有模组拦了 {@code LivingHurtEvent} / {@code LivingDamageEvent}
     *       ——「把生命固定为 1」的饰品就是这类。此时直接用
     *       {@code Player#die(DamageSource)}：它是 {@code public}（javap 确认过），
     *       完全跳过伤害管线，唯一的拦截点是 {@code LivingDeathEvent}；</li>
     *   <li>还活着就记一笔重试，下一 tick 再来，最多 {@link #DAO_RETRY_MAX} 次；
     *       真的被彻底拦住时才放弃并把血补满，避免玩家卡在 0 血不死不活的死相上。</li>
     * </ol>
     */
    private static void forceDeath(Player player, CompoundTag data) {
        if (!player.isAlive()) {
            data.remove(DAO_DYING);
            data.remove(DAO_RETRY);
            return;
        }
        DamageSource source = player.damageSources().genericKill();
        player.hurt(source, Float.MAX_VALUE);
        if (!player.isAlive()) {
            data.remove(DAO_DYING);
            data.remove(DAO_RETRY);
            return;
        }
        // 伤害被拦了 → 走死亡流程本身（不过伤害管线）
        player.die(source);
        if (!player.isAlive()) {
            data.remove(DAO_DYING);
            data.remove(DAO_RETRY);
            return;
        }
        int retry = data.getInt(DAO_RETRY) + 1;
        if (retry > DAO_RETRY_MAX) {
            LOG.warn("《道德经》：{} 的必死判定被外部完全拦截（重试 {} 次后仍存活），"
                    + "本次放弃并把血量补满，避免卡在 0 血", player.getName().getString(), retry);
            data.remove(DAO_DYING);
            data.remove(DAO_RETRY);
            player.setHealth(player.getMaxHealth());
            return;
        }
        data.putInt(DAO_RETRY, retry);
    }

    // ==================== 《查拉图斯特拉如是说》 ====================

    /**
     * 佩戴时十诫整体失效。
     *
     * <p>{@code CommandmentsHandler} 通过 {@code getScripture()} 取圣典来决定是否生效，
     * 这里让它在佩戴期间返回空，所有祝福与诅咒就一起停了。
     */
    public static boolean suppressesCommandments(Player player) {
        return ENABLED && wears(player, ClassicBook.ZARATHUSTRA);
    }

    /** 当前诅咒条数（已违逆且未补全）。 */
    public static int activeCurses(Player player) {
        CompoundTag data = scriptureData(player);
        if (data == null) {
            return 0;
        }
        int curses = 0;
        for (int i = 1; i <= 10; i++) {
            if (data.getBoolean("Curse_" + i)) {
                curses++;
            }
        }
        return curses;
    }

    private static CompoundTag scriptureData(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()))
                .map(slot -> slot.stack().getOrCreateTag())
                .orElse(null);
    }

    private static void tickZarathustra(Player player) {
        boolean worn = wears(player, ClassicBook.ZARATHUSTRA);

        if (!worn) {
            removeModifier(player, Attributes.ATTACK_DAMAGE, ZAR_ATK);
            removeModifier(player, Attributes.MAX_HEALTH, ZAR_HEALTH);
            removeModifier(player, Attributes.ARMOR, ZAR_ARMOR);
            removeModifier(player, Attributes.MOVEMENT_SPEED, ZAR_SPEED);
            return;
        }

        int curses = activeCurses(player);
        int blessings = Math.max(0, 10 - curses);
        double net = blessings * ZAR_BLESSING - curses * ZAR_CURSE;

        setModifier(player, Attributes.ATTACK_DAMAGE, ZAR_ATK, net);
        setModifier(player, Attributes.ARMOR, ZAR_ARMOR, net);
        setModifier(player, Attributes.MOVEMENT_SPEED, ZAR_SPEED, net);
        setModifier(player, Attributes.MAX_HEALTH, ZAR_HEALTH, net);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    // ==================== 《神曲》 ====================

    private static void tickComedy(Player player) {
        // 移速 +15% 同理搬到了 ClassicBookItem.getAttributeModifiers（Curios 负责挂载与显示），
        // 这里不再每刻改属性。
    }

    /** 还能不能穿越（冷却剩余刻数，0 表示可以）。 */
    public static int comedyCooldown(ServerPlayer player) {
        long readyAt = player.getPersistentData().getLong(COMEDY_READY_AT);
        long now = player.level().getGameTime();
        return (int) Math.max(0L, readyAt - now);
    }

    /**
     * Shift+T 的效果：随机穿越到一个「不是当前」的维度，落点找安全位置，
     * 传送后给 60 秒增益，冷却 180 秒。
     */
    public static void teleportAcrossDimensions(ServerPlayer player) {
        if (!wears(player, ClassicBook.DIVINE_COMEDY)) {
            return;
        }
        if (comedyCooldown(player) > 0) {
            player.displayClientMessage(Component.literal("§7【神曲】还差 "
                    + Math.max(1, comedyCooldown(player) / 20) + " 秒。"), true);
            return;
        }

        ServerLevel current = player.serverLevel();
        java.util.List<ServerLevel> candidates = new java.util.ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level != current) {
                candidates.add(level);
            }
        }
        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.literal("§7【神曲】这里没有别的维度可去。"), true);
            return;
        }

        ServerLevel target = candidates.get(player.getRandom().nextInt(candidates.size()));
        BlockPos landing = findSafeSpot(target, player.getRandom());
        if (landing == null) {
            player.displayClientMessage(Component.literal("§7【神曲】那边没有落脚的实心地面。"), true);
            return;
        }

        player.teleportTo(target, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.getPersistentData().putLong(COMEDY_READY_AT,
                target.getGameTime() + COMEDY_COOLDOWN_TICKS);

        int ticks = COMEDY_BUFF_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 2, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0, true, false, true));

        player.displayClientMessage(Component.literal("§6【神曲】你坠入了 "
                + target.dimension().location() + "。"), true);
        if (landingBuilt) {
            player.displayClientMessage(Component.literal("§7那边是一片虚空，已为你铺了一块落脚石。"), true);
        }
    }

    /**
     * 在目标维度里找一个「脚下是实心方块、头顶两格是空」的落点。
     *
     * <p>顺序上刻意分成两段：
     * <ol>
     *   <li><b>先只在已加载的区块里找</b>（{@code level.hasChunk}），这一步不生成任何东西，几乎不花钱；</li>
     *   <li>全都没命中，才允许<b>最多同步生成 {@value #SPOT_MAX_SYNC_GENERATE} 个区块</b>。</li>
     * </ol>
     *
     * <p>为什么不能「只认已加载区块」：玩家没去过的维度一个区块都没加载，
     * {@code level.getHeight(...)} 在未加载的区块上只会返回 {@code minY}，
     * 于是《神曲》在冷门维度里就永远提示「没有落脚的实心地面」（2026-10-30 的实测问题）。
     * 为什么也不能无条件生成：同步生成一整块（10~100 ms）发生在服务端主线程，
     * 24 次采样叠加就是秒级卡顿，多人服务器会直接踢人。
     * 折中就是「已加载优先 + 只额外生成一块 + 实在不行铺落脚石」。
     */
    private static BlockPos findSafeSpot(ServerLevel level, net.minecraft.util.RandomSource random) {
        landingBuilt = false;
        BlockPos spawn = level.getSharedSpawnPos();

        // 第一段：只挑已经加载的区块，不触发任何生成。
        for (int attempt = 0; attempt < SPOT_ATTEMPTS * 2; attempt++) {
            int x = spawn.getX() + random.nextInt(SPOT_SPREAD * 2) - SPOT_SPREAD;
            int z = spawn.getZ() + random.nextInt(SPOT_SPREAD * 2) - SPOT_SPREAD;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos spot = scanColumn(level, x, z);
            if (spot != null) {
                return spot;
            }
        }

        // 第二段：已加载区块里没有合适的地面，才付一次同步生成的代价。
        for (int generated = 0; generated < SPOT_MAX_SYNC_GENERATE; generated++) {
            int x = spawn.getX() + random.nextInt(SPOT_SPREAD * 2) - SPOT_SPREAD;
            int z = spawn.getZ() + random.nextInt(SPOT_SPREAD * 2) - SPOT_SPREAD;
            try {
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            } catch (Exception e) {
                LOG.warn("[yanscriptures] 神曲：区块生成失败 {} {}: {}", x, z, e.toString());
                continue;
            }
            BlockPos spot = scanColumn(level, x, z);
            if (spot != null) {
                return spot;
            }
        }

        landingBuilt = true;
        return buildLanding(level, spawn);
    }

    /** 从世界顶端往下扫，找第一个「实心地板 + 两格空气 + 无流体」的位置。 */
    private static BlockPos scanColumn(ServerLevel level, int x, int z) {
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 2;
        for (int y = top; y > bottom + 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos floor = feet.below();
            if (!level.getBlockState(floor).isSolidRender(level, floor)) {
                continue;
            }
            BlockState at = level.getBlockState(feet);
            BlockState above = level.getBlockState(feet.above());
            if (!at.isAir() || !above.isAir()) {
                continue;
            }
            if (!at.getFluidState().isEmpty() || !above.getFluidState().isEmpty()) {
                continue;
            }
            if (!level.getBlockState(floor).getFluidState().isEmpty()) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /**
     * 兜底：整列都找不到陆地（虚空、纯水、熔岩海）时，在出生点附近铺一块 3×3 的落脚石。
     * 只在「已经随机了 SPOT_ATTEMPTS 次」之后才会走到这里。
     */
    private static BlockPos buildLanding(ServerLevel level, BlockPos spawn) {
        int bottom = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 3;

        int x = spawn.getX();
        int z = spawn.getZ();
        if (!level.hasChunk(x >> 4, z >> 4)) {
            try {
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            } catch (Exception e) {
                LOG.warn("[yanscriptures] 神曲：兜底平台区块生成失败：{}", e.toString());
            }
        }

        int y = Math.min(top, Math.max(bottom + 2, level.getSeaLevel()));
        // 往上找一层不在方块里的高度
        while (y < top && (!level.getBlockState(new BlockPos(x, y, z)).isAir()
                || !level.getBlockState(new BlockPos(x, y + 1, z)).isAir())) {
            y++;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = new BlockPos(x + dx, y - 1, z + dz);
                level.setBlockAndUpdate(floor, Blocks.COBBLESTONE.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            }
        }
        return new BlockPos(x, y, z);
    }

    // ==================== 《堂吉诃德》 ====================

    private static void tickQuixote(Player player) {
        if (wears(player, ClassicBook.DON_QUIXOTE) && player.isPassenger()) {
            MobEffectInstance current = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (current == null || current.getAmplifier() < QUIXOTE_RESISTANCE_AMP
                    || current.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100,
                        QUIXOTE_RESISTANCE_AMP, true, false, false));
            }
        }
    }

    /** 目标生命值相对玩家最大生命的倍数越高，增伤越多（10 倍起，20 倍封顶）。 */
    private static float quixoteBonus(Player player, LivingEntity target) {
        if (player.getMaxHealth() <= 0.0F) {
            return 0.0F;
        }
        float ratio = target.getMaxHealth() / player.getMaxHealth();
        if (ratio < QUIXOTE_LOW_RATIO) {
            return 0.0F;
        }
        if (ratio >= QUIXOTE_HIGH_RATIO) {
            return QUIXOTE_MAX_BONUS;
        }
        float t = (ratio - QUIXOTE_LOW_RATIO) / (QUIXOTE_HIGH_RATIO - QUIXOTE_LOW_RATIO);
        return QUIXOTE_MIN_BONUS + (QUIXOTE_MAX_BONUS - QUIXOTE_MIN_BONUS) * t;
    }

    // ==================== 《百年孤独》 ====================

    private static int itemCount(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** 背包越空，伤害越高：5 件以下满额，36 件时归零，中间线性。 */
    private static float lonelyBonus(Player player) {
        int count = itemCount(player);
        if (count >= LONELY_SLOTS) {
            return 0.0F;
        }
        if (count <= LONELY_FEW_ITEMS) {
            return LONELY_MAX_BONUS;
        }
        float t = (float) (LONELY_SLOTS - count) / (LONELY_SLOTS - LONELY_FEW_ITEMS);
        return LONELY_MAX_BONUS * t;
    }

    private static boolean isAlone(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return serverLevel.getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(SOLITUDE_RADIUS),
                other -> other != player && other.isAlive() && !other.isSpectator()).isEmpty();
    }

    // ==================== 《几何原本》 ====================

    /** 还剩几条公理。 */
    private static int axiomsLeft(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(EUCLID_LEFT)) {
            return EUCLID_AXIOMS;
        }
        return Mth.clamp(data.getInt(EUCLID_LEFT), 0, EUCLID_AXIOMS);
    }

    /** 现存公设数量：公理每少一条，就从最后一公设开始少一项。 */
    private static int postulates(Player player) {
        return axiomsLeft(player);
    }

    private static boolean consumeAxiom(Player player) {
        CompoundTag data = player.getPersistentData();
        int left = axiomsLeft(player);
        if (left <= 0) {
            return false;
        }
        if (!data.contains(EUCLID_RESET_AT) || data.getLong(EUCLID_RESET_AT) == 0L) {
            data.putLong(EUCLID_RESET_AT, player.level().getGameTime());
        }
        data.putInt(EUCLID_LEFT, left - 1);
        data.putInt(EUCLID_BREAK, 1);
        player.displayClientMessage(Component.literal("§b【几何原本】一条公理为你挡住了死亡（剩 "
                + (left - 1) + " 条）。"), true);
        return true;
    }

    private static void tickEuclid(Player player) {
        CompoundTag data = player.getPersistentData();

        // 公理是「用掉就没了」，不能靠穿脱刷新：
        // 以前一摘下就把 EucAxioms / EucResetAt 删掉，于是「挨一下 → 摘下 → 戴上」
        // 就能无限重置五条公理。现在状态一直保留，摘下只是不再关心它。
        long resetAt = data.getLong(EUCLID_RESET_AT);
        boolean timedOut = resetAt > 0L
                && player.level().getGameTime() - resetAt >= EUCLID_RESET_TICKS;
        if (timedOut) {
            data.remove(EUCLID_LEFT);
            data.remove(EUCLID_RESET_AT);
            if (wears(player, ClassicBook.EUCLID)) {
                player.displayClientMessage(Component.literal("§b【几何原本】公理已重新写好。"), true);
            }
        }

        if (!wears(player, ClassicBook.EUCLID)) {
            // 只清「这一击有公理挡住」的临时标记，公理数量与计时保持不动
            data.remove(EUCLID_BREAK);
        }
    }

    // ==================== 《物种起源》 ====================

    private static String typeId(net.minecraft.world.damagesource.DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse(source.getMsgId());
    }

    /** 没有直接来源实体、也没有发射者的伤害，算环境伤害。 */
    private static boolean isEnvironment(net.minecraft.world.damagesource.DamageSource source) {
        return !(source.getEntity() instanceof LivingEntity)
                && !(source.getDirectEntity() instanceof LivingEntity);
    }

    private static boolean isBoss(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.monster.Enemy)) {
            return false;
        }
        if (entity.getType().getCategory() != MobCategory.MONSTER) {
            return false;
        }
        return entity.getMaxHealth() >= ORIGIN_BOSS_MIN_HEALTH;
    }

    private static String bossKey(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? "unknown" : id.toString().replace(':', '_').replace('/', '_');
    }

    private static void recordBossKill(Player player, LivingEntity victim) {
        if (!isBoss(victim)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        String key = bossKey(victim);
        int kills = data.getInt(ORIGIN_BOSS_PREFIX + key) + 1;
        data.putInt(ORIGIN_BOSS_PREFIX + key, kills);
        if (kills == ORIGIN_BOSS_KILLS) {
            data.putBoolean(ORIGIN_BOSS_DONE + key, true);
            player.displayClientMessage(Component.literal("§a【物种起源】你已经学会如何杀死它了。"), true);
        }
    }

    private static boolean hasBossMastery(Player player, LivingEntity target) {
        if (!isBoss(target)) {
            return false;
        }
        return player.getPersistentData().getBoolean(ORIGIN_BOSS_DONE + bossKey(target));
    }

    // ==================== 《九章算术》 ====================

    /**
     * 一次命中，按斐波那契格位推进。
     *
     * @return 乘到这个伤害上的系数
     */
    private static float nineChapters(Player player) {
        CompoundTag data = player.getPersistentData();
        int slot = player.getInventory().selected;
        int step = Mth.clamp(data.getInt(NINE_STEP), 0, NINE_SLOTS.length - 1);

        // 上一击乱序 → 这一击先吃 10% 的惩罚，然后一切清零
        if (data.getBoolean(NINE_PENALTY_KEY)) {
            data.putBoolean(NINE_PENALTY_KEY, false);
            clearNineBonuses(data);
            data.putInt(NINE_STEP, 0);
            return 1.0F - NINE_PENALTY;
        }

        if (slot == NINE_SLOTS[step]) {
            int add = Math.round(NINE_VALUES[step] * 100.0F);
            int total = data.getInt(NINE_BONUS_PREFIX + slot) + add;
            data.putInt(NINE_BONUS_PREFIX + slot, total);
            data.putInt(NINE_STEP, (step + 1) % NINE_SLOTS.length);
            // 本击就吃到本格的加成
            return 1.0F + total / 100.0F;
        }

        // 乱序：之前的收益全部作废，这一击按原样打，下一击再罚
        clearNineBonuses(data);
        data.putInt(NINE_STEP, 0);
        data.putBoolean(NINE_PENALTY_KEY, true);
        return 1.0F;
    }

    private static void clearNineBonuses(CompoundTag data) {
        for (int slot = 0; slot < 9; slot++) {
            data.remove(NINE_BONUS_PREFIX + slot);
        }
    }

    // ==================== 攻防计算 ====================

    private static float applyIncoming(Player player, net.minecraft.world.damagesource.DamageSource source,
                                       float amount) {
        float result = amount;

        // 《百年孤独》：独处时减伤
        if (wears(player, ClassicBook.HUNDRED_YEARS) && isAlone(player)) {
            result *= 1.0F - SOLITUDE_BONUS;
        }

        // 《几何原本》：公设一（受伤 -10%）与公设四（单次伤害不超过最大生命的 90%）
        if (wears(player, ClassicBook.EUCLID)) {
            int postulates = postulates(player);
            if (postulates >= 1) {
                result *= 1.0F - EUCLID_INCOMING;
            }
            if (postulates >= 4) {
                result = Math.min(result, player.getMaxHealth() * EUCLID_SINGLE_HIT_CAP);
            }
        }

        // 《物种起源》：环境伤害减半 + 已适应的伤害类型
        if (wears(player, ClassicBook.ORIGIN)) {
            if (isEnvironment(source)) {
                result *= 1.0F - ORIGIN_ENVIRONMENT_REDUCTION;
            }
            result *= OriginAdaptation.factor(player, source);
        }

        return Math.max(0.0F, result);
    }

    private static float applyOutgoing(Player player, LivingEntity target, float amount) {
        if (transferring) {
            return amount;
        }

        // 影子那边刚补过第二笔，原版横扫紧跟着打本体的那一笔要让掉（否则一刀三笔）
        if (isDoubleSettled(player, target)) {
            return 0.0F;
        }
        recordBodyHit(player, target);

        float result = amount;

        // 《查拉图斯特拉》：十诫失效后的属性改写已由属性修饰符完成，这一击无额外系数

        // 《堂吉诃德》：对生命倍数远高于自己的敌人增伤
        if (wears(player, ClassicBook.DON_QUIXOTE)) {
            result *= 1.0F + quixoteBonus(player, target);
        }

        // 《百年孤独》：独处增伤 + 背包越空伤害越高
        if (wears(player, ClassicBook.HUNDRED_YEARS)) {
            if (isAlone(player)) {
                result *= 1.0F + SOLITUDE_BONUS;
            }
            result *= 1.0F + lonelyBonus(player);
        }

        // 《几何原本》：公设二（造成伤害 +10%）
        if (wears(player, ClassicBook.EUCLID) && postulates(player) >= 2) {
            result *= 1.0F + EUCLID_OUTGOING;
        }

        // 《物种起源》：二次击杀过的首领
        if (wears(player, ClassicBook.ORIGIN) && hasBossMastery(player, target)) {
            result *= 1.0F + ORIGIN_BOSS_BONUS;
        }

        // 《九章算术》：斐波那契顺序
        if (wears(player, ClassicBook.NINE_CHAPTERS)) {
            result *= nineChapters(player);
        }

        // 《理想国》：在被打的那只生物旁边投下它的影子
        if (wears(player, ClassicBook.REPUBLIC) && !isRepublicShadow(target)) {
            spawnRepublicShadow(player, target);
        }

        return Math.max(0.0F, result);
    }

    // ==================== 属性修饰符 ====================

    /**
     * 属性百分比修正器。
     *
     * <p>乘区（作者 m09952 指正）：文本写「X 提高 10%」的，是**乘法乘区**
     * {@code MULTIPLY_BASE} —— 同一乘区里的百分比先相加，再一起乘到基础值上；
     * 它和「独立乘区」{@code MULTIPLY_TOTAL}（一条一条各自相乘）不是一回事，
     * 后者只留给 {@link MaterialBonusHandler} 的材料档位那套专门设计。
     */
    private static void setModifier(Player player, Attribute attribute, UUID id, double amount) {
        applyModifier(player.getAttribute(attribute), id, "classic_book", amount,
                AttributeModifier.Operation.MULTIPLY_BASE);
    }

    /**
     * 把修正器调整到目标状态，已经是目标状态就什么都不做。
     *
     * <p>旧写法每 tick {@code removeModifier} + {@code addTransientModifier} 一次，
     * 属性表每 tick 都被标脏、缓存被冲掉；数值其实绝大多数 tick 都没变。
     */
    private static void applyModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance instance,
                                      UUID id, String name, double amount,
                                      AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) <= 1.0E-4) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current != null
                && current.getOperation() == operation
                && Math.abs(current.getAmount() - amount) <= 1.0E-6) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    private static void setAddition(Player player, Attribute attribute, UUID id, double amount) {
        applyModifier(player.getAttribute(attribute), id, "classic_book", amount,
                AttributeModifier.Operation.ADDITION);
    }

    private static void removeModifier(Player player, Attribute attribute, UUID id) {
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /** 《几何原本》公设五：挖掘与攻击距离 +1。 */
    private static final class EuclidAttributes {
        static void tick(Player player) {
            boolean active = wears(player, ClassicBook.EUCLID) && postulates(player) >= 5;
            applyModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()), EUCLID_BLOCK_REACH,
                    "classic_euc_reach", active ? 1.0D : 0.0D, AttributeModifier.Operation.ADDITION);
            applyModifier(player.getAttribute(ForgeMod.ENTITY_REACH.get()), EUCLID_ENTITY_REACH,
                    "classic_euc_reach", active ? 1.0D : 0.0D, AttributeModifier.Operation.ADDITION);
        }
    }

    // ==================== 公共小工具 ====================

    /**
     * 玩家身上是否戴着指定的那本书。
     *
     * <p>真实扫描在 {@link WornCache#refresh(Player)} 里每 tick 做一次，
     * 这里只是查表——以前一个 tick 要被问六七遍，每遍都遍历整张饰品栏。
     */
    public static boolean wears(Player player, ClassicBook book) {
        return WornCache.wears(player, book);
    }

    // ==================== 《物种起源》适应记录 ====================

    private static final class OriginAdaptation {
        private static final String KEY_COUNTS = "OriginC_";
        private static final String KEY_ADAPTED = "OriginAdapted";

        /** 返回这一击还要不要乘减伤系数。 */
        static float factor(Player player, net.minecraft.world.damagesource.DamageSource source) {
            String id = typeId(source);
            CompoundTag data = player.getPersistentData();
            String adapted = data.getString(KEY_ADAPTED);

            if (containsAdapted(adapted, id)) {
                return 1.0F - ORIGIN_REDUCTION;
            }

            String countKey = KEY_COUNTS + sanitize(id);
            int hits = data.getInt(countKey) + 1;
            data.putInt(countKey, hits);

            // 作者 2026-11-05：取消「最多适应 3 种」的上限，能适应多少种就适应多少种
            if (hits >= ORIGIN_HITS_TO_ADAPT) {
                String joined = adapted.isEmpty() ? id : adapted + "|" + id;
                data.putString(KEY_ADAPTED, joined);
                player.displayClientMessage(Component.literal("§a【物种起源】你适应了这种伤害："
                        + id), true);
                return 1.0F - ORIGIN_REDUCTION;
            }
            return 1.0F;
        }

        private static boolean containsAdapted(String adapted, String id) {
            if (adapted.isEmpty()) {
                return false;
            }
            for (String part : adapted.split("\\|")) {
                if (part.equals(id)) {
                    return true;
                }
            }
            return false;
        }

        private static String sanitize(String id) {
            return id.replace(':', '_').replace('/', '_').replace('.', '_');
        }
    }
}
