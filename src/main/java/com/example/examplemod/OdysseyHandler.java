package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《奥德赛》—— 神明还愿意让你回家。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>死亡时什么都不掉；</li>
 *   <li>复活后 30 秒：生命回复 IV、伤害吸收 IV、力量 III、迅捷 III；</li>
 *   <li>如果本来就开着「死亡不掉落」，Shift + 右键可以传送回上一次的死亡点。</li>
 * </ul>
 *
 * <p><b>2026-11-01 修「没有生效」</b>：第一版只在 {@link LivingDropsEvent} 里
 * {@code setCanceled(true)}，但那个事件只管战利品表，<b>管不住玩家背包</b>
 * ——玩家背包是 {@code Player#dropEquipment} 丢的，于是东西照样散一地；
 * 就算把背包存进旧玩家的持久数据，复活后是一个新的玩家实体，
 * 旧实体的 {@code getPersistentData()} 不会自动跟过来，于是「存了也白存」。
 *
 * <p>现在的做法：死亡那一刻（{@link EventPriority#HIGHEST}，抢在 Curios 丢饰品之前）
 * 先把主背包和 Curios 槽位整个存进持久数据，<b>然后清空</b>，原版就没什么可丢的了；
 * 复活前在 {@link PlayerEvent.Clone} 里把这份快照搬到新玩家身上，
 * 复活时再装回去。开着「死亡不掉落」时原版本来就留东西，我们不插手（只记坐标）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OdysseyHandler {

    private static final String TAG_ITEMS = "YanOdysseyItems";
    private static final String TAG_CURIOS = "YanOdysseyCurios";
    private static final String TAG_DIMENSION = "YanOdysseyDim";
    private static final String TAG_X = "YanOdysseyX";
    private static final String TAG_Y = "YanOdysseyY";
    private static final String TAG_Z = "YanOdysseyZ";
    /** 复活瞬间不清算，先挂个标记，下一个 tick 再发 buff（见 {@link #onPlayerTick}）。 */
    private static final String TAG_BUFF_PENDING = "YanOdysseyBuffPending";

    private static final int RESPAWN_BUFF_TICKS = 600;

    private OdysseyHandler() {
    }

    /** 兜底：战利品表也不掉（生物掉落与玩家背包是两条路，这里只补上生物那部分）。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (wearsOdyssey(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!wearsOdyssey(player)) {
            return;
        }
        // 2026-11-10 修：本方法原来跑在 HIGHEST，抢在别人「取消死亡」之前就把背包清空存快照，
        // 结果玩家其实没死（例如《如我所书》把死亡取消了），背包却被吞了。
        // 现在放到 LOWEST，先看这一死亡有没有被别人取消，取消了就什么都不做。
        if (event.isCanceled()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putString(TAG_DIMENSION, player.level().dimension().location().toString());
        data.putDouble(TAG_X, player.getX());
        data.putDouble(TAG_Y, player.getY());
        data.putDouble(TAG_Z, player.getZ());

        // 开着「死亡不掉落」时原版自己会留东西，清空反而会把东西弄丢
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        ListTag items = new ListTag();
        player.getInventory().save(items);
        data.put(TAG_ITEMS, items);
        data.remove(TAG_CURIOS);
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            data.put(TAG_CURIOS, handler.saveInventory(true));
            var equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                equipped.setStackInSlot(i, ItemStack.EMPTY);
            }
        });
        // 背包清空 → 原版 dropEquipment 时地上什么都不会出现
        player.getInventory().clearContent();
    }

    /** 复活（换了一个玩家实体）时把快照搬过去。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        CompoundTag to = event.getEntity().getPersistentData();
        for (String key : new String[] {TAG_ITEMS, TAG_CURIOS, TAG_DIMENSION, TAG_X, TAG_Y, TAG_Z}) {
            if (from.contains(key)) {
                to.put(key, from.get(key).copy());
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        // 戴着书才给复活加成；但背包要还回去——那本来就是玩家的东西，
        // 不能因为死后把书摘了/丢了就吞掉一背包（原来这里要求 wears，会吞包）
        CompoundTag data = player.getPersistentData();
        if (data.contains(TAG_ITEMS, CompoundTag.TAG_LIST)) {
            ListTag items = data.getList(TAG_ITEMS, CompoundTag.TAG_COMPOUND);
            player.getInventory().load(items);
            data.remove(TAG_ITEMS);
        }
        if (data.contains(TAG_CURIOS, CompoundTag.TAG_LIST)) {
            ListTag curios = data.getList(TAG_CURIOS, CompoundTag.TAG_COMPOUND);
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.loadInventory(curios));
            data.remove(TAG_CURIOS);
        }
        // 复活事件发生在新玩家实体刚建好、Curios 刚装回去的那一刻：
        // WornCache 里还是旧快照（甚至死前的空快照），这时候问「戴着没有」经常得到 false，
        // 于是 buff 从来没发出去过 —— 作者报的「复活后 buff 不生效」就是这个。
        // 先记一个待发标记，下一个 tick 重新扫一遍饰品栏再发。
        data.putBoolean(TAG_BUFF_PENDING, true);
    }

    /** 复活后第一个 tick 才发 buff：此时饰品已装回、WornCache 也能重新扫到。 */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_BUFF_PENDING)) {
            return;
        }
        data.remove(TAG_BUFF_PENDING);
        WornCache.refresh(player);
        if (!wearsOdyssey(player)) {
            return;
        }
        add(player, MobEffects.REGENERATION, 3);
        add(player, MobEffects.ABSORPTION, 3);
        add(player, MobEffects.DAMAGE_BOOST, 2);
        add(player, MobEffects.MOVEMENT_SPEED, 2);
    }

    private static boolean wearsOdyssey(Player player) {
        return WornCache.wears(player, ClassicBook.ODYSSEY);
    }

    private static void add(Player player, net.minecraft.world.effect.MobEffect effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, RESPAWN_BUFF_TICKS, amplifier, false, true, true));
    }

    /**
     * Shift + 右键的入口：只在「开着死亡不掉落」时生效 ——
     * 否则物品本来就掉了，传回去没有意义（作者的原话就是这个条件）。
     */
    public static void returnToDeathPoint(ServerPlayer player) {
        if (!wearsOdyssey(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7【归途】只有开着「死亡不掉落」时才走得到回去的路。"), true);
            return;
        }
        if (!data.contains(TAG_DIMENSION, CompoundTag.TAG_STRING)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7【归途】还没有可以回去的地方。"), true);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString(TAG_DIMENSION));
        if (id == null || player.getServer() == null) {
            return;
        }
        ServerLevel target = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null) {
            return;
        }
        double x = data.getDouble(TAG_X);
        double y = data.getDouble(TAG_Y);
        double z = data.getDouble(TAG_Z);
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        data.remove(TAG_DIMENSION);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§3【归途】海风把你送回了上一次倒下的地方。"), true);
    }
}
