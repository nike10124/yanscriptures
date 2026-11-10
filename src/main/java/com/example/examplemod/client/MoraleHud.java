package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 右上角的「士气 X / Y」小条（《战争论》）。
 *
 * <p>士气是服务端记在玩家持久数据里的，客户端读不到，所以由
 * {@link com.example.examplemod.NetworkHandler.MoralePacket} 定期同步。
 * 作者 2026-11-07 明确要求「不要再放到左下角」（那里已经有 DPH 与罪孽层数），
 * 所以这一条画在右上角、坐标参考的右上角，不跟绿宝石平板之类的面板抢位置。
 *
 * <p>0 士气时整条隐藏；满了换成亮金色，方便一眼看出「下一刀必定暴击」。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MoraleHud {

    private static final int BACKGROUND = 0x90000000;
    private static final int COLOR_FRAME = 0xFF8A6D2F;
    private static final int COLOR_EMPTY = 0xFF2B2118;
    private static final int COLOR_FILL = 0xFFB8452F;
    private static final int COLOR_FULL = 0xFFF0C060;
    private static final int BAR_WIDTH = 74;
    private static final int BAR_HEIGHT = 3;
    /** 距右上角的内边距。 */
    private static final int MARGIN = 12;

    private static float morale;
    private static float cap;

    private MoraleHud() {
    }

    /** 由网络包调用（客户端线程）。 */
    public static void accept(float value, float max) {
        morale = Math.max(0.0F, value);
        cap = Math.max(0.0F, max);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || cap <= 0.0F || morale <= 0.0F) {
            return;
        }

        boolean full = morale + 1.0E-4F >= cap;
        Component text = Component.literal("§6士气 ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.format("%.1f", morale))
                        .withStyle(full ? ChatFormatting.YELLOW : ChatFormatting.RED))
                .append(Component.literal(" §7/ " + String.format("%.1f", cap)));

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(text);
        int width = Math.max(BAR_WIDTH, textWidth) + 8;
        int x = screenWidth - MARGIN - width;
        int y = MARGIN;

        graphics.fill(x - 2, y - 2, x + width, y + 16, BACKGROUND);
        graphics.drawString(mc.font, text, x + 2, y + 1, full ? COLOR_FULL : 0xFFFFFFFF, true);

        int barY = y + 12;
        graphics.fill(x + 2, barY, x + 2 + BAR_WIDTH, barY + BAR_HEIGHT, COLOR_EMPTY);
        int filled = Math.round(BAR_WIDTH * Math.min(1.0F, morale / cap));
        if (filled > 0) {
            graphics.fill(x + 2, barY, x + 2 + filled, barY + BAR_HEIGHT, full ? COLOR_FULL : COLOR_FILL);
        }
        graphics.fill(x + 2, barY, x + 2 + BAR_WIDTH, barY + 1, COLOR_FRAME);
    }
}
