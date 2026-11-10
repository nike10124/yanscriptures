package com.example.examplemod;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MiningHandler {

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (event.canHarvest()) return; // 如果玩家本来就能挖，不干预

        var slotResult = CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.PRINCIPIA.get()))
                .orElse(null);

        if (slotResult == null) return;

        ItemStack stack = slotResult.stack();
        int level = BookOfPrinciplesItem.getProgressLevel(stack);
        int requiredLevel = getRequiredLevel(event.getTargetBlock());

        // 强行允许挖掘
        if (level >= requiredLevel) {
            event.setCanHarvest(true);
        }
    }

    private static int getRequiredLevel(BlockState state) {
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 4; // 远古残骸（下界合金级）
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;   // 钻石级
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;      // 铁级
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;     // 石头级
        return 0;
    }
}