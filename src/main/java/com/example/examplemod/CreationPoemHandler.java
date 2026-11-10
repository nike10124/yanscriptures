package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreationPoemHandler {

    private static boolean hasBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .isPresent();
    }

    // ==================== [浪漫]：200% 反伤 + 抹除攻击者心智 ====================
    //
    // 「抹除心智」原来的实现是失明 II + 虚弱 255 —— 虚弱只压低伤害，怪物照样追着你打，
    // 失明对怪物更没有实质作用。所以那条描述一直是名不副实的。
    //
    // 现在按真正的语义做：让攻击者在 2 秒内**无法锁定目标**。
    //   1. 立刻清空它的当前目标与「最后伤害来源」；
    //   2. 打上一个到期时间标记；
    //   3. 在 LivingChangeTargetEvent 里拦掉这段时间内的重新锁定。
    // 这样怪物会短暂失去敌意、原地游荡，才是「心智被抹除」。
    private static final String TAG_MIND_ERASE_UNTIL = "YanMindEraseUntil";
    /** 抹除心智的持续时间：2 秒。 */
    private static final int MIND_ERASE_TICKS = 40;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            float reflectDamage = event.getAmount() * 2.0f;
            attacker.hurt(player.damageSources().magic(), reflectDamage);

            eraseMind(attacker, player);
        }
    }

    /** 让 {@code attacker} 在 2 秒内失去目标锁定能力。 */
    private static void eraseMind(LivingEntity attacker, Player player) {
        long until = player.level().getGameTime() + MIND_ERASE_TICKS;
        attacker.getPersistentData().putLong(TAG_MIND_ERASE_UNTIL, until);

        if (attacker instanceof net.minecraft.world.entity.Mob mob) {
            TargetGuard.clearTarget(mob);
            mob.setLastHurtByMob(null);
        }
        // 一点视觉反馈：屏幕上的认知紊乱（对玩家攻击者同样成立）
        attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MIND_ERASE_TICKS, 0, true, false));
    }

    /** 抹除心智期间，禁止重新锁定目标。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(
            net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        // 清目标本身会再派发一次本事件，那一层必须放行，否则无限递归
        if (TargetGuard.isClearing()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        long until = entity.getPersistentData().getLong(TAG_MIND_ERASE_UNTIL);
        if (until <= 0L || entity.level().getGameTime() >= until) {
            return;
        }
        event.setCanceled(true);
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            TargetGuard.clearTarget(mob);
        }
    }

    // ==================== [负世]：从源头阻止负面效果 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        if (!event.getEffectInstance().getEffect().isBeneficial()) {
            event.setResult(Event.Result.DENY);
        }
    }
}