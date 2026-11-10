# -*- coding: utf-8 -*-
"""JEI 信息页只留「作用」；著书台选书加启动自查。"""
import json
from pathlib import Path

# ---------- 1. 删掉「合成方式」与「现在有哪几本」两类信息页 ----------
p = Path("src/main/java/com/example/examplemod/compat/JeiCompat.java")
src = p.read_text(encoding="utf-8")

start = src.index("    /**\n     * 空书卷 / 著书笔：它们在 JEI 里本来就有工作台配方")
end = src.index("    /** 十张残卷：条件 + 那句「先看见自己的罪」。 */")
src = src[:start] + src[end:]

start = src.index("    /** 九本名著：目前只有「获取方式待定」一句")
end = src.index("    private static net.minecraft.world.item.Item classicItem(ClassicBook book) {")
src = src[:start] + src[end:]

src = src.replace("        registerStarterRecipeInfo(registration);\n", "")
src = src.replace("        registerClassicInfo(registration);\n", "")
p.write_text(src, encoding="utf-8")
print("jei trimmed")

# ---------- 2. 语言文件清理 ----------
p = Path("src/main/resources/assets/yanscriptures/lang/zh_cn.json")
data = json.loads(p.read_text(encoding="utf-8"))
for key in ("jei.yanscriptures.blank_scroll.info1", "jei.yanscriptures.blank_scroll.info2",
            "jei.yanscriptures.writing_quill.info1", "jei.yanscriptures.writing_quill.info2",
            "jei.yanscriptures.classic.pending"):
    data.pop(key, None)
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("lang keys:", len(data))
