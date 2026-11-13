package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 作者后门：ID 为 {@code nike10124} 的玩家第一次进入本模组的存档时，收到一本《至理之书》。
 *
 * <p>按存档记（{@link AuthorGiftData}），一个存档只发一次：扔了、放箱子、清背包都不会补发；
 * 换存档会重新发——新存档就当是新的一轮，和 {@link StarterKitHandler} 的规则一致。</p>
 *
 * <p>判定用游戏档案里的名字，且**只认这一个 ID**：这不是公共内容，是作者要的后门，
 * 所以这里写死名字是有意的（唯一一处硬编码玩家名，见作者 m20955）。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuthorGiftHandler {

    /** 触发后门的玩家 ID（作者 2026-11-13 明确）。 */
    private static final String AUTHOR_NAME = "nike10124";

    private AuthorGiftHandler() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (!AUTHOR_NAME.equalsIgnoreCase(name)) {
            return;
        }

        AuthorGiftData data = AuthorGiftData.get(player.server);
        if (!data.mark(player.getUUID())) {
            return;
        }

        ItemStack book = new ItemStack(ExampleMod.BOOK_OF_TRUTH.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.displayClientMessage(
                Component.translatable("message.yanscriptures.book_of_truth.welcome")
                        .withStyle(ChatFormatting.BLUE), false);
    }
}
