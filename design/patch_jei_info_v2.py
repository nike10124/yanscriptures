"""补 JEI 分类标题译文 + 著书台/寂灭之笺 的用途与来源信息页（2026-10-31）。"""

import json
from pathlib import Path

root = Path(".")

# ---------- 1) 语言文件 ----------
lang_path = root / "src/main/resources/assets/yanscriptures/lang/zh_cn.json"
lang = json.loads(lang_path.read_text(encoding="utf-8"))

lang["jei.yanscriptures.book_writing"] = "著书台"

lang["jei.yanscriptures.book_table.use"] = (
    "用途：写出一本书——书位放空书卷，三个选择格依次放纸、墨、线，"
    "材料池放下半部分要用的其他材料；用右侧上下小三角在三格能写出的书里挑一本，点「写」出书。"
)
lang["jei.yanscriptures.book_table.source"] = (
    "来源：手持著书笔（Shift + 右键）点在原版讲台上，讲台就变成著书台。"
)
lang["jei.yanscriptures.annihilated.use"] = (
    "用途：材料档位最高的纸（Ⅳ 档）。放进著书台当纸用，写出的书纸这一条线拿 +6%；"
    "也能在锻造台上当「纸」这一格的材料，把一本旧书的纸升到 Ⅳ 档。"
)
lang["jei.yanscriptures.annihilated.source"] = (
    "来源：把至邪之笺丢进岩浆，且岩浆所在竖列的正下方有已点亮的信标、光柱没被遮住；"
    "66% 成功变成寂灭之笺，失败则直接烧毁。"
)

lang_path.write_text(json.dumps(lang, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("lang keys:", len(lang))

# ---------- 2) JeiCompat：加两条信息页 ----------
jei_path = root / "src/main/java/com/example/examplemod/compat/JeiCompat.java"
src = jei_path.read_text(encoding="utf-8")

anchor = """        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.WRITING_QUILL.get())),
                Component.translatable("jei.yanscriptures.writing_quill.use")
                        .withStyle(s -> s.withColor(0x404040)));
    }"""

addition = """        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.WRITING_QUILL.get())),
                Component.translatable("jei.yanscriptures.writing_quill.use")
                        .withStyle(s -> s.withColor(0x404040)));

        // 著书台方块本身：用途 + 来源（作者 2026-10-31：它也缺一个信息页）
        registration.addItemStackInfo(
                List.of(new ItemStack(ExampleMod.BOOK_WRITING_TABLE_ITEM.get())),
                Component.translatable("jei.yanscriptures.book_table.use")
                        .withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.book_table.source")
                        .withStyle(s -> s.withColor(0x777777)));

        // 寂灭之笺：Ⅳ 档纸，来源是一个岩浆 + 信标的小仪式，物品本身只有一个名字，必须写清楚
        registration.addItemStackInfo(
                List.of(new ItemStack(com.example.examplemod.Materials.ANNIHILATED_PARCHMENT.get())),
                Component.translatable("jei.yanscriptures.annihilated.use")
                        .withStyle(s -> s.withColor(0x404040)),
                Component.translatable("jei.yanscriptures.annihilated.source")
                        .withStyle(s -> s.withColor(0x777777)));
    }"""

assert anchor in src, "锚点没找到"
src = src.replace(anchor, addition)
jei_path.write_text(src, encoding="utf-8")
print("JeiCompat ok")
