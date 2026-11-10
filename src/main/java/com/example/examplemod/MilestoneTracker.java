package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 十五件物品的「首次达成」追踪器。
 *
 * <p>工作方式：
 * <ol>
 *   <li>条件命中 → 在玩家持久化数据里写 {@code MsR_<id>}（ready）；</li>
 *   <li>每个 tick 的投递循环再检查一次「是否已违逆该诫」——十张残卷必须在对应的
 *       {@code Curse_<n>} 存在时才能真正发放；</li>
 *   <li>发放后写 {@code MsD_<id>}（done），永不再发。</li>
 * </ol>
 *
 * <p>所有判定只用语义阈值，不写死其他模组的实体 ID / 物品 ID。
 * 唯一写死的原版 ID 是末影龙（作者明确要求它算首领）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MilestoneTracker {

    private MilestoneTracker() {
    }

    // ==================== ID ====================

    public static final String SHEMA = "shema";
    public static final String SILENCE = "silence";
    public static final String THORNS = "thorns";
    public static final String SABBATH = "sabbath";
    public static final String REBELLION = "rebellion";
    public static final String BLOOD = "blood";
    public static final String HARP = "harp";
    public static final String SHINAR = "shinar";
    public static final String TONGUE = "tongue";
    public static final String VANITY = "vanity";
    public static final String FATE = "fate";
    public static final String CREATION = "creation";
    public static final String PILLAR = "pillar";
    public static final String CALAMITY = "calamity";

    // 九本名著（哲学 / 文学 / 理科各三本），判定实现在 ClassicMilestones
    public static final String DAO_DE_JING = "dao_de_jing";
    public static final String REPUBLIC = "republic";
    public static final String ZARATHUSTRA = "zarathustra";
    public static final String DIVINE_COMEDY = "divine_comedy";
    public static final String DON_QUIXOTE = "don_quixote";
    public static final String HUNDRED_YEARS = "hundred_years";
    public static final String EUCLID = "euclid";
    public static final String ORIGIN = "origin";
    public static final String NINE_CHAPTERS = "nine_chapters";

    /** 顺序即十诫顺序，前 10 个是残卷，后 4 个是史诗。 */
    public static final List<String> ALL = List.of(
            SHEMA, SILENCE, THORNS, SABBATH, REBELLION,
            BLOOD, HARP, SHINAR, TONGUE, VANITY,
            FATE, CREATION, PILLAR, CALAMITY);
    // 九本名著的进度已按作者 2026-11-11 的决定下线（低稀有度的书不显示进度提示），
    // 常量与判定仍保留在 ClassicMilestones 里，只是不再进 ALL、不再显示、不再自检。

    private static final String[] SCROLL_IDS = {
            SHEMA, SILENCE, THORNS, SABBATH, REBELLION,
            BLOOD, HARP, SHINAR, TONGUE, VANITY};

    // ==================== 持久化键 ====================

    // 包内可见：MilestoneSelfTest 需要伪造前置状态（塞回计数器 / 改时间戳）
    static final String READY = "MsR_";
    static final String DONE = "MsD_";
    static final String KILLS = "MsKills";
    static final String BOSS_KILLS = "MsBossKills";
    static final String COMBAT_UNTIL = "MsCU";
    static final String COMBAT_DAMAGE = "MsCDmg";
    static final String COMBAT_KILLS = "MsCKills";
    static final String IN_COMBAT = "MsIn";
    static final String LAST_BREAK = "MsLastBreak";
    static final String SILENCE_UNTIL = "MsSilUntil";
    static final String SILENCE_TOTEM = "MsSilTotem";
    static final String HEAL_ACC = "MsHealAcc";
    static final String HEAL_START = "MsHealStart";
    static final String TONGUE_COUNT = "MsTongueCount";
    static final String TONGUE_UNTIL = "MsTongueUntil";
    static final String PETS = "MsPets";

    // ==================== 公共查询 ====================

    public static boolean isDone(Player player, String id) {
        return player.getPersistentData().getBoolean(DONE + id);
    }

    public static boolean isReady(Player player, String id) {
        return player.getPersistentData().getBoolean(READY + id);
    }

    /**
     * 全局统一的时间基准。
     *
     * <p>不能用 {@code clock(player)}：固定时间的维度（下界、末地）
     * 不推进 gameTime，玩家一换维度 {@code now} 就会倒退，导致
     * {@code now - 记录值} 恒为负数——「歇工」「挂琴」永远不可能达成，
     * 战斗窗口也会永远关不上。主世界的 gameTime 才是唯一可靠的时钟。
     */
    static long clock(Player player) {
        if (player.getServer() != null) {
            return player.getServer().overworld().getGameTime();
        }
        return player.level().getGameTime();
    }

    /** 该 id 对应的物品；未知返回 {@code null}。 */
    public static Item itemOf(String id) {
        switch (id) {
            case SHEMA:
                return ExampleMod.SCROLL_SHEMA.get();
            case SILENCE:
                return ExampleMod.SCROLL_SILENCE.get();
            case THORNS:
                return ExampleMod.SCROLL_THORNS.get();
            case SABBATH:
                return ExampleMod.SCROLL_SABBATH.get();
            case REBELLION:
                return ExampleMod.SCROLL_REBELLION.get();
            case BLOOD:
                return ExampleMod.SCROLL_BLOOD.get();
            case HARP:
                return ExampleMod.SCROLL_HARP.get();
            case SHINAR:
                return ExampleMod.SCROLL_SHINAR.get();
            case TONGUE:
                return ExampleMod.SCROLL_TONGUE.get();
            case VANITY:
                return ExampleMod.SCROLL_VANITY.get();
            case FATE:
                return ExampleMod.EPIC_FATE.get();
            case CREATION:
                return ExampleMod.EPIC_CREATION.get();
            case PILLAR:
                return ExampleMod.EPIC_PILLAR.get();
            case CALAMITY:
                return ExampleMod.EPIC_CALAMITY.get();
            case DAO_DE_JING:
                return ExampleMod.BOOK_DAO_DE_JING.get();
            case REPUBLIC:
                return ExampleMod.BOOK_REPUBLIC.get();
            case ZARATHUSTRA:
                return ExampleMod.BOOK_ZARATHUSTRA.get();
            case DIVINE_COMEDY:
                return ExampleMod.BOOK_DIVINE_COMEDY.get();
            case DON_QUIXOTE:
                return ExampleMod.BOOK_DON_QUIXOTE.get();
            case HUNDRED_YEARS:
                return ExampleMod.BOOK_HUNDRED_YEARS.get();
            case EUCLID:
                return ExampleMod.BOOK_EUCLID.get();
            case ORIGIN:
                return ExampleMod.BOOK_ORIGIN.get();
            case NINE_CHAPTERS:
                return ExampleMod.BOOK_NINE_CHAPTERS.get();
            default:
                return null;
        }
    }

    /** 该 id 对应的诫条序号（1~10）；史诗或未知返回 0（不要求违逆）。 */
    public static int commandmentOf(String id) {
        for (int i = 0; i < SCROLL_IDS.length; i++) {
            if (SCROLL_IDS[i].equals(id)) return i + 1;
        }
        return 0;
    }

    /** 显示名（用于指令与提示）。 */
    public static Component displayName(String id) {
        Item item = itemOf(id);
        return item == null ? Component.literal(id) : item.getDescription();
    }

    // ==================== 标记与发放 ====================

    public static void markReady(ServerPlayer player, String id) {
        CompoundTag tag = player.getPersistentData();
        if (tag.getBoolean(DONE + id) || tag.getBoolean(READY + id)) return;
        tag.putBoolean(READY + id, true);

        // 条件够了但因为「还没违逆该诫」而暂时发不出去时，必须给玩家一个看得见的回执，
        // 否则他会以为这条判定坏了（静默不发放是这套设计里最容易被误判的环节）。
        int commandment = commandmentOf(id);
        if (commandment > 0 && !curseActive(player, commandment)) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.milestone.pending",
                            displayName(id), String.valueOf(commandment))
                            .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** 指令 / 调试用：跳过条件，直接标记就绪。 */
    public static void debugReady(ServerPlayer player, String id) {
        markReady(player, id);
        deliver(player);
    }

    /**
     * 指令用：连「先违逆该诫」的门槛一起跳过，直接把物品塞进背包。
     *
     * <p>和 {@link #debugReady} 的区别：那条走正常发放链，残卷会因为身上没有对应诅咒而发不出来；
     * 这条是纯粹的后台补发，用于验证物品本身。
     *
     * @return true = 这次真的发出去了；false = 已拥有、id 未知或物品缺失
     */
    public static boolean forceGrant(ServerPlayer player, String id) {
        Item item = itemOf(id);
        if (item == null) return false;
        CompoundTag tag = player.getPersistentData();
        if (tag.getBoolean(DONE + id)) return false;

        tag.putBoolean(READY + id, true);
        tag.putBoolean(DONE + id, true);
        give(player, new ItemStack(item));

        player.displayClientMessage(
                Component.translatable("message.yanscriptures.milestone.granted", displayName(id))
                        .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    /** 指令用：按顺序补发本轮全部可获取项（十张残卷 + 四件史诗；真我不在系统内）。 */
    public static int forceGrantAll(ServerPlayer player) {
        int count = 0;
        for (String id : ALL) {
            if (forceGrant(player, id)) count++;
        }
        if (count > 0) {
            player.playNotifySound(net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return count;
    }

    /** 自检期间置 true：只写「就绪」标记，不真的把东西发到手里。 */
    static boolean suppressDelivery = false;

    private static void deliver(ServerPlayer player) {
        if (suppressDelivery) return;
        CompoundTag tag = player.getPersistentData();
        for (String id : ALL) {
            if (tag.getBoolean(DONE + id)) continue;
            if (!tag.getBoolean(READY + id)) continue;

            int commandment = commandmentOf(id);
            if (commandment > 0 && !curseActive(player, commandment)) continue;

            Item item = itemOf(id);
            if (item == null) continue;

            tag.putBoolean(DONE + id, true);
            give(player, new ItemStack(item));

            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.milestone.granted", displayName(id))
                            .withStyle(ChatFormatting.GOLD), false);
            player.playNotifySound(net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            ItemEntity entity = player.drop(stack, false);
            if (entity != null) {
                entity.setNoPickUpDelay();
                entity.setTarget(player.getUUID());
            }
        }
    }

    /** 该诫当前是否「已违逆」（戴着《圣典·戒律》且带对应诅咒）。 */
    private static boolean curseActive(Player player, int commandment) {
        ItemStack scripture = wornScripture(player);
        if (scripture.isEmpty() || scripture.getTag() == null) return false;
        return scripture.getTag().getBoolean("Curse_" + commandment);
    }

    private static ItemStack wornScripture(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()))
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }

    // ==================== 周期检查 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        CompoundTag tag = player.getPersistentData();
        long now = clock(player);

        // 无论如何每 20 tick 尝试投递一次（残卷要等诅咒出现）
        if (player.tickCount % 20 == 0) {
            deliver(player);
        }
        if (player.tickCount % 20 != 0) return;

        checkSabbath(player, tag, now);
        checkCombatWindow(player, tag, now);
        checkSilence(player, tag, now);
        checkCreation(player, tag, now);
        checkPets(player, tag, now);

        if (player.experienceLevel >= MilestoneTuning.THORNS_MIN_LEVEL) {
            markReady(player, THORNS);
        }
        if (tag.getInt(KILLS) >= MilestoneTuning.BLOOD_MIN_KILLS
                && tag.getInt(BOSS_KILLS) >= MilestoneTuning.BLOOD_MIN_BOSS_KILLS) {
            markReady(player, BLOOD);
        }

        deliver(player);
    }

    /** 自检专用：无视 {@code tickCount % 20} 的对齐，立即跑一遍周期性判定。 */
    static void runPeriodicChecks(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        long now = clock(player);
        checkSabbath(player, tag, now);
        checkCombatWindow(player, tag, now);
        checkSilence(player, tag, now);
        checkCreation(player, tag, now);
        checkPets(player, tag, now);

        if (player.experienceLevel >= MilestoneTuning.THORNS_MIN_LEVEL) {
            markReady(player, THORNS);
        }
        if (tag.getInt(KILLS) >= MilestoneTuning.BLOOD_MIN_KILLS
                && tag.getInt(BOSS_KILLS) >= MilestoneTuning.BLOOD_MIN_BOSS_KILLS) {
            markReady(player, BLOOD);
        }
        deliver(player);
    }

    private static void checkSabbath(ServerPlayer player, CompoundTag tag, long now) {
        if (!tag.contains(LAST_BREAK)) {
            tag.putLong(LAST_BREAK, now);
            return;
        }
        if (now - tag.getLong(LAST_BREAK) >= MilestoneTuning.SABBATH_DAYS * MilestoneTuning.TICKS_PER_DAY) {
            markReady(player, SABBATH);
        }
    }

    private static void checkCombatWindow(ServerPlayer player, CompoundTag tag, long now) {
        if (!tag.getBoolean(IN_COMBAT)) return;
        if (now <= tag.getLong(COMBAT_UNTIL)) return;

        float damage = tag.getFloat(COMBAT_DAMAGE);
        int kills = tag.getInt(COMBAT_KILLS);
        if (damage >= MilestoneTuning.PILLAR_MIN_DAMAGE_TAKEN && player.isAlive()) {
            markReady(player, PILLAR);
        }
        if (kills >= MilestoneTuning.CALAMITY_MIN_KILLS) {
            markReady(player, CALAMITY);
        }
        tag.putBoolean(IN_COMBAT, false);
        tag.putFloat(COMBAT_DAMAGE, 0.0F);
        tag.putInt(COMBAT_KILLS, 0);
    }

    private static void checkSilence(ServerPlayer player, CompoundTag tag, long now) {
        long until = tag.getLong(SILENCE_UNTIL);
        if (until <= 0 || now < until) return;

        tag.putLong(SILENCE_UNTIL, 0L);
        if (!player.isAlive() || player.getHealth() <= 0.0F) return;

        // 「不依靠任何免死效果」：受伤前后图腾使用次数必须没有增加
        int totemUses = player.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        if (totemUses == tag.getInt(SILENCE_TOTEM)) {
            markReady(player, SILENCE);
        }
    }

    private static void checkCreation(ServerPlayer player, CompoundTag tag, long now) {
        long start = tag.getLong(HEAL_START);
        if (start <= 0) return;
        if (now - start > MilestoneTuning.CREATION_WINDOW_TICKS) {
            tag.putFloat(HEAL_ACC, 0.0F);
            tag.putLong(HEAL_START, 0L);
            return;
        }
        if (tag.getFloat(HEAL_ACC) >= MilestoneTuning.CREATION_HEAL_PER_SECOND) {
            markReady(player, CREATION);
        }
    }

    private static void checkPets(ServerPlayer player, CompoundTag tag, long now) {
        if (!tag.contains(PETS, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(PETS, Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag pet = list.getCompound(i);
            java.util.UUID id;
            try {
                id = java.util.UUID.fromString(pet.getString("Id"));
            } catch (IllegalArgumentException e) {
                // 存档里的 UUID 损坏：直接丢掉，否则每次 tick 都会抛异常
                list.remove(i);
                changed = true;
                continue;
            }
            Entity entity = findAnywhere(player, id);
            if (entity == null) {
                // 区块未加载或宠物在别的维度：不能当作死亡，否则「挂琴」永远攒不满
                continue;
            }
            if (!entity.isAlive()) {
                list.remove(i);
                changed = true;
                continue;
            }
            if (now - pet.getLong("Born") >= (long) MilestoneTuning.HARP_DAYS * MilestoneTuning.TICKS_PER_DAY) {
                markReady(player, HARP);
            }
        }
        if (changed) tag.put(PETS, list);
    }

    /** 在所有维度里找实体；找不到（含区块未加载）返回 {@code null}。 */
    private static Entity findAnywhere(ServerPlayer player, java.util.UUID id) {
        if (player.getServer() == null) return null;
        if (player.serverLevel().getEntity(id) != null) {
            return player.serverLevel().getEntity(id);
        }
        for (net.minecraft.server.level.ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    // ==================== 伤害 / 治疗 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        // 攻击方也算「处于战斗」：否则只进攻不挨打时窗口不开，50 杀的灾厄会被
        // 两次击杀之间的 5 秒空隙切成碎片，永远攒不满
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && !(event.getEntity() == attacker)) {
            CompoundTag attackerTag = attacker.getPersistentData();
            attackerTag.putBoolean(IN_COMBAT, true);
            attackerTag.putLong(COMBAT_UNTIL,
                    clock(attacker) + MilestoneTuning.COMBAT_WINDOW_TICKS);
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag tag = player.getPersistentData();
        long now = clock(player);

        // 战斗窗口
        tag.putBoolean(IN_COMBAT, true);
        tag.putLong(COMBAT_UNTIL, now + MilestoneTuning.COMBAT_WINDOW_TICKS);
        tag.putFloat(COMBAT_DAMAGE, tag.getFloat(COMBAT_DAMAGE) + event.getAmount());

        // 无声：单次伤害 ≥ 自身最大生命 × 1.2
        if (event.getAmount() >= player.getMaxHealth() * MilestoneTuning.SILENCE_DAMAGE_RATIO) {
            tag.putLong(SILENCE_UNTIL, now + MilestoneTuning.SILENCE_SURVIVE_TICKS);
            tag.putInt(SILENCE_TOTEM,
                    player.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        CompoundTag tag = player.getPersistentData();
        long now = clock(player);

        float amount = event.getAmount();

        // 命运：血量低于 10% 时，被单次超过 50% 最大生命的治疗拉回来
        float maxHealth = player.getMaxHealth();
        if (player.getHealth() < maxHealth * MilestoneTuning.FATE_HEALTH_RATIO
                && amount > maxHealth * MilestoneTuning.FATE_HEAL_RATIO) {
            markReady(player, FATE);
        }

        // 创生：一秒内累计治疗 ≥ 100
        long start = tag.getLong(HEAL_START);
        if (start <= 0 || now - start > MilestoneTuning.CREATION_WINDOW_TICKS) {
            tag.putLong(HEAL_START, now);
            tag.putFloat(HEAL_ACC, amount);
        } else {
            tag.putFloat(HEAL_ACC, tag.getFloat(HEAL_ACC) + amount);
        }
    }

    // ==================== 击杀 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        // 《理想国》的影子不算击杀：它是本体的血条副本，不是独立的敌人。
        if (ClassicBooksHandler.isRepublicShadow(victim)) return;

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        long now = clock(player);

        tag.putInt(KILLS, tag.getInt(KILLS) + 1);
        boolean boss = isBoss(victim);
        if (boss) tag.putInt(BOSS_KILLS, tag.getInt(BOSS_KILLS) + 1);

        // 战斗窗口内的击杀（灾厄）
        tag.putBoolean(IN_COMBAT, true);
        tag.putLong(COMBAT_UNTIL, now + MilestoneTuning.COMBAT_WINDOW_TICKS);
        tag.putInt(COMBAT_KILLS, tag.getInt(COMBAT_KILLS) + 1);

        // 壹 · 示马：本模组饰品随身，击败最大生命 ≥ 222 的敌人
        if (victim.getMaxHealth() >= MilestoneTuning.SHEMA_MIN_TARGET_HEALTH && shemaCurioCondition(player)) {
            markReady(player, SHEMA);
        }

        // 拾 · 虚空：背包 / 护甲 / 副手全空，击败最大生命 ≥ 300 的敌人
        if (victim.getMaxHealth() >= MilestoneTuning.VANITY_MIN_TARGET_HEALTH && inventoryEmpty(player)) {
            markReady(player, VANITY);
        }

        // 捌 · 示拿：在深暗之域击杀监守者（监守者只在深暗之域刷新，无需再判生态）
        if (victim.getType() == EntityType.WARDEN) {
            markReady(player, SHINAR);
        }

        // 玖 · 火舌：一次爆炸同时杀死 ≥ 7 名敌人
        // 用标签而不是具体伤害类型：模组爆炸、床、苦力怕、TNT 都归一到同一个语义
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            if (tag.getLong(TONGUE_UNTIL) < now) {
                tag.putInt(TONGUE_COUNT, 0);
            }
            tag.putLong(TONGUE_UNTIL, now + MilestoneTuning.TONGUE_WINDOW_TICKS);
            tag.putInt(TONGUE_COUNT, tag.getInt(TONGUE_COUNT) + 1);
            if (tag.getInt(TONGUE_COUNT) >= MilestoneTuning.TONGUE_MIN_KILLS) {
                markReady(player, TONGUE);
            }
        }
    }

    private static boolean isBoss(LivingEntity entity) {
        return BossDetection.isBoss(entity);
    }

    private static boolean inventoryEmpty(Player player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty()) return false;
        }
        for (ItemStack stack : inventory.armor) {
            if (!stack.isEmpty()) return false;
        }
        for (ItemStack stack : inventory.offhand) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 示马的饰品栏判定。
     *
     * <p>默认（严格档）：饰品栏里除豁免表 {@link CurioExemption} 之外的物品必须全部属于本模组；
     * 豁免表里的饰品（开局绑定的诅咒饰品及其反转道具）一概不参与判定。
     * 把 {@code MilestoneTuning.SHEMA_REQUIRE_EXCLUSIVE_CURIOS} 置 false 会退化成
     * 「只要有任意一件本模组饰品即可」，其余模组饰品一律忽略。
     */
    /** 调试用：示马判定当前是否通过（豁免表生效检查也走这条）。 */
    public static boolean shemaCurioOk(Player player) {
        return shemaCurioCondition(player);
    }

    private static boolean shemaCurioCondition(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    boolean hasOwn = false;
                    for (var entry : handler.getCurios().entrySet()) {
                        var slotHandler = entry.getValue();
                        for (int i = 0; i < slotHandler.getSlots(); i++) {
                            ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                            if (stack.isEmpty() || CurioExemption.isExempt(stack)) continue;
                            boolean mine = isOwnItem(stack);
                            if (mine) {
                                hasOwn = true;
                            } else if (MilestoneTuning.SHEMA_REQUIRE_EXCLUSIVE_CURIOS) {
                                return false;
                            }
                        }
                    }
                    return hasOwn;
                })
                .orElse(false);
    }

    private static boolean isOwnItem(ItemStack stack) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getKey(stack.getItem());
        return key != null && key.getNamespace().equals(ExampleMod.MODID);
    }

    // ==================== 治愈 / 驯服 / 破坏 ====================

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager zombie)) return;
        if (zombie.level().isClientSide) return;

        Player nearest = zombie.level().getNearestPlayer(zombie,
                MilestoneTuning.CURE_CREDIT_RADIUS);
        if (nearest instanceof ServerPlayer player) {
            markReady(player, REBELLION);
            deliver(player);
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) return;
        if (event.getAnimal().level().isClientSide) return;

        CompoundTag tag = player.getPersistentData();
        ListTag list = tag.contains(PETS, Tag.TAG_LIST)
                ? tag.getList(PETS, Tag.TAG_COMPOUND)
                : new ListTag();

        CompoundTag pet = new CompoundTag();
        pet.putString("Id", event.getAnimal().getUUID().toString());
        pet.putLong("Born", clock(player));
        list.add(pet);

        while (list.size() > MilestoneTuning.MAX_TRACKED_PETS) {
            list.remove(0);
        }
        tag.put(PETS, list);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        player.getPersistentData().putLong(LAST_BREAK, clock(player));
    }

    // ==================== 调试 ====================

    public static List<String> statusLines(Player player) {
        List<String> lines = new ArrayList<>();
        for (String id : ALL) {
            String state = isDone(player, id) ? "§a已获得"
                    : isReady(player, id) ? "§e已就绪（等待条件解锁）" : "§7未达成";
            lines.add("§f" + id + " §8- " + state);
        }
        return lines;
    }

    public static void reset(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        // 连累计计数（击杀数、战斗窗口、计时戳）一起清掉，否则重测时
        // 血声的 1000 杀、歇工的起始时间会带着上一轮的残留
        List<String> keys = new ArrayList<>();
        for (String key : tag.getAllKeys()) {
            if (key.startsWith("Ms")) keys.add(key);
        }
        for (String key : keys) {
            tag.remove(key);
        }
    }

    @SuppressWarnings("unused")
    private static void grantAdvancementNoop(ServerPlayer player, Advancement advancement) {
        // 预留：如果将来改成 advancement 驱动，这里统一发牌
    }
}
