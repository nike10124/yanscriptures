package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/**
 * 《至理之书》——作者开的后门之一（作者 2026-11-13 定）。
 *
 * <p>只有 ID 为 {@code nike10124} 的玩家，在带本模组的存档里**第一次进入**时收到一本，
 * 发放记录按存档记（{@link AuthorGiftData}），扔了不会补发。</p>
 *
 * <p>使用后随机得到一本**史诗稀有度**的名著，书本身消耗掉。</p>
 *
 * <p>名字固定蓝色、贴图直接用模组 logo（作者要求：「贴图就使用 logo 的贴图，字体颜色就用蓝色」）。</p>
 */
public class BookOfTruthItem extends Item {

    /** 铭文：作者钦定的两句话（作者 m20955）。 */
    private static final String LINE_ONE = "tooltip.yanscriptures.book_of_truth.line1";
    private static final String LINE_TWO = "tooltip.yanscriptures.book_of_truth.line2";

    /** 名字颜色：蓝色。 */
    private static final int NAME_COLOR = 0x5555FF;

    public BookOfTruthItem(Properties properties) {
        super(properties);
    }

    /**
     * 所有史诗名著里随机抽一本。
     *
     * <p>从 {@link ExampleMod#CLASSIC_BOOKS} 里筛 {@link Rarity#EPIC}，而不是维护第二份名单，
     * 以后再把某本书提成史诗也不用改这里。
     */
    private static List<ItemStack> epicBooks() {
        List<ItemStack> picks = new ArrayList<>();
        for (RegistryObject<Item> object : ExampleMod.CLASSIC_BOOKS) {
            Item item = object.get();
            if (item.getDefaultInstance().getRarity() == Rarity.EPIC) {
                picks.add(new ItemStack(item));
            }
        }
        return picks;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(style -> style.withColor(NAME_COLOR));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(LINE_ONE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LINE_TWO).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        List<ItemStack> picks = epicBooks();
        if (picks.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_of_truth.empty"), false);
            return InteractionResultHolder.fail(stack);
        }

        ItemStack reward = picks.get(player.getRandom().nextInt(picks.size())).copy();
        Component name = reward.getHoverName();
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        player.displayClientMessage(
                Component.translatable("message.yanscriptures.book_of_truth.granted", name)
                        .withStyle(ChatFormatting.BLUE), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
