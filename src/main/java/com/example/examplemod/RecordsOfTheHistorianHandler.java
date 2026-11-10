package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《史记》—— 究天人之际，通古今之变。
 *
 * <p>作者定稿（2026-11-06）：「每完成一个进度，使暴击率提高 0.5%，暴击伤害提高 1%
 * （都是 curios 里的，不是原版的），幸运值提高 0.5」。作者明确要求不设上限
 * （「大不了我在整合包里面把它获取时间延后」）。
 *
 * <p>暴击用的是装备系统的 {@code l2damagetracker:crit_rate} / {@code crit_damage}；
 * 没装那个模组时按 id 查不到属性，这两项静默跳过，幸运照常生效。
 *
 * <p>这些是「成长型的**固定数值**加成」，按作者 2026-11-07 的规矩一律写在物品侧
 * {@link ClassicBookItem#getAttributeModifiers} 里，由 Curios 显示成
 * 「当装备在书籍栏：+N 幸运值 …」。
 *
 * <p><b>2026-11-07 晚：计数改为「实时统计已完成的进度数量」。</b>
 * 原来靠事件累加一个计数，退出重进时那段累加值会丢，面板上的数字跟着归零（作者实测）。
 * 现在不再累加，而是直接数「这个玩家已经完成了多少个进度」——存档里本来就存着这份事实，
 * 所以怎么进出都不会错。计数缓存在内存里，完成进度时立刻重算，另外每 5 秒兜底重算一次。
 *
 * <p>统计口径：跳过 {@code recipes/} 开头的进度（配方解锁也算进度，但玩家不把它们当成就）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecordsOfTheHistorianHandler {

    /** 物品 NBT 里的展示值（物品侧只能读自己的栈）。 */
    public static final String TAG_COUNT = "YanAdvancementCount";

    public static final double CRIT_RATE_PER = 0.002D;
    public static final double CRIT_DAMAGE_PER = 0.005D;
    public static final double LUCK_PER = 0.2D;

    private static final String SLOT = "book";
    private static final int PUSH_INTERVAL = 20;
    private static final int RECOUNT_INTERVAL = 100;
    private static final String RECIPES_PREFIX = "recipes/";

    /** 缓存的「已完成进度数」，键是玩家 UUID。 */
    private static final Map<UUID, Integer> LIVE = new HashMap<>();

    private RecordsOfTheHistorianHandler() {
    }

    static {
        SessionCleanup.register(RecordsOfTheHistorianHandler::cleanupPlayer);
    }

    private static void cleanupPlayer(UUID id) {
        LIVE.remove(id);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int count = countCompleted(player);
        LIVE.put(player.getUUID(), count);
        pushCount(player, count);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LIVE.put(player.getUUID(), countCompleted(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % PUSH_INTERVAL != 0) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.RECORDS_OF_THE_HISTORIAN)) {
            return;
        }
        Integer cached = LIVE.get(player.getUUID());
        if (cached == null || player.tickCount % RECOUNT_INTERVAL == 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                cached = countCompleted(serverPlayer);
                LIVE.put(player.getUUID(), cached);
            } else {
                return;
            }
        }
        pushCount(player, cached);
    }

    /** 数这个玩家已经完成多少个进度（跳过配方类）。 */
    public static int countCompleted(ServerPlayer player) {
        int count = 0;
        for (Advancement advancement : player.server.getAdvancements().getAllAdvancements()) {
            ResourceLocation id = advancement.getId();
            if (id.getPath().startsWith(RECIPES_PREFIX)) {
                continue;
            }
            if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                count++;
            }
        }
        return count;
    }

    /** 物品侧读的进度：只认物品自己的 NBT。 */
    public static int countOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_COUNT);
    }

    /** 把进度写进戴着的那本《史记》，并让 Curios 重算修饰符。 */
    private static void pushCount(Player player, int count) {
        ItemStack stack = wornStack(player);
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getInt(TAG_COUNT) == count) {
            return;
        }
        stack.getOrCreateTag().putInt(TAG_COUNT, count);
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
                        && item.book() == ClassicBook.RECORDS_OF_THE_HISTORIAN) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static Attribute critRate() {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_rate"));
    }

    public static Attribute critDamage() {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_damage"));
    }
}
