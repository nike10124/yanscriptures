package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 《国富论》—— 分工带来财富。
 *
 * <p>作者定稿（2026-11-08）：
 * <ul>
 *   <li>快捷栏 + 物品栏里每有 <b>11 种不同的物品</b>（按物品 id 算，同一个 id 只算一次），得到 1 点财富；</li>
 *   <li>财富 1：经验获取 +20%；</li>
 *   <li>财富 2：单次受到的伤害至多扣掉生命上限的 80%（作者确认「指的是受到伤害」）；</li>
 *   <li>财富 3：每次攻击附带一次小范围的、当前伤害 30% 的追加攻击；</li>
 *   <li>财富较高时低档效果仍然生效。</li>
 * </ul>
 *
 * <p>「财富」是一个随背包实时变化的量（不存档），所以不写 NBT、也不发同步包：
 * 服务端在事件里现算，客户端画提示框时也用同一份背包现算，两边永远一致。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WealthOfNationsHandler {

    /** 每多少种不同的物品换 1 点财富。 */
    public static final int ITEMS_PER_WEALTH = 11;
    /** 财富上限：定稿只写到 3。 */
    public static final int MAX_WEALTH = 3;

    /** 物品栏里参与计算的槽位数：0~8 快捷栏，9~35 主物品栏（不含装备栏与副手）。 */
    private static final int SLOT_COUNT = 36;

    private static final double XP_BONUS = 0.20D;
    private static final float DAMAGE_CAP_RATIO = 0.80F;
    private static final double SPLASH_RATIO = 0.30D;
    private static final double SPLASH_RADIUS = 1.0D;

    /** 追加范围伤害时置位，避免我们自己打出去的那一笔又触发一次追加。 */
    private static boolean splashing;

    private WealthOfNationsHandler() {
    }

    /** 快捷栏 + 物品栏里不同物品 id 的种数（空格子不算）。 */
    public static int distinctItems(Player player) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids.size();
    }

    /** 当前财富点数（0~3），随背包实时变化。 */
    public static int wealth(Player player) {
        return Math.min(MAX_WEALTH, distinctItems(player) / ITEMS_PER_WEALTH);
    }

    /** 财富 1：经验获取 +20%。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExperience(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.WEALTH_OF_NATIONS) || wealth(player) < 1) {
            return;
        }
        int extra = (int) Math.round(event.getOriginalExperience() * XP_BONUS);
        if (extra > 0) {
            event.setDroppedExperience(event.getDroppedExperience() + extra);
        }
    }

    /** 财富 2：单次受到的伤害不超过生命上限的 80%。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.WEALTH_OF_NATIONS) || wealth(player) < 2) {
            return;
        }
        float cap = player.getMaxHealth() * DAMAGE_CAP_RATIO;
        if (event.getAmount() > cap) {
            event.setAmount(cap);
        }
    }

    /** 财富 3：每次攻击附带一次小范围、30% 当前伤害的追加攻击。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOutgoingHurt(LivingHurtEvent event) {
        if (splashing || event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || event.getEntity() == player) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.WEALTH_OF_NATIONS) || wealth(player) < 3) {
            return;
        }
        float extra = (float) (event.getAmount() * SPLASH_RATIO);
        if (extra <= 0.0F) {
            return;
        }
        LivingEntity target = event.getEntity();
        splashing = true;
        try {
            for (LivingEntity other : target.level().getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(SPLASH_RADIUS), e -> e != target && e != player && e.isAlive())) {
                // 本模组自己的「追加攻击」统一走 playerAttack，伤害来源算玩家本人。
                other.invulnerableTime = 0;
                other.hurt(player.damageSources().playerAttack(player), extra);
            }
        } finally {
            splashing = false;
        }
    }
}
