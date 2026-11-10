package com.example.examplemod.client;

import com.example.examplemod.BookOfMeItem;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.ScriptureOfCommandmentsItem;
import com.example.examplemod.ScriptureOfCovenantItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 鼠标悬停在长文本书籍饰品上时，按一下 Shift+Alt 打开大书页（{@link BookPageScreen}）。
 *
 * <p>为什么不再让 tooltip 自己翻页：tooltip 一页只有 5~12 行，几十行正文要点十几次滚轮。
 * 这里只做「打开」，不带任何服务端交互 —— 纯客户端界面，不需要发网络包，
 * 也不影响背包里正常拿起物品。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookPageOpener {

    private BookPageOpener() {
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 必须两个键同时按住；只按一个仍然走 tooltip 的旧逻辑（Shift 展开 / Shift+Alt 明细）
        if (!Screen.hasShiftDown() || !Screen.hasAltDown()) {
            return;
        }
        Screen current = event.getScreen();
        if (!(current instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        Slot slot = container.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) {
            return;
        }
        BookPageScreen screen = build(slot.getItem());
        if (screen != null) {
            // 延后一 tick 再换屏：直接在按键事件里 setScreen 会让当前界面继续处理这一下按键
            minecraft.execute(() -> minecraft.setScreen(screen));
        }
    }

    /** 按物品决定标题与正文；不认识的物品返回 null（保持原样）。 */
    private static BookPageScreen build(ItemStack stack) {
        if (stack.is(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get())) {
            return new BookPageScreen(
                    Component.literal("《十 诫》"),
                    Component.literal("守约者受祝福，违逆者受诅咒"),
                    ScriptureOfCommandmentsItem.fullLines(stack),
                    // 与 ScriptureOfCommandmentsItem.GRADIENT_FROM / GRADIENT_TO 完全一致
                    ScriptureOfCommandmentsItem.GRADIENT_FROM,
                    ScriptureOfCommandmentsItem.GRADIENT_TO);
        }
        if (stack.is(ExampleMod.COVENANT_CODEX.get())) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§6§l▍ 十二权柄"));
            lines.addAll(ScriptureOfCovenantItem.buildDescription());
            lines.add(Component.literal("§6§l▍ 典章明细"));
            lines.addAll(ScriptureOfCovenantItem.buildDetail());
            return new BookPageScreen(
                    Component.literal("《圣 约 · 原 典》"),
                    Component.literal("❖ ＴＨＥ ＣＯＶＥＮＡＮＴ ❖"),
                    lines,
                    // 与 ScriptureOfCovenantItem.GRADIENT_FROM / GRADIENT_TO 完全一致
                    0xFFFFD700, 0xFFFFFFF0);
        }
        if (stack.is(ExampleMod.BOOK_OF_ME.get())) {
            return new BookPageScreen(
                    Component.literal("《如 我 所 书》"),
                    Component.literal("一段史诗所结出的果实，其中闪耀着十四种光辉"),
                    BookOfMeItem.FULL_DESCRIPTION,
                    // 与 BookOfMeItem.NAME_FROM / NAME_TO 完全一致（天蓝 → 浅粉）
                    0xFF66AAFF, 0xFFFF99CC);
        }
        return null;
    }
}
