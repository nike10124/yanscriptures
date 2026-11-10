# -*- coding: utf-8 -*-
"""RecipeAudit：加一行「档位加成自查」，把全属性与三条线各自的数值打进日志。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/RecipeAudit.java")
s = p.read_text(encoding="utf-8")

old = """        bookWritingSelfCheck();
    }"""
new = """        bookWritingSelfCheck();
        tierBonusSelfCheck();
    }

    /**
     * 档位加成本自查：造一本 Ⅳ/Ⅲ/Ⅲ、三条线分别掷中攻击/生命/护甲的书，
     * 把 {@link MaterialBonus#perAttribute} 的四项数值写进日志。
     * 期望：全属性 11%，攻击力再多 6%、生命上限 2.5%、护甲 2.5%。
     */
    private static void tierBonusSelfCheck() {
        ItemStack sample = new ItemStack(ExampleMod.BOOK_DAO_DE_JING.get());
        MaterialBonus.stamp(sample, new int[]{4, 3, 3}, new String[]{"attack", "health", "armor"});
        double[] perAttr = MaterialBonus.perAttribute(sample);
        LOG.info("档位加成自查：Ⅳ/Ⅲ/Ⅲ（攻击/生命/护甲）→ 攻击 {}%、生命 {}%、护甲 {}%、移速 {}%（全属性 {}%）",
                perAttr[0] * 100.0D, perAttr[1] * 100.0D, perAttr[2] * 100.0D, perAttr[3] * 100.0D,
                MaterialBonus.totalPercent(sample) * 100.0D);
    }"""
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding="utf-8")
print("audit tier ok")
