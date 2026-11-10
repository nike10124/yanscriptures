package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《浮士德》—— 把生命上限卖出去，换六十秒不属于人的力量。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>Shift + 右键：<b>永久</b>扣掉整体生命上限的 20%（不少于 5 点）；</li>
 *   <li>换 60 秒：造成伤害 +50%、受到伤害 −50%、移动速度 +50%；</li>
 *   <li>冷却 60 秒；扣除不随摘下恢复，只有死亡重生或指令能洗掉。</li>
 * </ul>
 *
 * <p>为什么不用药水效果：作者的原话是「用 buff 的话容易被别的模组覆盖，而且收益太少」，
 * 所以三条加成里移速走属性修饰符，增伤/减伤在 {@link LivingHurtEvent} 里自己算。
 *
 * <p>生命上限这一条是<b>唯一破例</b>用 {@code MULTIPLY_TOTAL} 的地方：
 * 「每次乘 0.8」只有逐条相乘才对（0.8ⁿ），写成乘法乘区会变成相加，扣到第五次就见底了。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FaustHandler {

    static final String TAG_PACTS = "YanFaustPacts";
    static final String TAG_UNTIL = "YanFaustUntil";
    static final String TAG_COOLDOWN = "YanFaustCooldown";

    private static final double HEALTH_FACTOR = 0.8D;
    private static final double MIN_MAX_HEALTH = 5.0D;
    private static final long DURATION_MS = 60_000L;
    private static final long COOLDOWN_MS = 60_000L;
    private static final double OUTGOING_BONUS = 0.50D;
    private static final double INCOMING_REDUCTION = 0.50D;
    private static final double SPEED_BONUS = 0.50D;

    private static final java.util.UUID ID_HEALTH = BookAttr.id("faust_health");
    private static final java.util.UUID ID_SPEED = BookAttr.id("faust_speed");

    private FaustHandler() {
    }

    /** 是否处在「签约后的六十秒」里。 */
    public static boolean empowered(Player player) {
        long until = player.getPersistentData().getLong(TAG_UNTIL);
        return until > System.currentTimeMillis();
    }

    /** Shift + 右键的入口：由 {@code NetworkHandler.FaustPactPacket} 调用。 */
    public static void pact(ServerPlayer player) {
        if (!WornCache.wears(player, ClassicBook.FAUST)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = System.currentTimeMillis();
        long cooldown = data.getLong(TAG_COOLDOWN);
        if (now < cooldown) {
            long left = (cooldown - now) / 1000L + 1L;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c【契约】还要等 " + left + " 秒才能再签一次。"), true);
            return;
        }
        double currentMax = player.getAttributeValue(Attributes.MAX_HEALTH);
        double nextMax = currentMax * HEALTH_FACTOR;
        if (nextMax < MIN_MAX_HEALTH) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c【契约】你的生命上限已经不够再卖一次了。"), true);
            return;
        }
        int pacts = data.getInt(TAG_PACTS) + 1;
        data.putInt(TAG_PACTS, pacts);
        applyHealthPenalty(player, pacts);
        data.putLong(TAG_UNTIL, now + DURATION_MS);
        data.putLong(TAG_COOLDOWN, now + COOLDOWN_MS);
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, SPEED_BONUS);
        BookAttr.clampHealth(player);

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§8【契约】签下了。生命上限 −20%，接下来六十秒你不再属于自己。"), true);
    }

    private static void applyHealthPenalty(Player player, int pacts) {
        double factor = Math.pow(HEALTH_FACTOR, pacts) - 1.0D;
        BookAttr.set(player, Attributes.MAX_HEALTH, ID_HEALTH, factor,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        if (!empowered(player)) {
            BookAttr.clear(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player attacker) {
            if (WornCache.wears(attacker, ClassicBook.FAUST) && empowered(attacker)) {
                event.setAmount((float) (event.getAmount() * (1.0D + OUTGOING_BONUS)));
            }
        }
        if (event.getEntity() instanceof Player victim) {
            if (WornCache.wears(victim, ClassicBook.FAUST) && empowered(victim)) {
                event.setAmount((float) (event.getAmount() * (1.0D - INCOMING_REDUCTION)));
            }
        }
    }

    /** 死亡洗掉契约：生命上限与层数一起回到原样。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TAG_PACTS) <= 0) {
            return;
        }
        data.putInt(TAG_PACTS, 0);
        data.remove(TAG_UNTIL);
        data.remove(TAG_COOLDOWN);
        BookAttr.clear(player, Attributes.MAX_HEALTH, ID_HEALTH);
        BookAttr.clear(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
    }
}
