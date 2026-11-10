package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 训练假人：用来长期测试 DPS。
 *
 * <h2>为什么用原版僵尸改造，而不是新建实体</h2>
 * 新建实体要注册类型、写渲染器、做模型，成本高，而且和整合包里其它模组的渲染管线容易冲突。
 * 这里把一只原版僵尸改成：<b>不动、不出声、打不死、无敌帧清零</b>，
 * 它的血量与防御都清掉，这样每次命中都是完整伤害，测出来的 DPS 才准确。
 *
 * <h2>怎么用</h2>
 * {@code /ys debug dummy} 在自己脚下生成一只，名字叫「训练假人」。
 * 打它不会死，也不会还手；想移除用 {@code /ys debug dummy clear}（直接 discard，不走死亡）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainingDummy {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 标记标签：靠它识别假人。 */
    public static final String TAG = "YanTrainingDummy";

    private TrainingDummy() {
    }

    /**
     * 生成一只训练假人。
     *
     * <p>用<b>铁傀儡</b>而不是僵尸：雷击会把僵尸替换成别的实体（焦尸/溺尸之类），
     * 替换之后我们打的「训练假人」标签就丢了，不死逻辑随之失效 —— 实测就是被雷劈死的那次。
     * 铁傀儡不会被雷击变形，适合长期挂在场地里当靶子。
     */
    public static net.minecraft.world.entity.Mob spawn(ServerLevel level, BlockPos pos, String name) {
        net.minecraft.world.entity.Mob dummy = EntityType.IRON_GOLEM.create(level);
        if (dummy == null) {
            return null;
        }
        dummy.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        dummy.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.COMMAND, null, null);

        dummy.addTag(TAG);
        dummy.setCustomName(net.minecraft.network.chat.Component.literal(name));
        dummy.setCustomNameVisible(true);
        dummy.setGlowingTag(true);
        dummy.setNoAi(true);
        dummy.setSilent(true);
        dummy.setPersistenceRequired();
        // 血量拉高，避免任何情况下被一击清空
        var health = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(1024.0D);
        }
        var armor = dummy.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(0.0D);
        }
        var toughness = dummy.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            toughness.setBaseValue(0.0D);
        }
        dummy.setHealth(1024.0F);

        level.addFreshEntity(dummy);
        LOGGER.info("[训练假人] 已生成于 {} {} {}", pos.getX(), pos.getY(), pos.getZ());
        return dummy;
    }

    public static boolean isDummy(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    /**
     * 直接清除半径内的训练假人。
     *
     * <p>用 {@link net.minecraft.world.entity.Entity#discard()} 而不是 {@code /kill}：
     * discard 只是把实体从世界里摘掉，并由服务端给客户端发一条移除包 ——
     * 不触发死亡动画、不掉落、不派发 {@code LivingDeathEvent}、不走进任何「死亡后」逻辑，
     * 也就不会和假人自己的「拦截死亡」逻辑打架。写进存档的实体同样会被删掉，
     * 下次进世界不会再冒出来。
     *
     * @return 被清除的数量
     */
    public static int clear(ServerLevel level, net.minecraft.world.phys.Vec3 center, double radius) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        java.util.List<Mob> found = level.getEntitiesOfClass(Mob.class, box, TrainingDummy::isDummy);
        for (Mob mob : found) {
            mob.discard();
        }
        if (!found.isEmpty()) {
            LOGGER.info("[训练假人] 已直接清除 {} 只", found.size());
        }
        return found.size();
    }

    /**
     * 免疫伤害：打不死，但伤害事件照常派发（DPS 统计在别处读取）。
     *
     * <p><b>例外</b>：`/kill` 用的伤害类型是 {@code minecraft:generic_kill}，
     * 这里放行，否则指令杀不掉假人。审判用的是 {@code generic}（不是 generic_kill），
     * 因此不会误触发这个例外。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!isDummy(event.getEntity())) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && "genericKill".equals(event.getSource().getMsgId())) {
            return;   // /kill 放行
        }
        // 只取消，不把 amount 清零 ——
        // DPS 统计读的就是这个字段，清零会让假人的 DPS 永远是 0。
        event.setCanceled(true);
    }

    /** 真正的不死：连死亡事件都取消掉（但 /kill 仍然放行）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!isDummy(event.getEntity())) {
            return;
        }
        if ("genericKill".equals(event.getSource().getMsgId())) {
            return;   // 指令击杀放行
        }
        event.setCanceled(true);
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
        event.getEntity().revive();
        LOGGER.info("[训练假人] 拦截了一次致命效果");
    }

    /** 每 tick 清无敌帧，保证连续攻击都能完整结算。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isDummy(entity)) {
            return;
        }
        entity.invulnerableTime = 0;
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        entity.setRemainingFireTicks(0);
        if (entity.isDeadOrDying()) {
            entity.revive();
        }
    }
}
