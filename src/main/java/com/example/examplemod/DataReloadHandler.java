package com.example.examplemod;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 注册数据包重载监听器。
 *
 * <p>{@link CovenantBonusLoot} 需要在 {@code /reload} 与世界加载时都重新读取，
 * 所以挂在 Forge 的资源重载事件上，而不是自己读文件。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DataReloadHandler {

    private DataReloadHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new CovenantBonusLoot());
        // @reload 与世界加载都会重新读 data/<ns>/cauldron/*.json
        event.addListener(new CauldronRecipes());
    }
}
