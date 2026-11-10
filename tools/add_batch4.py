# -*- coding: utf-8 -*-
"""第四批九本名著：生成模型、给两个标签追加条目、补 lang 占位文案。"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BOOKS = [
    ("critique_of_pure_reason", "《纯粹理性批判》",
     "「有两样东西，越是反复思索，越让人心中充满敬畏：头顶的星空，与心中的道德律。」"),
    ("nicomachean_ethics", "《尼各马可伦理学》",
     "「德性来自习惯——我们反复做的事，造就了我们是什么样的人。」"),
    ("summa_theologica", "《神学大全》",
     "「信仰寻求理解。」"),
    ("wealth_of_nations", "《国富论》",
     "「我们的晚餐并非来自屠夫的仁慈，而是来自他们对自身利益的关切。」"),
    ("capital", "《资本论》",
     "「商品是天生的平等派。」"),
    ("inner_canon_of_the_yellow_emperor", "《黄帝内经》",
     "「上工治未病，不治已病。」"),
    ("one_thousand_and_one_nights", "《一千零一夜》",
     "「只要故事还没讲完，我就还不能死。」"),
    ("hamlet", "《哈姆雷特》",
     "「生存还是毁灭，这是一个值得考虑的问题。」"),
    ("the_count_of_monte_cristo", "《基督山伯爵》",
     "「人类的一切智慧都包含在这两个词里：等待与希望。」"),
]

EFFECT1 = "机制待定稿（第四批）：物品已注册，效果尚未实现。"
EFFECT2 = "佩戴后暂时没有任何加成。"


def write_models():
    folder = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "models", "item")
    for book_id, _, _ in BOOKS:
        path = os.path.join(folder, book_id + ".json")
        data = {"parent": "item/generated", "textures": {"layer0": "yanscriptures:item/" + book_id}}
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("models:", len(BOOKS))


def patch_tag(path):
    with io.open(path, encoding="utf-8") as f:
        data = json.load(f)
    values = data["values"]
    existing = set(v for v in values if isinstance(v, str))
    added = 0
    for book_id, _, _ in BOOKS:
        entry = "yanscriptures:" + book_id
        if entry not in existing:
            values.append(entry)
            added += 1
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(os.path.basename(path), "added:", added)


def patch_lang():
    path = os.path.join(ROOT, "src", "main", "resources", "assets", "yanscriptures", "lang", "zh_cn.json")
    with io.open(path, encoding="utf-8") as f:
        data = json.load(f)
    before = len(data)
    for book_id, name, quote in BOOKS:
        data["item.yanscriptures." + book_id] = name
        data["tooltip.yanscriptures.classic." + book_id + ".quote"] = quote
        data["tooltip.yanscriptures.classic." + book_id + ".effect1"] = EFFECT1
        data["tooltip.yanscriptures.classic." + book_id + ".effect2"] = EFFECT2
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("lang keys:", before, "->", len(data))


if __name__ == "__main__":
    write_models()
    patch_tag(os.path.join(ROOT, "src", "main", "resources", "data", "yanscriptures", "tags", "items", "books.json"))
    patch_tag(os.path.join(ROOT, "src", "main", "resources", "data", "curios", "tags", "items", "book.json"))
    patch_lang()
