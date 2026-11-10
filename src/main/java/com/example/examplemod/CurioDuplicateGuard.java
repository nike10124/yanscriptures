package com.example.examplemod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public class CurioDuplicateGuard {

    /**
     * 全局检查：玩家是否已经装备了同类型的饰品。
     * 所有饰品在 canEquip 里调用它即可。
     *
     * @return true 表示可以装备，false 表示已经装备过同类型
     */
    public static boolean canEquip(Player player, ItemStack stack) {
        // 统计玩家饰品栏里已经装备了多少个这种物品
        long count = CuriosApi.getCuriosInventory(player).resolve()
                .map(inv -> inv.findCurios(stack.getItem()).size())
                .orElse(0);
        // 已经存在同类型 → 不允许再装备
        return count == 0;
    }
}