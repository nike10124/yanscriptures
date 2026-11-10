package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 刻蚀残卷 —— 《圣典·戒律》十诫系统的收集物。
 *
 * <p>十张残卷各自对应一条诫，另有一张不属十诫的「启示残卷」。
 * 文案放在语言文件里（<code>tooltip.examplemod.scroll.&lt;key&gt;.verse</code> /
 * <code>.desc</code>），所以中文、英文可以分别翻译。</p>
 *
 * <p><b>当前状态（2026-10-22）：</b>只做注册与文案，<b>尚未接入诅咒反转机制</b>。
 * tooltip 里刻意只写经文与诫条出处，不写任何效果承诺 —— 等
 * {@link CommandmentsHandler} 的三态（祝福 / 诅咒 / 刻蚀）逻辑落地后再补效果行。</p>
 */
public class ScriptureScrollItem extends Item {

    /** 语言键后缀，例如 {@code "shema"} → {@code tooltip.examplemod.scroll.shema.*} */
    private final String key;

    public ScriptureScrollItem(Properties properties, String key) {
        super(properties);
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.empty());

        // 经文（灰色斜体）
        tooltip.add(Component.translatable("tooltip.examplemod.scroll." + this.key + ".verse")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        // 对应诫条 / 简要说明（暗灰）
        tooltip.add(Component.translatable("tooltip.examplemod.scroll." + this.key + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
