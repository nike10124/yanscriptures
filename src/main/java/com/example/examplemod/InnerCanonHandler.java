package com.example.examplemod;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《黄帝内经》—— 上工治未病。
 *
 * <p>作者定稿（2026-11-08）：
 * <ul>
 *   <li>获得「当前生命百分比 − 50%」的伤害减免（不小于 0）；</li>
 *   <li>每 30 秒按「木 → 火 → 土 → 金 → 水」轮换一次，分别给予
 *       生命 +15% / 攻击 +15% / 抗击退 / 护甲 +15% / 移速与攻速各 +15%。</li>
 * </ul>
 * 作者补充（同日回答）：抗击退的额度是「保证玩家不会被击退即可」，所以直接给满。
 *
 * <p>轮换的相位**不存玩家数据**，而是按世界时间算：{@code gameTime / 600 % 5}。
 * 这样服务端挂属性、客户端画提示框读到的是同一个相位，不需要额外的同步包；
 * 代价是中途戴上这本书时，会直接接上当前这段世界时间的五行，而不是从「木」开始。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InnerCanonHandler {

    /** 五行之一停留的时间：30 秒。 */
    public static final int CYCLE_TICKS = 30 * 20;

    /** 五行的中文名，顺序就是轮换顺序（木生火、火生土……）。 */
    public static final String[] ELEMENTS = {"木", "火", "土", "金", "水"};

    /** 生命 / 攻击 / 护甲 / 移动速度 / 攻击速度的统一加成幅度。 */
    public static final double BONUS = 0.15D;

    private static final java.util.UUID ID_HEALTH = BookAttr.id("classic_inner_canon_health");
    private static final java.util.UUID ID_ATTACK = BookAttr.id("classic_inner_canon_attack");
    private static final java.util.UUID ID_KNOCKBACK = BookAttr.id("classic_inner_canon_knockback");
    private static final java.util.UUID ID_ARMOR = BookAttr.id("classic_inner_canon_armor");
    private static final java.util.UUID ID_SPEED = BookAttr.id("classic_inner_canon_speed");
    private static final java.util.UUID ID_ATTACK_SPEED = BookAttr.id("classic_inner_canon_attack_speed");

    private InnerCanonHandler() {
    }

    /** 当前处于哪一行：0 木、1 火、2 土、3 金、4 水。客户端与服务端用同一套算法。 */
    public static int element(Level level) {
        long cycle = (long) CYCLE_TICKS * ELEMENTS.length;
        return (int) (Math.floorMod(level.getGameTime(), cycle) / CYCLE_TICKS);
    }

    /** 距离下一次轮换还有多少刻，给提示框用。 */
    public static int ticksUntilNext(Level level) {
        long cycle = (long) CYCLE_TICKS * ELEMENTS.length;
        return (int) (CYCLE_TICKS - Math.floorMod(level.getGameTime(), cycle) % CYCLE_TICKS);
    }

    /** 第 {@code index} 行给的东西，一句话，提示框直接显示。 */
    public static String bonusText(int index) {
        return switch (index) {
            case 0 -> "生命上限 +15%";
            case 1 -> "攻击力 +15%";
            case 2 -> "不会被击退";
            case 3 -> "护甲 +15%";
            default -> "移动速度与攻击速度 +15%";
        };
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        boolean worn = WornCache.wears(player, ClassicBook.INNER_CANON_OF_THE_YELLOW_EMPEROR);
        apply(player, worn ? element(player.level()) : -1);
    }

    /** 把第 {@code index} 行的加成挂上，其余四行清零；{@code index < 0} 表示全部清空。 */
    private static void apply(Player player, int index) {
        BookAttr.set(player, Attributes.MAX_HEALTH, ID_HEALTH, index == 0 ? BONUS : 0.0D);
        BookAttr.set(player, Attributes.ATTACK_DAMAGE, ID_ATTACK, index == 1 ? BONUS : 0.0D);
        // 抗击退的额度上限就是 1.0（= 100%），这里给满即可；加法叠加，不能用乘法。
        BookAttr.set(player, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK, index == 2 ? 1.0D : 0.0D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        BookAttr.set(player, Attributes.ARMOR, ID_ARMOR, index == 3 ? BONUS : 0.0D);
        BookAttr.set(player, Attributes.MOVEMENT_SPEED, ID_SPEED, index == 4 ? BONUS : 0.0D);
        BookAttr.set(player, Attributes.ATTACK_SPEED, ID_ATTACK_SPEED, index == 4 ? BONUS : 0.0D);
    }

    /**
     * 按「当前生命百分比 − 50%」减免伤害。
     *
     * <p>满血时减 50%，半血及以下不减，是连续量，所以只能放在事件里算，不能写成属性。
     * 用 {@link EventPriority#LOW}：和本模组其它改伤害的处理器同一个优先级段，各自做乘法。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!WornCache.wears(player, ClassicBook.INNER_CANON_OF_THE_YELLOW_EMPEROR)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        float ratio = player.getMaxHealth() <= 0.0F ? 0.0F : player.getHealth() / player.getMaxHealth();
        float reduction = Math.max(0.0F, ratio - 0.5F);
        if (reduction > 0.0F) {
            event.setAmount(amount * (1.0F - reduction));
        }
    }
}
