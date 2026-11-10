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
 * 左下角的「罪孽 N / 10」小条（《罪与罚》）。
 *
 * <p>层数是服务端记在玩家持久数据里的，客户端读不到，所以由
 * {@link com.example.examplemod.NetworkHandler.CrimeLayerPacket} 每 20 刻同步一次；
 * 0 层时整条隐藏，不占屏幕。
 *
 * <p>位置：DPH 面板（{@code height - 70}）上面一行，两者互不遮挡。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CrimeLayerHud {

    /** 与 CrimeAndPunishmentHandler.MAX_LAYERS 保持一致。 */
    private static final int MAX_LAYERS = 10;
    /** 满层用暗红，未满用普通红。 */
    private static final int COLOR_FULL = 0xFFFF5555;
    private static final int COLOR_PART = 0xFFB03030;
    private static final int BACKGROUND = 0x90000000;

    private static int layers;

    private CrimeLayerHud() {
    }

    /** 由网络包调用（客户端线程）。 */
    public static void accept(int value) {
        layers = Math.max(0, Math.min(MAX_LAYERS, value));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || layers <= 0) {
            return;
        }

        boolean full = layers >= MAX_LAYERS;
        Component text = Component.literal("§c罪孽 ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(String.valueOf(layers)).withStyle(full ? ChatFormatting.RED : ChatFormatting.GOLD))
                .append(Component.literal(" §7/ " + MAX_LAYERS));

        GuiGraphics graphics = event.getGuiGraphics();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = 12;
        int y = screenHeight - 82; // DPH 在 height - 70

        int width = mc.font.width(text) + 8;
        graphics.fill(x - 2, y - 2, x + width, y + 10, BACKGROUND);
        graphics.drawString(mc.font, text, x + 2, y, full ? COLOR_FULL : COLOR_PART, true);
    }
}
