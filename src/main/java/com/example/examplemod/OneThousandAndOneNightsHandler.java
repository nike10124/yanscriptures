package com.example.examplemod;

import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《一千零一夜》one_thousand_and_one_nights。
 *
 * <p>作者定稿（2026-11-08）：
 * 「每个游戏日获得一次［故事］效果，当生命值小于 10% 时，获得一秒的生命恢复 10 效果，
 * 若玩家最大生命值大于 100 点，该效果将变为使玩家生命值回复至 50%。
 * 每个游戏日，你都可以通过进行一次睡眠，使自己的随机属性
 * （攻击力，防御力，盔甲韧性，生命值，攻击速度，移动速度）提升 1%（上限一千零一次）。」
 *
 * <p>作者补充：成长出来的属性用物品侧的「当装备在书籍栏：」表达（见 {@link ClassicBookItem}），
 * 不另外做一套显示；像「造成伤害提高」这种没法写成属性的效果就不在面板上出现。
 *
 * <p>因此这里的职责只有两件事：①判定「成功睡了一整夜」并发一次随机成长，把累计值写进物品 NBT；
 * ②发［故事］效果。属性本身由物品侧读 NBT 提供。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OneThousandAndOneNightsHandler {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        SLEEPING.remove(id);
        SLEEP_DONE.remove(id);
        SLEEP_TICKS.remove(id);
        LAST_SLEEP.remove(id);
        SETTLED_AT.remove(id);
    }

    /** 物品 NBT：六个属性各自的累计层数。 */
    public static final String TAG_GROWTH = "YanNightsGrowth";
    /** 玩家 NBT：上次领［故事］的游戏日。 */
    private static final String TAG_STORY_DAY = "YanNightsStoryDay";
    /** 每层 +1%。 */
    public static final double PER_STACK = 0.01D;
    /** 上限（作者原文「上限一千零一次」）。 */
    public static final int CAP = 1001;

    /** 六个成长方向的键名（与物品侧一一对应）。 */
    public static final String[] KEYS = {"attack", "armor", "toughness", "health", "attackspeed", "speed"};
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final String SLOT = "book";
    /** 上一刻还在睡的玩家（兜底判定的前置状态）。 */
    private static final java.util.Set<UUID> SLEEPING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 这一次睡眠是否已经结算过成长（防止同一次睡眠被反复计数）。 */
    private static final java.util.Set<UUID> SLEEP_DONE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 戴失眠诅咒的人已经连续躺了多少刻（中途下床立刻清零）。 */
    private static final java.util.Map<UUID, Integer> SLEEP_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    /** 刚结束的那一场睡眠一共躺了多少刻（给醒来那一刻的判定用）。 */
    private static final java.util.Map<UUID, Integer> LAST_SLEEP = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 连续躺满这么多刻才算「睡了一觉」（5 秒）。上床下床反复横跳刷不出来。
     *
     * <p>为什么不设更长：实测戴着七咒之戒时，睡眠计数会被对方按在 90，
     * 玩家通常在 90~194 刻之间就被原来的唤醒流程带下床（见 2026-11-08 的
     * {@code latest.log}：sleepCounter=90 / 连续睡眠=194 刻），阈值一旦超过这段
     * 就永远等不到，所以取「原版判定睡着所需的一半」。
     */
    private static final int CURSED_SLEEP_TICKS = 100;
    /** 原版睡满一整夜是 100 刻（sleepCounter 满值），低于这个数就是中途下床。 */
    private static final int FULL_SLEEP_TICKS = 100;
    /** 反射拿到的 {@code Player#sleepCounter}（睡满阈值就是它）。 */
    private static java.lang.reflect.Field SLEEP_COUNTER;
    /** 同一次醒来可能被两条判定都看到，这段刻数内只结算一次。 */
    private static final int SETTLE_GAP_TICKS = 40;
    private static final java.util.Map<UUID, Integer> SETTLED_AT = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 「第七诅咒：失眠」的载体。用标签而不是写死 id：名字留在数据文件里，
     * 换整合包/换版本时只要改 JSON 即可（见 {@code data/yanscriptures/tags/items/insomnia_curses.json}）。
     */
    public static final TagKey<Item> INSOMNIA_CURSES = TagKey.create(Registries.ITEM,
            new ResourceLocation(ExampleMod.MODID, "insomnia_curses"));

    private static boolean worn(Player player) {
        return WornCache.wears(player, ClassicBook.ONE_THOUSAND_AND_ONE_NIGHTS);
    }

    /**
     * 佩戴《一千零一夜》时抵消「第七诅咒：失眠」的结算。
     *
     * <p>失眠的实现方式是「睡眠计时超过 90 就被按回 90」，而原版要满 100 才算睡着，
     * 所以戴戒指的人永远等不到天亮那一刻（也就等不到原版的自然醒来）。
     * 这里不去改别人的代码，而是自己数「连续躺了多久」：只有**连续躺满 10 秒**才结算一次，
     * 中途下床计数立刻归零——这样「上床-下床」反复横跳刷不出成长。
     */
    private static void checkSleepProgress(Player player) {
        UUID id = player.getUUID();
        if (!player.isSleeping()) {
            Integer slept = SLEEP_TICKS.remove(id);
            if (slept != null) {
                // 留给「刚醒来的那一刻」判定：这一觉究竟躺了多久。
                LAST_SLEEP.put(id, slept);
            }
            SLEEP_DONE.remove(id);
            return;
        }
        if (!worn(player)) {
            SLEEP_TICKS.remove(id);
            return;
        }
        int ticks = SLEEP_TICKS.merge(id, 1, Integer::sum);
        if (!hasInsomniaCurse(player)) {
            // 没有失眠诅咒的人走原版的路：真正睡醒时由 onWakeUp 结算（这里只负责计时）。
            return;
        }
        // 真正的「抵消」：对方每刻把睡眠计数按回 90，原版要 100 才算睡着
        // （isSleepingLongEnough / 夜晚跳过 / 清早自动起床都看这个数），所以戴着戒指
        // 会永远躺不到「睡够」，表现就是「起不来」。
        // 但不能一躺下就顶到 100：那样原版立刻认定「睡够了」，夜晚瞬间跳过 ——
        // 就是作者说的「秒睡秒醒」。所以按原版节奏走满 100 刻（5 秒）再补，
        // 让夜晚照常在 5 秒后跳过、天亮正常起床。
        if (ticks >= CURSED_SLEEP_TICKS) {
            forceSleepCounter(player);
        }
        if (ticks < CURSED_SLEEP_TICKS) {
            return;
        }
        if (!SLEEP_DONE.add(id)) {
            return;
        }
        LOG.info("《一千零一夜》：{} 戴着七咒之戒连续躺了 {} 刻，抵消失眠并结算成长", player.getName().getString(), ticks);
        growOnce(player);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.yanscriptures.nights.insomnia_waived"), true);
    }

    /**
     * 把睡眠计数补到「睡够」的阈值 100。
     *
     * <p>Player#sleepCounter 是私有字段、没有 setter（javap 确认），只能反射。
     * 只在「戴着本书 + 戴着失眠诅咒 + 正在睡」时调用，每刻一次，字段查找结果缓存。
     */
    private static void forceSleepCounter(Player player) {
        try {
            if (SLEEP_COUNTER == null) {
                SLEEP_COUNTER = net.minecraftforge.fml.util.ObfuscationReflectionHelper
                        .findField(Player.class, "sleepCounter");
            }
            if (player.getSleepTimer() < FULL_SLEEP_TICKS) {
                SLEEP_COUNTER.setInt(player, FULL_SLEEP_TICKS);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOG.warn("《一千零一夜》：补睡眠计数失败（{}），失眠抵消退化为按时间结算", exception.toString());
        }
    }

    /** 身上有没有挂着「失眠」那件饰品（只看饰品栏，背包里没戴不算）。 */
    private static boolean hasInsomniaCurse(Player player) {
        var optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return false;
        }
        for (var entry : optional.get().getCurios().entrySet()) {
            var slots = entry.getValue();
            for (int i = 0; i < slots.getSlots(); i++) {
                if (slots.getStacks().getStackInSlot(i).is(INSOMNIA_CURSES)) {
                    return true;
                }
            }
        }
        return false;
    }

    private OneThousandAndOneNightsHandler() {
    }

    /** 当前游戏日序号（每 24000 刻一天）。 */
    private static long dayOf(Level level) {
        return Math.floorDiv(level.getDayTime(), 24000L);
    }

    /** 物品侧读的累计层数，顺序同 {@link #KEYS}。 */
    public static int[] growthOf(ItemStack stack) {
        int[] out = new int[KEYS.length];
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_GROWTH)) {
            return out;
        }
        CompoundTag growth = tag.getCompound(TAG_GROWTH);
        for (int i = 0; i < KEYS.length; i++) {
            out[i] = growth.getInt(KEYS[i]);
        }
        return out;
    }

    public static int totalOf(ItemStack stack) {
        int total = 0;
        for (int value : growthOf(stack)) {
            total += value;
        }
        return total;
    }

    /** 玩家身上那份累计值（和物品 NBT 是同一份数据的权威来源）。 */
    private static int[] growthOf(Player player) {
        int[] out = new int[KEYS.length];
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_GROWTH)) {
            return out;
        }
        CompoundTag growth = data.getCompound(TAG_GROWTH);
        for (int i = 0; i < KEYS.length; i++) {
            out[i] = growth.getInt(KEYS[i]);
        }
        return out;
    }

    private static void storeGrowth(Player player, int[] values) {
        CompoundTag growth = new CompoundTag();
        for (int i = 0; i < KEYS.length; i++) {
            growth.putInt(KEYS[i], values[i]);
        }
        player.getPersistentData().put(TAG_GROWTH, growth);
    }

    /** 成功睡满一整夜才算「完成睡眠」。 */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        // 反编译求证（1.20.1 Player#tick）：只有「睡够」才会走 sleepCounter 满 100 的那一步；
        // 而「躺下就满足不了睡眠条件」（例如白天上床）时，原版同一刻就用
        // stopSleepInBed(false, true) 把你弹起来——这个 false 同样是「非立即醒」，
        // 所以光看 wakeImmediately 根本分不出「睡满一夜」和「刚躺下就被弹出来」。
        // 唯一可靠的判据是唤醒那一刻的 sleepCounter：睡满一夜正好是 100。
        if (event.wakeImmediately()) {
            return;
        }
        int timer = player.getSleepTimer();
        LOG.info("《一千零一夜》：{} 醒来（立即醒={} 世界已更新={} 夜间={} sleepCounter={} 连续睡眠={} 刻）",
                player.getName().getString(), event.wakeImmediately(), event.updateLevel(),
                player.level().isNight(), timer, SLEEP_TICKS.getOrDefault(player.getUUID(), 0));
        if (timer < FULL_SLEEP_TICKS) {
            return;
        }
        growOnce(player);
    }

    /**
     * 兜底：原版「睡到天亮」未必走 {@link PlayerWakeUpEvent}（作者用 `/time set night` 时复现过），
     * 所以再按「上一刻在睡、这一刻醒了、天已经亮了、而且连续睡了足够久」补一次判定。
     * 手动下床时还是夜里、或者只躺了两三秒，都不会误判。
     */
    private static void detectWakeFallback(Player player) {
        UUID id = player.getUUID();
        if (player.isSleeping()) {
            SLEEPING.add(id);
            return;
        }
        if (!SLEEPING.remove(id)) {
            return;
        }
        if (!worn(player)) {
            return;
        }
        if (player.level().isNight()) {
            return;
        }
        if (LAST_SLEEP.getOrDefault(id, 0) < FULL_SLEEP_TICKS) {
            return;
        }
        growOnce(player);
    }

    private static void growOnce(Player player) {
        if (!worn(player)) {
            return;
        }
        // 同一场睡眠只结算一次：醒来那一刻「wake 事件」与「兜底判定」可能都会走到这里。
        UUID id = player.getUUID();
        int now = player.tickCount;
        Integer last = SETTLED_AT.get(id);
        if (last != null && now - last < SETTLE_GAP_TICKS) {
            return;
        }
        SETTLED_AT.put(id, now);
        int[] growth = growthOf(player);
        int total = 0;
        for (int value : growth) {
            total += value;
        }
        if (total >= CAP) {
            return;
        }
        int pick = player.getRandom().nextInt(growth.length);
        growth[pick]++;
        storeGrowth(player, growth);
        pushGrowth(player);
        LOG.info("《一千零一夜》：{} 睡满一夜，{} +1%（累计 {}/{}）",
                player.getName().getString(), KEYS[pick], total + 1, CAP);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        detectWakeFallback(player);
        checkSleepProgress(player);
        if (!worn(player)) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            pushGrowth(player);
        }
        grantStory(player);
    }

    /** ［故事］：每个游戏日一次，低血时救助。 */
    private static void grantStory(Player player) {
        long day = dayOf(player.level());
        CompoundTag data = player.getPersistentData();
        // 2026-11-10（外部审查）：getLong 缺键返回 0，而新档第 0 天 day 就是 0，
        // 「0 >= 0」直接 return —— 于是新档第一天永远发不出［故事］。缺键按 -1 处理。
        long lastStoryDay = data.contains(TAG_STORY_DAY) ? data.getLong(TAG_STORY_DAY) : -1L;
        if (lastStoryDay >= day) {
            return;
        }
        float max = player.getMaxHealth();
        if (player.getHealth() >= max * 0.10F) {
            return;
        }
        data.putLong(TAG_STORY_DAY, day);

        if (max > 100.0F) {
            player.setHealth(max * 0.5F);
        } else {
            // 「一秒的生命恢复 10」：等级 X = amplifier 9，持续 20 刻。
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 9, true, false, false));
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d【故事】" + (max > 100.0F
                ? "生命值回复至一半" : "生命恢复 X · 1 秒")), true);
    }

    /** 把玩家身上的累计值写进戴着的那本书，并让 Curios 重算（这样面板就会显示成长）。 */
    private static void pushGrowth(Player player) {
        ItemStack stack = wornStack(player);
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag want = new CompoundTag();
        int[] growth = growthOf(player);
        for (int i = 0; i < KEYS.length; i++) {
            want.putInt(KEYS[i], growth[i]);
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_GROWTH) && tag.getCompound(TAG_GROWTH).equals(want)) {
            return;
        }
        stack.getOrCreateTag().put(TAG_GROWTH, want);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var stacks = handler.getCurios().get(SLOT);
            if (stacks != null) {
                stacks.update();
            }
        });
    }

    private static ItemStack wornStack(Player player) {
        var optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (var entry : optional.get().getCurios().entrySet()) {
            var slots = entry.getValue();
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStacks().getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof ClassicBookItem item
                        && item.book() == ClassicBook.ONE_THOUSAND_AND_ONE_NIGHTS) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 给物品侧用：这套成长要到哪种属性上（供以后调整时统一引用）。 */
    public static ResourceLocation attributeName(int index) {
        return switch (index) {
            case 0 -> new ResourceLocation("minecraft", "generic.attack_damage");
            case 1 -> new ResourceLocation("minecraft", "generic.armor");
            case 2 -> new ResourceLocation("minecraft", "generic.armor_toughness");
            case 3 -> new ResourceLocation("minecraft", "generic.max_health");
            case 4 -> new ResourceLocation("minecraft", "generic.attack_speed");
            default -> new ResourceLocation("minecraft", "generic.movement_speed");
        };
    }

    /** 便于别的类引用属性常量。 */
    public static final class Attrs {
        private Attrs() {
        }

        public static final net.minecraft.world.entity.ai.attributes.Attribute ATTACK = Attributes.ATTACK_DAMAGE;
        public static final net.minecraft.world.entity.ai.attributes.Attribute ARMOR = Attributes.ARMOR;
        public static final net.minecraft.world.entity.ai.attributes.Attribute TOUGHNESS = Attributes.ARMOR_TOUGHNESS;
        public static final net.minecraft.world.entity.ai.attributes.Attribute HEALTH = Attributes.MAX_HEALTH;
        public static final net.minecraft.world.entity.ai.attributes.Attribute ATTACK_SPEED = Attributes.ATTACK_SPEED;
        public static final net.minecraft.world.entity.ai.attributes.Attribute SPEED = Attributes.MOVEMENT_SPEED;
    }
}
