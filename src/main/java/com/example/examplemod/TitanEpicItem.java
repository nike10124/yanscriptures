package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class TitanEpicItem extends Item {
    private final String titanType;
    private final long cooldownMillis;

    public TitanEpicItem(Properties properties, String titanType, long cooldownMillis) {
        super(properties);
        this.titanType = titanType;
        this.cooldownMillis = cooldownMillis;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        long now = System.currentTimeMillis();
        long cdEnd = player.getPersistentData().getLong("TitanEpic_" + titanType);

        // 检查冷却
        if (now < cdEnd) {
            long remaining = (cdEnd - now) / 1000;
            player.displayClientMessage(Component.translatable("message.yanscriptures.cooldown.seconds", remaining), true);
            return InteractionResultHolder.fail(stack);
        }

        // 根据类型触发不同效果
        switch (titanType) {
            case "fate":
                // 命运：因果逆转
                player.getPersistentData().putBoolean("Fate_Active", true);
                player.getPersistentData().putLong("Fate_EndTime", now + 30000); // 30秒后结束
                player.displayClientMessage(Component.translatable("message.yanscriptures.titan.fate"), true);
                break;
            case "creation":
                // 创生：生息之环
                player.getPersistentData().putBoolean("Creation_Active", true);
                player.getPersistentData().putBoolean("Creation_Hurt", false);
                player.getPersistentData().putBoolean("Creation_LowHealth", false);
                player.getPersistentData().putBoolean("Creation_Phase1Done", false);
                player.getPersistentData().putLong("Creation_EndTime", now + 30000); // 30秒后彻底结束
                player.getPersistentData().putLong("Creation_Phase1End", now + 20000); // 20秒后进入判定阶段

                // 立即给予生命回复 II（持续20秒）
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 400, 1, true, false));
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.yanscriptures.titan.creation"), true);
                break;
            case "pillar":
                // 支柱：地脉锚定
                player.getPersistentData().putBoolean("Pillar_Active", true);
                player.getPersistentData().putLong("Pillar_EndTime", now + 10000); // 10秒后结束

                // 施加缓慢255级（彻底无法移动）
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 255, true, false));


                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.yanscriptures.titan.pillar"), true);
                break;
            case "calamity":
                // 灾厄：祸乱之种
                player.getPersistentData().putBoolean("Calamity_Active", true);
                player.getPersistentData().putLong("Calamity_EndTime", now + 15000); // 15秒后结束
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.yanscriptures.titan.calamity"), true);
                break;
            case "ego":
                // 真我：自我觉醒
                player.getPersistentData().putBoolean("Ego_Active", true);
                player.getPersistentData().putLong("Ego_EndTime", now + 20000); // 20秒后结束
                player.getPersistentData().putLong("Ego_StartTime", now);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.yanscriptures.titan.ego"), true);
                break;

        }

        // 记录冷却
        player.getPersistentData().putLong("TitanEpic_" + titanType, now + cooldownMillis);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.empty());

        // 根据物品类型显示不同的描述（统一采用灰色描述 + 蓝色冷却）
        if ("fate".equals(titanType)) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_fate.desc")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_fate.cooldown")
                    .withStyle(net.minecraft.ChatFormatting.BLUE));
        } else if ("creation".equals(titanType)) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_creation.desc1")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)); //  改为灰色
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_creation.desc2")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_creation.desc3")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_creation.cooldown")
                    .withStyle(net.minecraft.ChatFormatting.BLUE));
        } else if ("pillar".equals(titanType)) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_pillar.desc")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_pillar.cooldown")
                    .withStyle(net.minecraft.ChatFormatting.BLUE)); //  补上冷却提示
        } else if ("calamity".equals(titanType)) {
            //  灾厄专属多行描述
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_calamity.desc1")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_calamity.desc2")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_calamity.desc3")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_calamity.cooldown")
                    .withStyle(net.minecraft.ChatFormatting.BLUE));
        } else if ("ego".equals(titanType)) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_ego.desc1")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_ego.desc2")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_ego.desc3")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.yanscriptures.epic_ego.cooldown")
                    .withStyle(net.minecraft.ChatFormatting.BLUE));
        }
    }
}