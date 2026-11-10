package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 《寂灭之笺》的炼成（2026-10-28）。
 *
 * <p>规则由作者定：把《至邪之笺》丢进岩浆，岩浆正下方要有<b>激活的信标</b>，
 * 66% 的概率化成《寂灭之笺》，失败的会被烧成灰。</p>
 *
 * <p>实现上不扫描全世界：只在《至邪之笺》落地的瞬间登记，之后每刻看一眼它还在不在岩浆里。
 * 信标是否「激活」用存档数据里的 {@code Levels}（金字塔层数，0 表示没点燃）判断，
 * 不去反编译信标内部状态，免得跟着别的模组或版本变动一起坏。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnnihilationRitual {

    /** 成功概率。 */
    private static final float SUCCESS_CHANCE = 0.66F;

    /**
     * 往下找信标的最大深度。
     *
     * <p>信标的光柱是可以穿透岩浆的，所以不限制「必须紧贴岩浆下方」——
     * 只要是同一竖列里有一座点亮的信标、且光柱一路没被不透光的方块挡住就行。</p>
     */
    private static final int BEACON_SEARCH_DEPTH = 256;

    /** 登记表：物品实体 → 什么时候可以放弃追踪（游戏刻）。 */
    private static final Map<ItemEntity, Long> TRACKED = new WeakHashMap<>();

    private AnnihilationRitual() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity entity)) {
            return;
        }
        if (!entity.getItem().is(Materials.CORRUPT_PARCHMENT.get())) {
            return;
        }
        // 给 30 秒的追踪期，够它在岩浆里烧完或者成功
        TRACKED.put(entity, event.getLevel().getGameTime() + 600L);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) {
            return;
        }

        TRACKED.entrySet().removeIf(entry -> {
            ItemEntity entity = entry.getKey();
            if (entity == null || !entity.isAlive()) {
                return true;
            }
            if (!(entity.level() instanceof ServerLevel level)) {
                return true;
            }
            long now = level.getGameTime();
            return tryConvert(entity, level) || now > entry.getValue();
        });
    }

    /**
     * 判断这一张纸此刻该不该转化。
     *
     * @return true 表示已经处理完（转化成功或被烧掉），不用再追踪
     */
    private static boolean tryConvert(ItemEntity entity, ServerLevel level) {
        if (entity.getItem().isEmpty() || !entity.getItem().is(Materials.CORRUPT_PARCHMENT.get())) {
            return true;
        }

        BlockPos pos = entity.blockPosition();
        if (!level.getBlockState(pos).is(Blocks.LAVA) && !level.getBlockState(pos).getFluidState()
                .is(net.minecraft.tags.FluidTags.LAVA)) {
            return false;
        }
        if (!hasActiveBeaconBelow(level, pos)) {
            return false;
        }

        if (level.random.nextFloat() < SUCCESS_CHANCE) {
            ItemStack result = new ItemStack(Materials.ANNIHILATED_PARCHMENT.get());
            entity.setItem(result);
            // 从岩浆里抬出来，否则下一秒就被烧掉
            entity.setPos(entity.getX(), pos.getY() + 1.2D, entity.getZ());
            entity.setDeltaMovement(0.0D, 0.25D, 0.0D);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    24, 0.3D, 0.4D, 0.3D, 0.02D);
        } else {
            entity.discard();
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.5F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    12, 0.25D, 0.25D, 0.25D, 0.01D);
        }
        return true;
    }

    /**
     * 同一竖列下方有没有「光柱能照到这里」的、已点亮的信标。
     *
     * <p>判定分两步：</p>
     * <ol>
     *   <li>从岩浆往下逐格找 {@code minecraft:beacon}；</li>
     *   <li>中途遇到<b>不透光的方块</b>就说明光柱被挡住了，直接放弃；</li>
     *   <li>找到信标后看它的 {@code Levels}（金字塔层数，0 = 没点亮）。</li>
     * </ol>
     *
     * <p>岩浆、水、玻璃这类不算遮挡——它们不会挡住信标的光柱，
     * 所以「岩浆被光柱穿透」这个画面正好成立。</p>
     */
    private static boolean hasActiveBeaconBelow(ServerLevel level, BlockPos lavaPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int depth = 1; depth <= BEACON_SEARCH_DEPTH; depth++) {
            cursor.set(lavaPos.getX(), lavaPos.getY() - depth, lavaPos.getZ());
            if (level.isOutsideBuildHeight(cursor)) {
                return false;
            }
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.BEACON)) {
                if (!(level.getBlockEntity(cursor) instanceof BeaconBlockEntity beacon)) {
                    return false;
                }
                CompoundTag tag = beacon.getUpdateTag();
                return tag.getInt("Levels") > 0;
            }
            if (state.isSolidRender(level, cursor)) {
                return false;
            }
        }
        return false;
    }
}
