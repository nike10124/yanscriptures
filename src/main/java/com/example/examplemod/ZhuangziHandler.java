package com.example.examplemod;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《庄子》—— 北冥有鱼，其名为鲲。
 *
 * <p>作者定稿（2026-11-06）：
 * 「坠落伤害完全免疫，在空中按住空格，可以大幅度降低下降速度，按住 shift 可以大幅度加快下降速度，
 * 空手右键可为翘翅提供动力，滑翔（鞘翅/滑翔伞）时移动速度 +20%」。
 *
 * <p>这一半是服务端：摔落免疫与滑翔时的移速加成。
 * 空中控速与鞘翅动力必须改玩家自己的速度，只能放到客户端做
 * （玩家移动本来就是客户端算好再报给服务端的），见 {@code client/ZhuangziFlightClient}。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZhuangziHandler {

    /** 滑翔时的移速加成（MULTIPLY_BASE，与工具提示里的 +20% 一致）。 */
    private static final double GLIDE_SPEED = 0.20D;

    private static final java.util.UUID ID_GLIDE = BookAttr.id("classic_zhuangzi_glide");

    private ZhuangziHandler() {
    }

    /** 摔落伤害直接免掉（双保险：事件取消 + 下面按伤害类型再免疫一次）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (WornCache.wears(player, ClassicBook.ZHUANGZI)) {
            event.setCanceled(true);
        }
    }

    /** 别的模组直接构造摔落伤害时，事件取消拦不住，这里按伤害类型再挡一次。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FALL) && WornCache.wears(player, ClassicBook.ZHUANGZI)) {
            event.setCanceled(true);
        }
    }

    /** 滑翔时给 20% 移速，落地/收翅立刻退回 0（{@link BookAttr#set} 会自己删掉修饰符）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        boolean glide = WornCache.wears(player, ClassicBook.ZHUANGZI) && isGliding(player);
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_GLIDE, glide ? GLIDE_SPEED : 0.0D);
    }

    private static boolean isGliding(LivingEntity entity) {
        return entity.isFallFlying();
    }
}
