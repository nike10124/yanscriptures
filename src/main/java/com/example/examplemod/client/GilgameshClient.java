package com.example.examplemod.client;

import com.example.examplemod.EnumaCastPacket;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.NetworkHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《吉尔伽美什史诗》的客户端部分：按住 R 键五秒蓄力，满五秒才把释放请求发给服务端。
 *
 * <p>作者 2026-11-07：「按住按键之后持续五秒钟，才能开始释放」。计时放在客户端有两个好处：
 * 不用每刻发包，而且蓄力粒子可以立刻跟着手走；服务端仍然会复核佩戴、手持剑与冷却。
 *
 * <p>蓄力演出：三圈螺旋向内收的 {@code portal} / {@code reverse_portal}，外加少量
 * {@code dragon_breath} 当「混沌能量」——和释放时的剑气用同一套粒子语言。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GilgameshClient {

    /** 蓄力需要的刻数：5 秒。 */
    private static final int CHARGE_TICKS = 100;

    private static int heldTicks;

    /** 这一轮已经发过包了，必须松手才能重新蓄力。 */
    private static boolean sent;

    /** 这一轮已经提示过「需要手持剑类武器」了，避免每刻刷屏。 */
    private static boolean warned;

    private GilgameshClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            reset();
            return;
        }
        if (!ExampleMod.ENUMA_KEY.isDown() || sent) {
            if (!ExampleMod.ENUMA_KEY.isDown()) {
                reset();
            }
            return;
        }
        if (!wearingBook(player)) {
            // 作者 2026-11-09：没戴《吉尔伽美什史诗》时不该有任何提示，一个字都不弹。
            reset();
            return;
        }
        if (!holdingSword(player)) {
            // 手里不是剑就不蓄力——否则玩家会看到蓄力演出、却等来一条「需要剑」的提示。
            if (!warned) {
                warned = true;
                player.displayClientMessage(Component.translatable("message.yanscriptures.enuma.no_sword"), true);
            }
            heldTicks = 0;
            return;
        }
        warned = false;
        heldTicks++;
        if (heldTicks % 2 == 0) {
            spiral(player, heldTicks);
        }
        if (heldTicks % 20 == 0 && heldTicks < CHARGE_TICKS) {
            player.displayClientMessage(Component.translatable(
                    "message.yanscriptures.enuma.charging", heldTicks / 20), true);
        }
        if (heldTicks >= CHARGE_TICKS) {
            sent = true;
            NetworkHandler.CHANNEL.sendToServer(new EnumaCastPacket());
        }
    }

    private static void reset() {
        heldTicks = 0;
        sent = false;
        warned = false;
    }

    /**
     * 客户端自己判断有没有戴这本书。
     *
     * <p>Curios 的饰品栏在客户端也有一份（服务端会同步），所以这里能直接读到；
     * 用它挡在蓄力之前，没戴书的人按 R 完全不会有反应（连「需要剑」的提示都不弹）。
     */
    private static boolean wearingBook(LocalPlayer player) {
        var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler("book"))
                .orElse(null);
        if (handler == null) {
            return false;
        }
        var stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            var stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ExampleMod.BOOK_GILGAMESH.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 主手是不是剑。
     *
     * <p>原版的 {@code ItemTags.SWORDS} 只收录原版剑，别的模组的剑不一定进标签，
     * 所以再补一条「物品是不是 {@link SwordItem}」——两条任一成立都算剑。
     */
    private static boolean holdingSword(LocalPlayer player) {
        var held = player.getMainHandItem();
        return held.is(net.minecraft.tags.ItemTags.SWORDS) || held.getItem() instanceof net.minecraft.world.item.SwordItem;
    }

    /**
     * 三层向内收拢的粒子环，越蓄越紧。
     *
     * <p>每一层都按「环」取样（{@link #RING_POINTS} 个点），而不是原来每层只放一个点——
     * 只有一个点的时候看着像三个小光点在绕圈，完全不是「环」。
     * 半径从 {@link #RING_RADIUS_START} 收到 {@link #RING_RADIUS_END}，作者要求「至少三格以上」。
     */
    /**
     * 向内收拢的三圈螺旋，越蓄越快、越蓄越紧。
     *
     * <p>作者 2026-11-07 澄清：「放大的环」指的是发射之后往前推进的红环，不是蓄力时的特效，
     * 所以这里保持最初的小螺旋（半径 2.6 → 0.6）。
     */
    private static void spiral(LocalPlayer player, int tick) {
        double progress = Math.min(1.0D, tick / (double) CHARGE_TICKS);
        double radius = 2.6D - 2.0D * progress;
        double spin = tick * 0.45D;
        double baseY = player.getY() + 0.2D;
        for (int ring = 0; ring < 3; ring++) {
            double angle = spin + ring * (Math.PI * 2.0D / 3.0D);
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = baseY + 0.6D + ring * 0.55D + progress * 0.8D;
            player.level().addParticle(ParticleTypes.PORTAL, x, y, z,
                    (player.getX() - x) * 0.12D, 0.02D, (player.getZ() - z) * 0.12D);
            if (ring == 1) {
                player.level().addParticle(ParticleTypes.REVERSE_PORTAL, x, y + 0.2D, z, 0.0D, 0.03D, 0.0D);
            }
        }
        if (tick % 10 == 0) {
            player.level().addParticle(ParticleTypes.DRAGON_BREATH,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.06D, 0.0D);
        }
    }
}
