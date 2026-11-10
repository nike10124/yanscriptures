package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《圣约·原典》的专属 HUD：显示「万军之磐石」的 777 特殊生命。
 *
 * <p>数据来自饰品自身的 NBT（服务端写入，Curios 负责同步），因此客户端不需要额外的网络包。
 * 只有在佩戴原典时才显示，不带饰品的玩家看不到这份界面。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CovenantHud {

    /** 与 CovenantCodexHandler.SPECIAL_HEALTH_MAX 保持一致。 */
    private static final int MAX = 777;

    /** 最近一次从服务端收到的数值。 */
    private static int syncedHp = MAX;
    private static int syncedDps = 0;
    /** 数据是否新鲜（超过 3 秒没更新就当作未佩戴）。 */
    private static long lastUpdateMs = 0L;

    /** 服务端推送入口。 */
    public static void accept(NetworkHandler.CovenantStatsPacket packet) {
        syncedHp = Math.max(0, Math.min(MAX, packet.specialHp()));
        syncedDps = Math.max(0, packet.dps());
        lastUpdateMs = net.minecraft.Util.getMillis();
    }

    private static final int WIDTH = 132;
    private static final int HEIGHT = 8;
    private static final int MARGIN = 12;
    private static final int BOTTOM_OFFSET = 38;

    private CovenantHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        ItemStack stack = findCovenant(mc);
        if (stack.isEmpty()) {
            return;
        }

        int hp = syncedHp;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = MARGIN;
        int y = screenHeight - BOTTOM_OFFSET;

        // 底框
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xB0000000);
        // 灰底（未填充部分）
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0x60FFFFFF);

        // 填充：按比例变色
        float ratio = hp / (float) MAX;
        int fill = Math.round(WIDTH * ratio);
        int color = ratio > 0.5F ? 0xFFF2E3A0 : ratio > 0.2F ? 0xFFFFC24B : 0xFFFF5555;
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + HEIGHT, color);
        }
        // 高光，让条看起来有金属感
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + 2, 0x40FFFFFF);
        }

        String label = "§6圣约·万军之磐石  §f" + hp + " §7/ " + MAX;
        graphics.drawString(mc.font, label, x, y - 11, 0xFFFFFF, true);

        // DPS 已移到全局显示（DpsHud），这里只留特殊生命
    }

    private static ItemStack findCovenant(Minecraft mc) {
        try {
            return CuriosApi.getCuriosInventory(mc.player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                    .map(result -> result.stack())
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }
}
