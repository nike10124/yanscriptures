package com.example.examplemod;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 把「材料档位」加成挂到玩家属性上（2026-10-31 新增，同日改为「每一本书都算」）。
 *
 * <p>规则（作者 m08405 / m08693）：饰品栏里的<b>每一本</b>带档位的书都各给一份加成；
 * 每本书的三条材料线各掷一项自己的属性，只加成到那一项上。四本书就是四份相加。</p>
 *
 * <p>全部用 {@link AttributeModifier.Operation#MULTIPLY_TOTAL}：原版属性计算里最后一步的乘算层，
 * 乘的是「固定值加成全部算完后的总数」，因此不会被别的模组的 ADDITION 冲淡 —— 这就是作者要的独立乘区。</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaterialBonusHandler {

    /** 四条属性各挂一条聚合修正器：把当前戴着的所有书的加成先加起来，再一次性写进去。 */
    private static final UUID ID_ATTACK = UUID.nameUUIDFromBytes("material_bonus_attack".getBytes());
    private static final UUID ID_HEALTH = UUID.nameUUIDFromBytes("material_bonus_health".getBytes());
    private static final UUID ID_ARMOR = UUID.nameUUIDFromBytes("material_bonus_armor".getBytes());
    private static final UUID ID_SPEED = UUID.nameUUIDFromBytes("material_bonus_speed".getBytes());

    private MaterialBonusHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        // 每 20 刻对一次就够了：换书、摘书最多晚半秒生效，省下的是每秒 20 次的饰品栏遍历
        if (player.tickCount % 20 != 0) {
            return;
        }

        List<ItemStack> books = wornBooks(player);
        // 四条属性分别累计：每条材料线掷中的那一项只加到自己那一项上
        double[] totals = new double[4];
        for (ItemStack stack : books) {
            double[] perAttr = MaterialBonus.perAttribute(stack);
            for (int i = 0; i < totals.length; i++) {
                totals[i] += perAttr[i];
            }
        }
        double attack = totals[0];
        double health = totals[1];
        double armor = totals[2];
        double speed = totals[3];

        apply(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, attack);
        apply(player, Attributes.MAX_HEALTH, ID_HEALTH, health);
        apply(player, Attributes.ARMOR, ID_ARMOR, armor);
        apply(player, Attributes.MOVEMENT_SPEED, ID_SPEED, speed);

        // 摘书后生命上限掉下来时，血条可能停在新上限之上
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 饰品栏里所有带档位的书；同一本书戴不了两遍，不用去重。 */
    private static List<ItemStack> wornBooks(Player player) {
        List<ItemStack> books = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> handler.getCurios().forEach((id, stash) -> {
            for (int slot = 0; slot < stash.getSlots(); slot++) {
                ItemStack stack = stash.getStacks().getStackInSlot(slot);
                if (!stack.isEmpty() && MaterialBonus.totalPercent(stack) > 0.0D) {
                    books.add(stack);
                }
            }
        }));
        return books;
    }

    /**
     * 挂一条乘算修正器。数值是「这一 tick 所有书加起来的总和」。
     *
     * @param percent 总和百分比；0 表示这次不该有加成
     */
    private static void apply(Player player, Attribute attribute, UUID id, double percent) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (percent <= 1.0E-6D) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && Math.abs(existing.getAmount() - percent) < 1.0E-9D) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, "yanscriptures_material_bonus",
                percent, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
