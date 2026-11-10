package com.example.examplemod.client;

import com.example.examplemod.ClassicBook;
import com.example.examplemod.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《一千零一夜》戴着时，把七咒之戒那句「七咒唤醒了你……」（失眠）在客户端换掉。
 *
 * <p>只拦显示，不碰对方逻辑：对方仍然照常把睡眠计数按在 90、照常发这条消息，
 * 我们只是在它到达聊天栏之前替换成自己的叙述。判断依据是消息的翻译键
 * {@code message.enigmaticlegacy.cursed_sleep}——这里不得不写死对方的键名，
 * 属于「不硬编码别家类名」约定里允许的例外：只是一个可被语言文件改写的字符串。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseMessageClient {

    /** 对方那条失眠提示的翻译键。 */
    private static final String INSOMNIA_MESSAGE = "message.enigmaticlegacy.cursed_sleep";

    private CurseMessageClient() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!(event.getMessage().getContents() instanceof TranslatableContents contents)) {
            return;
        }
        if (!INSOMNIA_MESSAGE.equals(contents.getKey())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !wearsNights(player)) {
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(
                Component.translatable("message.yanscriptures.nights.curse_muted")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
    }

    /** 客户端自己的佩戴判定：只认书籍栏里的那一本。 */
    private static boolean wearsNights(LocalPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    var socks = handler.getCurios().get("book");
                    if (socks == null) {
                        return false;
                    }
                    for (int i = 0; i < socks.getSlots(); i++) {
                        var stack = socks.getStacks().getStackInSlot(i);
                        if (stack.getItem() instanceof com.example.examplemod.ClassicBookItem item
                                && item.book() == ClassicBook.ONE_THOUSAND_AND_ONE_NIGHTS) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }
}
