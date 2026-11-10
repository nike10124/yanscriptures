package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 刻蚀残卷 —— 《圣典·戒律》十诫系统的消耗品。
 *
 * <p>右键使用：把残卷对应那条诫「补全」——该诫的诅咒被永久移除，祝福转为强化版，
 * 而且此后即使再次做出违逆行为也不会降罪。</p>
 *
 * <p>状态存放在圣典的物品 NBT 里：
 * <ul>
 *   <li>{@code Curse_<n>} —— 已违逆（诅咒生效）</li>
 *   <li>{@code Completed_<n>} —— 已补全（强化祝福，永久免罪）</li>
 *   <li>两者都没有 —— 未违逆（普通祝福）</li>
 * </ul>
 * 补全时会移除 {@code Curse_<n>}，因此 {@link CommandmentsHandler} 里所有"已违逆"分支
 * 会自动落到祝福分支；触发点另有 {@code canViolate()} 守卫，防止二次降罪。</p>
 *
 * <p>启示残卷的诫号为 0，不属于十诫，右键只会得到提示。</p>
 */
public class ScriptureScrollItem extends Item {

    /** 对应的诫号 1..10；启示残卷为 0。 */
    private final int commandment;

    /** 语言键后缀，例如 {@code "shema"} → {@code tooltip.yanscriptures.scroll.shema.*} */
    private final String key;

    public ScriptureScrollItem(Properties properties, String key, int commandment) {
        super(properties);
        this.key = key;
        this.commandment = commandment;
    }

    public String getKey() {
        return this.key;
    }

    public int getCommandment() {
        return this.commandment;
    }

    /**
     * 启示残卷的名字用深红色（2026-11-05 作者定）。
     *
     * <p>十张诫卷统一为「少见」稀有度（黄色），唯独启示残卷不指向任何一诫，
     * 作者要求名字用深红以作区分；组件级颜色会覆盖稀有度颜色，所以它注册时
     * 也不设稀有度（见 {@code ExampleMod.scroll(...)}）。</p>
     */
    private static final int REVELATION_COLOR = 0xAA0000;

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        if (this.commandment > 0) {
            return name;
        }
        return name.copy().withStyle(net.minecraft.network.chat.Style.EMPTY
                .withColor(net.minecraft.network.chat.TextColor.fromRgb(REVELATION_COLOR)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 判定全部在服务端做，避免客户端先行消耗
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // 启示残卷不指向任何一诫
        if (this.commandment <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.scroll.no_target"), true);
            return InteractionResultHolder.fail(stack);
        }

        ItemStack scripture = findScripture(player);
        if (scripture.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.scroll.no_scripture"), true);
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag tag = scripture.getOrCreateTag();
        int n = this.commandment;

        if (tag.getBoolean("Completed_" + n)) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.scroll.already", n), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!tag.getBoolean("Curse_" + n)) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.scroll.not_violated", n), true);
            return InteractionResultHolder.fail(stack);
        }

        // ==================== 补全 ====================
        tag.putBoolean("Completed_" + n, true);
        tag.remove("Curse_" + n);

        // 第六诫的特殊处理：血罪层数会持续压血量和攻击，补全后必须一起清掉
        if (n == 6) {
            tag.remove("BloodGuilt_6");
        }

        // 创造模式不消耗，方便调试
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(
                Component.translatable("message.yanscriptures.scroll.completed", n), true);

        return InteractionResultHolder.success(stack);
    }

    /** 从饰品栏找《圣典·戒律》。 */
    private static ItemStack findScripture(Player player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()))
                .map(slotResult -> slotResult.stack())
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        // 经文（灰色斜体）
        tooltip.add(Component.translatable("tooltip.yanscriptures.scroll." + this.key + ".verse")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        // 对应诫条 / 简要说明（暗灰）
        tooltip.add(Component.translatable("tooltip.yanscriptures.scroll." + this.key + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY));

        // 操作提示：只有十诫残卷才有"补全"的用法
        if (this.commandment > 0) {
            tooltip.add(Component.translatable("tooltip.yanscriptures.scroll.usage")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            // 启示残卷：预告终局。这是指向「圣约·原典」的唯一线索。
            boolean cleared = stack.getTag() != null && stack.getTag().getBoolean("TrialCleared");
            if (cleared) {
                // 通过试炼的那张：亮白色的宣告
                tooltip.add(Component.translatable("tooltip.yanscriptures.scroll.revelation.cleared")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            } else {
                // 普通的：暗红色，像血写的预告
                tooltip.add(Component.translatable("tooltip.yanscriptures.scroll.revelation.omens")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            }
        }
    }
}
