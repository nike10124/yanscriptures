package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 全局 DPS 显示：任何玩家打怪都会看到自己的输出。
 *
 * <p>与饰品无关 —— 它是测试工具，不该因为没戴某件装备就消失。
 * 数据由 {@link DpsMeter} 每秒推送一次；连续 5 秒没有输出后自动淡出。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DpsHud {

    /** 当前 DPS 与命中统计。 */
    private static int dps = 0;
    private static int hits = 0;
    private static int crits = 0;
    /** 最后一次收到非零 DPS 的时间。 */
    private static long lastNonZeroMs = 0L;
    /** 淡出延迟（毫秒）：停手 5 秒后隐藏。 */
    private static final long FADE_AFTER_MS = 5000L;

    private DpsHud() {
    }

    public static void accept(int value, int hitCount, int critCount) {
        dps = value;
        hits = hitCount;
        crits = critCount;
        if (value > 0) {
            lastNonZeroMs = net.minecraft.Util.getMillis();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        boolean recent = now - lastNonZeroMs <= FADE_AFTER_MS;
        if (dps <= 0 && !recent) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 放在左下角血条上方，避免和原版 HUD 打架
        int x = 12;
        int y = screenHeight - 56;

        String text = "§eDPS §f" + dps;
        if (hits > 0) {
            text += "  §7暴击 §f" + crits + "§7/§f" + hits;
        }
        int width = mc.font.width(text) + 8;
        graphics.fill(x - 2, y - 2, x + width, y + 10, 0x90000000);
        graphics.drawString(mc.font, text, x + 2, y, 0xFFFFFF, true);
    }
}
