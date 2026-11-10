package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BookOfMeScrollHandler {

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled event) {
        // 只在按住 Shift 时生效
        if (!Screen.hasShiftDown()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) return;

        // 只处理容器类 GUI（背包、饰品栏都继承自它）
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;

        // 1. 优先判断鼠标拿着的物品
        ItemStack carried = screen.getMenu().getCarried();
        if (handleScroll(event, carried)) return;

        // 2. 其次判断鼠标悬停的槽位
        Slot slot = screen.getSlotUnderMouse();
        if (slot != null && slot.hasItem()) {
            handleScroll(event, slot.getItem());
        }
    }

    // 根据物品类型决定滚动哪个
    private static boolean handleScroll(ScreenEvent.MouseScrolled event, ItemStack stack) {
        if (stack.isEmpty()) return false;

        double delta = event.getScrollDelta();
        int step = (int) Math.signum(delta) * 2;

        // 圣典·戒律
        if (stack.is(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get())) {
            ScriptureOfCommandmentsItem.scrollOffset -= step;
            // 🚨 动态获取总行数：按住 Alt 时 50 行，否则 30 行
            int totalLines = ScriptureOfCommandmentsItem.getTotalLines(Screen.hasAltDown());
            int maxOffset = Math.max(0, totalLines - ScriptureOfCommandmentsItem.PAGE_SIZE);
            if (ScriptureOfCommandmentsItem.scrollOffset < 0) ScriptureOfCommandmentsItem.scrollOffset = 0;
            if (ScriptureOfCommandmentsItem.scrollOffset > maxOffset) ScriptureOfCommandmentsItem.scrollOffset = maxOffset;
            event.setCanceled(true);
            return true;
        }

        // 如我所书
        if (stack.is(ExampleMod.BOOK_OF_ME.get())) {
            BookOfMeItem.scrollOffset -= step;
            int maxOffset = Math.max(0, BookOfMeItem.FULL_DESCRIPTION.size() - BookOfMeItem.PAGE_SIZE);
            if (BookOfMeItem.scrollOffset < 0) BookOfMeItem.scrollOffset = 0;
            if (BookOfMeItem.scrollOffset > maxOffset) BookOfMeItem.scrollOffset = maxOffset;
            event.setCanceled(true);
            return true;
        }

        // 圣约·原典（十二权柄 / 典章明细）
        if (stack.is(ExampleMod.COVENANT_CODEX.get())) {
            // 视图不同，每屏行数不同，上限也随之变化
            boolean detail = net.minecraft.client.gui.screens.Screen.hasAltDown();
            ScriptureOfCovenantItem.scrollOffset -= step;
            int maxOffset = ScriptureOfCovenantItem.maxScroll(detail);
            if (ScriptureOfCovenantItem.scrollOffset < 0) ScriptureOfCovenantItem.scrollOffset = 0;
            if (ScriptureOfCovenantItem.scrollOffset > maxOffset) {
                ScriptureOfCovenantItem.scrollOffset = maxOffset;
            }
            event.setCanceled(true);
            return true;
        }

        return false;
    }
}