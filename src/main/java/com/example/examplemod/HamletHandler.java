package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 《哈姆雷特》—— 生存还是毁灭，这是一个值得考虑的问题。
 *
 * <p>作者定稿（2026-11-08）：攻击时按概率改写这一下的伤害 ——
 * 15% 只造成 10%（并让**下一次**攻击必定暴击）、35% 造成 50%、35% 造成 150%、
 * 15% 造成 200% 且本次必定暴击。
 *
 * <p>「必定暴击」按 2026-11-06 的统一规则走 Curios 的暴击属性：
 * 倍率 = 1 + max(0.5, {@code l2damagetracker:crit_damage})，取不到这个属性就退回原版暴击的 ×1.5。
 * 也就是说我们自己把暴击倍率乘上去，不依赖别家模组再判一次暴击。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HamletHandler {

    private static final float CHANCE_FAINT = 0.15F;
    private static final float CHANCE_WEAK = 0.35F;
    private static final float CHANCE_STRONG = 0.35F;
    private static final float CHANCE_PEAK = 0.15F;

    private static final double FACTOR_FAINT = 0.10D;
    private static final double FACTOR_WEAK = 0.50D;
    private static final double FACTOR_STRONG = 1.50D;
    private static final double FACTOR_PEAK = 2.00D;

    /** 拿不到暴击伤害属性时的兜底倍率（原版暴击是 ×1.5）。 */
    private static final double VANILLA_CRIT_BONUS = 0.5D;

    /** 玩家 UUID → 下一次攻击是否必定暴击。 */
    private static final Map<UUID, Boolean> NEXT_CRIT = new HashMap<>();

    static {
        SessionCleanup.register(HamletHandler::cleanupPlayer);
    }

    private HamletHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || event.getEntity() == player) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.HAMLET)) {
            return;
        }
        UUID id = player.getUUID();

        // 先消费上一次攒下的「必定暴击」——它只作用在下一次攻击上，不叠加到本次掷出的结果里。
        boolean crit = Boolean.TRUE.equals(NEXT_CRIT.remove(id));

        float roll = player.getRandom().nextFloat();
        double factor;
        if (roll < CHANCE_FAINT) {
            factor = FACTOR_FAINT;
            NEXT_CRIT.put(id, true);
        } else if (roll < CHANCE_FAINT + CHANCE_WEAK) {
            factor = FACTOR_WEAK;
        } else if (roll < CHANCE_FAINT + CHANCE_WEAK + CHANCE_STRONG) {
            factor = FACTOR_STRONG;
        } else {
            factor = FACTOR_PEAK;
            crit = true;
        }

        double amount = event.getAmount() * factor;
        if (crit) {
            amount *= critMultiplier(player);
        }
        event.setAmount((float) amount);
    }

    /** 暴击倍率：1 + max(0.5, 暴击伤害属性)；没有这个属性就按原版 ×1.5。 */
    private static double critMultiplier(Player player) {
        Attribute crit = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("l2damagetracker", "crit_damage"));
        double bonus = crit == null ? 0.0D : player.getAttributeValue(crit);
        return 1.0D + Math.max(VANILLA_CRIT_BONUS, bonus);
    }

    private static void cleanupPlayer(UUID playerId) {
        NEXT_CRIT.remove(playerId);
    }
}
