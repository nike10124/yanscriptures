package com.example.examplemod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端侧的「梦游」：穿墙、悬停、上下自由移动，全部自己算，不依赖创造飞行。
 *
 * <p>为什么不再用 {@code abilities.flying}：作者实测（2026-11-01 夜）只把 y 方向钉死、
 * 人只能在一个平面上平移。创造飞行的上升是 {@code Player#travel} 里那套
 * 「输入 → 视觉 → 速度」的链路，任何一环被别的东西（服务端 abilities 同步、别的
 * 模组的移动处理）改了，就整条链路失效，而且本地看不出来。
 *
 * <p>所以这里直接改速度：不按键就悬停（y 速度清零），跳就上升、蹲就下降，
 * 水平移动沿用玩家自己的移速（梦里是原版生存速度）。速度是客户端算的，
 * 位置包会照常发给服务端，服务端那头不会再被重力拉回去。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = com.example.examplemod.ExampleMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamStateClient {

    /** 每次上升/下降给的速度（格/刻），和创造飞行的手感接近。 */
    private static final double CLIMB_SPEED = 0.32D;

    private static boolean active;

    private DreamStateClient() {
    }

    /** 由 {@code NetworkHandler.DreamStatePacket} 调用。 */
    public static void accept(boolean on) {
        active = on;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        player.noPhysics = on;
        player.setNoGravity(on);
        if (on) {
            player.fallDistance = 0.0F;
        } else {
            player.setDeltaMovement(player.getDeltaMovement().x, Math.min(0.0D, player.getDeltaMovement().y),
                    player.getDeltaMovement().z);
        }
    }

    /**
     * 每刻重压一次「无碰撞」—— 这是穿墙真正生效的地方。
     *
     * <p>原版 {@code Player#tick()} 第一件事就是 {@code this.noPhysics = this.isSpectator();}。
     * {@code ClientTickEvent.END} 跑在它之后，设了也没用：下一刻开头被重置，等
     * {@code aiStep()→travel()→move()} 真正算移动时，{@code noPhysics} 又是 false，
     * 于是只能悬停、不能穿墙（作者 m12495 反馈）。
     * {@code LivingEvent.LivingTickEvent} 由 {@code ForgeHooks.onLivingTick} 在
     * {@code LivingEntity#tick()} 第一条指令触发，晚于那次重置、早于 {@code aiStep()}，
     * 时序正好。</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!active) {
            return;
        }
        if (event.getEntity() instanceof LocalPlayer player) {
            player.noPhysics = true;
            player.setNoGravity(true);
            player.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        // 别的系统（重生、维度切换、别的模组）可能把这两个标志清掉，每刻重新压一遍
        player.noPhysics = true;
        player.setNoGravity(true);
        player.fallDistance = 0.0F;

        double vy;
        if (mc.options.keyJump.isDown()) {
            vy = CLIMB_SPEED;
        } else if (mc.options.keyShift.isDown()) {
            vy = -CLIMB_SPEED;
        } else {
            vy = 0.0D;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, vy, motion.z);
    }

    /** 退出存档/断线时把状态收回来，免得下次进来人是悬空的。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
    }
}
