package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 水炼药锅的「投料合成」。
 *
 * <p>玩家把物品扔进装有水的炼药锅，锅里的物品凑齐任意一条 {@link CauldronRecipes} 时，
 * 按配方扣掉对应数量，在锅口生成产物。</p>
 *
 * <p>不做持久化记账：物品实体本来就是短命的，只需要在它们还“新鲜”的几秒里
 * 每刻看一眼它们脚下是不是炼药锅。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CauldronRecipeHandler {

    /**
     * 待检查的物品实体 → 过期时刻（游戏刻）。
     *
     * <p>物品被扔出时在玩家脚下生成、飞一段才落到锅里，所以不能在
     * {@link EntityJoinLevelEvent} 那一刻就判断它脚下有没有锅；只能先登记，
     * 之后每刻重新算它的位置。</p>
     */
    private static final Map<ItemEntity, Long> PENDING = new WeakHashMap<>();

    /** 登记后盯 6 秒，够物品飞过来并落进锅里。 */
    private static final int LIFETIME_TICKS = 120;

    private static final double RADIUS = 0.75D;

    private CauldronRecipeHandler() {
    }

    /** 玩家扔出的物品：先登记，落到锅里再算。 */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer().level().isClientSide) {
            return;
        }
        mark(event.getEntity(), event.getPlayer().level().getGameTime() + LIFETIME_TICKS);
    }

    /** 别的途径生成、且一出生就落在锅上的物品（发射器、掉落物被推过来等）。 */
    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || event.getLevel().isClientSide) {
            return;
        }
        if (cauldronAt(item) != null) {
            mark(item, item.level().getGameTime() + LIFETIME_TICKS);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<ItemEntity> finished = new ArrayList<>();

        // 必须用快照：tryBrew 生成产物会触发 EntityJoinLevelEvent，那里的 mark()
        // 会往 PENDING 里写，直接遍历 entrySet 会 ConcurrentModificationException。
        for (ItemEntity item : new ArrayList<>(PENDING.keySet())) {
            Long expiresAt = PENDING.get(item);
            if (expiresAt == null || item.isRemoved() || item.level().isClientSide) {
                finished.add(item);
                continue;
            }
            BlockPos pos = cauldronAt(item);
            if (pos != null) {
                if (tryBrew(item, pos)) {
                    finished.add(item);
                    continue;
                }
                // 材料已经在锅里但还没凑齐：偶尔冒个泡，让玩家知道“它在煮”
                idleBubbles(item.level(), pos);
            }
            if (item.level().getGameTime() > expiresAt) {
                // 到期只停止盯着它，东西还留在锅里，之后再有新的投料照样能凑成
                finished.add(item);
            }
        }
        finished.forEach(PENDING::remove);
    }

    /** 把锅口附近的物品凑一遍；成了就消耗并产出，返回是否产出了东西。 */
    private static boolean tryBrew(ItemEntity anchor, BlockPos pos) {
        if (!(anchor.level() instanceof ServerLevel level)) {
            return false;
        }

        AABB box = new AABB(pos).inflate(RADIUS).setMinY(pos.getY()).setMaxY(pos.getY() + 1.2D);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                entity -> !entity.isRemoved() && pos.equals(cauldronAt(entity)));
        if (nearby.isEmpty()) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity entity : nearby) {
            stacks.add(entity.getItem());
        }

        CauldronRecipes.Match match = CauldronRecipes.match(stacks);
        if (match == null) {
            return false;
        }

        // 先扣料：按匹配结果逐堆扣掉对应数量
        int[] counts = match.counts();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) {
                continue;
            }
            ItemEntity entity = nearby.get(i);
            ItemStack stack = entity.getItem();
            stack.shrink(counts[i]);
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
        }

        // 在锅口生成产物
        ItemStack result = match.recipe().result().copy();
        ItemEntity output = new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, result);
        output.setDefaultPickUpDelay();
        level.addFreshEntity(output);
        // 产物落在锅口正上方，onItemJoin 会把它当成投料登记；立刻摘掉，
        // 免得同一批材料被反复“煮”。
        PENDING.remove(output);

        playBrewEffects(level, pos);
        // 谁扔的就记在谁头上：投料实体的 Owner 是扔出它的玩家
        if (anchor.getOwner() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            YanEventTrigger.INSTANCE.trigger(serverPlayer, "cauldron_brew");
        }
        return true;
    }

    /** 成锅的特效与音效：水花往上翻、紫烟散开、一点附魔光点，再配三层声音。 */
    private static void playBrewEffects(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.9D;
        double z = pos.getZ() + 0.5D;

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                x, y, z, 18, 0.28D, 0.05D, 0.28D, 0.15D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                x, y, z, 12, 0.3D, 0.1D, 0.3D, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                x, y + 0.15D, z, 14, 0.25D, 0.12D, 0.25D, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 0.25D, z, 6, 0.2D, 0.1D, 0.2D, 0.01D);

        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                SoundSource.BLOCKS, 0.8F, 1.4F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.6F);
    }

    /** 投料未凑齐时的一点“正在熬”的反馈，每 8 刻冒两三个泡。 */
    private static void idleBubbles(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.isClientSide || level.getGameTime() % 8L != 0L) {
            return;
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.5D, pos.getY() + 0.55D, pos.getZ() + 0.5D,
                    3, 0.22D, 0.05D, 0.22D, 0.01D);
        }
    }

    private static void mark(ItemEntity item, long expiresAt) {
        PENDING.put(item, expiresAt);
    }

    /** 物品落在哪口「装有水」的炼药锅上；没有就返回 null。 */
    private static BlockPos cauldronAt(ItemEntity item) {
        if (item.isRemoved()) {
            return null;
        }
        // 物品可能在锅正上方（还没掉进去）也可能已经贴着锅底，上下各看一格
        BlockPos base = item.blockPosition();
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = base.above(dy);
            BlockState state = item.level().getBlockState(pos);
            if (state.is(Blocks.WATER_CAULDRON)) {
                return pos;
            }
        }
        return null;
    }
}
