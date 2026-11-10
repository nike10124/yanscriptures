# -*- coding: utf-8 -*-
"""语言键：全属性行 + 两件物品的用途。"""
import json
from pathlib import Path

p = Path("src/main/resources/assets/yanscriptures/lang/zh_cn.json")
data = json.loads(p.read_text(encoding="utf-8"))
data["tooltip.yanscriptures.worn.all"] = "+%s%% 全属性（三条相加）"
data["jei.yanscriptures.blank_scroll.use"] = "用途：放到著书台上当「空白的书卷」，配合纸、墨、线写出一本书。"
data["jei.yanscriptures.writing_quill.use"] = "用途：手持它 Shift + 右键原版讲台，把讲台改造成著书台。"
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("lang keys:", len(data))
