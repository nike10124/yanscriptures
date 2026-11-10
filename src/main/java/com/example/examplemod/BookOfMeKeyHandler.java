package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class BookOfMeKeyHandler {

    /** 上一帧的 Alt 状态，只在变化时发包。 */
    private static boolean lastAltState = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // ==================== V 键 ====================
        while (ExampleMod.BLANK_PAGE_KEY.consumeClick()) {
            Player player = mc.player;
            boolean hasBookOfMe = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                    .isPresent();

            if (hasBookOfMe) {
                // 🚨 发送到服务端执行，避免客户端数据被覆盖
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BookOfMeActionPacket());
            } else {
                // 退而求其次，尝试空白的书页
                boolean hasBlankPage = CuriosApi.getCuriosInventory(player).resolve()
                        .flatMap(inv -> inv.findFirstCurio(ExampleMod.BLANK_PAGE.get()))
                        .isPresent();
                if (hasBlankPage) {
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BlankPageActionPacket());
                }
            }
        }

        // ==================== Shift + T：《神曲》穿越维度 ====================
        while (ExampleMod.COMEDY_TELEPORT_KEY.consumeClick()) {
            if (!mc.options.keyShift.isDown()) continue;
            if (!ClassicBooksHandler.wears(mc.player, ClassicBook.DIVINE_COMEDY)) continue;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ComedyTeleportPacket());
        }

        // ==================== Shift + D：《梦的解析》入梦 ====================
        while (ExampleMod.DREAM_ENTER_KEY.consumeClick()) {
            if (!mc.options.keyShift.isDown()) continue;
            if (!ClassicBooksHandler.wears(mc.player, ClassicBook.INTERPRETATION_OF_DREAMS)) continue;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.DreamEnterPacket());
        }

        // ==================== Z 键：时停 ====================
        // 两个毕业饰品共用 Z 键：
        //   只戴《如我所书》 → 20 秒时停
        //   戴着《圣约·原典》→「日影停住」7 秒（两个都戴时圣约优先）
        // 具体参数由服务端按佩戴情况决定，这里只负责「戴着就发」。
        while (ExampleMod.BOOK_OF_ME_Z_KEY.consumeClick()) {
            Player player = mc.player;
            boolean hasAny = CuriosApi.getCuriosInventory(player).resolve()
                    .map(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()).isPresent()
                            || inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()).isPresent())
                    .orElse(false);
            if (hasAny) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BookOfMeTimeStopPacket());
            }
        }
        // ==================== L 键（需按住 Shift）：理性附魔 ====================
        while (ExampleMod.BOOK_OF_ME_L_KEY.consumeClick()) {
            Player player = mc.player;
            if (!player.isShiftKeyDown()) return;

            boolean hasBookOfMe = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                    .isPresent();
            if (hasBookOfMe) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BookOfMeEnchantPacket());
            }
        }

        // ==================== K 键：主动脱困 ====================
        // 两个毕业饰品通用：按下即沿视线方向传出去，不依赖任何自动判定。
        while (ExampleMod.ESCAPE_KEY.consumeClick()) {
            Player player = mc.player;
            boolean hasAny = CuriosApi.getCuriosInventory(player).resolve()
                    .map(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()).isPresent()
                            || inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()).isPresent())
                    .orElse(false);
            if (hasAny) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EscapePacket());
            }
        }

        // ==================== Alt 键状态同步（用于申命之赐的降灾） ====================
        // Player 没有公开接口读 Alt，只能在客户端检测、把状态告诉服务端。
        boolean altNow = net.minecraft.client.gui.screens.Screen.hasAltDown();
        if (altNow != lastAltState) {
            lastAltState = altNow;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.AltStatePacket(altNow));
        }

        // ==================== F 键：飞行开关（圣约·原典） ====================
        while (ExampleMod.COVENANT_FLIGHT_KEY.consumeClick()) {
            Player player = mc.player;
            boolean hasCovenant = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                    .isPresent();
            if (hasCovenant) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CovenantFlightPacket());
            }
        }

        // ==================== J 键：七碗倾倒（圣约·原典） ====================
        while (ExampleMod.COVENANT_JUDGEMENT_KEY.consumeClick()) {
            Player player = mc.player;
            boolean hasCovenant = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.findFirstCurio(ExampleMod.COVENANT_CODEX.get()))
                    .isPresent();
            if (hasCovenant) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CovenantJudgementPacket());
            }
        }
    }

    // ==================== 服务端专用：如我所书的记录/回溯 ====================
    public static void useBookOfMeServer(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        long now = System.currentTimeMillis();

        if (tag.getLong("CooldownEnd") > now) {
            long remaining = (tag.getLong("CooldownEnd") - now) / 1000;
            player.displayClientMessage(Component.literal("§c【永恒诗篇】冷却中，剩余 " + remaining + " 秒。"), true);
            return;
        }

        if (tag.contains("SavedHealth")) {
            // ==================== 回溯 ====================
            player.getActiveEffects().clear();
            float savedHealth = tag.getFloat("SavedHealth");
            float clampedHealth = Math.max(0.0f, Math.min(savedHealth, player.getMaxHealth()));
            player.setHealth(clampedHealth);
            player.getFoodData().setFoodLevel(tag.getInt("SavedFood"));
            if (tag.contains("SavedEffects")) {
                ListTag list = tag.getList("SavedEffects", 10);
                for (int i = 0; i < list.size(); i++) {
                    player.addEffect(MobEffectInstance.load(list.getCompound(i)));
                }
            }

            // 🚨 新增：记录保护标记，持续 5 秒（5000 毫秒）
            // 这能防止原版自然恢复在同步期间把血量覆盖掉
            player.getPersistentData().putFloat("BookOfMe_ProtectedHealth", clampedHealth);
            player.getPersistentData().putLong("BookOfMe_ProtectEnd", now + 5000);

            tag.putLong("CooldownEnd", now + 180000);
            tag.remove("SavedHealth");
            player.displayClientMessage(Component.literal("§d【永恒诗篇】状态已回溯。"), true);
        } else {
            // ==================== 记录 ====================
            tag.putFloat("SavedHealth", player.getHealth());
            tag.putInt("SavedFood", player.getFoodData().getFoodLevel());
            ListTag list = new ListTag();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                list.add(effect.save(new CompoundTag()));
            }
            tag.put("SavedEffects", list);
            player.displayClientMessage(Component.literal("§d【永恒诗篇】状态已记录。"), true);
        }
    }
}