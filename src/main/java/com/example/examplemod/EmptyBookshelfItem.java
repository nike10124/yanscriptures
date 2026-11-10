package com.example.examplemod;

import java.util.List;
import java.util.UUID;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 空书架：放在**护符**栏位的一件饰品，效果是给佩戴者多两个书籍栏位。
 *
 * <p>扩槽走 Curios 官方的「栏位修正」属性：{@link CuriosApi#addSlotModifier(Multimap, String, UUID, double, AttributeModifier.Operation)}
 * 往物品的属性表里加一条修正，Curios 自己会在提示框里把它渲染成「+2 书籍 栏位」，
 * 戴上也由 Curios 自己结算——和「属性加成写在物品侧」是同一套机制。
 *
 * <p>所以这里没有额外的事件监听器：戴上算、摘下撤，全部由 Curios 跟着饰品栏走。
 */
public class EmptyBookshelfItem extends Item implements ICurioItem {

    /** 要扩的栏位类型（见 {@code ExampleMod.registerCuriosSlots}）。 */
    private static final String SLOT = "book";
    /** 多给几格。 */
    private static final double AMOUNT = 2.0D;

    public EmptyBookshelfItem(Properties properties) {
        super(properties);
    }

    /** 只允许放在护符栏，而且同一时间只能戴一座（作者 2026-11-09 报「可以重复戴」）。 */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        if (!"charm".equals(context.identifier())) {
            return false;
        }
        net.minecraft.world.entity.LivingEntity wearer = context.entity();
        if (wearer == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(wearer).resolve().map(inventory -> {
            // 同一个槽位里已经放的（校验自己）直接放行，否则会把已戴好的书挤出去。
            var charm = inventory.getStacksHandler(context.identifier()).orElse(null);
            if (charm != null && context.index() < charm.getStacks().getSlots()) {
                ItemStack current = charm.getStacks().getStackInSlot(context.index());
                if (current.getItem() == this) {
                    return true;
                }
            }
            return inventory.findFirstCurio(this).isEmpty();
        }).orElse(true);
    }

    /**
     * 物品侧的栏位修正：Curios 读这张表，既用来算佩戴者的栏位数，
     * 也用来在提示框里显示「+N 某某 栏位」。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid,
                                                                       ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        CuriosApi.addSlotModifier(modifiers, SLOT, uuid, AMOUNT, AttributeModifier.Operation.ADDITION);
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yanscriptures.empty_bookshelf.desc")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    // ==================== 清理旧版残留（2026-11-09） ====================

    /** 开发期第一版写进存档的永久修正 UUID（`addPermanentSlotModifier` 那一版）。 */
    private static final UUID LEGACY_MODIFIER_ID =
            UUID.nameUUIDFromBytes("yanscriptures:empty_bookshelf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    /** 书籍栏的基础格数。 */
    private static final int BASE_SLOTS = 4;

    /**
     * 把开发期试错留下的额外书籍栏清掉，回到基础格数。
     *
     * <p>为什么需要它：第一版用 {@code growSlotType} 直接加格子、第二版用
     * {@code addPermanentSlotModifier} 写永久修正，这两者都**落进了玩家存档**。
     * 代码虽然已经换成物品侧属性，存档里的旧账不会自己消失——戴上新物品就会变成 +4。
     *
     * <p>只清「多出来且是空的」格子，避免把书吞掉。
     */
    public static String resetLegacy(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(inventory ->
                inventory.removeSlotModifier(SLOT, LEGACY_MODIFIER_ID));
        int slots = currentSlots(player);
        int extra = slots - BASE_SLOTS;
        if (extra <= 0) {
            return "书籍栏已经是基础格数（" + slots + " 格），没有需要清理的残留";
        }
        if (!extrasEmpty(player, slots)) {
            return "书籍栏现在是 " + slots + " 格，但多出来的格子里还放着书，请先清空再执行";
        }
        CuriosApi.getSlotHelper().shrinkSlotType(SLOT, extra, player);
        return "已清理残留：" + slots + " → " + currentSlots(player) + " 格";
    }

    /** 调试/命令用：当前书籍栏格数。 */
    public static int currentSlots(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(SLOT))
                .map(handler -> handler.getStacks().getSlots())
                .orElse(-1);
    }

    private static boolean extrasEmpty(ServerPlayer player, int slots) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(SLOT))
                .map(handler -> {
                    for (int i = BASE_SLOTS; i < slots; i++) {
                        if (!handler.getStacks().getStackInSlot(i).isEmpty()) {
                            return false;
                        }
                    }
                    return true;
                })
                .orElse(false);
    }

    /** {@code /ys bookshelf fix}：清掉开发期遗留的额外书籍栏。 */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCommand(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("bookshelf")
                .then(Commands.literal("fix")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(() -> Component.literal("§a" + resetLegacy(player)), true);
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("§7书籍栏当前 " + currentSlots(player) + " 格"), false);
                            return 1;
                        })));
    }
}
