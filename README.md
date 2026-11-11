# Yan 的书籍饰品（Yan's Scriptures）

一本本书就是一件件饰品。收集世界名著，把它们戴在「书籍」饰品栏里，让文字变成力量。

- Minecraft **1.20.1** / **Forge 47.x** / **Curios 5.14.1+**
- 模组 ID：`yanscriptures`
- 当前版本：`0.1.0`

## 内容一览

**36 本名著**，分属哲学、文学、科学、军事、政治、医学、工艺、心理、历史九类。每本书都有各自的机制，不是单纯的数值堆叠；稀有度为「史诗」的 14 本带独立的保命/爆发逻辑。

**三件毕业饰品**：

| 饰品 | 定位 |
| --- | --- |
| 圣约·原典 | 近乎绝对的免伤、十二权柄、七碗倾倒 |
| 如我所书 | 命运/支柱/创生/灾厄/真我五首诗，时间暂停、状态回溯 |
| 十诫 | 开局赠送，守约者受祝福、违逆者受诅咒 |

**配套系统**：

- 著书台：三种基础材料（纸 / 墨 / 线）各四档，档位决定属性加成，配方数据驱动，可用 KubeJS 增删改；
- 炼药锅配方、锻造台换料、著书笔与空白书卷；
- 十张诫命残卷、五块史诗石板、启示残卷；
- 27 条成就（含史诗书成就与「百家争鸣」收集成就）。

## 构建

```
gradlew build
```

> 说明：本仓库**不包含** `libs/` 目录。里面是本地编译所需的第三方模组 jar（Curios、JEI、Jade 等），再分发受各自许可限制。
> 若需要在离线环境编译，请把对应版本的 jar 放回 `libs/`，或在 `build.gradle` 里改用 Maven 仓库依赖。

产物在 `build/libs/yanscriptures-<version>.jar`。

## 目录结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java/com/example/examplemod/` | 全部逻辑；一件饰品一个文件 |
| `src/main/resources/assets/yanscriptures/` | 贴图、模型、语言文件（`zh_cn.json` / `en_us.json`） |
| `src/main/resources/data/yanscriptures/` | 配方、标签、成就、战利品表 |
| `docs/` | 玩家向说明、开发笔记、配方表、材料清单 |
| `tools/` | 生成贴图/文档/语言文件的 Python 脚本 |
| `design/` | 历史像素画脚本（早期探索，保留备查） |

玩家文档见：[`docs/模组内容总览.md`](docs/模组内容总览.md)

## 许可证

All Rights Reserved。源码公开仅供查阅与学习，未经作者许可不得重新分发、修改后再发布，或用于商业用途。详见 [`LICENSE.md`](LICENSE.md)。

Minecraft 与 Forge 相关文件（`LICENSE.txt`、`CREDITS.txt` 等）保留其原始许可。
