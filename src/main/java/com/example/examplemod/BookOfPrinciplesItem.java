package com.example.examplemod;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.UUID;

public class BookOfPrinciplesItem extends Item implements ICurioItem {
    public BookOfPrinciplesItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) { return true; }

    // 辅助方法：从 NBT 读取等级
    public static int getProgressLevel(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("UnlockedCount")) {
            return stack.getTag().getInt("UnlockedCount");
        }
        return 0;
    }

    // 装备时的动态效果（用急迫效果完美解决挖掘速度问题）
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide) {
            int level = getProgressLevel(stack);
            if (level > 0) {
                // 每 20 tick (1秒) 刷新一次，提供对应等级的急迫效果
                player.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SPEED, // 原版急迫效果
                        20,                   // 持续 1 秒
                        level - 1,            // 等级：0级=急迫I, 4级=急迫V
                        true,                 // 隐藏粒子
                        false                 // 隐藏图标
                ));
            }
        }
    }

    // ==================== 属性面板加成（攻击、护甲、触及半径）====================
    // [2026-11-01 再改] 这里改为「读玩家实时进度、返回真实数值」：
    // ① Curios 会把它列进 curios.modifiers.book 提示段 —— 面板上能看见；
    // ② 面板里的数字随进度变化（tooltip 每帧重算，不受"只在槽位变化时调用"的限制）；
    // ③ 穿上那一刻 Curios 也会按当前数值挂到玩家身上。
    // 数值仍然由 ProgressAttributeHandler 兜底对账（防丢、防陈旧），两边 UUID 完全一致，
    // 所以不会叠加 —— handler 只"缺了才补、不同才换"。
    public static final UUID P_FIXED_ATTACK   = UUID.nameUUIDFromBytes("principia_fixed_attack".getBytes());
    public static final UUID P_FIXED_ARMOR    = UUID.nameUUIDFromBytes("principia_fixed_armor".getBytes());
    public static final UUID P_ATTACK_PERCENT = UUID.nameUUIDFromBytes("principia_attack_percent".getBytes());
    public static final UUID P_ARMOR_PERCENT  = UUID.nameUUIDFromBytes("principia_armor_percent".getBytes());
    public static final UUID P_BLOCK_REACH    = UUID.nameUUIDFromBytes("principia_block_reach".getBytes());
    public static final UUID P_ENTITY_REACH   = UUID.nameUUIDFromBytes("principia_entity_reach".getBytes());

    /** 进阶等级对应的攻/甲百分比：5% / 15% / 25%。 */
    public static final float[] PERCENT = {0.05F, 0.15F, 0.25F};

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = LinkedHashMultimap.create();
        if (!(slotContext.entity() instanceof Player player)) return map;
        if (player.level().isClientSide) {
            // 客户端没有持久化数据源，用物品 NBT 上的镜像值，保证提示与服务器一致
            return fromMirror(stack, map);
        }
        return build(map, MiningProgressTracker.getCurrentLevel(player),
                MiningProgressTracker.getCurrentAdvancedLevel(player));
    }

    private static Multimap<Attribute, AttributeModifier> fromMirror(ItemStack stack,
                                                                    Multimap<Attribute, AttributeModifier> map) {
        return build(map, getProgressLevel(stack), getAdvancedLevel(stack));
    }

    private static Multimap<Attribute, AttributeModifier> build(Multimap<Attribute, AttributeModifier> map,
                                                               int miningLevel, int advancedLevel) {
        map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(P_FIXED_ATTACK, "principia_fixed_attack",
                miningLevel, AttributeModifier.Operation.ADDITION));
        map.put(Attributes.ARMOR, new AttributeModifier(P_FIXED_ARMOR, "principia_fixed_armor",
                miningLevel, AttributeModifier.Operation.ADDITION));

        float percent = PERCENT[Math.min(advancedLevel, 2)];
        map.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(P_ATTACK_PERCENT, "principia_attack_percent",
                percent, AttributeModifier.Operation.MULTIPLY_BASE));
        map.put(Attributes.ARMOR, new AttributeModifier(P_ARMOR_PERCENT, "principia_armor_percent",
                percent, AttributeModifier.Operation.MULTIPLY_BASE));

        // 触及半径只在挖矿满级（下界合金档）才给，+1 格；其余等级连修饰符都不挂，
        // 否则 Curios 面板会出现一行「+0 方块范围」
        if (miningLevel == 4) {
            map.put(ForgeMod.BLOCK_REACH.get(), new AttributeModifier(P_BLOCK_REACH, "principia_block_reach",
                    1.0D, AttributeModifier.Operation.ADDITION));
            map.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(P_ENTITY_REACH, "principia_entity_reach",
                    1.0D, AttributeModifier.Operation.ADDITION));
        }
        return map;
    }

    // 新增辅助方法：读取进阶等级
    public static int getAdvancedLevel(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("AdvancedLevel")) {
            return stack.getTag().getInt("AdvancedLevel");
        }
        return 0;
    }
    // 添加物品悬浮提示（详细说明机制和当前进度）
    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        int miningLevel = getProgressLevel(stack);
        int advancedLevel = getAdvancedLevel(stack);
        float[] percentArray = {0.05f, 0.15f, 0.25f};
        // 显示成 5 / 15 / 25，避免 float 渲染出「5.0%」
        int percent = Math.round(percentArray[Math.min(advancedLevel, 2)] * 100);

        // 物品名已经写明书名，这里不再重复标题
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));

        // 显示挖矿带来的固定加成
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.fixed_bonus", miningLevel, miningLevel)
                .withStyle(net.minecraft.ChatFormatting.BLUE));

        // 显示进阶带来的百分比加成
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.percent_bonus", percent, percent)
                .withStyle(net.minecraft.ChatFormatting.GREEN));

        // 优先级判定：究极形态（龙蛋） > 终极形态（下界合金） > 普通提示
        // 优先级：完美形态（双满级） > 究极形态（进阶满级） > 终极形态（挖矿满级） > 普通提示
        if (miningLevel == 4 && advancedLevel >= 2) {
            // 1. 最高成就：完美形态（挖矿4级 + 进阶2阶）
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.perfect")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)); // 亮紫色，作为最高荣耀
        } else if (advancedLevel >= 2) {
            // 2. 进阶满级：究极形态（获得龙蛋）
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.ultimate")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE)); // 深紫色
        } else if (miningLevel == 4) {
            // 3. 挖矿满级：终极形态（获得下界合金）
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.principia.max")
                    .withStyle(net.minecraft.ChatFormatting.GOLD)); // 金色
        }
    }
}