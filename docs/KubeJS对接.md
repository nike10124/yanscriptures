# 整合包对接：用 KubeJS 改《Yan 的书籍饰品》的配方

> 面向整合包作者。模组本身不打算单独游玩（作者 2026-11-01 定调），所以合成尽量交给整合包。
> 目前**著书台的配方已经是数据驱动的**，KubeJS 可以直接增删改；其余配方见文末。

---

## 一、著书台配方是什么

| 项目 | 值 |
| --- | --- |
| 配方类型 | `yanscriptures:book_writing` |
| 自带配方 | `data/yanscriptures/recipes/book_writing/*.json`（10 条） |
| 一条配方要什么 | **纸**：某档 × N 个；**墨**：某档 × M 个；**线**：某档 × K 个；可选**额外材料**若干；**成品**一本书 |

三个「档」是模组自己的材料等级，**不是具体物品**：

| 线 | 档位 | 物品 |
| --- | --- | --- |
| 纸 | 1 / 2 / 3 / 4 | 草纸 / 羊皮纸 / 至邪之笺 / 寂灭之笺 |
| 墨 | 1 / 2 / 3 | 墨水 / 血墨 / 不洁之墨 |
| 线 | 1 / 2 / 3 | 麻绳 / 金绳 / 龙筋 |

规则：**高等级的料能写低等级的书，低等级不能写高等级的**。所以配方写「纸 2 档」时，
玩家放羊皮纸、至邪之笺、寂灭之笺都算数（都按羊皮纸的要求算数量）。

配方**不决定**书的属性：写出来那本书的材料加成 NBT（`YanPaper` / `YanInk` / `YanThread`）
由台子按玩家**实际用的料**现算。整合包不能靠配方改属性，只能用 `/ys book` 直接发一本。

---

## 二、加一条配方

```js
// kubejs/server_scripts/yanscriptures_books.js
ServerEvents.recipes(event => {
  event.custom({
    type: "yanscriptures:book_writing",
    paper:  { tier: 2, count: 2 },
    ink:    { tier: 2, count: 1 },
    thread: { tier: 2, count: 2 },
    extras: [
      { item: "minecraft:enchanted_golden_apple", count: 1 }
    ],
    result: { item: "yanscriptures:republic" }
  }).id("pack:book_writing/republic_alt")
})
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `paper` / `ink` / `thread` | 是 | 对象 `{ "tier": 档位, "count": 数量 }`；`tier: 0` = 任何档位都行；`count` 必须 ≥ 1 |
| `extras` | 否 | 数组，每项 `{ "item": "物品id", "count": 数量 }`，只能从材料池出 |
| `result` | 是 | `{ "item": "物品id", "count": 1 }`，或简写 `"result": "yanscriptures:dao_de_jing"` |

**校验很严**：缺字段、档位为负、物品 id 不存在，都会在日志里抛具体错误（例如
`著书台配方 pack:xxx 的 extras 引用了不存在的物品：foo:bar`），不会静默失败。
改完执行 `/reload`（或重进存档）即可，不用重启客户端。

---

## 三、删掉自带配方

```js
ServerEvents.recipes(event => {
  // 全部删掉，再从零写自己的
  event.remove({ type: "yanscriptures:book_writing" })

  // 或者只删某几条
  event.remove({ id: "yanscriptures:book_writing/dao_de_jing" })
})
```

自带的 10 条 id 都是 `yanscriptures:book_writing/<文件名>`：
`dao_de_jing`、`republic`、`zarathustra`、`divine_comedy`、`don_quixote`、`hundred_years`、
`euclid`、`origin`、`nine_chapters`、`book_of_peace`。

---

## 四、改一条自带配方

两种做法，任选：

1. **覆盖**：把同名 JSON 放进 `kubejs/data/yanscriptures/recipes/book_writing/<同名>.json`，
   默认的那份就不会生效（同一 id 后加载的覆盖先加载的）。
2. **删了重加**：`event.remove({ id: ... })` 然后 `event.custom({...}).id("yanscriptures:book_writing/<同名>")`。

> ⚠️ `event.replaceInput(...)` 对自定义配方类型**无效**——它只认原版 `crafting` / `smelting` 之类。
> 改自定义类型只有「删 + 加」或「同路径覆盖」两条路。

---

## 五、测试用指令

```
/ys book <书> <纸档 1-4> <墨档 1-3> <线档 1-3>
```

需要 OP。直接往背包塞一本指定档位的书，额外属性随机——不用为了看一眼「Ⅳ/Ⅲ/Ⅲ」的属性去凑材料。
书 id 可以写 `dao_de_jing`，也可以写 `yanscriptures:dao_de_jing`；必须是 `#yanscriptures:books` 里的物品。

---

## 六、还有哪些配方是数据驱动的

| 配方 | 类型 | 位置 |
| --- | --- | --- |
| 著书台写书 | `yanscriptures:book_writing` | `data/yanscriptures/recipes/book_writing/*.json` |
| 锻造台换料重铸 | `yanscriptures:material_upgrade`（其实是 `minecraft:smithing` 的子类型） | `data/yanscriptures/recipes/material_upgrade.json` |
| 工作台类（空白页、空书卷、书与笔等） | 原版 `minecraft:crafting_shaped` 等 | `data/yanscriptures/recipes/*.json` |

炼药锅那套（`yanscriptures:cauldron`）与「写书配方」在代码里查表，暂未数据化；要改请找作者。

---

## 七、常见坑

- `paper` / `ink` / `thread` 写成数字（`"paper": 2`）会报「缺少 paper 对象」——必须带 `tier` 与 `count`。
- `extras` 只认 `item`，不认 `tag`。
- 配方只描述「要什么料」，**不描述属性**；想要固定属性就 `/ys book` 或让玩家自己挑料。
- 改完配方后 JEI 分类会跟着变（它读的是世界配方表），不用另做兼容。
