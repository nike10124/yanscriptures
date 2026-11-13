# 让《圣约·原典》《如我所书》不被下界亚波伦收缴

下界亚波伦进入「天启（The Apocalypse）」阶段时，会把玩家身上的饰品（Curios）收走。
**这一步发生在另一个模组 Goety: Revelation（启示录）的附属 `revelationfix` 里，不是本模组的行为**，
所以它只认自己的白名单文件，与我们的 jar 无关。

白名单文件（游戏目录下）：

```
config/revelationfix/revelationfix-common.toml
```

要给本模组放行，只需要这一行里多两个 id：

```toml
["The Apocalypse"]
	whitelistItems = ["enigmaticlegacy:cursed_ring", "yanscriptures:covenant_codex", "yanscriptures:book_of_me"]
```

## 三种用法，按你的发布方式挑一种

### 1. 玩家自己改（最省事，推荐写进说明）

用记事本打开上面那个 toml，把 `whitelistItems` 那一行替换成上面那行，保存，**重启游戏**。
原来的 `enigmaticlegacy:cursed_ring` 必须留着，那是人家自己的。

### 2. 整份覆盖（适合「我确定对方是全新安装」）

把本目录的 `config/revelationfix/revelationfix-common.toml` 复制到游戏目录，覆盖同名文件。
注意：这份文件是在我这台机器上生成的，**除白名单外都是该模组的默认值**；
如果玩家之前调过启示录的其他设置（光环伤害上限、结界准备时间、亚波伦血量等），覆盖会一并冲掉。

### 3. 整合包（推荐）

把 `config/revelationfix/revelationfix-common.toml` 放进整合包的 `overrides/config/revelationfix/` 下。
首次启动时整合包会把 overrides 铺进实例，玩家无需手动操作；之后再有人改启示录的其他设置也不会被覆盖。

## 三个容易踩的坑

1. **不要只发这一行**给玩家让他「加在文件末尾」—— toml 里同名键重复会解析失败，必须是替换原来那一行。
2. **该模组更新或配置文件校验失败时会重置回默认**（我这边就被重置过一次，目录里还留着 5 个 `revelationfix-item-*.toml.bak`）。整合包更新后要复查这一行还在不在。
3. 白名单只解决「天启阶段不被收缴」。其它死亡惩罚、结界伤害等另算，不在这一行的作用范围内。

## 如果不想让玩家改配置

只能靠代码在运行时改对方的配置值（反射拿到 `ForgeConfigSpec.ConfigValue` 再 `set` + `save`），
坏处是要写死对方的配置键、并且依赖对方实现细节；本模组目前**没有**这么做。

---

# 下面这段可以直接发给对方（替换整份文件的方式）

> 装了两件模组饰品（《圣约·原典》《如我所书》）之后，下界亚波伦的「天启」阶段会把它们收走。
> 这是启示录（revelationfix）自带的白名单机制，需要替换一个配置文件，共 5 步，约 1 分钟：
>
> 1. **完全退出游戏**（不是退到主菜单，是关掉游戏窗口）；开服的话先停服。
> 2. 打开游戏目录里的 `config/revelationfix/`，把原来的 `revelationfix-common.toml`
>    改名为 `revelationfix-common.toml.bak`（留个备份，出问题能还原）。
> 3. 把我给你的 `revelationfix-common.toml` 放到同一个文件夹里，文件名不要改。
> 4. 用记事本打开它，确认这一行里有三个 id（顺序不重要）：
>    `whitelistItems = ["enigmaticlegacy:cursed_ring", "yanscriptures:covenant_codex", "yanscriptures:book_of_me"]`
> 5. 启动游戏，进一次世界，再退出。重新打开这个文件，确认第 4 步那三个 id 还在
>    —— 还在就说明生效了；如果被改回只剩一个 id，说明配置版本不匹配，把游戏的
>    `logs/latest.log` 发我。
>
> **注意**：整份替换会把这个模组的所有设置恢复成默认值。
> 如果你之前调过启示录的其它项（光环伤害上限、结界准备时间、亚波伦血量/减伤/无敌帧、仆从强化等），
> 需要按自己的记录改回去；不想动其它设置的话，就让对方只改第 4 步那一行，不要整份替换。
>
> **服务器**：联机时以服务端目录下的这份文件为准，客户端那份不起作用；
> 单人/局域网用你自己的游戏目录那份即可。
>
> **模组更新后**：启示录升级可能重写这份配置。更新后请再检查一次第 4 步那行；
> 整合包作者最好把它放进 `overrides/config/revelationfix/`，这样不用每次手动补。

