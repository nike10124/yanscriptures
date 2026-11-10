package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 面板「每击伤害」显示（DPH = Damage Per Hit）。
 *
 * <p>和 {@link DpsHud} 的分工：DPS 那边是<b>实测</b>结果（打出去以后统计的），
 * 这里读的是<b>面板</b>——也就是 {@code Attributes.ATTACK_DAMAGE} 这个属性算完的值，
 * 用来验收「百分比乘区到底有没有生效」，不用打怪就能看到数字变没变。
 *
 * <p>口径提醒：{@code getAttributeValue(ATTACK_DAMAGE)} 已经包含
 * 加法（{@code ADDITION}）、乘法乘区（{@code MULTIPLY_BASE}）与独立乘区（{@code MULTIPLY_TOTAL}）
 * 三层的结果，所以它比空手时的 1.0 高多少就是全部加成之后的样子。
 * 「预计 DPS」= 每击伤害 × 攻速，只是估算，真实 DPS 看左边的 {@link DpsHud}。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DphHud {

    /** 背景色（半透明黑），与 DPS 面板保持一致。 */
    private static final int BACKGROUND = 0x90000000;

    private DphHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        double perHit = mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
        // 攻速异常（0 或负数，被别的模组改坏）时就不显示预计 DPS，免得出现 Infinity
        boolean showRate = speed > 1.0E-4D;

        StringBuilder text = new StringBuilder("§eDPH §f").append(fmt(perHit));
        if (showRate) {
            text.append("  §7攻速 §f").append(fmt(speed)).append("§7/s");
            text.append("  §7预计 §f").append(fmt(perHit * speed)).append("§7 DPS");
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // DPS 面板在左下角血条上方（height - 56），这里再往上一层，互不遮挡
        int x = 12;
        int y = screenHeight - 70;

        int width = mc.font.width(text.toString()) + 8;
        graphics.fill(x - 2, y - 2, x + width, y + 10, BACKGROUND);
        graphics.drawString(mc.font, text.toString(), x + 2, y, 0xFFFFFF, true);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
