package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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

public class ScriptureOfCommandmentsItem extends Item implements ICurioItem {
    public ScriptureOfCommandmentsItem(Properties properties) {
        super(properties);
    }

    // ==================== 名字渐变（2026-11-05 作者要求「统一下」）====================
    // 大书页里《十诫》的标题用的是暗红 → 暖金；名字本身如果只靠稀有度就会变成
    // 稀有的青色，两边对不上。所以这里和《如我所书》《圣约·原典》一样自己算渐变名，
    // 大书页的标题色也直接引这两个常量（不再各写一份字面量）。
    public static final int GRADIENT_FROM = 0xFFC03A2A; // 暗红
    public static final int GRADIENT_TO   = 0xFFF0C060; // 暖金

    @Override
    public Component getName(ItemStack stack) {
        String plain = Component.translatable(this.getDescriptionId(stack)).getString();
        int length = plain.length();
        net.minecraft.network.chat.MutableComponent root = Component.empty();
        for (int i = 0; i < length; i++) {
            float t = length <= 1 ? 0.0F : i / (float) (length - 1);
            int color = lerpColor(GRADIENT_FROM, GRADIENT_TO, t);
            root.append(Component.literal(String.valueOf(plain.charAt(i)))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(color))
                            .withBold(true)));
        }
        return root;
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) { return true; }

    // ==================== 超级绑定诅咒 ====================
    // 默认锁死：只有创造模式能摘下。
    // [2026-10-23] 新增解锁条件：十条戒律全部「补全」（反转）后，圣典自动失去束缚，普通玩家也能摘下。
    // 这为「圣约·原典」那条路线留出了入口 —— 旧约完成，才能立新约。
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        Entity entity = slotContext.entity();
        if (entity instanceof Player player && player.isCreative()) return true;
        return allCommandmentsCompleted(stack);
    }

    /** 十条戒律是否全部补全（即 10 个 {@code Completed_<n>} 都为真）。 */
    public static boolean allCommandmentsCompleted(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return false;
        for (int i = 1; i <= 10; i++) {
            if (!tag.getBoolean("Completed_" + i)) return false;
        }
        return true;
    }

    // ==================== 滚轮翻页用的静态字段 ====================
    public static int scrollOffset = 0;
    public static final int PAGE_SIZE = 5;

    public static int getTotalLines(boolean isAltDown) {
        return isAltDown ? 50 : 30;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (scrollOffset < 0) scrollOffset = 0;

        boolean isShiftDown = Screen.hasShiftDown();

        if (!isShiftDown) {
            tooltip.add(Component.literal("§c十诫刻于此卷。守约者受祝福，违逆者受诅咒。").withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal("§e[Shift] §7查看十诫　§e[Shift+Alt] §7完整书页"));
            tooltip.add(Component.literal("§4§l此卷一旦戴上，凡人之躯再难摘下。").withStyle(ChatFormatting.DARK_RED));
            if (allCommandmentsCompleted(stack)) {
                tooltip.add(Component.literal("§d§l十诫已全部补全 —— 束缚已解，此卷可摘下。").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.literal("§8（十条戒律全部补全后，束缚自解）").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();

        List<Component> fullDescription = buildFullDescription(tag);
        int maxOffset = Math.max(0, fullDescription.size() - PAGE_SIZE);
        int offset = Math.min(scrollOffset, maxOffset);
        int end = Math.min(fullDescription.size(), offset + PAGE_SIZE);

        for (int i = offset; i < end; i++) {
            tooltip.add(fullDescription.get(i));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal(String.format("§8[滚轮] 上下翻阅 §7%d-%d / %d",
                offset + 1, end, fullDescription.size())));
    }

    // ==================== 十诫构建 ====================
    private static List<Component> buildFullDescription(CompoundTag tag) {
        return buildFullDescription(tag, Screen.hasAltDown());
    }

    /** 大书页视图：强制显示全部触发条件与诅咒，不看当前按着什么键。 */
    public static List<Component> fullLines(ItemStack stack) {
        return buildFullDescription(stack.getOrCreateTag(), true);
    }

    private static List<Component> buildFullDescription(CompoundTag tag, boolean alt) {
        List<Component> list = new ArrayList<>();

        // ==================== [补全 2026-10-22] 顶部汇总 ====================
        // 已补全的诫在下面各条里会显示祝福行（因为 Curse_n 已被移除），
        // 单看正文分不清"还没违逆"和"已经补全"，所以在这里统一标出来。
        StringBuilder completed = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (tag.getBoolean("Completed_" + i)) {
                if (completed.length() > 0) completed.append("、");
                completed.append(i);
            }
        }
        if (completed.length() > 0) {
            list.add(Component.literal("§5§l◆ 已补全：第 " + completed + " 诫")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            list.add(Component.literal("§7补全之诫不再降罪，祝福转为强化版。")
                    .withStyle(ChatFormatting.GRAY));
            list.add(Component.empty());
        }

        // ========== 1. 独一 ==========
        list.add(Component.literal("§7【第一诫 · 独一】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「除我以外，你不可有别的神」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：经验获取 +100%，全属性 +10%（补全：经验 ×3，全属性 +15%）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：装备任何非本模组饰品").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：攻击力 -10%，每件非本模组饰品额外 -8%（最多 -40%）").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_1")) {
                list.add(Component.literal("§4诅咒：攻击力 -10%，每件非本模组饰品额外 -8%（最多 -40%）").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：经验获取 +100%，全属性 +10%（补全：经验 ×3，全属性 +15%）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 2. 无像 ==========
        list.add(Component.literal("§7【第二诫 · 无像】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可为自己雕刻偶像」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：最大生命值 +10%（补全 +15%）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：使用任何图腾类物品").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：图腾类物品完全失效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_2")) {
                list.add(Component.literal("§4诅咒：图腾类物品完全失效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：最大生命值 +10%（补全 +15%）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 3. 圣名 ==========
        list.add(Component.literal("§7【第三诫 · 圣名】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可妄称耶和华你 神的名」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：攻击力 +15%（补全 +20%，并解封铁砧与砂轮）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：使用附魔台/铁砧/砂轮").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：攻击力 -15%，附魔功能失效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_3")) {
                list.add(Component.literal("§4诅咒：攻击力 -15%，附魔功能失效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：攻击力 +15%（补全 +20%，并解封铁砧与砂轮）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 4. 安息 ==========
        list.add(Component.literal("§7【第四诫 · 安息】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「当记念安息日，守为圣日」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：移动速度 +15%（补全 +20%）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：游戏日结束时累计静止不足 60 秒").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：缓慢 III + 挖掘疲劳 III 持续整天").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_4")) {
                list.add(Component.literal("§4诅咒：缓慢 III + 挖掘疲劳 III 持续整天").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：移动速度 +15%（补全 +20%）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 5. 孝道 ==========
        list.add(Component.literal("§7【第五诫 · 孝道】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「当孝敬父母」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：村民交易折扣，铁傀儡保护你（补全后可伤害友善生物）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：攻击友善生物").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：无法伤害友善生物，自身缓慢 III").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_5")) {
                list.add(Component.literal("§4诅咒：无法伤害友善生物，自身缓慢 III").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：村民交易折扣，铁傀儡保护你（补全后可伤害友善生物）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 6. 不杀 ==========
        list.add(Component.literal("§7【第六诫 · 不杀】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可杀人」").withStyle(ChatFormatting.GRAY));
        int bloodGuilt = tag.getInt("BloodGuilt_6");
        if (alt) {
            list.add(Component.literal("§b祝福：受到的伤害降低 20%（补全 25%，并清零血罪）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：首次击杀生物 50% 概率触发，之后每次击杀叠加 1 层").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：血罪每层 -7% 生命与攻击（上限 7 层），非玩家伤害无法伤害任何生物").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_6")) {
                list.add(Component.literal("§4诅咒：血罪 " + bloodGuilt + " 层，每层 -7% 生命与攻击").withStyle(ChatFormatting.DARK_RED));
                list.add(Component.literal("§4诅咒：非玩家伤害无法伤害任何生物").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：受到的伤害降低 20%（补全 25%，并清零血罪）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 7. 忠贞 ==========
        list.add(Component.literal("§7【第七诫 · 忠贞】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可奸淫」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：中立生物永不攻击你，驯服率 +50%（补全 +100%）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：攻击或猎杀中立生物").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：中立生物主动攻击你，无法驯服").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_7")) {
                list.add(Component.literal("§4诅咒：中立生物主动攻击你，无法驯服").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：中立生物永不攻击你，驯服率 +50%（补全 +100%）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 8. 不盗 ==========
        list.add(Component.literal("§7【第八诫 · 不盗】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可偷盗」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：幸运 +2（补全 +3）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：从野生的战利品容器里取出物品（自己摆放的箱子不算）").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：物品 30% 概率消失，抢夺无效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_8")) {
                list.add(Component.literal("§4诅咒：物品 30% 概率消失，抢夺无效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：幸运 +2（补全 +3）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 9. 真言 ==========
        list.add(Component.literal("§7【第九诫 · 真言】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可作假见证」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：攻击速度 +15%（补全 +20%）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：聊天栏发消息、告示牌、书与笔成书、铁砧重命名、命名牌").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：每次攻击 20% 概率落空").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_9")) {
                list.add(Component.literal("§4诅咒：每次攻击 20% 概率落空").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：攻击速度 +15%（补全 +20%）").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 10. 知足 ==========
        list.add(Component.literal("§7【第十诫 · 知足】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可贪恋人的房屋并他一切所有的」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：挖掘速度 +20%，时运 +1（补全 +30%、时运 +2）").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：挖掘矿石类方块").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：矿石 25% 概率不掉落，时运无效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_10")) {
                list.add(Component.literal("§4诅咒：矿石 25% 概率不掉落，时运无效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：挖掘速度 +20%，时运 +1（补全 +30%、时运 +2）").withStyle(ChatFormatting.AQUA));
            }
        }

        return list;
    }
}