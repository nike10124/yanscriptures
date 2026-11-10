package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GuiHandler {

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return; // 右键
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 鼠标拿着附魔物品
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) return;
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(carried);
        if (enchantments.isEmpty()) return;

        // 2. 鼠标悬停在《空白的书页》上
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !(slot.getItem().getItem() instanceof BlankPageItem)) return;

        // 3. 收集附魔 ID 并通过网络包发送给服务端
        List<String> enchantmentIds = new ArrayList<>();
        for (Enchantment ench : enchantments.keySet()) {
            enchantmentIds.add(ForgeRegistries.ENCHANTMENTS.getKey(ench).toString());
        }
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.RecordEnchantPacket(enchantmentIds));

        // 4. 取消原版逻辑
        event.setCanceled(true);
    }
}