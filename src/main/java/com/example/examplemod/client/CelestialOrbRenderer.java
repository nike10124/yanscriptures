package com.example.examplemod.client;

import com.example.examplemod.CelestialOrbEntity;
import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 三颗天体的渲染：一张灰度球面贴图，按种类染色。
 *
 * <p>不用粒子（作者 2026-11-07 的要求），而是一张永远朝向镜头的四边形——
 * 球体的明暗已经烘进贴图，所以正面看就是一颗球。
 */
public class CelestialOrbRenderer extends EntityRenderer<CelestialOrbEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/orb.png");

    public CelestialOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CelestialOrbEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(CelestialOrbEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float size = entity.displaySize();
        int color = switch (entity.kind()) {
            case CelestialOrbEntity.KIND_SUN -> 0xFFD24A;
            case CelestialOrbEntity.KIND_MOON -> 0xE6F0FF;
            default -> 0x4A7BE0;
        };
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        pose.pushPose();
        // 朝向镜头 + 自转 180°，让贴图的「上」朝上。
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(size, size, size);

        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        float half = 0.5F;
        // 加一点自发光：天体不该被环境光压暗。
        int light = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        /*
         * 颜色必须写进顶点色，不能用 RenderSystem.setShaderColor：
         * MultiBufferSource 是先攒顶点、等实体渲染结束才统一提交，
         * 那时 setShaderColor 早就被后面的渲染重置回白色了 —— 所以第一版三颗球全是灰白的。
         */
        int red255 = (int) (red * 255.0F);
        int green255 = (int) (green * 255.0F);
        int blue255 = (int) (blue * 255.0F);
        buffer.vertex(matrix, -half, -half, 0.0F).color(red255, green255, blue255, 255)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, half, -half, 0.0F).color(red255, green255, blue255, 255)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, half, half, 0.0F).color(red255, green255, blue255, 255)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, -half, half, 0.0F).color(red255, green255, blue255, 255)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, 0.0F, 1.0F).endVertex();

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
