package com.example.examplemod;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 工作台做出来的书也要记材料档位（2026-10-31 新增）。
 *
 * <p>著书台是在 {@code BookWritingTableBlockEntity.craft()} 里直接打档的；
 * 而《道德经》这类走工作台 JSON 配方的书，配方本身没法写动态 NBT，
 * 所以在工作台合成完成的那一刻，回头读一眼合成格里的纸/墨/线，按同样的规则打上去。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GradeCraftHandler {

    private GradeCraftHandler() {
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (result.isEmpty() || !isGradedBook(result)) {
            return;
        }
        // 著书台出的书已经带档位，别覆盖掉它自己的随机属性
        if (result.hasTag() && result.getTag().contains(MaterialBonus.TAG_PAPER)) {
            return;
        }
        Container grid = event.getInventory();
        int[] tiers = new int[3];
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int line = MaterialBonus.lineOf(stack);
            if (line >= 0) {
                tiers[line] = Math.max(tiers[line], MaterialBonus.tierOf(stack));
            }
        }
        if (tiers[0] <= 0 && tiers[1] <= 0 && tiers[2] <= 0) {
            return;
        }
        RandomSource random = event.getEntity() != null && event.getEntity().level() != null
                ? event.getEntity().level().getRandom()
                : RandomSource.create();
        MaterialBonus.stamp(result, tiers, MaterialBonus.rollAttrs(random));
    }

    private static boolean isGradedBook(ItemStack stack) {
        return stack.getItem() instanceof ClassicBookItem || stack.getItem() instanceof BookOfPeaceItem;
    }
}
