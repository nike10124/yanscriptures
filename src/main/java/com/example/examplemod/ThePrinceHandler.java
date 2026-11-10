package com.example.examplemod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.logging.LogUtils;

/**
 * 《君主论》—— 君主的光环照着自己人。
 *
 * <p>作者定稿（2026-11-01）：
 * <ul>
 *   <li>生命 ≥ 80%：攻击力 +8%；</li>
 *   <li>生命 ≤ 30%：受到伤害 −15%、移动速度 +10%；</li>
 *   <li>半径 16 格内己方宠物/仆从（含诡厄巫法的 servant、其他模组仆从）生命值、护甲、攻击力 +20%。</li>
 * </ul>
 *
 * <p>「仆从」怎么认：不写别的模组的类名，统一看
 * {@code data/yanscriptures/tags/entity_types/minions.json}；
 * 有主人的（原版猫狗、诡厄巫法的 servant 等实现了 {@link OwnableEntity} 的）额外要求
 * <b>主人就是佩戴者</b>，免得把别人的仆从也加了 20%。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThePrinceHandler {

    /** 玩家下线时清账（2026-11-10，D 批「状态残留」）。 */
    public static void cleanupPlayer(UUID id) {
        AURA_SEEN.remove(id);
        AURA_OWNER.remove(id);
    }

    private static final TagKey<EntityType<?>> MINIONS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(ExampleMod.MODID, "minions"));

    private static final double HIGH_HEALTH_RATIO = 0.80D;
    private static final double LOW_HEALTH_RATIO = 0.30D;
    private static final double HIGH_ATTACK = 0.08D;
    private static final double LOW_SPEED = 0.10D;
    private static final double LOW_INCOMING = 0.15D;
    private static final double AURA = 0.20D;
    private static final double AURA_RADIUS = 16.0D;

    private static final UUID ID_ATTACK = BookAttr.id("prince_attack");
    private static final UUID ID_SPEED = BookAttr.id("prince_speed");

    /** 已经被加过光环的实体：实体 UUID → 最后一次被看到的 gameTime。 */
    private static final Map<UUID, Long> AURA_SEEN = new HashMap<>();
    /** 实体 UUID → 加成来源玩家，用来在摘书时精准撤掉。 */
    private static final Map<UUID, UUID> AURA_OWNER = new HashMap<>();

    /** 标签只在第一次戴书时查一次，日志里留一行，省得光环「没反应」时无从下手。 */
    private static boolean tagChecked;

    private ThePrinceHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.THE_PRINCE)) {
            return;
        }
        if (healthRatio(player) <= LOW_HEALTH_RATIO) {
            event.setAmount((float) (event.getAmount() * (1.0D - LOW_INCOMING)));
        }
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
        boolean worn = WornCache.wears(player, ClassicBook.THE_PRINCE);
        double ratio = healthRatio(player);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, worn && ratio >= HIGH_HEALTH_RATIO ? HIGH_ATTACK : 0.0D);
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, worn && ratio <= LOW_HEALTH_RATIO ? LOW_SPEED : 0.0D);

        if (worn) {
            if (!tagChecked) {
                tagChecked = true;
                logTagSize(player);
            }
            auraAround(player);
        } else {
            releaseAuraOf(player);
        }
        sweepStaleAura(player);
    }

    private static double healthRatio(Player player) {
        float max = player.getMaxHealth();
        return max <= 0.0F ? 0.0D : player.getHealth() / max;
    }

    private static void auraAround(Player player) {
        long now = player.level().getGameTime();
        for (LivingEntity minion : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(AURA_RADIUS))) {
            if (minion == player || !minion.isAlive() || !isOwnMinion(player, minion)) {
                continue;
            }
            applyAura(minion);
            AURA_SEEN.put(minion.getUUID(), now);
            AURA_OWNER.put(minion.getUUID(), player.getUUID());
        }
    }

    private static boolean isOwnMinion(Player player, LivingEntity candidate) {
        if (!candidate.getType().is(MINIONS)) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable) {
            return player.getUUID().equals(ownable.getOwnerUUID());
        }
        return true;
    }

    private static void applyAura(LivingEntity minion) {
        UUID healthId = BookAttr.id("prince_minion_health_" + minion.getUUID());
        boolean fresh = isMissing(minion, Attributes.MAX_HEALTH, healthId);
        BookAttr.set(minion, Attributes.MAX_HEALTH, healthId, AURA);
        BookAttr.set(minion, Attributes.ARMOR, BookAttr.id("prince_minion_armor_" + minion.getUUID()), AURA);
        BookAttr.set(minion, Attributes.ATTACK_DAMAGE, BookAttr.id("prince_minion_attack_" + minion.getUUID()), AURA);
        if (fresh && minion.isAlive()) {
            // 生命上限涨了但当前血量不会自己涨，主人看上去就像「光环没生效」。
            // 只在新加上的那一次补血，免得在光环里站着不动也能无限回血。
            float maxNow = minion.getMaxHealth();
            minion.heal((float) (maxNow * AURA / (1.0D + AURA)));
        }
    }

    private static boolean isMissing(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = entity.getAttribute(attribute);
        return instance != null && instance.getModifier(id) == null;
    }

    /** 光环依赖 {@code #yanscriptures:minions}；标签若因别的模组改名而整份加载失败，这里会直接喊出来。 */
    private static void logTagSize(Player player) {
        int size = player.level().registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getTag(MINIONS)
                .map(holders -> holders.size())
                .orElse(0);
        if (size == 0) {
            LogUtils.getLogger().warn("《君主论》：实体标签 #yanscriptures:minions 为空或加载失败，16 格光环不会生效");
        } else {
            LogUtils.getLogger().info("《君主论》：仆从标签共 {} 个实体，光环生效范围为 {} 格", size, (int) AURA_RADIUS);
        }
    }

    private static void removeAura(LivingEntity minion) {
        BookAttr.clear(minion, Attributes.MAX_HEALTH, BookAttr.id("prince_minion_health_" + minion.getUUID()));
        BookAttr.clear(minion, Attributes.ARMOR, BookAttr.id("prince_minion_armor_" + minion.getUUID()));
        BookAttr.clear(minion, Attributes.ATTACK_DAMAGE, BookAttr.id("prince_minion_attack_" + minion.getUUID()));
        BookAttr.clampHealth(minion);
    }

    private static void releaseAuraOf(Player player) {
        Iterator<Map.Entry<UUID, UUID>> it = AURA_OWNER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            if (!entry.getValue().equals(player.getUUID())) {
                continue;
            }
            AURA_SEEN.remove(entry.getKey());
            Entity entity = findEntity(player, entry.getKey());
            if (entity instanceof LivingEntity living) {
                removeAura(living);
            }
            it.remove();
        }
    }

    /** 仆从走远/被换掉/死亡之后，光环要撤掉——否则它会一直带着 +20%。 */
    private static void sweepStaleAura(Player player) {
        long now = player.level().getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = AURA_SEEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() <= 100L) {
                continue;
            }
            Entity entity = findEntity(player, entry.getKey());
            if (entity instanceof LivingEntity living) {
                removeAura(living);
            }
            AURA_OWNER.remove(entry.getKey());
            it.remove();
        }
    }

    private static Entity findEntity(Player player, UUID id) {
        if (player.getServer() == null) {
            return null;
        }
        for (var level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
