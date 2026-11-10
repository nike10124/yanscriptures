package com.example.examplemod.client;

import com.example.examplemod.CapitalHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《资本论》右上角资本条。
 *
 * <p>位置：右上角，紧贴屏幕边缘 12 像素，画在《战争论》士气条下面一行——
 * 作者明确说过不要再往左下角堆（那里已经有 DPH 与罪孽层数）。
 *
 * <p>数值由 {@code NetworkHandler.CapitalPacket} 同步；{@code capital < 0} 表示隐藏。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = com.example.examplemod.ExampleMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapitalHud {

    private static final int MARGIN = 12;
    private static final int TOP = 30;
    private static final int BAR_WIDTH = 74;
    private static final int BAR_HEIGHT = 4;
    private static final int BACKGROUND = 0x90000000;
    private static final int COLOR_BAR = 0xFFC08A2A;
    private static final int COLOR_TEXT = 0xFFE8C070;

    private static long capital = -1L;

    private CapitalHud() {
    }

    public static void accept(long value) {
        capital = value;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || capital < 0L) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        PoseStack pose = graphics.pose();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();

        String text = "资本 " + capital;
        int width = minecraft.font.width(text);
        int x = screenWidth - MARGIN - Math.max(width, BAR_WIDTH);
        int y = TOP;

        graphics.fill(x - 2, y - 2, x + Math.max(width, BAR_WIDTH) + 2, y + 10, BACKGROUND);
        RenderSystem.enableBlend();
        graphics.drawString(minecraft.font, Component.literal(text), x, y, COLOR_TEXT, true);

        // 进度条：当前档位到下一档的进度
        long toNext = CapitalHandler.toNextStep(player);
        float progress = toNext <= 0L ? 1.0F : 1.0F - (toNext / 500.0F);
        int barY = y + 12;
        graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, BACKGROUND);
        int filled = (int) (BAR_WIDTH * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT, COLOR_BAR);
        }
        RenderSystem.disableBlend();
    }
}
