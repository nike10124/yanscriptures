package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 首领判定。
 *
 * <p>不再用生命值阈值：整合包里普通怪的血量能涨到几百上千，
 * 「血量 ≥ 300 就是首领」在大包里几乎必然误判。
 *
 * <p>现在的判定分两层：
 * <ol>
 *   <li><b>原版三首领</b>写死识别 —— 末影龙、凋灵、监守者。
 *       它们没有统一的标签，但语义上永远是首领。</li>
 *   <li><b>其它模组的首领</b>看实体类型标签：
 *       {@code #forge:bosses}（Forge 约定标签，扩展模组通常都会写）、
 *       {@code #c:bosses}（跨加载器约定）、
 *       以及本模组自己的 {@code #yanscriptures:bosses}（供整合包补救漏标的首领）。</li>
 * </ol>
 */
public final class BossDetection {

    /** Forge 约定标签，Goety 等模组会往这里登记首领。 */
    public static final TagKey<EntityType<?>> FORGE_BOSSES = tag("forge:bosses");

    /** 跨加载器约定标签（部分模组只写这个）。 */
    public static final TagKey<EntityType<?>> COMMON_BOSSES = tag("c:bosses");

    /** 本模组标签：给漏标的首领兜底，整合包可直接追加 ID。 */
    public static final TagKey<EntityType<?>> OWN_BOSSES = tag("yanscriptures:bosses");

    private BossDetection() {
    }

    private static TagKey<EntityType<?>> tag(String id) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(id));
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;
        EntityType<?> type = entity.getType();
        if (type == EntityType.ENDER_DRAGON
                || type == EntityType.WITHER
                || type == EntityType.WARDEN) {
            return true;
        }
        return type.is(FORGE_BOSSES) || type.is(COMMON_BOSSES) || type.is(OWN_BOSSES);
    }

    /** 调试用：说明这只生物为什么被/没被判定为首领。 */
    public static String describe(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        StringBuilder sb = new StringBuilder();
        sb.append("§f").append(entity.getName().getString())
                .append(" §8(§7")
                .append(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type)))
                .append("§8) §7最大生命 §f").append(entity.getMaxHealth());

        java.util.List<String> hits = new java.util.ArrayList<>();
        if (type == EntityType.ENDER_DRAGON) hits.add("原版·末影龙");
        if (type == EntityType.WITHER) hits.add("原版·凋灵");
        if (type == EntityType.WARDEN) hits.add("原版·监守者");
        if (type.is(FORGE_BOSSES)) hits.add("#forge:bosses");
        if (type.is(COMMON_BOSSES)) hits.add("#c:bosses");
        if (type.is(OWN_BOSSES)) hits.add("#yanscriptures:bosses");

        sb.append(" §8| ").append(hits.isEmpty()
                ? "§7标签命中：§8（无）§7 → 判为普通生物"
                : "§a标签命中：" + String.join("§7、§a", hits) + " §7 → §a判为首领");
        return sb.toString();
    }
}
