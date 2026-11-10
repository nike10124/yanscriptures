package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class EntityInfoOverlay {

    // ==================== [修复 2026-10-21] 每帧只画一次 ====================
    // 原实现监听 RenderGuiOverlayEvent.Post —— 该事件对【每一个 HUD 元素】（血条、快捷栏、聊天、
    // Boss 条……）各触发一次，导致这个实体信息面板每帧被重绘十几次。
    // 改用 RenderGuiEvent.Post：整帧 HUD 全部画完后只触发一次。
    // 原代码：public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return; // 打开GUI时不显示

        // 获取准星指向的实体
        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity living)) return;

        GuiGraphics gui = event.getGuiGraphics();
        int x = 10;
        int y = 10;

        // 生物名称
        gui.drawString(mc.font, living.getName().getString(), x, y, 0xFFFFFF);
        y += 12;

        // 当前生命值 / 最大生命值
        float health = living.getHealth();
        float maxHealth = living.getMaxHealth();
        gui.drawString(mc.font, "§c❤ " + String.format("%.1f", health) + " / " + String.format("%.1f", maxHealth), x, y, 0xFF5555);
        y += 12;

        // 护甲值
        double armor = living.getAttributeValue(Attributes.ARMOR);
        gui.drawString(mc.font, "§b🛡 " + String.format("%.1f", armor), x, y, 0x55FFFF);
    }
}