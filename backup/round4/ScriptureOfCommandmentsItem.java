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

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) { return true; }

    // ==================== 超级绑定诅咒 ====================
    // 只有创造模式能摘下；生存/冒险/旁观全部锁死
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        Entity entity = slotContext.entity();
        return entity instanceof Player player && player.isCreative();
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
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§c十诫刻于此卷。守约者受祝福，违逆者受诅咒。").withStyle(ChatFormatting.RED));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§e按住 [Shift] 查看十诫").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("§8（按住 Shift + 滚轮 可上下翻阅）").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("§8（按住 Shift + Alt 可显示触发条件与诅咒）").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("§4§l此卷一旦戴上，凡人之躯再难摘下。").withStyle(ChatFormatting.DARK_RED));
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        tooltip.add(Component.empty());

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
        List<Component> list = new ArrayList<>();
        boolean alt = Screen.hasAltDown();

        // ========== 1. 独一 ==========
        list.add(Component.literal("§7【第一诫 · 独一】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「除我以外，你不可有别的神」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：经验获取 +100%，全属性 +10%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：装备任何非本模组饰品").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：攻击力 -10%，每件非本模组饰品额外 -8%（最多 -40%）").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_1")) {
                list.add(Component.literal("§4诅咒：攻击力 -10%，每件非本模组饰品额外 -8%（最多 -40%）").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：经验获取 +100%，全属性 +10%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 2. 无像 ==========
        list.add(Component.literal("§7【第二诫 · 无像】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可为自己雕刻偶像」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：最大生命值 +10%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：使用任何图腾类物品").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：图腾类物品完全失效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_2")) {
                list.add(Component.literal("§4诅咒：图腾类物品完全失效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：最大生命值 +10%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 3. 圣名 ==========
        list.add(Component.literal("§7【第三诫 · 圣名】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可妄称耶和华你 神的名」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：攻击力 +15%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：使用附魔台/铁砧/砂轮").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：攻击力 -15%，附魔功能失效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_3")) {
                list.add(Component.literal("§4诅咒：攻击力 -15%，附魔功能失效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：攻击力 +15%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 4. 安息 ==========
        list.add(Component.literal("§7【第四诫 · 安息】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「当记念安息日，守为圣日」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：移动速度 +15%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：游戏日结束时累计静止不足 60 秒").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：缓慢 III + 挖掘疲劳 III 持续整天").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_4")) {
                list.add(Component.literal("§4诅咒：缓慢 III + 挖掘疲劳 III 持续整天").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：移动速度 +15%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 5. 孝道 ==========
        list.add(Component.literal("§7【第五诫 · 孝道】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「当孝敬父母」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：村民交易折扣，铁傀儡保护你").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：攻击友善生物").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：无法伤害友善生物，自身缓慢 III").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_5")) {
                list.add(Component.literal("§4诅咒：无法伤害友善生物，自身缓慢 III").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：村民交易折扣，铁傀儡保护你").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 6. 不杀 ==========
        list.add(Component.literal("§7【第六诫 · 不杀】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可杀人」").withStyle(ChatFormatting.GRAY));
        int bloodGuilt = tag.getInt("BloodGuilt_6");
        if (alt) {
            list.add(Component.literal("§b祝福：受到的伤害降低 20%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：首次击杀生物 50% 概率触发，之后每次击杀叠加 1 层").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：血罪每层 -7% 生命与攻击（上限 7 层），非玩家伤害无法伤害任何生物").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_6")) {
                list.add(Component.literal("§4诅咒：血罪 " + bloodGuilt + " 层，每层 -7% 生命与攻击").withStyle(ChatFormatting.DARK_RED));
                list.add(Component.literal("§4诅咒：非玩家伤害无法伤害任何生物").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：受到的伤害降低 20%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 7. 忠贞 ==========
        list.add(Component.literal("§7【第七诫 · 忠贞】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可奸淫」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：中立生物永不攻击你，驯服率 +50%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：攻击或猎杀中立生物").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：中立生物主动攻击你，无法驯服").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_7")) {
                list.add(Component.literal("§4诅咒：中立生物主动攻击你，无法驯服").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：中立生物永不攻击你，驯服率 +50%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 8. 不盗 ==========
        list.add(Component.literal("§7【第八诫 · 不盗】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可偷盗」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：幸运 +2").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：从容器中取出物品").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：物品 30% 概率消失，抢夺无效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_8")) {
                list.add(Component.literal("§4诅咒：物品 30% 概率消失，抢夺无效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：幸运 +2").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 9. 真言 ==========
        list.add(Component.literal("§7【第九诫 · 真言】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可作假见证」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：攻击速度 +15%").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：聊天栏发消息、告示牌、书与笔成书、铁砧重命名、命名牌").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：每次攻击 20% 概率落空").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_9")) {
                list.add(Component.literal("§4诅咒：每次攻击 20% 概率落空").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：攻击速度 +15%").withStyle(ChatFormatting.AQUA));
            }
        }

        // ========== 10. 知足 ==========
        list.add(Component.literal("§7【第十诫 · 知足】").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("§7「不可贪恋人的房屋并他一切所有的」").withStyle(ChatFormatting.GRAY));
        if (alt) {
            list.add(Component.literal("§b祝福：挖掘速度 +20%，时运 +1").withStyle(ChatFormatting.AQUA));
            list.add(Component.literal("§8触发：挖掘矿石类方块").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.literal("§4诅咒：矿石 25% 概率不掉落，时运无效").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (tag.getBoolean("Curse_10")) {
                list.add(Component.literal("§4诅咒：矿石 25% 概率不掉落，时运无效").withStyle(ChatFormatting.DARK_RED));
            } else {
                list.add(Component.literal("§b祝福：挖掘速度 +20%，时运 +1").withStyle(ChatFormatting.AQUA));
            }
        }

        return list;
    }
}