//package com.example.examplemod.mixin;
//
//import com.example.examplemod.CommandmentsHandler;
//import net.minecraft.core.BlockPos;
//import net.minecraft.world.entity.Entity;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraft.world.level.block.state.BlockState;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//@Mixin(Block.class)
//public class BlockDropResourcesMixin {
//
//    @Inject(
//            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
//            at = @At("HEAD"),
//            cancellable = true,
//            remap = false
//    )
//    private static void onDropResources(BlockState state, Level level, BlockPos pos,
//                                        BlockEntity blockEntity, Entity entity, ItemStack tool,
//                                        CallbackInfo ci) {
//        System.out.println("[YanBook-Mixin] dropResources called: " + state.getBlock());
//        if (CommandmentsHandler.shouldCancelOreDrop(state, entity)) {
//            System.out.println("[YanBook-Mixin] Cancelling drop!");
//            ci.cancel();
//        }
//    }
//}