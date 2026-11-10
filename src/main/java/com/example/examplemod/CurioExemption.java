package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 第一诫「独一」与示马残卷共用的饰品豁免表。
 *
 * <p>背景：整合包里普遍存在「开局必定获得、绑定后无法卸下」的诅咒饰品，
 * 以及为了逆转这些诅咒而必须常驻佩戴的配套饰品。它们与玩家的信仰无关，
 * 却会让「饰品栏里没有别的神」这条判定永远失败。所以这里引入一份物品标签：
 *
 * <pre>data/yanscriptures/tags/items/curio_exempt.json</pre>
 *
 * <p>放在标签里而不是代码里，是为了让整合包作者不必改代码、也不必等模组更新，
 * 只要在自己数据包里往 {@code #yanscriptures:curio_exempt} 追加物品 ID 即可。
 * 判定仍是语义化的：不针对某个模组，只针对「被列入豁免表的物品」。
 */
public final class CurioExemption {

    public static final TagKey<Item> CURIO_EXEMPT =
            ItemTags.create(new ResourceLocation(ExampleMod.MODID + ":curio_exempt"));

    private CurioExemption() {
    }

    /** 该饰品是否已在豁免表中（豁免 = 既不算本模组饰品，也不算外来信仰）。 */
    public static boolean isExempt(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CURIO_EXEMPT);
    }

    /**
     * 调试用：把饰品栏逐格分类打印出来，方便在游戏里确认豁免表有没有生效。
     *
     * @return 每行一条，形如 {@code [ring 0] 七咒之戒 —— 豁免}
     */
    public static java.util.List<String> audit(net.minecraft.world.entity.player.Player player) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve()
                .ifPresentOrElse(handler -> {
                    handler.getCurios().forEach((slot, slotHandler) -> {
                        for (int i = 0; i < slotHandler.getSlots(); i++) {
                            ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                            if (stack.isEmpty()) continue;
                            String mark;
                            if (isExempt(stack)) {
                                mark = "§b豁免";
                            } else if (isOwn(stack)) {
                                mark = "§a本模组";
                            } else {
                                mark = "§c外来";
                            }
                            lines.add("§7[" + slot + " " + i + "] §f"
                                    + stack.getHoverName().getString() + " §8(§7"
                                    + String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS
                                    .getKey(stack.getItem())) + "§8) —— " + mark);
                        }
                    });
                }, () -> lines.add("§c读取不到饰品栏。"));
        if (lines.isEmpty()) lines.add("§7饰品栏是空的。");
        return lines;
    }

    private static boolean isOwn(ItemStack stack) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getKey(stack.getItem());
        return key != null && key.getNamespace().equals(ExampleMod.MODID);
    }
}
