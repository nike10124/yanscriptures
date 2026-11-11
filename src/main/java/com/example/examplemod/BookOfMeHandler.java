package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BookOfMeHandler {

    // ==================== 光环锚点记账 ====================
    /** 锚点实体 id（0 = 尚未创建）。 */
    private static final String TAG_ANCHOR_ID = "YanBook_RingAnchorId";
    /** 是否已经执行过「卸下书」的清理。 */
    private static final String TAG_ANCHOR_CLEARED = "YanBook_RingCleared";

    // ==================== 属性修饰符 UUID ====================
    private static final UUID ATK_UUID = UUID.nameUUIDFromBytes("book_of_me_double_atk".getBytes());
    private static final UUID HEALTH_UUID = UUID.nameUUIDFromBytes("book_of_me_double_health".getBytes());
    private static final UUID ARMOR_UUID = UUID.nameUUIDFromBytes("book_of_me_double_armor".getBytes());
    private static final UUID SPEED_UUID = UUID.nameUUIDFromBytes("book_of_me_double_speed".getBytes());
    private static final UUID FLYING_SPEED_UUID = UUID.nameUUIDFromBytes("book_of_me_flying_speed".getBytes());

    // ==================== 工具方法 ====================
    private static boolean hasBookOfMe(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.BOOK_OF_ME.get()))
                .isPresent();
    }

    // ==================== 飞行速度工具 ====================
    // [升级 #2 2026-10-21] 原实现用反射直写 Abilities.flyingSpeed 私有字段（见下方注释版）。
    // 反射的问题：字段一旦改名/映射变化就静默失效（被 catch 吞掉，玩家只觉得"飞得没变快"）。
    // 1.20.1 提供了公开访问器，直接调用即可。
    private static void setFlyingSpeed(Player player, float speed) {
        try {
            player.getAbilities().setFlyingSpeed(speed);
        } catch (Exception e) {
            // 静默失败，不影响游戏
        }
    }

    // ---- 原反射实现（保留备查：若上面的 setFlyingSpeed 在你的映射下无法解析，
    //      删掉上面那个方法并取消注释本段即可回滚）----
    // private static java.lang.reflect.Field flyingSpeedField;
    // private static void setFlyingSpeed(Player player, float speed) {
    //     try {
    //         if (flyingSpeedField == null) {
    //             flyingSpeedField = net.minecraft.world.entity.player.Abilities.class
    //                     .getDeclaredField("flyingSpeed");
    //             flyingSpeedField.setAccessible(true);
    //         }
    //         flyingSpeedField.setFloat(player.getAbilities(), speed);
    //     } catch (Exception e) {
    //     }
    // }

    // ==================== 主 tick ====================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        try {
            boolean hasBook = hasBookOfMe(player);

            // 登录后第一 tick 强制满血 —— 只对「戴着书」的玩家生效。
            // 原来这段在 hasBook 判断之前，等于所有玩家第一次进世界都会被回满血。
            if (hasBook && !player.getPersistentData().getBoolean("YanBook_FirstTick")) {
                player.getPersistentData().putBoolean("YanBook_FirstTick", true);
                player.setHealth(player.getMaxHealth());
            }

            if (!hasBook) {
                removeAttributeDoubling(player);
                // 飞行改用「多方申请」：这里只撤回本书自己那一份，
                // 不再每 tick 直接关 mayfly/flying（那会把《圣约·原典》《纯粹理性批判》刚给的飞行抹平）。
                FlightPermissions.set(player, FlightPermissions.BOOK_OF_ME, false);
                if (!player.isCreative() && !player.isSpectator()) {
                    // 卸下书后，恢复飞行速度到原版
                    setFlyingSpeed(player, 0.05F);
                }

                // 2026-11-10：光环已经改成纯粒子、不再需要锚点实体，但老存档里可能还留着
                // 当年那只隐形盔甲架 —— 卸下书时按记下的 id 精确清掉一次（不清扫全维度）。
                if (!player.getPersistentData().getBoolean(TAG_ANCHOR_CLEARED)) {
                    player.getPersistentData().putBoolean(TAG_ANCHOR_CLEARED, true);
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        long anchorId = player.getPersistentData().getLong(TAG_ANCHOR_ID);
                        if (anchorId != 0L) {
                            net.minecraft.world.entity.Entity anchor = serverLevel.getEntity((int) anchorId);
                            if (anchor != null) {
                                anchor.discard();
                            }
                            player.getPersistentData().remove(TAG_ANCHOR_ID);
                        }
                    }
                }
                return;
            }

            // 戴着书 → 重置「已清理」标记，下次卸下时才会再清一次
            player.getPersistentData().putBoolean(TAG_ANCHOR_CLEARED, false);

            // ---- 时停逻辑 ----
            // 实体冻结、重力还原、计时都交给 TimeStopController（两个饰品共用一份实现）。
            if (player.getPersistentData().getBoolean("BookOfMe_TimeStop_Active")) {
                long now = System.currentTimeMillis();
                long end = player.getPersistentData().getLong("BookOfMe_TimeStop_End");
                if (now >= end) {
                    player.getPersistentData().putBoolean("BookOfMe_TimeStop_Active", false);
                    TimeStopController.restore(player);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§b【时停】时间恢复流动。"), true);
                }
            }

            // ---- 创造飞行 ----
            FlightPermissions.set(player, FlightPermissions.BOOK_OF_ME, true);

            // ---- 所有属性 X2 ----
            applyAttributeDoubling(player);

            // ---- 真我之诗：飞行速度提高 100% ----
            if (player.getAbilities().flying) {
                setFlyingSpeed(player, 0.10F);
            } else {
                setFlyingSpeed(player, 0.05F);
            }

            // ---- 隐藏效果 ----
            if (player.tickCount % 20 == 0) {
                applyHiddenEffects(player);
            }

            // ---- 神性特效：莫比乌斯环 ----
            if (player.tickCount % 2 == 0) {
                try {
                    spawnMobiusRing(player);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            // 🚨 最终保险：任何异常都不让游戏崩溃
        }
    }

    // ==================== 属性翻倍 ====================
    private static void applyAttributeDoubling(Player player) {
        try {
            var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
            var health = player.getAttribute(Attributes.MAX_HEALTH);
            var armor = player.getAttribute(Attributes.ARMOR);
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

            if (atk != null && atk.getModifier(ATK_UUID) == null) {
                atk.addTransientModifier(new AttributeModifier(ATK_UUID, "book_of_me_double_atk", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (health != null && health.getModifier(HEALTH_UUID) == null) {
                health.addTransientModifier(new AttributeModifier(HEALTH_UUID, "book_of_me_double_health", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (armor != null && armor.getModifier(ARMOR_UUID) == null) {
                armor.addTransientModifier(new AttributeModifier(ARMOR_UUID, "book_of_me_double_armor", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (speed != null && speed.getModifier(SPEED_UUID) == null) {
                speed.addTransientModifier(new AttributeModifier(SPEED_UUID, "book_of_me_double_speed", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        } catch (Exception ignored) {
        }
    }

    private static void removeAttributeDoubling(Player player) {
        try {
            var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
            var health = player.getAttribute(Attributes.MAX_HEALTH);
            var armor = player.getAttribute(Attributes.ARMOR);
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

            if (atk != null) atk.removeModifier(ATK_UUID);
            if (health != null) health.removeModifier(HEALTH_UUID);
            if (armor != null) armor.removeModifier(ARMOR_UUID);
            if (speed != null) {
                speed.removeModifier(SPEED_UUID);
                speed.removeModifier(FLYING_SPEED_UUID);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 死亡时保护《如我所书》不掉落 ====================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        event.getDrops().removeIf(itemEntity ->
                itemEntity.getItem().getItem() instanceof BookOfMeItem);
    }

    // ==================== 伤害事件：无敌 + 免疫环境伤害 + 拦截 kill ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        // ---- 60 秒无敌期间，取消所有伤害 ----
        if (System.currentTimeMillis() < player.getPersistentData().getLong("BookOfMe_InvincibleEnd")) {
            event.setCanceled(true);
            return;
        }

        var source = event.getSource();

        // ---- 拦截 /kill 指令 ----
        // 作者 2026-11-10 定：毕业饰品连 /kill 与虚空都要扛得住，这里恢复拦截。
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§d【真我】命运的力量挡下了虚空意志。"), true);
            return;
        }

        // ---- 虚空伤害减免 66% ----
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setAmount(event.getAmount() * 0.34f);
            if (player.getHealth() - event.getAmount() <= 0) {
                player.setHealth(1.0f);
                event.setCanceled(true);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§d【真我】虚空意志无法吞噬你。"), true);
            }
            return;
        }

        // ---- [升级 #3 2026-10-21] 环境伤害兜底：没有"直接来源实体"的伤害一律视为环境 ----
        // 原实现只枚举原版标签（见下方白名单），别的模组自定义的环境伤害若没打标签就会漏过去。
        // 这条规则把覆盖面与"对方是否规范打标签"解耦：凡是绕过实体直接作用到玩家身上的伤害都免疫。
        // 已知副作用（与设定一致，故接受）：中毒/凋零/饥饿这类 Debuff 伤害同样没有来源实体，
        // 也会被一起免疫 —— 这与第五诫【负世】"免疫负面效果"的定位相符。
        if (source.getDirectEntity() == null) {
            event.setCanceled(true);
            return;
        }

        // ---- 免疫环境伤害（原白名单，保留：用于"由实体造成但应免疫"的情况，如闪电、弹射物）----
        // 2026-11-10（外部审查）：上面那条 getDirectEntity() == null 已经把「无直接来源」的伤害
        // 全部取消了，所以这里的 IS_FALL / IS_DROWNING / IS_FREEZING / LAVA 实际不可达。
        // 故意不删：它们是「哪天上面的规则被收窄」时的安全网，删掉会留下一个安静的漏洞，
        // 保留的代价只是四行字符串比较。可达的那些（IS_FIRE 火球、IS_PROJECTILE 箭）仍然靠这里。
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING) ||
                source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE) ||
                source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) {
            event.setCanceled(true);
        }
    }

    // ==================== 治疗事件：无视禁疗 ====================
    // [修复 P0-6 2026-10-21] 原优先级 HIGHEST 是【最先】执行的，此时还没有任何处理器 cancel 过，
    // 所以 event.isCanceled() 恒为 false，"无视禁疗"实际上从来没生效过。
    // 要推翻别人（在后执行的处理器）的结果，必须用 LOWEST —— 它最后执行，此时能看到所有 cancel。
    // 原代码：@SubscribeEvent(priority = EventPriority.HIGHEST)
    // [修复 2026-11-10] 还差一半：光有 LOWEST 不够，Forge 只把「已被取消」的事件送给
    // 声明了 receiveCanceled = true 的监听器，否则这里的 isCanceled() 永远是 false ——
    // 也就是说「免疫禁疗」之前从来没生效过（外部审查逐行指出）。
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        if (event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    // ==================== 死亡事件：88% 免死 + 60 秒无敌 ====================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        if (player.getRandom().nextFloat() < 0.88f) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.getActiveEffects().removeIf(effect -> !effect.getEffect().isBeneficial());
            player.getPersistentData().putLong("BookOfMe_InvincibleEnd", System.currentTimeMillis() + 60000);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§d【真我】命运拒绝了你，获得 60 秒无敌。"), true);
        } else {
            // [修复 2026-10-23] 这 12% 是「真的死了」。记下时间戳，
            // 让巡检层（onPlayerTickCanary）在这段时间内不要再复活玩家 ——
            // 否则服务端血量被拉回、客户端已经进了死亡界面，会出现「点复活没反应 / 原地复活」的卡死。
            player.getPersistentData().putLong("BookOfMe_DeathAllowed", System.currentTimeMillis());
        }
    }

    /** 最近 60 秒内是否「真的死过一次」。巡检层据此避让。 */
    private static boolean recentlyDiedForReal(Player player) {
        long at = player.getPersistentData().getLong("BookOfMe_DeathAllowed");
        return at > 0L && System.currentTimeMillis() - at < 60000L;
    }

    // ==================== 隐藏效果 ====================
    private static void applyHiddenEffects(Player player) {
        try {
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 40, 4, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 400, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 40, 4, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 1, true, false, false));
        } catch (Exception ignored) {
        }
    }

    // ==================== L3 不死巡检层（2026-10-21 新增） ====================
    // 背景：LivingHurtEvent / LivingDeathEvent 只能看到"走伤害管道"的死亡。
    //       其他模组若直接调用 player.setHealth(0) 或 removeAllEffects()，这两个事件都不会触发。
    //       本应是 Mixin 的活（注入 setHealth / removeAllEffects），但注入 LivingEntity 是全局
    //       高危注入点，兼容性代价远大于收益，故改用"每 tick 巡检 + 覆盖"：
    //       拦不住"被改写"，但保证 1 tick 内恢复，玩家体感上没有区别。
    // 执行时机：Phase.START，早于本 tick 内 LivingEntity 的死亡判定。
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickCanary(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (!hasBookOfMe(player)) return;

        try {
            // ① 血量被"绕过伤害管道"直接清零 → 在死亡判定前救回
            //
            // 【重要边界】只在 deathTime == 0 时介入。
            // deathTime 只有在实体真正走完 die() 流程后才会开始自增；
            // 如果在这里无条件复活，会把 L2 那 88% 的免死判定变成 100%（平衡性破坏）。
            // 也就是说：本层只救"被直接改血但还没死成"的情况，不抢 L2 的活。
            //
            // [修复 2026-10-23] 再补一道：最近 60 秒内「真的死过」就完全避让。
            // 否则一旦玩家已经进入死亡界面，服务端还在这里把血量拉回来，
            // 就会出现「点复活没反应 / 从标题界面回来后原地复活」的卡死。
            if (player.getHealth() <= 0.0F && player.deathTime == 0 && !recentlyDiedForReal(player)) {
                player.setHealth(player.getMaxHealth());
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§d【真我】命运拒绝了一次越界的抹杀。"), true);
            }

            // ② 关键光环被清空 → 立即重铺，而不是等到下一个 20 tick 周期
            //    只检查两个"没了就会立刻出事"的（抗火、抗性），避免每 tick 全量重铺。
            if (player.getEffect(MobEffects.FIRE_RESISTANCE) == null
                    || player.getEffect(MobEffects.DAMAGE_RESISTANCE) == null) {
                applyHiddenEffects(player);
            }
        } catch (Exception ignored) {
            // 巡检层自身绝不能抛异常打断玩家 tick
        }
    }

    // ==================== 神性特效：正对后脑勺的莫比乌斯环 ====================
    /*
     * 2026-11-10 重写（作者 m18820：隐形盔甲架是早期的技术妥协，能优化就优化）。
     *
     * 旧实现为了让粒子有一个「可读的坐标」，真的在玩家后脑勺生成了一只隐形盔甲架当锚点，
     * 每 2 刻传送它、每 2 刻从它身上取坐标画两个正交圆环。问题有三个：
     *   1. 每多一个戴书的玩家就多一只实体（区块卸载后还会重建第二只，见审查第 6 条）；
     *   2. 这具盔甲架会进实体列表，被打包进存档、被别的模组扫描到，甚至被 /kill 清掉；
     *   3. 双环只是「两个圆」，不是莫比乌斯带。
     *
     * 现在：坐标每刻由玩家自己算（不需要任何实体），曲线换成真正的莫比乌斯带参数方程
     *   (R + w·cos(t/2))·cos t,  w·sin(t/2),  (R + w·cos(t/2))·sin t
     * 颜色蓝粉交替，用 DUST 粒子（自带颜色），仍是纯服务端发包，客户端不需要改动。
     */
    private static void spawnMobiusRing(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        // 1. 锚点：背后（不是头顶、不是后脑勺）——取胸口高度再往身后推 0.38 格
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double anchorX = player.getX() - forwardX * 0.38;
        double anchorY = player.getY() + player.getBbHeight() * 0.62 + 0.4D;
        double anchorZ = player.getZ() - forwardZ * 0.38;

        // 圆环所在的平面：右方向 × 上方向，法线朝玩家正前方（所以环面是竖着的，不是朝下）
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);

        // 2. 无穷符号（Gerono 双纽线）：x = cos t、y = sin t·cos t，直接张在 right × up 平面上，
        //    再沿前后方向加一点点起伏，让它不是完全平的一片。
        double halfWidth = 0.55;
        double halfHeight = 0.30;
        double spin = player.tickCount * 0.05;

        int points = 64;
        for (int i = 0; i < points; i++) {
            double t = spin + i * (Math.PI * 2.0 / points);
            double ring = Math.cos(t);
            double height = Math.sin(t) * Math.cos(t);
            double depth = 0.06 * Math.sin(t * 2.0);

            double px = anchorX + rightX * ring * halfWidth + forwardX * depth;
            double py = anchorY + height * halfHeight;
            double pz = anchorZ + rightZ * ring * halfWidth + forwardZ * depth;

            int color = (i % 2 == 0) ? 0x4FC3FF : 0xFF69B4; // 蓝 / 粉
            serverLevel.sendParticles(
                    new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(
                                    ((color >> 16) & 0xFF) / 255.0F,
                                    ((color >> 8) & 0xFF) / 255.0F,
                                    (color & 0xFF) / 255.0F),
                            0.9F),
                    px, py, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // 3. 每 20 刻补一圈附魔粒子，增加「发光」的厚度
        if (player.tickCount % 20 == 0) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    anchorX, anchorY, anchorZ, 6, 0.4, 0.4, 0.4, 0.05);
        }
    }
}