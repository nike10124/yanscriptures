# -*- coding: utf-8 -*-
"""补齐「当装备在书籍栏」区块的语言键。"""
import json
from pathlib import Path

p = Path("src/main/resources/assets/yanscriptures/lang/zh_cn.json")
data = json.loads(p.read_text(encoding="utf-8"))
data["tooltip.yanscriptures.worn.head"] = "当装备在书籍栏："
data["tooltip.yanscriptures.worn.line"] = "+%s%% %s"
data["tooltip.yanscriptures.worn.slot"] = "槽位：书"
# 旧的单块显示已不再使用
for key in ("tooltip.yanscriptures.grade.all", "tooltip.yanscriptures.grade.extra"):
    data.pop(key, None)
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("lang keys:", len(data))
