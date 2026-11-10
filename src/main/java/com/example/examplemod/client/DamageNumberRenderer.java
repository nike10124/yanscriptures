package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 命中伤害飘字的客户端一半：把服务端发来的「这一下多少伤害」画在被打的实体头上。
 *
 * <p>为什么不用原版的 {@code Entity#setCustomName} 那一套：那会改实体数据、要发包同步、
 * 还会影响别的模组读名字。这里只在本地存一份短命的记录，画完就丢，不碰任何实体状态。
 *
 * <p>时间轴：0 → {@link #LIFE_MS} 之间，文字从实体头顶往上飘 0.6 格，最后 40% 开始淡出。
 * 同一实体连续被打会叠好几条，各自独立计时。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DamageNumberRenderer {

    /** 一条飘字活多久（毫秒）。 */
    private static final long LIFE_MS = 900L;
    /** 最多同时留多少条，防止刷怪塔里把内存堆满。 */
    private static final int MAX_FLOATERS = 64;
    /** 超过这个距离就不画（格）。 */
    private static final double MAX_DISTANCE = 64.0D;
    /** 世界坐标到字号的换算：0.03 约等于原版名牌的 2 倍大。 */
    private static final float SCALE = 0.03F;

    /** 暴击用金色，普通用白色。 */
    private static final int COLOR_CRIT = 0xFFD24A;
    private static final int COLOR_NORMAL = 0xFFFFFF;
    /** 角标（例如「复仇」）的颜色与上移量。 */
    private static final int COLOR_TAG = 0xFFDD66;
    private static final float TAG_OFFSET = 9.0F;

    private record Floater(int entityId, float amount, boolean crit, String tag, long bornMs) {
    }

    private static final List<Floater> FLOATERS = new ArrayList<>();

    private DamageNumberRenderer() {
    }

    /** 网络包处理时调用（只在客户端加载这个类）。 */
    public static void accept(int entityId, float amount, boolean crit, String tag) {
        FLOATERS.add(new Floater(entityId, amount, crit, tag == null ? "" : tag, net.minecraft.Util.getMillis()));
        while (FLOATERS.size() > MAX_FLOATERS) {
            FLOATERS.remove(0);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 放在粒子之后：这样飘字不会被怪自己挡掉
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        FLOATERS.removeIf(f -> now - f.bornMs() > LIFE_MS);
        if (FLOATERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        net.minecraft.world.phys.Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick();

        for (Iterator<Floater> it = FLOATERS.iterator(); it.hasNext(); ) {
            Floater floater = it.next();
            Entity entity = mc.level.getEntity(floater.entityId());
            if (entity == null) {
                it.remove();
                continue;
            }

            double age = (now - floater.bornMs()) / (double) LIFE_MS;
            // 插值一下位置，免得 20 刻的位置跳一格
            double px = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double py = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double pz = Mth.lerp(partialTick, entity.zOld, entity.getZ());

            double dx = px - cameraPos.x;
            double dy = py + entity.getBbHeight() * 0.85D + 0.3D + age * 0.6D - cameraPos.y;
            double dz = pz - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }

            // 淡出：前 60% 全不透明，之后线性掉到 0
            float alpha = age <= 0.6D ? 1.0F : (float) (1.0D - (age - 0.6D) / 0.4D);
            int base = floater.crit() ? COLOR_CRIT : COLOR_NORMAL;
            int color = ((int) (alpha * 255.0F) << 24) | base;

            String text = String.format(Locale.ROOT, "%.1f", floater.amount());
            if (floater.crit()) {
                text += "!";
            }
            Component label = Component.literal(text);

            pose.pushPose();
            pose.translate(dx, dy, dz);
            // 正对镜头：乘上相机的旋转，再把 X/Y 翻成正方向
            pose.mulPose(camera.rotation());
            pose.scale(-SCALE, -SCALE, SCALE);
            Matrix4f matrix = pose.last().pose();
            float half = mc.font.width(label) / 2.0F;
            mc.font.drawInBatch(label, -half, 0.0F, color, true, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            // 角标画在数字上方一行（例如《基督山伯爵》的「复仇」）
            if (!floater.tag().isEmpty()) {
                Component tag = Component.literal(floater.tag());
                float tagHalf = mc.font.width(tag) / 2.0F;
                int tagColor = ((int) (alpha * 255.0F) << 24) | COLOR_TAG;
                mc.font.drawInBatch(tag, -tagHalf, -TAG_OFFSET, tagColor, true, matrix, buffer,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            pose.popPose();
        }
        buffer.endBatch();
    }
}
