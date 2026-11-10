package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物状态面板：显示目标生物身上全部的增益与减益。
 *
 * <h2>数据来源：服务端直推</h2>
 * 之前直接读客户端实体的 {@code getActiveEffects()}，结果面板经常显示「无状态」，
 * 而目标身上明明挂着效果 —— 说明原版的效果同步在某些模组/实体上并不可靠。
 * 现在改成：客户端每 10 刻带上实体 ID 问一次，服务端把它读到的列表原样回传。
 * 这样客户端显示的就是<b>服务端真实状态</b>。
 *
 * <h2>取目标的三层兜底</h2>
 * <ol>
 *   <li>原版准星命中的实体</li>
 *   <li>32 格内射线与包围盒相交的最近生物</li>
 *   <li>8 格内视锥（约 25°）最近的生物</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MobEffectHud {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int PANEL_WIDTH = 158;
    private static final int ROW_HEIGHT = 10;
    private static final int MAX_ROWS = 12;
    private static final double PICK_DISTANCE = 32.0D;
    private static final double CONE_DOT = 0.90D;
    /** 请求间隔（刻）。 */
    private static final int REQUEST_INTERVAL = 10;
    /** 直推数据多久算过期（毫秒）。 */
    private static final long SYNC_TTL_MS = 2000L;

    private static final Map<Integer, Synced> SYNCED = new HashMap<>();

    private static long lastRequestTick = -1000L;
    private static long lastDebugMs = 0L;
    private static boolean started = false;

    private MobEffectHud() {
    }

    /** 服务端回传的数据。 */
    public static void accept(NetworkHandler.MobEffectSyncPacket packet) {
        SYNCED.put(packet.entityId(), new Synced(
                packet.ids(), packet.amplifiers(), packet.durations(),
                packet.beneficial(), net.minecraft.Util.getMillis()));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        if (!started) {
            started = true;
            LOGGER.info("[生物状态] 面板已启动");
        }

        LivingEntity target = findTarget(mc, event.getPartialTick());
        if (target == null) {
            return;
        }

        // 周期性地向服务端要一次真实数据
        long tick = mc.level.getGameTime();
        if (tick - lastRequestTick >= REQUEST_INTERVAL) {
            lastRequestTick = tick;
            NetworkHandler.CHANNEL.sendToServer(
                    new NetworkHandler.MobEffectRequestPacket(target.getId()));
        }

        Synced synced = SYNCED.get(target.getId());
        boolean fresh = synced != null
                && net.minecraft.Util.getMillis() - synced.receivedMs <= SYNC_TTL_MS;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = screenWidth - PANEL_WIDTH - 4;
        int y = screenHeight / 2 - 40;

        int count = fresh ? synced.ids.size() : target.getActiveEffects().size();
        String title = "§f" + target.getName().getString() + " §8[" + count + "]";
        graphics.fill(x - 4, y - 14, screenWidth - 2, y - 2, 0x90000000);
        graphics.drawString(mc.font, title, x, y - 12, 0xFFFFFF, true);

        if (count == 0) {
            graphics.fill(x - 4, y - 1, screenWidth - 2, y + ROW_HEIGHT + 1, 0x90000000);
            graphics.drawString(mc.font, fresh ? "§8无状态" : "§8同步中…", x, y, 0xFFFFFF, true);
            return;
        }

        int rows = Math.min(count, MAX_ROWS);
        graphics.fill(x - 4, y - 1, screenWidth - 2, y + rows * ROW_HEIGHT + 1, 0x90000000);

        int row = 0;
        if (fresh) {
            for (int i = 0; i < synced.ids.size() && row < MAX_ROWS; i++) {
                String id = synced.ids.get(i);
                var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .get(new net.minecraft.resources.ResourceLocation(id));
                String name = effect == null ? id : effect.getDisplayName().getString();
                int level = synced.amplifiers[i] + 1;
                int seconds = synced.durations[i] / 20;
                String line = (synced.beneficial[i] ? "§a▲ " : "§c▼ ") + name
                        + (level > 1 ? " §f" + level : "")
                        + " §7" + seconds + "s";
                graphics.drawString(mc.font, line, x, y + row * ROW_HEIGHT, 0xFFFFFF, true);
                row++;
            }
        } else {
            for (MobEffectInstance instance : target.getActiveEffects()) {
                if (row >= MAX_ROWS) {
                    break;
                }
                boolean good = instance.getEffect().isBeneficial();
                String name = instance.getEffect().getDisplayName().getString();
                int level = instance.getAmplifier() + 1;
                int seconds = instance.getDuration() / 20;
                String line = (good ? "§a▲ " : "§c▼ ") + name
                        + (level > 1 ? " §f" + level : "")
                        + " §7" + seconds + "s";
                graphics.drawString(mc.font, line, x, y + row * ROW_HEIGHT, 0xFFFFFF, true);
                row++;
            }
        }

        if (count > MAX_ROWS) {
            graphics.drawString(mc.font, "§8…还有 " + (count - MAX_ROWS) + " 条",
                    x, y + MAX_ROWS * ROW_HEIGHT, 0xFFFFFF, true);
        }

        long now = net.minecraft.Util.getMillis();
        if (now - lastDebugMs > 5000L) {
            lastDebugMs = now;
            LOGGER.info("[生物状态] 目标={} 服务端返回={} 条（{}）",
                    target.getName().getString(), count, fresh ? "已同步" : "未同步");
        }
    }

    private static final class Synced {
        final List<String> ids;
        final int[] amplifiers;
        final int[] durations;
        final boolean[] beneficial;
        final long receivedMs;

        Synced(List<String> ids, int[] amplifiers, int[] durations, boolean[] beneficial, long receivedMs) {
            this.ids = ids;
            this.amplifiers = amplifiers;
            this.durations = durations;
            this.beneficial = beneficial;
            this.receivedMs = receivedMs;
        }
    }

    /**
     * 候选生物缓存（2026-11-10，E 批性能）。
     *
     * <p>{@link #findTarget} 原来每帧都调一次 {@code level.getEntities(...)} 扫视锥，
     * 那是每帧级的实体扫描 —— 一个 HUD 提示不该有这种开销。
     * 现在按 100ms 缓存「附近活着的生物」列表，射线/锥形判定依旧每帧重算，
     * 所以准星指向的响应不会变钝。</p>
     */
    private static final long CANDIDATE_TTL_MS = 100L;
    private static List<LivingEntity> candidates = List.of();
    private static long candidatesAtMs;
    private static Object candidatesLevel;

    /** 三层兜底取目标。 */
    private static LivingEntity findTarget(Minecraft mc, float partialTick) {
        try {
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    && hit.getEntity() instanceof LivingEntity living) {
                return living;
            }

            Vec3 eye = mc.player.getEyePosition(partialTick);
            Vec3 look = mc.player.getViewVector(partialTick);
            Vec3 end = eye.add(look.scale(PICK_DISTANCE));

            // AABB(Vec3, Vec3) 不会自动排序 min/max，必须手动取
            net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                    Math.min(eye.x, end.x) - 2.0D, Math.min(eye.y, end.y) - 2.0D, Math.min(eye.z, end.z) - 2.0D,
                    Math.max(eye.x, end.x) + 2.0D, Math.max(eye.y, end.y) + 2.0D, Math.max(eye.z, end.z) + 2.0D);

            LivingEntity byRay = null;
            double rayDist = PICK_DISTANCE * PICK_DISTANCE;
            LivingEntity byCone = null;
            double coneDist = Double.MAX_VALUE;

            for (LivingEntity living : scanCandidates(mc, searchBox)) {

                var hit = living.getBoundingBox().inflate(0.4D).clip(eye, end);
                if (hit.isPresent()) {
                    double distance = eye.distanceToSqr(hit.get());
                    if (distance < rayDist) {
                        rayDist = distance;
                        byRay = living;
                    }
                }

                Vec3 toEntity = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(eye);
                double distance = toEntity.length();
                if (distance > 0.01D && distance <= 8.0D) {
                    double dot = toEntity.scale(1.0D / distance).dot(look);
                    if (dot >= CONE_DOT && distance < coneDist) {
                        coneDist = distance;
                        byCone = living;
                    }
                }
            }

            return byRay != null ? byRay : byCone;
        } catch (Throwable t) {
            LOGGER.warn("[生物状态] 选目标失败：{}", t.toString());
            return null;
        }
    }

    /**
     * 100ms 内复用同一份候选列表。
     *
     * <p>缓存框在传入框外再放宽 1 格，覆盖锥形判定那 8 格的距离，
     * 免得玩家一边走一边「贴脸却没有提示」。</p>
     */
    private static List<LivingEntity> scanCandidates(Minecraft mc,
                                                     net.minecraft.world.phys.AABB searchBox) {
        long now = System.currentTimeMillis();
        if (candidatesLevel == mc.level && now - candidatesAtMs < CANDIDATE_TTL_MS) {
            return candidates;
        }

        List<LivingEntity> found = new java.util.ArrayList<>();
        for (var entity : mc.level.getEntities(mc.player, searchBox.inflate(1.0D),
                e -> e instanceof LivingEntity && e != mc.player && e.isAlive())) {
            found.add((LivingEntity) entity);
        }

        candidates = found;
        candidatesAtMs = now;
        candidatesLevel = mc.level;
        return found;
    }
}
