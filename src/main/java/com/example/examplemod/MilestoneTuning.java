package com.example.examplemod;

/**
 * 十五件物品获取条件的全部阈值。
 *
 * <p>作者要求的核心约束：
 * <ul>
 *   <li>全部只对「首次达成」结算一次；</li>
 *   <li>全部必须后期可达；</li>
 *   <li>判定只用语义阈值，不写死其他模组的实体 ID / 物品 ID。</li>
 * </ul>
 *
 * <p>所有数字都可以在这里一行改掉，不用翻 {@link MilestoneTracker}。
 */
public final class MilestoneTuning {

    private MilestoneTuning() {
    }

    /** 「一场战斗」的判定窗口：最后一次造成/受到伤害后多少 tick 内仍算同一场。 */
    public static final int COMBAT_WINDOW_TICKS = 100;

    // [已废弃] 首领不再用血量阈值判定 —— 大整合包里的普通怪血量也能到几百上千。
    // 现在由 BossDetection 处理：原版三首领写死识别，其余走 #forge:bosses / #c:bosses /
    // #yanscriptures:bosses 实体类型标签。保留常量仅为记录历史取舍。
    // public static final float BOSS_HEALTH_THRESHOLD = 300.0F;
    // public static final float DRAGON_HEALTH_THRESHOLD = 200.0F;

    /** 一个游戏日 = 24000 tick。 */
    public static final long TICKS_PER_DAY = 24000L;

    // ==================== 十张反转残卷 ====================

    /** 壹 · 示马：击败的敌人的最大生命下限。 */
    public static final float SHEMA_MIN_TARGET_HEALTH = 222.0F;

    /** 贰 · 无声：单次伤害相对自身最大生命的倍率下限。 */
    public static final float SILENCE_DAMAGE_RATIO = 1.2F;

    /** 贰 · 无声：逃生的判定宽限（tick），受伤后这么长时间内还活着才算。 */
    public static final int SILENCE_SURVIVE_TICKS = 100;

    /** 叁 · 荆棘：经验等级下限。 */
    public static final int THORNS_MIN_LEVEL = 100;

    /** 肆 · 歇工：连续多少个游戏日不破坏方块。 */
    public static final int SABBATH_DAYS = 3;

    /** 陆 · 血声：累计击杀下限。 */
    public static final int BLOOD_MIN_KILLS = 1000;

    /** 陆 · 血声：累计首领击杀下限。 */
    public static final int BLOOD_MIN_BOSS_KILLS = 5;

    /** 柒 · 挂琴：宠物需要存活的游戏日数。 */
    public static final int HARP_DAYS = 7;

    /** 玖 · 火舌：一次爆炸需要同时杀死的敌人数量。 */
    public static final int TONGUE_MIN_KILLS = 7;

    /** 玖 · 火舌：爆炸击杀的归并窗口（tick）。 */
    public static final int TONGUE_WINDOW_TICKS = 20;

    /** 拾 · 虚空：击杀目标的血量下限（同时要求背包全空）。 */
    public static final float VANITY_MIN_TARGET_HEALTH = 300.0F;

    // ==================== 四件可获取的泰坦史诗 ====================

    /** 命运：触发时自身生命必须低于最大生命的多大比例。 */
    public static final float FATE_HEALTH_RATIO = 0.10F;

    /** 命运：单次治疗量必须超过最大生命的多大比例。 */
    public static final float FATE_HEAL_RATIO = 0.50F;

    /**
     * 示马是否要求「饰品栏里只有本模组的物品」（豁免表内的饰品不计）。
     *
     * <p>开局绑定的诅咒饰品（七咒之戒、灾厄之册）以及为逆转这些诅咒而常驻的配套饰品
     * 都写进了 {@code data/yanscriptures/tags/items/curio_exempt.json}，
     * 这里保持严格档即可：除豁免表之外，饰品栏里不能有别的东西。
     * 置 false 会退化为「至少一件本模组饰品」，其它模组饰品全部忽略。
     */
    public static final boolean SHEMA_REQUIRE_EXCLUSIVE_CURIOS = true;

    /** 创生：单秒治疗量下限。 */
    public static final float CREATION_HEAL_PER_SECOND = 100.0F;

    /** 创生：治疗累计窗口（tick）。 */
    public static final int CREATION_WINDOW_TICKS = 20;

    /** 支柱：单场战斗承伤下限。 */
    public static final float PILLAR_MIN_DAMAGE_TAKEN = 496.0F;

    /** 灾厄：单场战斗击杀下限。 */
    public static final int CALAMITY_MIN_KILLS = 50;

    // ==================== 杂项 ====================

    /** 治愈僵尸村民时，认定「是谁治愈的」的搜索半径。 */
    public static final double CURE_CREDIT_RADIUS = 8.0D;

    /** 同时记录的最大宠物数量。 */
    public static final int MAX_TRACKED_PETS = 4;
}
