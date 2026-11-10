package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 圣约·原典 —— 十诫路线的毕业饰品（{@code book} 槽位）。
 *
 * <h2>三段式介绍</h2>
 * <ul>
 *   <li><b>普通悬停</b>：卷名、十二权柄名号、快捷键提示</li>
 *   <li><b>Shift</b>：十二条权的经文（每条 3 行，翻页）</li>
 *   <li><b>Shift + Alt</b>：全部数值与机制明细（含尚未铸成的条目）</li>
 * </ul>
 * 两种翻页视图共用同一个滚轮偏移，但每屏行数不同，所以页码与上限都要按视图分别计算。
 *
 * <h2>十二权柄</h2>
 * <ol>
 *   <li>万邦止戈 —— 生物不主动锁定佩戴者；被攻击后还手 10 秒</li>
 *   <li>不死之约 —— 高于《如我所书》的保命能力（尚未铸成）</li>
 *   <li>日影停住 —— Z 键，7 秒，冷却 30 秒，半径 64</li>
 *   <li>七碗倾倒 —— J 键，777/秒 × 7 秒，半径 16，冷却 30 秒</li>
 *   <li>不可近身 —— 免疫负面效果与基本伤害、禁疗免疫、777% 反伤、7 秒无敌帧、创造飞行</li>
 *   <li>申命之赐 —— 全部有益效果七级；Shift 右键赐福、Alt 右键降灾</li>
 *   <li>万军之磐石 —— 777 特殊生命与全套数值加成、暴击</li>
 *   <li>复活在我 —— 永久图腾</li>
 *   <li>万军之约 —— 击杀成长</li>
 *   <li>五饼二鱼 —— 掉落全拿并翻倍（尚未铸成）</li>
 *   <li>云柱火柱 —— K 键沿视线脱困</li>
 *   <li>圣所 —— 团队伤害分担（尚未铸成）</li>
 * </ol>
 */
public class ScriptureOfCovenantItem extends Item implements ICurioItem {

    /** 权柄数量。 */
    public static final int POWER_COUNT = 12;
    /** 每个权柄在经文视图里占 3 行 + 1 空行。 */
    public static final int LINES_PER_POWER = 4;
    /** 经文视图的总行数。 */
    public static final int TOTAL_LINES = POWER_COUNT * LINES_PER_POWER;
    /** 经文视图每屏行数（正好 3 个权柄，不会把一个权柄切成两半）。 */
    public static final int PAGE_SIZE = 12;
    /** 明细视图每屏行数。 */
    public static final int DETAIL_PAGE_SIZE = 18;

    /** 滚轮偏移（行）。两个视图共用。 */
    public static int scrollOffset = 0;

    public ScriptureOfCovenantItem(Properties properties) {
        super(properties);
    }

    // ==================== 槽位约束 ====================

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return CurioDuplicateGuard.canEquip(player, stack);
        }
        return true;
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        // 毕业饰品，戴上即绑定：活着取不下来，创造模式除外
        if (slotContext.entity() instanceof Player player) {
            return player.isCreative();
        }
        return true;
    }

    // ==================== 「万军之磐石」的固定数值 ====================
    // 2026-11-01（作者 m09952 / m10018）：原本由 CovenantCodexHandler 每 tick 挂 transient 修饰符，
    // 这样 Curios 看不到、物品提示里也不会出现那一段「当装备在书籍栏：」。
    // 现在从物品自身的 getAttributeModifiers 返回 —— Curios 的 CurioStacksHandler.update()
    // 会把这些修饰符挂到佩戴者身上（卸下时自动移除），同时列进 curios.modifiers.book 提示段。
    // 数值与旧版逐条一致。

    /** 全套加成的基础数值（护甲 / 幸运 / 攻击力 / 盔甲韧性都是 777）。 */
    public static final double ATTRIBUTE_BONUS = 777.0D;

    private static final UUID ID_ARMOR = UUID.nameUUIDFromBytes("covenant_armor".getBytes());
    private static final UUID ID_LUCK = UUID.nameUUIDFromBytes("covenant_luck".getBytes());
    private static final UUID ID_ATTACK = UUID.nameUUIDFromBytes("covenant_attack".getBytes());
    private static final UUID ID_TOUGHNESS = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee01");
    private static final UUID ID_KNOCKBACK = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee02");
    private static final UUID ID_SPEED = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee03");
    private static final UUID ID_ATTACK_SPEED = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee04");
    private static final UUID ID_BLOCK_REACH = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee05");
    private static final UUID ID_ENTITY_REACH = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee06");
    private static final UUID ID_CRIT_RATE = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee07");
    private static final UUID ID_CRIT_DMG = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee08");
    private static final UUID ID_ATTACK_PCT = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee09");
    private static final UUID ID_HEALTH_PCT = UUID.fromString("1b2c3d4e-5f60-4711-8899-aabbccddee0a");

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        var map = com.google.common.collect.LinkedHashMultimap
                .<net.minecraft.world.entity.ai.attributes.Attribute,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
        var addition = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
        var multiplyBase = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE;

        // —— 加法区 ——
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_ARMOR, "covenant_armor", ATTRIBUTE_BONUS, addition));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.LUCK,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_LUCK, "covenant_luck", ATTRIBUTE_BONUS, addition));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_ATTACK, "covenant_attack", ATTRIBUTE_BONUS, addition));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_TOUGHNESS, "covenant_toughness", ATTRIBUTE_BONUS, addition));
        // 韧性 1.0 已经等于「完全免击退」
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_KNOCKBACK, "covenant_knockback", 1.0D, addition));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_SPEED, "covenant_speed", 0.0777D, addition));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_ATTACK_SPEED, "covenant_attack_speed", 7.77D, addition));
        // 方块 / 实体交互距离 +7（Forge 扩展属性）
        map.put(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(),
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_BLOCK_REACH, "covenant_block_reach", 7.0D, addition));
        map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_ENTITY_REACH, "covenant_entity_reach", 7.0D, addition));

        // —— 乘法区：攻/血 +77%（MULTIPLY_BASE 之间相加；独立乘区只留给材料档位）——
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_ATTACK_PCT, "covenant_attack_pct", 0.77D, multiplyBase));
        map.put(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ID_HEALTH_PCT, "covenant_health_pct", 0.77D, multiplyBase));

        // —— 可选兼容：装备系统的暴击属性（按 id 去查，没装那个模组时静默跳过）——
        putOptional(map, "l2damagetracker:crit_rate", ID_CRIT_RATE, "covenant_crit_rate", 0.77D, addition);
        putOptional(map, "l2damagetracker:crit_damage", ID_CRIT_DMG, "covenant_crit_dmg", 7.77D, addition);

        return map;
    }

    /** 按属性 ID 追加一条修饰符；属性不存在时静默跳过。 */
    private static void putOptional(
            com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier> map,
            String attributeId, UUID id, String name, double amount,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        try {
            var attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                    new net.minecraft.resources.ResourceLocation(attributeId));
            if (attribute != null) {
                map.put(attribute, new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, name, amount, operation));
            }
        } catch (Throwable ignored) {
            // 注册表未就绪或属性不存在，跳过即可
        }
    }

    // ==================== 名称：逐字渐变（天启七印那种质感） ====================

    private static final String DISPLAY_NAME = "圣约·原典";

    /** 渐变起点：金色。 */
    private static final int GRADIENT_FROM = 0xFFD700;
    /** 渐变终点：暖白。 */
    private static final int GRADIENT_TO = 0xFFFFF0;

    /**
     * 物品名逐字渐变：金色 (255,215,0) → 暖白 (255,255,240)。
     *
     * <p>用「组件级颜色」而不是 §x 转义：物品名的外层会被稀有度样式覆盖，
     * 只有子组件自己带 {@code TextColor} 才能稳定生效。
     */
    @Override
    public Component getName(ItemStack stack) {
        int length = DISPLAY_NAME.length();
        net.minecraft.network.chat.MutableComponent root = Component.empty();
        for (int i = 0; i < length; i++) {
            float t = length <= 1 ? 0.0F : i / (float) (length - 1);
            int color = lerp(GRADIENT_FROM, GRADIENT_TO, t);
            root.append(Component.literal(String.valueOf(DISPLAY_NAME.charAt(i)))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(color))
                            .withBold(true)));
        }
        return root;
    }

    /** 按比例混合两个 RGB 颜色。 */
    private static int lerp(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int digit(int value, int shift) {
        return (value >> shift) & 0xF;
    }

    // ==================== 文案：十二权柄经文 ====================

    /** 十二条权的完整描述（客户端构建，依赖语言文件）。 */
    public static List<Component> buildDescription() {
        List<Component> list = new ArrayList<>();
        for (int i = 1; i <= POWER_COUNT; i++) {
            String base = "tooltip.yanscriptures.covenant_codex.power." + i;
            list.add(Component.translatable(base + ".name"));
            list.add(Component.translatable(base + ".line1"));
            list.add(Component.translatable(base + ".line2"));
            list.add(Component.empty());
        }
        return list;
    }

    // ==================== 文案：数值与机制明细 ====================

    /** 全部数值与机制（Shift + Alt 视图）。 */
    public static List<Component> buildDetail() {
        List<Component> list = new ArrayList<>();
        line(list, "§6§l▍ 典章·操作");
        line(list, "§7 K　沿视线脱困（自动判定已关闭，避免误触）");
        line(list, "§7 Z　日影停住：7 秒 · 半径 64 · 冷却 30 秒");
        line(list, "§7 J　七碗倾倒：每刻 × 12 种伤害类型 · 半径 16 · 持续 7 秒 · 冷却 30 秒");
        line(list, "§7 　　单次伤害 777 + 面板攻击 × 7.77，另计暴击（77% × 7.77）");
        line(list, "§7 Shift + 右键　对生物赐福（全部有益效果 七级）");
        line(list, "§7 Alt + 右键　　对生物降灾（全部负面效果 七级）");
        line(list, "");

        line(list, "§6§l▍ 典章·防御");
        line(list, "§7 特殊生命 777：受击先扣它，静止 3 秒后每秒回复 1");
        line(list, "§7 免疫负面效果（药水效果）");
        line(list, "§7 免疫基本伤害：火焰 · 摔落 · 溺水 · 冰冻 · 雷击 · 爆炸");
        line(list, "§7 免疫禁疗：治疗被取消时重新放行");
        line(list, "§7 免疫直接改血：血量下降会被原样拉回");
        line(list, "§7 反伤 777%：有来源反给攻击者，无来源反给 16 格内全部敌人");
        line(list, "§7 无敌帧 7 秒　创造飞行　永久复活");
        line(list, "");

        line(list, "§6§l▍ 典章·权能");
        line(list, "§7 万邦止戈：生物不再锁定你；你打过的会还手 10 秒");
        line(list, "§7 不死之约：生命永不低于 1，每秒回复最大生命 7%");
        line(list, "§7 申命之赐：全部有益效果维持七级，每 20 刻刷新");
        line(list, "§7 万军之约：每击杀 +1 攻击 / +1 生命上限，上限 777 次");
        line(list, "§7 圣所：16 格内同伴受到的伤害由你承担 77%");
        line(list, "§7 云柱火柱：按 K 沿视线传送到 8~16 格外的安全落点");
        line(list, "§7 五饼二鱼：击杀后展开该生物的全部掉落，数量 ×2（上限 256 件）");
        line(list, "");

        line(list, "§8按住 [Shift] 可查看十二权柄经文");
        return list;
    }

    private static void line(List<Component> list, String text) {
        list.add(text.isEmpty() ? Component.empty() : Component.literal(text));
    }

    // ==================== 翻页辅助 ====================

    /** 当前视图的总行数。 */
    public static int totalLines(boolean detail) {
        return detail ? buildDetail().size() : TOTAL_LINES;
    }

    /** 当前视图每屏行数。 */
    public static int pageSize(boolean detail) {
        return detail ? DETAIL_PAGE_SIZE : PAGE_SIZE;
    }

    /** 当前视图的最大滚轮偏移。 */
    public static int maxScroll(boolean detail) {
        return Math.max(0, totalLines(detail) - pageSize(detail));
    }

    // ==================== 提示 ====================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();

        if (!shift) {
            appendSummary(tooltip);
            return;
        }

        boolean detail = alt;
        List<Component> lines = detail ? buildDetail() : buildDescription();
        int size = pageSize(detail);
        int maxOffset = Math.max(0, lines.size() - size);
        int offset = Math.min(scrollOffset, maxOffset);

        tooltip.add(Component.empty());
        tooltip.add(Component.literal(detail
                        ? "§6§l✦  圣约·原典 · 典章  ✦"
                        : "§6§l✦  圣约·原典 · 十二权柄  ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.empty());

        int end = Math.min(lines.size(), offset + size);
        for (int i = offset; i < end; i++) {
            tooltip.add(lines.get(i));
        }

        tooltip.add(Component.empty());
        int page = offset / size + 1;
        int pages = maxOffset / size + 1;
        tooltip.add(Component.literal("§8第 " + page + " / " + pages + " 屏　·　滚轮翻阅")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendSummary(List<Component> tooltip) {
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6§l✦  圣 约 · 原 典  ✦"));
        tooltip.add(Component.literal("§7旧约已成，新约自血与火中订立；十诫归一，七印俱开。"));
        tooltip.add(Component.literal("§8「我是阿尔法，我是欧米伽；是初，是终。」")
                .withStyle(ChatFormatting.ITALIC));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§e[Shift] §7权柄经文　§e[Shift+Alt] §7完整书页"));
        tooltip.add(Component.literal("§8K 脱困 · Z 日影停住 · J 七碗倾倒 · F 飞行"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 主动效果都走按键（Z / J / K）与右键实体（赐福 / 降灾），物品本身不需要右键
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
