package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 著书笔。
 *
 * <p>对着一本原版讲台的底座 Shift + 右键，会把讲台改造成著书台：
 * 朝向、以及讲台上摊着的那本书都会跟着搬过去。笔本身不消耗，可以反复用。</p>
 */
public class WritingQuillItem extends Item {

    public WritingQuillItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yanscriptures.writing_quill")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        // 只认原版讲台；其他方块一律交还给正常逻辑
        if (!state.is(Blocks.LECTERN)) {
            return InteractionResult.PASS;
        }

        // 不按 Shift 时照常翻书，不抢原版交互
        if (!context.getPlayer().isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Direction facing = state.getValue(LecternBlock.FACING);

        // 先把讲台上的书取下来，改造完再放回著书台
        ItemStack book = ItemStack.EMPTY;
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            book = lectern.getBook();
            lectern.setBook(ItemStack.EMPTY);
        }

        BlockState replaced = ExampleMod.BOOK_WRITING_TABLE.get().defaultBlockState()
                .setValue(BookWritingTableBlock.FACING, facing);
        level.setBlockAndUpdate(pos, replaced);

        if (!book.isEmpty()
                && level.getBlockEntity(pos) instanceof BookWritingTableBlockEntity table) {
            table.setItem(BookWritingTableBlockEntity.BOOK_SLOT, book);
        }

        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);

        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.yanscriptures.writing_quill.converted"), true);
        }
        return InteractionResult.CONSUME;
    }
}
