package com.example.examplemod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatHandler {

    // 工具方法：检查玩家饰品栏里有没有装备《方块与和平》
    private static boolean hasBookEquipped(Player player) {
        Optional<SlotResult> slotResult = CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_PEACE.get()))
                .orElse(Optional.empty());
        return slotResult.isPresent();
    }

    // 1. 玩家受到伤害时
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        // 只处理玩家受到的伤害
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // 如果没有装备对应的书，直接返回，不触发机制
        if (!hasBookEquipped(player)) return;

        // 按 Shift 切换姿态
        if (player.isShiftKeyDown()) {
            // 防御姿态（按住 Shift）：受到的伤害降低 30%
            event.setAmount(event.getAmount() * 0.7f);
        } else {
            // 攻击姿态（松开 Shift）：受到的伤害提高 20%
            event.setAmount(event.getAmount() * 1.2f);
        }
    }

    // 2. 玩家造成伤害时（同时处理《方块与和平》的姿态和《翠玉录》的魔法伤害）
    // 2. 玩家造成伤害时（处理《方块与和平》姿态和《翠玉录》魔法伤害）
    @SubscribeEvent
    public static void onPlayerDealDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // ----- 效果一：《方块与和平》的攻防姿态 -----
        boolean hasPeaceBook = CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_PEACE.get()).isPresent())
                .orElse(false);

        if (hasPeaceBook) {
            if (player.isShiftKeyDown()) {
                event.setAmount(event.getAmount() * 0.7f); // 防御姿态
            } else {
                event.setAmount(event.getAmount() * 1.2f); // 攻击姿态
            }
        }

        // ----- 效果二：《翠玉录》的魔法伤害加成 -----
        boolean hasTablet = CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(ExampleMod.EMERALD_TABLET.get()).isPresent())
                .orElse(false);

        if (hasTablet) {
            // 🚨 核心修改：不再读取自定义属性，直接根据已解锁的药水种类计算伤害！
            int unlockedCount = PotionProgressTracker.getTotalUnlockedCount(player);
            if (unlockedCount > 0) {
                float magicDamage = unlockedCount * 1.0f; // 每种药水增加 1 点魔法伤害
                event.setAmount(event.getAmount() + magicDamage); // 直接附加在本次物理伤害上
            }
        }
    }
}