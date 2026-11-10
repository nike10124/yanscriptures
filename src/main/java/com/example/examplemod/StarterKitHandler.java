package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 开局赠礼：每个存档里，玩家**第一次进入**时收到《自然哲学的数学原理》与《圣典·戒律》。
 *
 * <p>为什么这两件要白送：
 * <ul>
 *   <li>《原理》是挖掘/附魔这条线的前置，属于「没有它就进不了系统」的东西；</li>
 *   <li>《圣典·戒律》是十诫系统的容器，玩家必须先拿着它，诅咒才会被记录——</li>
 * </ul>
 * 让玩家去合成或去找这两件，只会让前四个小时变成空转。
 *
 * <p>发放状态记在存档里（{@link StarterKitData}），按玩家 UUID 记录，
 * 所以：同一存档里只发一次；把书扔了、放进箱子、用指令清掉背包，都不会再补发；
 * 换存档会重新发一次，这是有意的。
 *
 * <p>早期版本把标记写在玩家自己的持久化数据里，靠 Forge 在克隆/重生时复制，
 * 实测出现了「背包里没有就再发一份」的复发现象，已改为存档级账本。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StarterKitHandler {

    /** 旧版标记键，仅用于把老存档迁移成新账本。 */
    private static final String LEGACY_TAG = "YanStarterKitGranted";

    private StarterKitHandler() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        StarterKitData data = StarterKitData.get(player.server);
        boolean legacy = player.getPersistentData().getBoolean(LEGACY_TAG);

        if (legacy) {
            // 老存档：已经领过，补记进账本，不再发放
            data.mark(player.getUUID());
            player.getPersistentData().remove(LEGACY_TAG);
            return;
        }

        if (!data.mark(player.getUUID())) {
            return;
        }

        give(player, new ItemStack(ExampleMod.PRINCIPIA.get()));
        give(player, new ItemStack(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()));

        player.displayClientMessage(
                Component.translatable("message.yanscriptures.starter_kit"), false);
    }

    /**
     * 先入包，包满则掉在脚下。
     *
     * <p>注意：{@code Inventory.add} 成功后会把传入 stack 的数量清零，
     * 所以这里不能先取名字再入包，也不需要——名字由语言文件提供。
     */
    private static void give(ServerPlayer player, ItemStack stack) {
        // 双重保险：账本丢了（比如老存档）但玩家其实已经拿着，就别发成两份
        if (player.getInventory().contains(stack) || isEquipped(player, stack)) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static boolean isEquipped(ServerPlayer player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> !inv.findCurios(stack.getItem()).isEmpty())
                .orElse(false);
    }
}
