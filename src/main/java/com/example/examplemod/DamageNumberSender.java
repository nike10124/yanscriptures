package com.example.examplemod;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 命中伤害飘字的服务端一半：算出「这一下打了多少」，发给附近会看到它的玩家。
 *
 * <p>客户端拿不到别人打怪的伤害事件（伤害是服务端权威的），所以必须由这里发包；
 * 画字的那一半在 {@code client/DamageNumberRenderer}。
 *
 * <h2>为什么挂在 {@link LivingHurtEvent} 的 LOWEST</h2>
 * 这个位置拿到的是<b>本模组所有加成算完之后</b>的金额（暴击、背刺、震卦这些都改过了），
 * 用来验收乘区最直观。而且它带 {@code receiveCanceled = true}：
 * 训练假人的伤害在 HIGHEST 就被取消了，不加这个开关就永远看不到假人身上的飘字。
 *
 * <p>注意它比真实扣血略「乐观」——护甲减免发生在这一步之后，
 * 所以要的是「我的加成有没有生效」看这个，要「实际扣了多少血」得看怪的血条。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DamageNumberSender {

    /** 多少格以内的玩家能收到飘字。 */
    private static final double RANGE = 48.0D;

    private DamageNumberSender() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        // 打自己（含自伤类机制）不飘字
        if (event.getEntity() == attacker) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }

        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        attacker.getX(), attacker.getY(), attacker.getZ(),
                        RANGE, attacker.level().dimension())),
                new NetworkHandler.DamageNumberPacket(
                        event.getEntity().getId(), amount, isVanillaCrit(attacker), ""));
    }

    /**
     * 额外补一条带角标的飘字（例如《基督山伯爵》的「复仇」）。
     *
     * <p>不走 {@link #onHurt} 那条路：那个监听只看「谁的攻击」，而这类角标是某一本书
     * 在别人算完之后额外补的，金额也可能已经被上层改过，直接给最终数字更准。
     */
    public static void send(net.minecraft.world.entity.LivingEntity target, float amount, boolean crit, String tag) {
        if (target.level().isClientSide() || amount <= 0.0F) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        target.getX(), target.getY(), target.getZ(),
                        RANGE, target.level().dimension())),
                new NetworkHandler.DamageNumberPacket(target.getId(), amount, crit, tag));
    }

    /**
     * 原版暴击的判定条件（照 {@code Player#attack} 抄的一份）。
     *
     * <p>不引别人的暴击属性：本模组没装暴击相关的属性扩展，
     * 这里只关心「飘字是不是金色」，判错了也只是颜色问题。
     */
    private static boolean isVanillaCrit(Player player) {
        return player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
    }
}
