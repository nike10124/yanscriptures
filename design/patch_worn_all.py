# -*- coding: utf-8 -*-
"""全属性行（三条相加）+ 三条各自属性行；JEI 信息页补「用途」。"""
from pathlib import Path

# ---------- 1. 加成：全属性 = 三条相加，再各自给一项 ----------
p = Path("src/main/java/com/example/examplemod/MaterialBonus.java")
s = p.read_text(encoding="utf-8")

old = """    /**
     * 四项属性各自的实际加成（不含别的书）：<b>每条材料线只加到它自己掷中的那一项</b>，
     * 三条线互不影响（作者 m08693：三种材料按三个统计，换一条线只改那一项）。
     *
     * @return 顺序同 {@link #ATTR_KEYS}
     */
    public static double[] perAttribute(ItemStack stack) {
        int[] tiers = tiers(stack);
        String[] attrs = attrs(stack);
        double[] out = {0.0D, 0.0D, 0.0D, 0.0D};
        for (int line = 0; line < 3; line++) {
            out[indexOf(attrs[line])] += percent(tiers[line]);
        }
        return out;
    }"""
new = """    /**
     * 四项属性各自的实际加成（不含别的书）：三条线相加给一项「全属性」，
     * 每条线再把自己那一份加到它掷中的那一项上（作者 m08876：全属性用三条相加表示）。
     *
     * @return 顺序同 {@link #ATTR_KEYS}
     */
    public static double[] perAttribute(ItemStack stack) {
        int[] tiers = tiers(stack);
        String[] attrs = attrs(stack);
        double total = totalPercent(stack);
        double[] out = {total, total, total, total};
        for (int line = 0; line < 3; line++) {
            out[indexOf(attrs[line])] += percent(tiers[line]);
        }
        return out;
    }"""
assert old in s
s = s.replace(old, new)

old = """        double[] perAttr = perAttribute(stack);
        if (perAttr[0] <= 0.0D && perAttr[1] <= 0.0D && perAttr[2] <= 0.0D && perAttr[3] <= 0.0D) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.yanscriptures.grade.none")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                .withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < ATTR_KEYS.length; i++) {
            double pct = perAttr[i] * 100.0D;
            if (pct <= 0.0D) {
                continue;
            }
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.line",
                            fmt(pct), Component.translatable("attribute.name.generic." + fullAttrKey(ATTR_KEYS[i])))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }"""
new = """        double total = totalPercent(stack);
        if (total <= 0.0D) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.yanscriptures.grade.none")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.head")
                .withStyle(ChatFormatting.GOLD));

        // 先一行「全属性」= 三条相加，再逐条列出各自掷中的那一项
        tooltip.add(Component.translatable("tooltip.yanscriptures.worn.all", fmt(total * 100.0D))
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        int[] tiers = tiers(stack);
        String[] attrs = attrs(stack);
        for (int line = 0; line < 3; line++) {
            double pct = percent(tiers[line]) * 100.0D;
            if (pct <= 0.0D) {
                continue;
            }
            String key = safeAttr(attrs[line]);
            tooltip.add(Component.translatable("tooltip.yanscriptures.worn.line",
                            fmt(pct), Component.translatable("attribute.name.generic." + fullAttrKey(key)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }"""
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("tooltip ok")

# ---------- 2. JEI 信息页：只写用途 ----------
p = Path("src/main/java/com/example/examplemod/compat/JeiCompat.java")
s = p.read_text(encoding="utf-8")

anchor = "    /** 十张残卷：条件 + 那句「先看见自己的罪」。 */"
addition = """    /**
     * 空书卷 / 著书笔：信息页只写<b>用途</b>（作者 m08876）。
     * 合成方式按 <kbd>R</kbd> 就能看到，不在这里重复。
     */
    private static void registerUsageInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                List.of(new ItemStack(com.example.examplemod.Materials.BLANK_SCROLL.get())),
                Component.translatable("jei.yanscriptures.blank_scroll.use")
                        .withStyle(s -> s.withColor(0x404040)));

        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.WRITING_QUILL.get())),
                Component.translatable("jei.yanscriptures.writing_quill.use")
                        .withStyle(s -> s.withColor(0x404040)));
    }

"""
assert anchor in s
s = s.replace(anchor, addition + anchor)

if "registerUsageInfo(registration);" not in s:
    s = s.replace("        registerScrollInfo(registration);",
                  "        registerUsageInfo(registration);\n        registerScrollInfo(registration);")
p.write_text(s, encoding="utf-8")
print("jei usage ok")
