package com.example.examplemod.client;

import com.example.examplemod.ClassicBooksHandler;
import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把《理想国》的影子压暗成半透明的剪影。
 *
 * <p>影子是本体实体的一份复制，没有自己的渲染器，所以这里用渲染事件统一染色，
 * 不改任何模组的实体渲染代码。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowRenderTint {

    private ShadowRenderTint() {
    }

    @SubscribeEvent
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        if (!ClassicBooksHandler.isRepublicShadow(event.getEntity())) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.14F, 0.12F, 0.18F, 0.55F);
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (!ClassicBooksHandler.isRepublicShadow(event.getEntity())) {
            return;
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
