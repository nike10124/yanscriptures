# -*- coding: utf-8 -*-
"""同步文档：选择按配方 id 记忆；底部提示按宽度自适应。"""
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
P = r"D:\MCModDev\forge-mdk\docs\模组内容总览.md"
text = open(P, encoding="utf-8").read()
old = "- 选了第几本会被记住，退出重进也还在。"
new = ("- 选中的那本按**配方 id** 记住（存在方块实体的 `SelectedId` 里），关界面、拆下来再放、换界面重开都还是那一本；\n"
       "  候选列表重算后先按 id 找回它，找不到（比如换成低档材料后这本书不再可写）才退回第一条。")
if old not in text:
    print("MISS-1")
text = text.replace(old, new)

old2 = ("- 左上角两行：上面是书名（按 52 像素截断成「《一千零一夜…》」），下面只在缺东西时写「还需 N 项」（红字），")
new2 = ("- 材料池列表底下有一行淡蓝色小字「材料齐全后，使用空书卷右键著书台获取书籍」，字号按可用宽度自动缩放（写满 168 像素、贴着右边框）。\n"
        "- 左上角两行：上面是书名（按 52 像素截断成「《一千零一夜…》」），下面只在缺东西时写「还需 N 项」（红字），")
if old2 not in text:
    print("MISS-2")
text = text.replace(old2, new2)
open(P, "w", encoding="utf-8", newline="\n").write(text)
print("done")
