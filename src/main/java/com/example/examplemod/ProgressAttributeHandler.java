package com.example.examplemod;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

/**
 * 《自然哲学的数学原理》与《翠玉录》的成长属性统一维护器。
 *
 * <h2>为什么需要这个类（升级 #5 · 2026-10-21）</h2>
 *
 * 原来两本书的属性写在 {@code ICurioItem#getAttributeModifiers} 里，只读取**物品自身的 NBT**。
 * 而 Curios 只在"饰品槽内容发生变化"（装备/卸下/替换）时才会重新调用该方法 ——
 * 你在 NBT 上写新的进度并不会触发重算，于是出现"必须脱下来再穿上才生效"的怪现象，
 * 文案里被迫加了"请重新装备本书以刷新属性"的提示。
 * <p>
 * 这不是属性系统的通病（{@code addTransientModifier} 会立刻重算），而是读取时机问题。
 * <p>
 * <h2>现在的做法</h2>
 *
 * 照搬 {@link BookOfMeHandler} 已被验证的模式：<b>每 10 tick 用固定 UUID 维护瞬态修饰符</b>。
 * <ul>
 *   <li>数据源改为**玩家持久化数据**（挖矿等级 / 药水图鉴），而不是物品 NBT —— 物品可能被复制、替换、重铸；</li>
 *   <li>顺手把进度写回物品 NBT，保证 tooltip 与实际加成永远一致，黄色提示可以删掉；</li>
 *   <li>卸下时立即清除，不会残留；</li>
 *   <li>UUID 与原实现完全一致，便于与其他逻辑共存。</li>
 * </ul>
 *
 * <b>2026-11-01 角色变更：</b>两个物品的 {@code getAttributeModifiers} 已改回「返回当前真实数值」，
 * 这样 Curios 面板（{@code curios.modifiers.book}）能显示，穿上那一刻也会由 Curios 挂到玩家身上。
 * 本类保留下来当<b>兜底对账层</b>：只做「缺了才补、数值/运算不同才换、脱下就清」，
 * UUID 与物品完全一致，因此不会和 Curios 叠加。它解决的是「Curios 不会因为进度变化而重算」的陈旧问题
 * （以及别的模组/指令把修饰符摘掉的意外情况）。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProgressAttributeHandler {

    // ==================== 《自然哲学的数学原理》修饰符 UUID（与原实现保持一致）====================
    private static final UUID P_FIXED_ATTACK   = UUID.nameUUIDFromBytes("principia_fixed_attack".getBytes());
    private static final UUID P_FIXED_ARMOR    = UUID.nameUUIDFromBytes("principia_fixed_armor".getBytes());
    private static final UUID P_ATTACK_PERCENT = UUID.nameUUIDFromBytes("principia_attack_percent".getBytes());
    private static final UUID P_ARMOR_PERCENT  = UUID.nameUUIDFromBytes("principia_armor_percent".getBytes());
    private static final UUID P_BLOCK_REACH    = UUID.nameUUIDFromBytes("principia_block_reach".getBytes());
    private static final UUID P_ENTITY_REACH   = UUID.nameUUIDFromBytes("principia_entity_reach".getBytes());

    // ==================== 《翠玉录》修饰符 UUID（与原实现保持一致）====================
    private static final UUID E_BASE_HEALTH   = UUID.nameUUIDFromBytes("emerald_tablet_base_health".getBytes());
    private static final UUID E_GROWTH_HEALTH = UUID.nameUUIDFromBytes("emerald_tablet_growth_health".getBytes());
    private static final UUID E_BASE_ATTACK   = UUID.nameUUIDFromBytes("emerald_tablet_base_attack".getBytes());

    /** 刷新间隔（tick）。10 tick = 0.5 秒，玩家感知不到延迟，开销可忽略。 */
    private static final int SYNC_INTERVAL = 10;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % SYNC_INTERVAL != 0) return;

        try {
            syncPrincipia(player);
            syncEmeraldTablet(player);
        } catch (Exception ignored) {
            // 属性维护失败不应打断玩家 tick
        }
    }

    // ==================== 《自然哲学的数学原理》====================

    private static void syncPrincipia(Player player) {
        ItemStack stack = findEquipped(player, ExampleMod.PRINCIPIA.get());

        if (stack.isEmpty()) {
            clearPrincipia(player);
            return;
        }

        // 数据源：玩家持久化数据（MiningProgressTracker 在拾取矿物时写入）
        int miningLevel = MiningProgressTracker.getCurrentLevel(player);
        int advancedLevel = MiningProgressTracker.getCurrentAdvancedLevel(player);

        // 顺手回写物品 NBT，让 tooltip 与实际加成永远一致（旧方案需要"重新装备"才能刷新）
        var tag = stack.getOrCreateTag();
        if (tag.getInt("UnlockedCount") != miningLevel) tag.putInt("UnlockedCount", miningLevel);
        if (tag.getInt("AdvancedLevel") != advancedLevel) tag.putInt("AdvancedLevel", advancedLevel);

        // 百分比：5% / 15% / 25%
        float[] percentArray = {0.05f, 0.15f, 0.25f};
        float percent = percentArray[Math.min(advancedLevel, 2)];

        // 触及半径：只在挖矿满级（下界合金档）给 +1 格，低等级没有（作者 2026-11-01 定）
        double reachBonus = (miningLevel == 4) ? 1.0 : 0.0;

        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE),
                P_FIXED_ATTACK, "principia_fixed_attack", miningLevel,
                AttributeModifier.Operation.ADDITION);
        setModifier(player.getAttribute(Attributes.ARMOR),
                P_FIXED_ARMOR, "principia_fixed_armor", miningLevel,
                AttributeModifier.Operation.ADDITION);

        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE),
                P_ATTACK_PERCENT, "principia_attack_percent", percent,
                AttributeModifier.Operation.MULTIPLY_BASE);
        setModifier(player.getAttribute(Attributes.ARMOR),
                P_ARMOR_PERCENT, "principia_armor_percent", percent,
                AttributeModifier.Operation.MULTIPLY_BASE);

        setModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()),
                P_BLOCK_REACH, "principia_block_reach", reachBonus,
                AttributeModifier.Operation.ADDITION);
        setModifier(player.getAttribute(ForgeMod.ENTITY_REACH.get()),
                P_ENTITY_REACH, "principia_entity_reach", reachBonus,
                AttributeModifier.Operation.ADDITION);
    }

    private static void clearPrincipia(Player player) {
        removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), P_FIXED_ATTACK);
        removeModifier(player.getAttribute(Attributes.ARMOR), P_FIXED_ARMOR);
        removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), P_ATTACK_PERCENT);
        removeModifier(player.getAttribute(Attributes.ARMOR), P_ARMOR_PERCENT);
        removeModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()), P_BLOCK_REACH);
        removeModifier(player.getAttribute(ForgeMod.ENTITY_REACH.get()), P_ENTITY_REACH);
    }

    // ==================== 《翠玉录》====================

    private static void syncEmeraldTablet(Player player) {
        ItemStack stack = findEquipped(player, ExampleMod.EMERALD_TABLET.get());

        if (stack.isEmpty()) {
            boolean hadModifier = player.getAttribute(Attributes.MAX_HEALTH) != null
                    && player.getAttribute(Attributes.MAX_HEALTH).getModifier(E_BASE_HEALTH) != null;
            removeModifier(player.getAttribute(Attributes.MAX_HEALTH), E_BASE_HEALTH);
            removeModifier(player.getAttribute(Attributes.MAX_HEALTH), E_GROWTH_HEALTH);
            removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), E_BASE_ATTACK);
            // 上限下降后当前血量可能超出，必须手动收紧，否则会出现"血条溢出"
            if (hadModifier) clampHealth(player);
            return;
        }

        int unlockedCount = PotionProgressTracker.getTotalUnlockedCount(player);

        var tag = stack.getOrCreateTag();
        if (tag.getInt("UnlockedCount") != unlockedCount) tag.putInt("UnlockedCount", unlockedCount);

        // 攻击力 +4：纯粹的「近战补偿」（Config.tabletAttackBonus）。
        // 成长部分已经全部搬到魔法伤害上（见 EmeraldTabletMagic），
        // 这里给一点固定攻击力，保证纯近战玩家戴上它还是一件能用的饰品。
        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE),
                E_BASE_ATTACK, "emerald_tablet_base_attack", Config.tabletAttackBonus,
                AttributeModifier.Operation.ADDITION);

        // [2026-11-01 按作者记忆修正] 最大生命**没有基础值**：只有「每解锁一种药水 +2」。
        // 旧存档里挂过一条 +4 基础生命（E_BASE_HEALTH），这里顺手清掉。
        removeModifier(player.getAttribute(Attributes.MAX_HEALTH), E_BASE_HEALTH);
        boolean hadHealthBonus = player.getAttribute(Attributes.MAX_HEALTH) != null
                && player.getAttribute(Attributes.MAX_HEALTH).getModifier(E_GROWTH_HEALTH) != null;

        // 成长：每解锁一种药水 +2 最大生命
        if (unlockedCount > 0) {
            setModifier(player.getAttribute(Attributes.MAX_HEALTH),
                    E_GROWTH_HEALTH, "emerald_tablet_growth_health", unlockedCount * 2.0,
                    AttributeModifier.Operation.ADDITION);
        } else {
            removeModifier(player.getAttribute(Attributes.MAX_HEALTH), E_GROWTH_HEALTH);
        }

        // 上限变动后当前血量可能超出，必须收紧，否则血条溢出
        if (hadHealthBonus) clampHealth(player);
    }

    // ==================== 通用工具 ====================

    /** 取某件饰品在当前饰品栏中的那一份（没有则返回 EMPTY）。 */
    private static ItemStack findEquipped(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(item))
                .map(slotResult -> slotResult.stack())
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 仅在"数值或运算方式变化时"才重建修饰符。
     * 这是关键：无脑每 tick remove + add 会让 AttributeInstance 不停 onDirty，
     * 导致最大生命反复钳制、客户端血条抖动。
     */
    private static void setModifier(AttributeInstance instance, UUID id, String name,
                                   double amount, AttributeModifier.Operation operation) {
        if (instance == null) return;

        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) < 1.0E-6D) {
            // 加成为 0 就别留一条 +0 的修饰符（Curios 面板会显示「+0 方块范围」）
            if (current != null) instance.removeModifier(id);
            return;
        }
        if (current != null
                && current.getAmount() == amount
                && current.getOperation() == operation) {
            return; // 无变化，什么都不做
        }

        if (current != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    private static void removeModifier(AttributeInstance instance, UUID id) {
        if (instance == null) return;
        // 先判断是否存在再删：部分实现里 removeModifier 会无条件 onDirty，
        // 每 10 tick 对全体玩家空删一遍会造成无意义的属性重算。
        if (instance.getModifier(id) == null) return;
        instance.removeModifier(id);
    }

    private static void clampHealth(Player player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
