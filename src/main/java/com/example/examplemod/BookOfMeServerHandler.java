package com.example.examplemod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BookOfMeServerHandler {

    // 服务端每 tick 检查一次血量保护
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long protectEnd = player.getPersistentData().getLong("BookOfMe_ProtectEnd");
        if (protectEnd == 0) return;

        long now = System.currentTimeMillis();
        if (now < protectEnd) {
            float protectedHealth = player.getPersistentData().getFloat("BookOfMe_ProtectedHealth");
            if (player.getHealth() < protectedHealth) {
                player.setHealth(protectedHealth);
            }
        } else {
            // 保护期结束，清除标记
            player.getPersistentData().remove("BookOfMe_ProtectEnd");
            player.getPersistentData().remove("BookOfMe_ProtectedHealth");
        }
    }
}