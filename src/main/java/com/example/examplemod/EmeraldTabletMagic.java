package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 《翠玉录》的成长加成：只放大**魔法伤害**。
 *
 * <h2>为什么不再给近战加数字</h2>
 *
 * 早期原版没有任何「玩家主动造成魔法伤害」的稳定来源，所以这本书的成长部分
 * 被写成「命中时给这一击加 N 点伤害」——名字叫魔法，实际是物理。
 * 现在整合包里普遍有法术模组，这份加成可以落回它该在的位置。
 *
 * <h2>怎么判定「魔法」</h2>
 *
 * 不认模组名，只认伤害类型标签：{@code #yanscriptures:magic}。
 * 这个标签默认并入两个**通用**标签：
 * <ul>
 *   <li>{@code #forge:is_magic} —— Forge 的标准魔法伤害标签，各类法术模组会把自己的
 *       伤害类型加进去（诡厄巫法就把 8 种法术伤害都加了）；</li>
 *   <li>{@code #minecraft:witch_resistant_to} —— 原版的「女巫抗性」语义，含原版魔法伤害。</li>
 * </ul>
 * 整合包要加新模组、或者想把某个伤害类型也算作魔法，只要往
 * {@code data/yanscriptures/tags/damage_type/magic.json} 里加一行，代码不用动。
 *
 * <h2>为什么是「百分比 + 保底」</h2>
 *
 * 纯百分比在低数值模组里几乎看不见（诡厄巫法的法术基础伤害只有 4~12），
 * 纯固定值在大型整合包里又会变成蚊子腿（法术动辄两三百）。
 * 所以两段相加：百分比负责随包缩放，固定保底负责让低数值模组也吃得到。
 * 两段各自有上限，防止「药水种类上百」的整合包把法术堆成秒杀。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldTabletMagic {

    /** 由本模组定义的伤害类型标签，默认并入通用魔法标签。 */
    public static final TagKey<DamageType> MAGIC_TAG =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ExampleMod.MODID, "magic"));

    private EmeraldTabletMagic() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!source.is(MAGIC_TAG)) {
            return;
        }
        // 只放大「自己打出来的」魔法：药水云、别人的法术、反伤都不该吃这本书的成长
        if (!(source.getEntity() instanceof Player player)) {
            return;
        }
        if (!wears(player)) {
            return;
        }

        int kinds = PotionProgressTracker.getTotalUnlockedCount(player);
        if (kinds <= 0) {
            return;
        }

        float base = event.getAmount();
        if (base <= 0.0F) {
            return;
        }

        float percent = Math.min(kinds * Config.tabletMagicPercentPerKind, Config.tabletMagicPercentCap);
        float flat = Math.min(kinds * Config.tabletMagicFlatPerKind, Config.tabletMagicFlatCap);
        float bonus = base * percent + flat;

        if (bonus > 0.0F) {
            event.setAmount(base + bonus);
        }
    }

    /** 当前进度会给魔法伤害加多少百分比（0.35 = +35%），供 tooltip 复用。 */
    public static float percentFor(int kinds) {
        return Math.min(kinds * Config.tabletMagicPercentPerKind, Config.tabletMagicPercentCap);
    }

    /** 当前进度对应的固定保底加成点数，供 tooltip 复用。 */
    public static float flatFor(int kinds) {
        return Math.min(kinds * Config.tabletMagicFlatPerKind, Config.tabletMagicFlatCap);
    }

    private static boolean wears(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(ExampleMod.EMERALD_TABLET.get()).isPresent())
                .orElse(false);
    }
}
