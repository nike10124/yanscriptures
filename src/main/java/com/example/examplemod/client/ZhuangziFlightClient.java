package com.example.examplemod.client;

import com.example.examplemod.ClassicBook;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.WornCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《庄子》的操控部分：空中控速 + 空手右键给鞘翅加速。
 *
 * <p>为什么放在客户端：玩家自己的位移是客户端算完再报给服务端的，
 * 在服务端改 {@code setDeltaMovement} 会被下一包客户端位置直接盖掉。
 *
 * <p>三条规则（作者定稿）：
 * <ul>
 *   <li>空中按住空格：把下降速度压到 {@code -0.08} 以内（缓降）。</li>
 *   <li>空中按住 Shift：每 tick 再往下压一点（加速下降）。</li>
 *   <li>滑翔中、主手空着、按住右键：沿视线方向补一点推力（每 4 刻一次）。</li>
 * </ul>
 *
 * <p>不碰创造飞行、旁观、以及梦的解析里那个「无碰撞悬浮」状态（{@code noPhysics}）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZhuangziFlightClient {

    /** 缓降时允许的最大下落速度（负值；比这更快就压回来）。 */
    private static final double SLOW_DESCENT = -0.08D;

    /** 按住 Shift 时每 tick 额外增加的向下速度。 */
    private static final double FAST_DESCENT = 0.06D;

    /** 空手右键一次给的推力（按视线方向）。 */
    private static final double BOOST = 0.08D;

    /** 推力的冷却刻数，避免一 tick 加一截、飞成火箭。 */
    private static final int BOOST_COOLDOWN = 4;

    private static int cooldown;

    private ZhuangziFlightClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!wears(player) || player.isSpectator() || player.getAbilities().flying || player.noPhysics) {
            return;
        }
        if (player.isFallFlying()) {
            boost(player, minecraft);
            return;
        }
        if (player.onGround()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (minecraft.options.keyJump.isDown()) {
            if (motion.y < SLOW_DESCENT) {
                player.setDeltaMovement(motion.x, SLOW_DESCENT, motion.z);
            }
        } else if (minecraft.options.keyShift.isDown()) {
            player.setDeltaMovement(motion.x, motion.y - FAST_DESCENT, motion.z);
        }
    }

    private static void boost(LocalPlayer player, Minecraft minecraft) {
        if (cooldown > 0 || !player.getMainHandItem().isEmpty() || !minecraft.options.keyUse.isDown()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.add(look.x * BOOST, look.y * BOOST, look.z * BOOST));
        cooldown = BOOST_COOLDOWN;
    }

    private static boolean wears(LocalPlayer player) {
        return WornCache.wears(player, ClassicBook.ZHUANGZI);
    }
}
