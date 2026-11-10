# -*- coding: utf-8 -*-
"""生成「饰品机制详解（开发向）」：玩家可见效果 + 实现细节（文件/事件/常量/NBT）。"""
import glob
import json
import os
import re

ROOT = r"D:\MCModDev\forge-mdk"
SRC = ROOT + r"\src\main\java\com\example\examplemod"
LANG = ROOT + r"\src\main\resources\assets\yanscriptures\lang\zh_cn.json"
OUT = ROOT + r"\docs\饰品机制详解_开发向.md"

lang = json.loads(open(LANG, encoding="utf-8").read())

CAT = {
    "PHILOSOPHY": "哲学", "LITERATURE": "文学", "SCIENCE": "理科", "HISTORY": "史书",
    "MILITARY": "军事", "POLITICS": "政治", "MEDICINE": "医学", "CRAFT": "工艺",
    "PSYCHOLOGY": "心理",
}
EXCLUDE = {"ExampleMod.java", "NetworkHandler.java", "ClassicBook.java",
           "WornCache.java", "BookAttr.java", "SessionCleanup.java"}
SHARED = {"ClassicBooksHandler.java"}
ORDER = ["哲学", "文学", "理科", "史书", "军事", "政治", "医学", "工艺", "心理"]

# 行为细节：代码里能读出来、但看效果文案看不出来的部分（无敌、掉落、计数口径、边界）。
NOTES = {
    "dao_de_jing": [
        "空血不是 0 血：用 0.5 点生命表示，否则客户端会弹死亡界面而服务端还活着，点“重生”会错位",
        "空血期间 `LivingDeathEvent` 一律取消，这一击被吃掉后每秒升级生命恢复",
        "回到满血即死；30 秒到点时生命低于 max(5, 上限 10%) 也会死（防“生命被固定为 1”的无敌饰品）",
        "必死判定走 `hurt` → `Player#die`，被外部拦截时每刻重试，最多 60 次后放弃并补满血",
    ],
    "republic": [
        "影子在本体朝玩家一侧 1.1 格生成，无 AI、只在原地，10 秒后消散",
        "影子不可被杀死：死亡事件被取消并直接 discard，所以掉落、经验、成就、击杀统计、其它模组挂在死亡上的结算都不会发生",
        "影子受到的伤害全部转给本体，转伤前后都清零本体无敌帧，因此允许“同时打本体和影子”打出两笔（同 tick 会去重）",
        "同一本体不会叠出第二个影子；影子本身不计入任何账本",
    ],
    "zarathustra": [
        "佩戴期间十诫整体失效：`CommandmentsHandler` 取圣典时返回空，祝福与诅咒一起停",
        "每压住一条诅咒：攻击、生命上限、护甲、移动速度各 -3%；每压住一条祝福：同样四项各 +4%",
        "只改佩戴者本人，不影响同服其他人",
    ],
    "divine_comedy": [
        "Shift + T 随机穿越到一个**不是当前**的维度，落点找安全位置（地表最高处；必要时铺 3×3 脚下平台）",
        "落地给 60 秒抗性提升 II、生命恢复 III、夜视、抗火",
        "冷却 180 秒，就绪时间写在玩家持久数据里，重登不清",
    ],
    "don_quixote": [
        "骑乘（乘坐任何载具/生物）时持续获得抗性提升，离开载具后自然结束",
        "对“生命上限远高于自己”的敌人额外增伤，倍数按双方生命上限比计算",
    ],
    "hundred_years": [
        "独处（附近没有其他玩家）时增伤；背包越空伤害越高",
        "背包物品数超过阈值时，手里物品的提示框内容被清空（保留行数换成空串，避免创造模式背包越界）",
    ],
    "euclid": [
        "公理按完成数量分档：达成 2 条造成伤害 +10%，达成 3 条经验额外加成",
        "受到致命伤时消耗一条公理：血量置 1、本次伤害取消；公理耗尽后不再生效",
    ],
    "origin": [
        "同一类伤害连续承受 10 次后永久 -25%，**种类不设上限**（作者 2026-11-05 取消“最多 3 种”）",
        "记录被击败的首领，二次击杀过的首领额外增伤",
        "环境伤害（无源伤害）减半",
    ],
    "nine_chapters": [
        "伤害按斐波那契顺序叠加：连续命中时倍率 1、1、2、3、5…，中断后重置",
    ],
    "art_of_war": [
        "背刺/对未锁定你的目标必定暴击并额外增伤；按 Shift 获得隐身 II",
        "暴击按 `l2damagetracker:crit_rate/crit_damage` 结算，不用原版暴击",
    ],
    "the_prince": [
        "生命 ≥80%：攻击 +8%；生命 ≤30%：受伤 -15%、移速 +10%（同一时刻只吃其中一档）",
        "16 格内属于你的仆从（`#yanscriptures:minions` + `OwnableEntity` 主人判定）获得 +20% 攻/甲/血，离开光环后撤销",
    ],
    "crime_and_punishment": [
        "层数上限 10，每层攻击与受伤各 -2%；满 10 层时无敌帧 -0.2 秒、但最终伤害 ×1.2",
        "每次造成伤害独立掷 1‰ 雷击彩蛋（物理 + 魔法各一段，半径 3.5），带 `rolling` 保险丝防雷中雷",
        "死亡叠层，层数在左下角以“罪孽 N / 10”显示",
    ],
    "faust": [
        "签契约后永久失去 20% 生命上限（至少留 5 点），换来 60 秒的 +50% 造成伤害、-50% 受伤、+50% 移速",
        "生命上限惩罚用独立乘区（`MULTIPLY_TOTAL`），多份契约相乘",
        "冷却 60 秒；到期自动结束并恢复",
    ],
    "odyssey": [
        "死亡时快照背包与饰品并清空，复活后按原样归还；需要游戏规则 keepInventory 才会保留",
        "复活后 30 秒获得回复/吸收/力量/迅捷",
        "Shift + 右键可传送回死亡点（同样要求 keepInventory）",
    ],
    "materia_medica": [
        "正面效果时长 ×1.5、负面 ×0.5；瞬发效果与无限时长不处理",
        "等级额外 +2（上限 IV）；抗性提升只在 I~II 级时才再抬",
        "等级提升在下一 tick 结算，避免与原版“喝完药水才加效果”的时序打架",
    ],
    "tiangong_kaiwu": [
        "铁砧消耗经验 -50%",
        "附魔台给出的等级 +20（不改玩家等级，只改结果等级）",
    ],
    "interpretation_of_dreams": [
        "Shift + D 入梦 30 秒：可穿墙、飞行、无重力；32 格内生物被冻结（无 AI、不锁定、不掉血）",
        "梦里造成的伤害不立刻生效，30% 在醒来时一次性结算给本体",
        "被非做梦玩家攻击时直接免疫；冻结生物不会被任何伤害杀死",
        "醒来回到入梦时的位置；冻结名单写在玩家持久数据里，崩服不会留下不会动的怪",
    ],
    "i_ching": [
        "每 60 秒自动换一卦，六卦效果互斥；换卦立刻撤掉上一卦",
        "`/ys iching <卦>` 可手动指定，`random` 随机",
        "震的增伤按最终移动速度计算（×200%）",
    ],
    "thirty_six_strategies": [
        "生命 ≤30% 时自动隐身 6 秒并清空仇恨，冷却 30 秒",
        "隐身期间完全不会被锁定（`LivingChangeTargetEvent` 拒绝 + 每 5 刻清一次目标）",
        "光环：24 格内生物的索敌范围 -35%，离开范围后撤销",
    ],
    "iliad": [
        "8 格内只有 1 个生物锁定你 → 单挑：伤害 +15%、受伤 -10%，并免疫失明/反胃/漂浮/黑暗/中毒/凋零这类“干扰决斗”的效果",
        "被 2 个以上围攻 → 伤害 -10%，并周期性吃随机负面效果",
        "横扫半径扩大；单挑时对方也享受同样的公平（同类效果同样被清理）",
    ],
    "analects": [
        "16 格内**你自己**的宠物或仆从每人 +5% 伤害，最多 10 个",
        "命中后回复生命上限的 1%/个，结算放在下一 tick（避免“先回血再吃刀”看不出效果）",
    ],
    "records_of_the_historian": [
        "计数实时统计：遍历玩家已完成进度（跳过 `recipes/` 开头的配方解锁）",
        "每个进度 +0.5% 暴击率、+1% 暴击伤害、+0.5 幸运，没有上限",
        "数值写在物品侧属性表里（Curios 面板可见），进度数存于物品 NBT",
    ],
    "on_war": [
        "受到伤害的 50% 转化为士气，士气上限为生命上限的 20%",
        "士气满时下一击必定暴击，并额外造成一笔真实伤害（无视护甲、抗性、附魔），带 `striking` 保险丝防递归",
        "脱战 10 秒清空士气；没有内置冷却，清零后可立刻重新积累",
    ],
    "de_revolutionibus": [
        "金太阳、银月亮、蓝地球呈等边三角形绕玩家旋转，5 秒一圈",
        "接触判定按目标碰撞箱外扩 1.2 格，不随球的显示大小变",
        "太阳伤害按时间 150%（正午 250%、深夜 50%）；月亮 20% 攻击力 + 负面效果（夜间等级更高）；地球给玩家正面效果",
        "球是实体（`CelestialOrbEntity`），不参与战斗、不可点击",
    ],
    "aeneid": [
        "非首领亡灵对你中立（`forge:bosses` 标签判定），你亲手打过的那只会还手 15 秒",
        "挨打时附近亡灵被派去反击：每刻重设目标 + 寻路 + 近身补刀",
        "中毒/凋零伤害转成回血；瞬间治疗转成等量魔法伤害；生命回复每跳扣 1 点（最低扣到 1 点，不会扣死）",
        "饥饿回饱食度、饱和反过来扣",
    ],
    "zhuangzi": [
        "摔落伤害完全免疫（`LivingFallEvent` 取消 + 坠落伤害类型再挡一次）",
        "空中按空格大幅缓降、按 Shift 加速下坠；滑翔时移速 +20%",
        "滑翔中空手右键给一股向前的推力，有短冷却",
    ],
    "gilgamesh": [
        "按住 R 蓄力 5 秒才释放（客户端计时，服务端复核佩戴与持剑）",
        "96 格锥形范围，半径随距离线性变宽（末端约 25 格），伤害为 10000% 攻击力",
        "沿途凿开石头、矿石、木头、树叶、草、泥土、水与岩浆，**不掉落物品**；上限 70000 个方块、每 tick 3000 个",
        "对方有单次伤害上限时，改为按“实际掉血 × 10”补伤",
        "冷却 600 秒",
    ],
    "critique_of_pure_reason": [
        "附魔点球：一格内不重复、不同格可重复，附魔书不算；每点 +0.5% 伤害、+1% 生命上限",
        "带东西的格子 >21 且全部带附魔 → 创造飞行；条件不满足时只撤自己这份飞行许可",
        "对“你还没主动打过”的生物受到的伤害 -20%（对任何生物生效）",
    ],
    "capital": [
        "击杀生物 +min(100, 其生命上限)，村民交易 +50，猪灵金锭交易 +15（右键或丢在脚边被捡走都算）",
        "死亡资本减半；资本存玩家持久数据，重登不清",
        "每 500 资本攻击 +1%，上限 +30%",
    ],
    "wealth_of_nations": [
        "只数快捷栏与物品栏（36 格），按物品 id 去重；每 11 种 +1 点，最多 3 点",
        "1 点经验 +20%；2 点单次受伤不超过生命上限 80%；3 点攻击附带 30% 伤害追击（半径 1 格）",
        "高档包含低档；当前点数显示在物品介绍里",
    ],
    "inner_canon": [
        "受伤减免 = 当前生命百分比 - 50%（满血没有减免，半血及以下不减免）",
        "每 30 秒按木 → 火 → 土 → 金 → 水轮换，中途戴上会接上当前这一行而不是从头",
    ],
    "nicomachean_ethics": [
        "交战判定 = 有敌意生物盯着你，或最近 10 秒内你打过有敌意的生物；打和平生物不算",
        "每连续 5 秒没受伤多一层，四层依次：移速、攻击、暴击率+暴击伤害、造成伤害，之后每次攻击附带等量 1 格范围伤害",
        "挨打或脱战 10 秒全部清零；层数只加不减",
    ],
    "one_thousand_and_one_nights": [
        "只有“真正睡满一夜”才结算：按 `getSleepTimer() == 100` 判定，中途下床、白天强制上床都不算",
        "戴着七咒之戒（`#yanscriptures:insomnia_curses`）时，连续躺满 5 秒即算睡满，并替换掉对方的失眠提示",
        "每个游戏日只结算一次；成长随机到六项属性之一，累计上限 1001",
        "［故事］每天一次：生命掉到 10% 以下时回救（上限 >100 回一半，否则生命恢复）",
    ],
    "summa_theologica": [
        "超过生命上限 700% 的伤害整笔作废；没超过的最多只打掉 49% 并回复该笔的 10%",
        "［子］受伤得、［父］造成伤害得、［灵］获得经验得；各一层，持续 10 秒（戴圣约 70 秒），重复获得刷新",
        "［父］每秒对 1.5 格内敌对生物造成攻击力 10% 的伤害，打前清零无敌帧、打完还原（不占目标的无敌时间）",
        "［灵］经验 +50%、掉落翻倍（只对你击杀的生物）、移速/攻速/触及加成",
        "每拥有一条十诫祝福，基础值 +5%；三枚印记的状态同步到客户端给物品介绍里的三个字上色",
    ],
    "hamlet": [
        "每次造成伤害都掷一次骰子：15% 打 10%（并记下一次必暴）、35% 打 50%、35% 打 150%、15% 打 200% 且本次暴击",
        "必暴只作用于下一次造成伤害的攻击，不叠加",
        "暴击倍率 = 1 + 暴击伤害属性（没有该属性时按原版 150%）",
    ],
    "the_count_of_monte_cristo": [
        "名单只记“伤害过你”的生物，杀死过你的那只会多 20%",
        "名单生物死亡或你摘下书时移除；红色描边用计分板队伍染色，摘书时只关我们自己加的光",
        "名单非空时攻击速度 +25%；挨打时飘出“复仇”角标",
    ],
    "epic_fate": ["30 秒内替你挡一次死亡并把伤害清零，死亡时传送到安全位置", "未触发则 60 秒内获得幸运 III；冷却 300 秒"],
    "epic_pillar": ["10 秒内不能移动，受伤 -50%、几乎免疫击退，并把伤害的 50% 反弹", "冷却 180 秒"],
    "epic_creation": [
        "20 秒生命恢复 II，期间进食翻倍、溢出治疗转为伤害吸收",
        "期间无伤或生命低于 15% 时，额外 10 秒生命恢复 VIII",
        "冷却 120 秒",
    ],
    "epic_calamity": [
        "15 秒内攻击附加灾厄印记，每层目标受伤 +8%（最多 5 层），印记目标死亡额外掉经验并回血",
        "期间你受到的伤害 +15%；冷却 180 秒",
    ],
    "epic_ego": [
        "20 秒内基础属性翻倍并获得创造飞行，每秒消耗 1 点饥饿，耗尽即结束",
        "击杀生物延长 2 秒（上限 40 秒）；冷却 300 秒",
    ],
    "book_of_peace": [
        "非潜行：造成伤害 +20%、受到伤害 +20%；潜行：造成伤害 -30%、受到伤害 -30%",
    ],
    "blank_page": [
        "记录的是“附魔种类”而不是等级；手持右键点附魔物品登记",
        "已记录数量决定生命上限/攻击/幸运加成",
        "按键消耗 10 种附魔记录当前状态，再按一次回溯（冷却 15 分钟）",
    ],
    "principia": [
        "按已解锁的挖矿档位给攻击/护甲，满级额外 +1 格触及距离",
        "进度实时生效，不需要重新装备",
        "矿种 Ⅳ 档的进阶加成按 25% 封顶",
    ],
    "emerald_tablet": [
        "每酿造或饮用一种新药水，提升生命上限与攻击；另按种类给一笔魔法伤害固定值",
        "只算“新种类”，重复喝同一种不再加",
    ],
    "empty_bookshelf": [
        "佩戴时书籍栏位 +2：用 `CuriosApi.addSlotModifier(...)` 写进物品侧属性表，由 Curios 自己加成/撤销",
        "只能放进 `charm` 栏，且同一时间只能戴一座",
        "摘书时若多出来的格子还放着书，格子会保留到你把它们取走（不会吞物品）",
        "旧存档多出来的栏位可用 `/ys bookshelf fix` 清理（要求多余格子为空）",
    ],
    "book_of_me": [
        "十四种光辉里每一种都由独立模块判定（增益、保命、反伤、按键），互不覆盖",
        "死亡触发类效果会先把伤害清零再传送到安全位置",
        "按 V 记录/回溯状态，记录内容写在玩家持久数据里",
        "门径（K）与濒死传送共用 `EscapeController`，与《圣约·原典》同一套",
    ],
    "covenant_codex": [
        "十二权柄各自独立生效，任一满足条件即挂上对应修正或事件",
        "七印是开启其余权柄的前置；`F` 键是否决飞行（会一票否决其它书的飞行许可）",
        "云柱火柱（K）与《如我所书》共用一份脱困实现",
        "申命之赐授予的有益效果来自原版效果表，授予时写入日志",
    ],
    "scripture_of_commandments": [
        "十诫同时生效：每条独立计数祝福/诅咒，补全后祝福换成强化档",
        "不杀/不盗等判定走事件（死亡、容器开启），只对佩戴者生效",
        "与《查拉图斯特拉如是说》冲突时整体失效",
    ],
}


def emit_notes(out, key, indent=""):
    notes = NOTES.get(key)
    if not notes:
        return
    out.append(indent + "**行为细节**")
    out.append("")
    for note in notes:
        out.append(indent + "- " + note)
    out.append("")


files = {}
for path in glob.glob(SRC + r"\*.java"):
    files[os.path.basename(path)] = open(path, encoding="utf-8").read()


def clean(text):
    text = re.sub(r"§.", "", text)
    text = re.sub(r"^【[^】]*】\s*", "", text)
    text = re.sub(r"%\d*\$?s", "N", text)
    return text.replace("%%", "%").strip()


def events_of(src):
    pat = re.compile(
        r"@SubscribeEvent(?:\(([^)]*)\))?\s*(?://[^\n]*\n\s*)*(?:@[^\n]*\n\s*)*public static void (\w+)\(([\w\.<>]+) event",
        re.S)
    out = []
    for prio, name, ev in pat.findall(src):
        short = ev.split(".")[-1]
        prio = prio.strip()
        if prio:
            prio = re.sub(r"EventPriority\.", "", prio)
            out.append("%s（%s，%s）" % (short, name, prio))
        else:
            out.append("%s（%s）" % (short, name))
    return out


def consts_of(src, limit=14):
    out = []
    for m in re.finditer(
            r"private static final (?:int|long|double|float|boolean|String|UUID|TagKey<[^>]+>)\s+(\w+)\s*=\s*([^;]+);",
            src):
        name, value = m.group(1), m.group(2).strip()
        if len(value) > 60:
            value = value[:57] + "..."
        out.append("`%s = %s`" % (name, value))
        if len(out) >= limit:
            break
    return out


def nbt_of(src):
    keys = []
    for lit in re.findall(r'"([A-Za-z][A-Za-z0-9_]*Yan[A-Za-z0-9_]*)"', src):
        if lit not in keys:
            keys.append(lit)
    return keys


book_src = files["ClassicBook.java"]
pairs = re.findall(r'\(\s*"([a-z_]+)"\s*,\s*(?:Category\.)?([A-Z_]+)', book_src)

lines = []
lines.append("# 饰品机制详解（开发向）")
lines.append("")
lines.append("面向维护者与整合包作者。每件饰品分两部分：**玩家可见效果**（与游戏内文案一致）")
lines.append("和**实现**（实现文件、挂载事件与优先级、关键常量、NBT 键）。")
lines.append("")
lines.append("## 通用约定")
lines.append("")
lines.append("| 约定 | 说明 |")
lines.append("| --- | --- |")
lines.append("| 佩戴判定 | `WornCache.wears(player, ClassicBook)`；每 tick 由 `ClassicBooksHandler.onPlayerTick` 刷新一次快照，不要自己遍历 Curios |")
lines.append("| 属性修正 | `BookAttr.set(player, attribute, uuid, amount[, operation])`，默认 `MULTIPLY_BASE`；按玩家 UUID 记账的类必须在 `SessionCleanup` 静态块 `register(...)` |")
lines.append("| 无条件属性 | 写在物品侧 `ClassicBookItem#getAttributeModifiers`（Curios 的「当装备在书籍栏」块），不要在 Handler 里挂 |")
lines.append("| 暴击 | 一律读 `l2damagetracker:crit_rate` / `crit_damage`，不用原版暴击判定 |")
lines.append("| 每件一文件 | 新饰品新建独立 Handler，不再往 `ClassicBooksHandler` 里塞 |")
lines.append("| 网络 | `NetworkHandler.CHANNEL` + `PacketDistributor.PLAYER`；HUD 类各自 `client/*Hud.java` |")
lines.append("")

lines.append("## 一、名著（36 本）")
lines.append("")

for cat in ORDER:
    ids = [bid for bid, c in pairs if CAT.get(c) == cat]
    if not ids:
        continue
    lines.append("### " + cat)
    lines.append("")
    for bid in ids:
        enum = bid.upper()
        ref_files = [n for n, src in files.items()
                     if ("ClassicBook." + enum) in src and n not in EXCLUDE]
        text_files = [n for n in ref_files if not n.endswith("ClassicBook.java")]
        name = lang.get("item.yanscriptures." + bid, bid)
        if not name.startswith("《"):
            name = "《" + name + "》"
        lines.append("#### " + name + " `" + bid + "`")
        lines.append("")
        effs = []
        for suffix in ("effect1", "effect2"):
            key = "tooltip.yanscriptures.classic.%s.%s" % (bid, suffix)
            if key in lang:
                effs.append("- " + clean(lang[key]))
        if effs:
            lines.append("**玩家可见效果**")
            lines.append("")
            lines.extend(effs)
            lines.append("")
        emit_notes(lines, bid)
        if not text_files:
            lines.append("**实现**：见 `ClassicBooksHandler.java`（按 id 分派）。")
            lines.append("")
            continue
        lines.append("**实现**")
        lines.append("")
        for name in text_files:
            src = files[name]
            lines.append("- 文件：`%s`（%d 行）" % (name, len(src.splitlines())))
            evs = events_of(src)
            if evs:
                lines.append("- 事件：" + "；".join(evs[:8]))
            if name in SHARED:
                lines.append("- 常量：该文件是多本书共用的分派器，各书常量按注释分段，不在此重复列出")
            else:
                cs = consts_of(src)
                if cs:
                    lines.append("- 常量：" + "、".join(cs))
            nb = nbt_of(src)
            if nb:
                lines.append("- NBT：`" + "`、`".join(nb[:10]) + "`")
        lines.append("")

lines.append("## 二、史诗（5 件）")
lines.append("")
EPIC = {
    "epic_fate": ["FateEpicHandler.java", "TitanEpicItem.java"],
    "epic_pillar": ["PillarEpicHandler.java", "TitanEpicItem.java"],
    "epic_creation": ["CreationEpicHandler.java", "TitanEpicItem.java"],
    "epic_calamity": ["CalamityEpicHandler.java", "TitanEpicItem.java"],
    "epic_ego": ["EgoEpicHandler.java", "TitanEpicItem.java"],
}
for bid, named in EPIC.items():
    lines.append("#### " + lang.get("item.yanscriptures." + bid, bid) + " `" + bid + "`")
    lines.append("")
    effs = []
    for suffix in ("desc", "desc1", "desc2", "desc3", "cooldown"):
        key = "tooltip.yanscriptures.%s.%s" % (bid, suffix)
        if key in lang:
            effs.append("- " + clean(lang[key]))
    if effs:
        lines.append("**玩家可见效果**")
        lines.append("")
        lines.extend(effs)
        lines.append("")
    emit_notes(lines, bid, "    ")
    lines.append("**实现**")
    lines.append("")
    for name in named:
        if name not in files:
            continue
        src = files[name]
        lines.append("- 文件：`%s`（%d 行）" % (name, len(src.splitlines())))
        evs = events_of(src)
        if evs:
            lines.append("- 事件：" + "；".join(evs[:8]))
        cs = consts_of(src, 10)
        if cs:
            lines.append("- 常量：" + "、".join(cs))
        nb = nbt_of(src)
        if nb:
            lines.append("- NBT：`" + "`、`".join(nb[:10]) + "`")
    lines.append("")

lines.append("## 三、其他饰品")
lines.append("")
OTHERS = [
    ("book_of_peace", "《方块与和平》", ["BookOfPeaceItem.java", "CombatHandler.java"],
     ["attack", "defense"]),
    ("blank_page", "空白的书页", ["BlankPageItem.java", "BlankPageKeyHandler.java", "PotionEventHandler.java"],
     ["desc", "how_to_record", "available", "bonus", "state_hint"]),
    ("principia", "《自然哲学的数学原理》", ["BookOfPrinciplesItem.java", "ProgressAttributeHandler.java", "MiningHandler.java"],
     ["desc", "percent_bonus", "fixed_bonus", "max", "perfect", "ultimate"]),
    ("emerald_tablet", "《翠玉录》", ["BookOfEmeraldTabletItem.java", "ProgressAttributeHandler.java", "PotionEventHandler.java"],
     ["desc", "magic"]),
    ("empty_bookshelf", "空书架", ["EmptyBookshelfItem.java"], ["desc"]),
]
EXTRA = {
    "empty_bookshelf": [
        "佩戴时书籍栏位 +2：通过 `CuriosApi.addSlotModifier(...)` 写进物品侧属性表，由 Curios 自己加成 / 撤销",
        "只能放进 `charm` 栏，且同一时间只能戴一座（`canEquip` 校验）",
        "旧版本留下的多余栏位可用 `/ys bookshelf fix` 清理（多出来的格子必须为空）",
    ],
}
for bid, title, named, suffixes in OTHERS:
    lines.append("#### " + title + " `" + bid + "`")
    lines.append("")
    effs = []
    for suffix in suffixes:
        key = "tooltip.yanscriptures.%s.%s" % (bid, suffix)
        if key in lang:
            effs.append("- " + clean(lang[key]))
    effs.extend("- " + extra for extra in EXTRA.get(bid, []))
    if effs:
        lines.append("**玩家可见效果与要点**")
        lines.append("")
        lines.extend(effs)
        lines.append("")
    emit_notes(lines, bid, "    ")
    lines.append("**实现**")
    lines.append("")
    for name in named:
        if name not in files:
            continue
        src = files[name]
        lines.append("- 文件：`%s`（%d 行）" % (name, len(src.splitlines())))
        evs = events_of(src)
        if evs:
            lines.append("- 事件：" + "；".join(evs[:8]))
        cs = consts_of(src, 10)
        if cs:
            lines.append("- 常量：" + "、".join(cs))
        nb = nbt_of(src)
        if nb:
            lines.append("- NBT：`" + "`、`".join(nb[:10]) + "`")
    lines.append("")



TEN_ROWS = [
 "| 1 | 独一 | 经验 +100%，全属性 +10% | 经验 ×3，全属性 +15% | 装备任何非本模组饰品 | 攻击 -10%，每件额外 -8%（最多 -40%） |",
 "| 2 | 无像 | 最大生命 +10% | 最大生命 +15% | 使用图腾类物品 | 图腾完全失效 |",
 "| 3 | 圣名 | 攻击 +15% | 攻击 +20%，解封铁砧与砂轮 | 使用附魔台 / 铁砧 / 砂轮 | 攻击 -15%，附魔功能失效 |",
 "| 4 | 安息 | 移速 +15% | 移速 +20% | 游戏日结束时静止不足 60 秒 | 缓慢 III + 挖掘疲劳 III 整天 |",
 "| 5 | 孝道 | 村民折扣、铁傀儡保护 | 可以伤害友善生物 | 攻击友善生物 | 无法伤害友善生物，自身缓慢 III |",
 "| 6 | 不杀 | 受伤 -20% | 受伤 -25%，清零血罪 | 击杀（首杀触发，之后叠加血罪） | 每层血罪 -7% 生命与攻击（上限 7 层） |",
 "| 7 | 忠贞 | 中立生物不攻击，驯服率 +50% | 驯服率 +100% | 攻击或猎杀中立生物 | 中立生物主动攻击，无法驯服 |",
 "| 8 | 不盗 | 幸运 +2 | 幸运 +3 | 从野生的战利品容器取出物品 | 物品 30% 概率消失，抢夺无效 |",
 "| 9 | 真言 | 攻击速度 +15% | 攻击速度 +20% | 聊天、告示牌、成书、铁砧重命名、命名牌 | 每次攻击 20% 概率落空 |",
 "| 10 | 知足 | 挖掘 +20%，时运 +1 | 挖掘 +30%，时运 +2 | 挖掘矿石类方块 | 矿石 25% 概率不掉落，时运无效 |",
]

COVENANT_ROWS = [
 "| 权柄 | 效果 |",
 "|---|---|",
 "| 万邦止戈 | 生物不主动锁定佩戴者；你亲手打过的那只获得 10 秒仇恨窗口（否则打不动依赖「锁定」判定的 Boss） |",
 "| 不死之约 | 生命值永不低于 1；每秒回复最大生命 7% |",
 "| 日影停住 | **Z 键**：7 秒时停，半径 64，冷却 30 秒 |",
 "| 七碗倾倒 | **J 键**：半径 16、持续 7 秒、冷却 30 秒，每刻 × 12 种伤害类型（详见下） |",
 "| 不可近身 | 免疫负面效果；免疫火/落/溺水/冻/雷/爆炸/岩浆；免疫禁疗；免疫直接改血；777% 反伤；无敌帧 7 秒；创造飞行 |",
 "| 申命之赐 | 自身常驻全部原版有益效果七级；Shift+右键赐福生物、Alt+右键降灾 |",
 "| 万军之磐石 | 777 特殊生命 + 777 点护甲/幸运/攻击/韧性等定值加成 |",
 "| 复活在我 | 死亡事件取消 + 满血 + 清负面 |",
 "| 万军之约 | 每击杀 +1 攻击 / +1 生命上限，上限 777 次 |",
 "| 五饼二鱼 | 击杀后展开该生物战利品的全部条目，数量 ×2 |",
 "| 云柱火柱 | **K 键**沿视线 8~16 格脱困（自动判定已关闭，避免误触） |",
 "| 圣所 | 16 格内同伴受到的伤害由佩戴者承担 77% |",
]

lines.append("## 四、毕业饰品（长页）")
lines.append("")
lines.append("这三件的效果与判定都比较重，**这里写的是机制本身**，不是文件清单。")
lines.append("")

# ---------- 圣典·戒律 ----------
lines.append("### 4.1 圣典·戒律 `scripture_of_commandments`")
lines.append("")
emit_notes(lines, "scripture_of_commandments")
lines.append("**判定模型**：十条诫各自两个布尔，`Curse_n`（已违逆）与 `Completed_n`（已用残卷赎清）。")
lines.append("某条诫处于「祝福态」当且仅当 `!Curse_n && !Completed_n`——注意 `Completed_n` 也参与判定，")
lines.append("所以补全过的诫既不会再触发诅咒，也不会退回未触发的初始态；")
lines.append("`active(player)` = 12 − 处于「已违逆且未补全」的条数，是《圣约·原典》里「十诫祝福数」的来源。")
lines.append("")
lines.append("**祝福与诅咒**（数值与触发点同玩家向文档的十二诫表）：")
lines.append("")
lines.append("| # | 诫条 | 祝福 | 补全后强化 | 违逆触发 | 诅咒 |")
lines.append("|---|---|---|---|---|---|")
for row in TEN_ROWS:
    lines.append(row)
lines.append("")
lines.append("**几条容易踩的实现细节**：")
lines.append("")
lines.append("- 第 1 诫「独一」：身上每有一件**非本模组**饰品就叠一层攻击惩罚（每件 -8%，封顶 -40%）；")
lines.append("  祝福态则是经验 +100% 与全属性 +10%。判定发生在每一次属性/经验结算前，不是在穿戴那一刻定死。")
lines.append("- 第 6 诫「不杀」：首杀只叠到 50% 就触发诅咒；之后每次击杀继续叠加「血罪」，")
lines.append("  每层 -7% 生命与攻击（上限 7 层）。血罪层数由 `BloodSin_n` 计数，补全后清零。")
lines.append("- 第 8 诫「不盗」：判据是**容器方块实体的 NBT 里有没有 `LootTable`**，不看是谁放的；")
lines.append("  自己摆的箱子若用结构方块/命令塞了战利品表，同样会被算成野生容器。")
lines.append("- 十诫与《查拉图斯特拉如是说》冲突：佩戴后者期间整套十诫**整体失效**，不判祝福也不判诅咒。")
lines.append("")

# ---------- 圣约·原典 ----------
lines.append("### 4.2 圣约·原典 `covenant_codex`")
lines.append("")
emit_notes(lines, "covenant_codex")
lines.append("**十二权柄**（Shift 看长页，Shift+Alt 看整屏书页）：")
lines.append("")
for row in COVENANT_ROWS:
    lines.append(row)
lines.append("")
lines.append("**高生存是怎么实现的**——四层，从外到内：")
lines.append("")
lines.append("1. **完全免疫的环境伤害**：火、掉落、溺水、冰冻、雷击、爆炸、岩浆在 `LivingAttackEvent`（HIGHEST）")
lines.append("   直接取消，连特殊生命都不扣，也不留燃烧/冻伤痕迹。")
lines.append("2. **特殊生命池**：初始 777 点，存玩家持久数据 `CovenantSpecialHp`。")
lines.append("   其余所有伤害按 `ceil(伤害 × 0.23)` 从池里扣（这就是「受伤降低 77%」的实现方式），")
lines.append("   池子扣到 0 之前本体一滴血不掉；受伤 3 秒后每 20 刻回 1 点。")
lines.append("3. **无敌帧拉长**：每次结算后把 `invulnerableTime` 拉到 140 刻（7 秒），")
lines.append("   所以连续挨打不会出现「一秒吃十下」的情况。")
lines.append("4. **777% 反伤**：同一时刻对来源打一发 `holy_wrath`（无视护甲/抗性/无敌帧），伤害 = 本次伤害 × 7.77；")
lines.append("   无来源伤害（环境、效果）则反给 16 格内所有敌人。")
lines.append("   来源正在死亡时跳过反伤，避免在别人的死亡演出里重入。")
lines.append("")
lines.append("死亡另有保险：「复活在我」直接把 `LivingDeathEvent` 取消、回满血并清负面。")
lines.append("")
lines.append("**为什么不用 `heal()` / 不用 tick 巡检**：")
lines.append("")
lines.append("- 回血、清负面、给效果都走事件或直接改状态，避免被《埃涅阿斯纪》那类「反转治疗」抓住。")
lines.append("- 所有事件体都包 `try/catch(Throwable)` 并把异常打到 stderr：")
lines.append("  一旦异常抛回事件总线，Forge 记录异常时会撞上 log4j 的 loader 冲突，")
lines.append("  把一次小错误放大成整局崩溃（`LinkageError: MessageSupplier`）。")
lines.append("")
lines.append("**J 键「七碗倾倒」的伤害构成**（`JudgementController.java`）：")
lines.append("")
lines.append("- 持续 7 秒（140 刻）、半径 16 格、冷却 30 秒。")
lines.append("- 每刻对范围内每个非玩家生物：先把它的速度清零（定身），")
lines.append("  然后**同一次结算里打 12 种伤害类型各一下**——`holy_wrath`、玩家攻击、魔法、凋零、溺水、")
lines.append("  冰冻、雷击、着火、岩浆、摔落、通用、仙人掌。")
lines.append("- 每一发的数值都是 `777 + 面板攻击 × 7.77`，所以「破限伤」靠的是：")
lines.append("  ① 12 条伤害流水线里总有一条没被限伤配置拦住；② 每条流水线本身都按魔法/环境伤害计算，")
lines.append("  不吃护甲与抗性；③ 训练假人只吃数字不强制扣血，真实生物走完整条。")
lines.append("- **兜底**：若 12 发打完目标血量几乎没动（说明每种伤害都被某处免疫/限伤吃了），")
lines.append("  直接把血写成 `max(0, 当前 - 计算值)`；写归零就用 `holy_wrath` 直接 `die()`。")
lines.append("  这条兜底是「破除限伤」的最后一道——不走任何伤害事件。")
lines.append("")

# ---------- 如我所书 ----------
lines.append("### 4.3 如我所书 `book_of_me`")
lines.append("")
emit_notes(lines, "book_of_me")
lines.append("这本书把「保命」做成四条互相兜底的战斗规则（`BookOfMeCombatHandler.java`），")
lines.append("只要一条生效，玩家就不会死：")
lines.append("")
lines.append("**R1 负面效果免疫**：每 2 刻扫一遍身上的效果，`isBeneficial() == false` 的一律 `removeEffect`；")
lines.append("另外每 5 刻把血补满一次——这一条是防「绕过事件层直接 `setHealth`」的兜底。")
lines.append("")
lines.append("**R2 无敌帧穿透**：攻击命中时清目标的 `invulnerableTime`，")
lines.append("并反射扫描目标里名字含 `invul / iframe / cooldown / hurtcool` 的整数字段与同步数据一并清零。")
lines.append("为什么不能只在事件里清：有模组把限制写在 `LivingEntity#hurt` 的**最开头**，早于任何 Forge 事件。")
lines.append("")
lines.append("**R3 伤害免疫**：佩戴者进入 `LivingAttackEvent` / `LivingDamageEvent` 时一律取消、数值清零，")
lines.append("不看来源（实测某些尾杀伤害的 source 实体是空的）。另有两条全局卫生：")
lines.append("正在死亡的实体不再造成伤害、时停期间被冻结的实体不再造成伤害。")
lines.append("")
lines.append("**R4 限伤穿透（破除限伤）**：目标是「打了不掉血」的 Boss。")
lines.append("攻击时把匹配到的限伤配置临时抬高到 `1.0E9`，400 毫秒无战斗后再还原：")
lines.append("")
lines.append("```java")
lines.append("CAP_CONFIGS = {")
lines.append('    {"com.Polarice3.Goety.config.AttributesConfig", "ApostleDamageCap"},')
lines.append('    {"z1gned.goetyrevelation.config.ModConfig", "APOLLYON_HURT_LIMIT"}')
lines.append("}")
lines.append("RAISED_CAP = 1.0E9D;")
lines.append("CAP_HOLD_MS = 400;")
lines.append("```")
lines.append("")
lines.append("限伤没有通用接口，只能按配置语义匹配；取不到对应配置就静默跳过（不报错、不影响正常攻击）。")
lines.append("")
lines.append("**其他能力**：七印与十四种光辉（见玩家向文档）、V 键回溯、头顶铭刻的锚点等，")
lines.append("数值与冷却都在 `BookOfMeHandler.java` 顶部常量里。")
lines.append("")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines).rstrip() + "\n")
print("written", OUT, "lines:", len(lines))
