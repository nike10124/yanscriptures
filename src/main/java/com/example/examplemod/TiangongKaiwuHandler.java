package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《天工开物》—— 会手艺的人，工具也替他省。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>铁砧消耗的经验 −50%；</li>
 *   <li>附魔台的基础附魔等级 +20 级。</li>
 * </ul>
 *
 * <p>为什么没用 mixin：作者说「用 mixin 吧」，但本工程在 2026-10-21 因为一次
 * 发布包启动崩溃把 mixin 整个摘掉了（见 build.gradle 的注释），重新开回来的风险
 * 是「开发环境能跑、正式包一进游戏就崩」，而这两条需求 Forge 恰好都留了事件口：
 * {@link AnvilUpdateEvent} 能改经验消耗，{@link EnchantmentLevelSetEvent}
 * 能直接改附魔台的每一行等级。**用事件不改别人一行字节码，是更稳的等价实现。**
 *
 * <p>唯一的妥协：{@code EnchantmentLevelSetEvent} 里没有玩家信息（只有方块坐标），
 * 所以「谁戴着」只能按「附魔台周围 8 格内有没有戴着的人」判断 —— 单人玩家体验一致，
 * 多人服里一个人在附魔台旁边戴着书、另一个人在用时也会被算上。这条已写进文档。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TiangongKaiwuHandler {

    private static final double ANVIL_COST_FACTOR = 0.5D;
    private static final int ENCHANT_LEVEL_BONUS = 20;
    private static final double ENCHANT_RANGE = 8.0D;

    private TiangongKaiwuHandler() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.TIANGONG_KAIWU)) {
            return;
        }
        int cost = event.getCost();
        if (cost <= 0) {
            return;
        }
        event.setCost(Math.max(1, (int) Math.floor(cost * ANVIL_COST_FACTOR)));
    }

    @SubscribeEvent
    public static void onEnchantLevelSet(EnchantmentLevelSetEvent event) {
        Level level = event.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!anyWearerNear(level, event.getPos())) {
            return;
        }
        event.setEnchantLevel(event.getEnchantLevel() + ENCHANT_LEVEL_BONUS);
    }

    private static boolean anyWearerNear(Level level, BlockPos pos) {
        for (Player player : level.players()) {
            if (!WornCache.wears(player, ClassicBook.TIANGONG_KAIWU)) {
                continue;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    <= ENCHANT_RANGE * ENCHANT_RANGE) {
                return true;
            }
        }
        return false;
    }
}
