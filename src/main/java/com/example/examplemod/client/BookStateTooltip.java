package com.example.examplemod.client;

import java.util.List;

import com.example.examplemod.ClassicBook;
import com.example.examplemod.CritiqueOfPureReasonHandler;
import com.example.examplemod.InnerCanonHandler;
import com.example.examplemod.OneThousandAndOneNightsHandler;
import com.example.examplemod.WealthOfNationsHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 名著提示框里「实时数字/状态」那一行。
 *
 * <p>作者 2026-11-08 定：这类信息**直接显示在物品介绍上，不做单独 HUD**。
 * 所以这里只往 tooltip 里追加行，不注册任何渲染器。
 *
 * <p>两本书的数字都是「客户端自己就能算出来的」：
 * <ul>
 *   <li>《国富论》的财富只看自己的物品栏，本地数就行；</li>
 *   <li>《黄帝内经》的五行按世界时间算（见 {@link InnerCanonHandler#element}），和服务器同一套公式。</li>
 * </ul>
 * 所以都不需要发数据包，也就不会出现「提示框和实际效果对不上」的情况。
 */
@OnlyIn(Dist.CLIENT)
public final class BookStateTooltip {

    private BookStateTooltip() {
    }

    public static void append(ClassicBook book, ItemStack stack, List<Component> tooltip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (book == ClassicBook.WEALTH_OF_NATIONS) {
            if (minecraft.player == null) {
                return;
            }
            int distinct = WealthOfNationsHandler.distinctItems(minecraft.player);
            int wealth = WealthOfNationsHandler.wealth(minecraft.player);
            tooltip.add(Component.translatable("tooltip.yanscriptures.state.wealth",
                    wealth, WealthOfNationsHandler.MAX_WEALTH, distinct,
                    WealthOfNationsHandler.ITEMS_PER_WEALTH)
                    .withStyle(ChatFormatting.GOLD));
        } else if (book == ClassicBook.INNER_CANON_OF_THE_YELLOW_EMPEROR) {
            if (minecraft.level == null) {
                return;
            }
            int element = InnerCanonHandler.element(minecraft.level);
            int seconds = (InnerCanonHandler.ticksUntilNext(minecraft.level) + 19) / 20;
            tooltip.add(Component.translatable("tooltip.yanscriptures.state.element",
                    InnerCanonHandler.ELEMENTS[element], InnerCanonHandler.bonusText(element), seconds)
                    .withStyle(ChatFormatting.GREEN));
        } else if (book == ClassicBook.CRITIQUE_OF_PURE_REASON) {
            if (minecraft.player == null) {
                return;
            }
            int points = CritiqueOfPureReasonHandler.pointsOf(minecraft.player);
            tooltip.add(Component.translatable("tooltip.yanscriptures.state.enchant_points",
                    points, trim(points * 0.5D), trim(points * 1.0D))
                    .withStyle(ChatFormatting.AQUA));
            int[] progress = CritiqueOfPureReasonHandler.flightProgress(minecraft.player);
            int enchanted = progress[0];
            int plain = progress[1];
            if (enchanted > 21 && plain == 0) {
                tooltip.add(Component.translatable("tooltip.yanscriptures.state.flight.on", enchanted)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add((plain > 0
                        ? Component.translatable("tooltip.yanscriptures.state.flight.off_extra", enchanted, plain)
                        : Component.translatable("tooltip.yanscriptures.state.flight.off", enchanted))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (book == ClassicBook.ONE_THOUSAND_AND_ONE_NIGHTS) {
            int[] growth = OneThousandAndOneNightsHandler.growthOf(stack);
            int total = 0;
            for (int value : growth) {
                total += value;
            }
            tooltip.add(Component.translatable("tooltip.yanscriptures.state.nights",
                    total, OneThousandAndOneNightsHandler.CAP).withStyle(ChatFormatting.DARK_PURPLE));
        } else if (book == ClassicBook.SUMMA_THEOLOGICA) {
            // 作者 2026-11-09：用「子 / 父 / 灵」三个字的颜色表示印记是否生效（亮金=生效，暗灰=没有）。
            MutableComponent line = Component.translatable("tooltip.yanscriptures.state.marks").withStyle(ChatFormatting.GRAY);
            line.append(Component.translatable("tooltip.yanscriptures.state.mark.son").withStyle(
                    SummaStateClient.active(SummaStateClient.SON) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("tooltip.yanscriptures.state.mark.father").withStyle(
                    SummaStateClient.active(SummaStateClient.FATHER) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("tooltip.yanscriptures.state.mark.spirit").withStyle(
                    SummaStateClient.active(SummaStateClient.SPIRIT) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            tooltip.add(line);
        }
    }

    /** 0.5 这种小数去掉尾随的 .0，避免显示「+10.0%」。 */
    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
