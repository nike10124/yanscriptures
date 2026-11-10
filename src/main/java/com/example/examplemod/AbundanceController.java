package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「五饼二鱼」—— 击杀者拾取该生物的**全部可能掉落**，且数量翻倍。
 *
 * <h2>实现思路：反射展开战利品表</h2>
 * 原版战利品表是「按权重随机抽」的，正常一次击杀只会抽到其中一小部分。
 * 要做「所有可掉落物」，就必须跳过随机，直接遍历每一张表的每一个条目：
 *
 * <pre>
 * LootTable.pools            (private)      → List&lt;LootPool&gt;
 * LootPool.entries           (package)      → LootPoolEntryContainer[]
 * LootPoolEntryContainer.expand(ctx, c)     (public)  → 每个条目
 * LootPoolEntry.createItemStack(c, ctx)     (public)  → 产出物品
 * LootPool.functions         (package)      → LootItemFunction[]，逐条应用
 * </pre>
 *
 * 只有 {@code pools} / {@code entries} / {@code functions} 三个字段需要反射，
 * 后面的展开与产出都是公开方法。
 *
 * <h2>失败兜底</h2>
 * 若反射失败（字段改名、结构变化），自动降级为「用公开 API 重掷 7 次」，
 * 至少保证倍增与稀有掉落，不会让击杀本身出错。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbundanceController {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 重掷兜底的次数。 */
    private static final int FALLBACK_ROLLS = 7;
    /** 兜底的幸运值。 */
    private static final float FALLBACK_LUCK = 7.77F;
    /** 单次击杀最多生成多少件（防止极端战利品表把服务器拖死）。 */
    private static final int MAX_STACKS = 256;
    /** 每件掉落物数量的倍率。 */
    private static final int COUNT_MULTIPLIER = 2;
    /** 重掷补全：最多掷多少次。 */
    private static final int MAX_ROLLS = 400;
    /** 重掷补全：连续多少次没出新物品就认为「已经收集完整」。 */
    private static final int DRY_ROLLS = 40;

    private static Field poolsField;
    private static Field entriesField;
    private static Field functionsField;
    private static boolean reflectionBroken = false;

    private AbundanceController() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (ClassicBooksHandler.isRepublicShadow(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide() || !CovenantCodexHandler.wearsCovenantPublic(killer)) return;
        if (event.getEntity() instanceof Player) return;
        if (!(killer.level() instanceof ServerLevel level)) return;

        Entity dead = event.getEntity();
        if (!(dead instanceof net.minecraft.world.entity.LivingEntity living)) {
            return;
        }

        try {
            // ① 圣约专属掉落改在 {@link #onDeath} 里处理 —— 那里一定会触发，
            //    而 LivingDropsEvent 对某些 Boss（使徒）压根不会派发。

            ResourceLocation tableId = living.getLootTable();
            if (tableId == null || LootTable.EMPTY.equals(tableId)) {
                return;
            }
            LootTable table = level.getServer().getLootData().getLootTable(tableId);
            if (table == LootTable.EMPTY) {
                return;
            }

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, dead)
                    .withParameter(LootContextParams.ORIGIN, dead.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .withLuck(FALLBACK_LUCK)
                    .create(LootContextParamSets.ENTITY);

            // ① 反射展开：精确拿到战利品表里的每一条（不受概率影响）
            Map<String, ItemStack> all = reflectionBroken
                    ? new LinkedHashMap<>()
                    : expandAll(table, params, tableId);

            // ② 重掷补全：把随机性再跑一遍，直到「连续多次没有新物品」为止。
            //    反射可能因为结构差异失效，而这一步走的是公开 API，能独立兜住。
            enrichByRolling(table, params, tableId, all);

            // ③ 落地
            if (all.isEmpty()) {
                // 连重掷都拿不到东西（例如战利品表条件不满足）：退回「复制真实掉落」
                List<ItemEntity> copies = new ArrayList<>();
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem().copy();
                    if (!stack.isEmpty()) {
                        copies.add(spawn(level, dead, stack));
                    }
                }
                event.getDrops().addAll(copies);
                LOGGER.info("[五饼二鱼] {} 的战利品表展开为空，改为复制现有掉落 {} 件",
                        dead.getName().getString(), copies.size());
            } else {
                int spawned = 0;
                for (ItemStack stack : all.values()) {
                    if (spawned >= MAX_STACKS) {
                        break;
                    }
                    ItemStack copy = stack.copy();
                    int max = Math.max(1, copy.getMaxStackSize());
                    copy.setCount(Math.min(max, copy.getCount() * COUNT_MULTIPLIER));
                    event.getDrops().add(spawn(level, dead, copy));
                    spawned++;
                }
                LOGGER.info("[五饼二鱼] {} 的全部掉落已展开：{} 种，生成 {} 件（×{}）",
                        dead.getName().getString(), all.size(), spawned, COUNT_MULTIPLIER);
            }

            // ④ 专属掉落已在开头处理，这里不再重复
        } catch (Throwable t) {
            LOGGER.warn("[五饼二鱼] 掉落展开失败：{}", t.toString());
        }
    }

    /**
     * 用公开 API 反复重掷，直到「连续 {@link #DRY_ROLLS} 次没有新物品」。
     *
     * <p>这是「拿到全部可掉落物」最稳的一条路：不依赖任何非公开字段，
     * 只靠概率收敛 —— 稀有物品只要存在，掷够次数就会出现。
     */
    private static void enrichByRolling(LootTable table, LootParams params,
                                        ResourceLocation tableId, Map<String, ItemStack> target) {
        int rolls = 0;
        int dry = 0;
        while (rolls < MAX_ROLLS && dry < DRY_ROLLS) {
            final boolean[] added = {false};
            try {
                table.getRandomItemsRaw(new LootContext.Builder(params).create(tableId),
                        stack -> {
                            if (merge(target, stack)) {
                                added[0] = true;
                            }
                        });
            } catch (Throwable t) {
                break;
            }
            rolls++;
            if (added[0]) {
                dry = 0;
            } else {
                dry++;
            }
        }
    }

    // ==================== 反射展开 ====================

    /** 遍历整张战利品表，收集所有条目的产出。 */
    @SuppressWarnings("unchecked")
    private static Map<String, ItemStack> expandAll(LootTable table, LootParams params, ResourceLocation tableId) {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        try {
            LootContext context = new LootContext.Builder(params).create(tableId);

            if (poolsField == null) {
                poolsField = LootTable.class.getDeclaredField("pools");
                poolsField.setAccessible(true);
            }
            List<LootPool> pools = (List<LootPool>) poolsField.get(table);

            for (LootPool pool : pools) {
                LootItemFunction[] functions = poolFunctions(pool);
                for (LootPoolEntryContainer container : poolEntries(pool)) {
                    // expand 是公开方法，但容器类型来自包内字段，所以仍然按反射取到数组后调用
                    container.expand(context, entry -> entry.createItemStack(stack -> {
                        ItemStack out = stack;
                        for (LootItemFunction function : functions) {
                            out = function.apply(out, context);
                        }
                        if (!out.isEmpty()) {
                            merge(result, out.copy());
                        }
                    }, context));
                }
            }
        } catch (Throwable t) {
            reflectionBroken = true;
            LOGGER.warn("[五饼二鱼] 反射展开失败，降级为重掷：{}", t.toString());
        }
        return result;
    }

    private static LootPoolEntryContainer[] poolEntries(LootPool pool) throws Exception {
        if (entriesField == null) {
            entriesField = LootPool.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
        }
        return (LootPoolEntryContainer[]) entriesField.get(pool);
    }

    private static LootItemFunction[] poolFunctions(LootPool pool) throws Exception {
        if (functionsField == null) {
            functionsField = LootPool.class.getDeclaredField("functions");
            functionsField.setAccessible(true);
        }
        return (LootItemFunction[]) functionsField.get(pool);
    }

    // ==================== 工具 ====================

    /** 按「物品 + NBT」归并，数量取最大值（而不是累加），避免同一物品重复堆一大串。 */
    private static boolean merge(Map<String, ItemStack> map, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String key = stack.getItem().toString() + "|" + tagString(stack.getTag());
        ItemStack existing = map.get(key);
        if (existing == null) {
            map.put(key, stack.copy());
            return true;
        }
        if (stack.getCount() > existing.getCount()) {
            map.put(key, stack.copy());
        }
        return false;
    }

    private static String tagString(CompoundTag tag) {
        return tag == null || tag.isEmpty() ? "" : tag.toString();
    }

    // ==================== 圣约专属掉落：挂在死亡事件上 ====================

    /**
     * 佩戴者击杀特定生物时，额外生成该物品。
     *
     * <p>为什么用 {@code LivingDeathEvent} 而不是 {@code LivingDropsEvent}：
     * 有些 Boss（实测是使徒）根本不派发 {@code LivingDropsEvent} —— 它们的掉落由自己的
     * 代码处理。挂在死亡事件上则无论对方怎么写都能生效。
     *
     * <p>事件优先级用 LOWEST，确保其它模组先处理完死亡（避免它们把实体移除后我们找不到位置）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (ClassicBooksHandler.isRepublicShadow(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (killer.level().isClientSide()) return;
        if (!(killer.level() instanceof ServerLevel level)) return;
        if (!CovenantCodexHandler.wearsCovenantPublic(killer)) return;

        Entity dead = event.getEntity();
        if (dead instanceof Player) return;

        try {
            for (ItemStack bonus : CovenantBonusLoot.forEntity(dead)) {
                ItemEntity entity = new ItemEntity(level,
                        dead.getX(), dead.getY() + 0.5D, dead.getZ(), bonus.copy());
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
                LOGGER.info("[五饼二鱼] 追加专属掉落：{}（来自 {}）",
                        bonus.getHoverName().getString(), dead.getName().getString());
            }
        } catch (Throwable t) {
            LOGGER.warn("[五饼二鱼] 专属掉落失败：{}", t.toString());
        }
    }

    private static ItemEntity spawn(ServerLevel level, Entity around, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level,
                around.getX(), around.getY() + 0.3D, around.getZ(), stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
