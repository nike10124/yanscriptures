package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;

/**
 * 九本名著饰品的身份表。
 *
 * <p>这里只管「它是哪本书、属于哪一类、文案键叫什么」，
 * 真正的机制全部写在 {@link ClassicBooksHandler} 里。
 * 这样做的好处是：加一本新书 = 加一个枚举项 + 一条 lang + 一个模型，
 * 不会到处散落 if 判断。
 */
public enum ClassicBook {

    // ==================== 哲学：如何看世界 ====================
    /** 《道德经》：反者道之动。生命越低，越难以被杀死。 */
    DAO_DE_JING("dao_de_jing", Category.PHILOSOPHY),
    /** 《理想国》：洞穴寓言。看得见影子之外的东西。 */
    REPUBLIC("republic", Category.PHILOSOPHY, Rarity.EPIC),
    /** 《查拉图斯特拉如是说》：深渊凝视。同一个对手，越打越顺手。 */
    ZARATHUSTRA("zarathustra", Category.PHILOSOPHY),

    // ==================== 文学：叙事与命运 ====================
    /** 《神曲》：地狱、炼狱、天堂三层，按所处高度改变命途。 */
    DIVINE_COMEDY("divine_comedy", Category.LITERATURE),
    /** 《堂吉诃德》：风车也是巨人。伤害在 50%~250% 之间乱跳。 */
    DON_QUIXOTE("don_quixote", Category.LITERATURE, Rarity.EPIC),
    /** 《百年孤独》：独行者。身边没有同伴时更强。 */
    HUNDRED_YEARS("hundred_years", Category.LITERATURE),

    // ==================== 理科：世界的规则 ====================
    /** 《几何原本》：公理与证明。站定不动会一层层筑起防御。 */
    EUCLID("euclid", Category.SCIENCE, Rarity.EPIC),
    /** 《物种起源》：适应。被同一种伤害打多了就长出抗性。 */
    ORIGIN("origin", Category.SCIENCE, Rarity.EPIC),
    /** 《九章算术》：均输。把一次重击摊成持续的小额亏损。 */
    NINE_CHAPTERS("nine_chapters", Category.SCIENCE),

    // ==================== 第二批（2026-11-01 只注册，机制未定稿）====================
    /** 《孙子兵法》：兵者，诡道也。 */
    ART_OF_WAR("art_of_war", Category.MILITARY),
    /** 《君主论》：目的证明手段。 */
    THE_PRINCE("the_prince", Category.POLITICS),
    /** 《罪与罚》：罪孽与救赎。 */
    CRIME_AND_PUNISHMENT("crime_and_punishment", Category.LITERATURE),
    /** 《浮士德》：与魔鬼的契约。 */
    FAUST("faust", Category.LITERATURE),
    /** 《奥德赛》：十年归乡。 */
    ODYSSEY("odyssey", Category.LITERATURE),
    /** 《本草纲目》：草木皆药。 */
    MATERIA_MEDICA("materia_medica", Category.MEDICINE, Rarity.EPIC),
    /** 《天工开物》：匠作之术。 */
    TIANGONG_KAIWU("tiangong_kaiwu", Category.CRAFT),
    /** 《梦的解析》：梦是愿望的达成。 */
    INTERPRETATION_OF_DREAMS("interpretation_of_dreams", Category.PSYCHOLOGY, Rarity.EPIC),
    /** 《易经》：穷则变，变则通。 */
    I_CHING("i_ching", Category.PHILOSOPHY),

    // ==================== 第三批（2026-11-06 只注册，机制未定稿）====================
    /** 《伊利亚特》：阿喀琉斯的愤怒。 */
    ILIAD("iliad", Category.LITERATURE),
    /** 《埃涅阿斯纪》：下冥界问路。 */
    AENEID("aeneid", Category.LITERATURE),
    /** 《吉尔伽美什史诗》：求不死草。 */
    GILGAMESH("gilgamesh", Category.LITERATURE, Rarity.EPIC),
    /** 《论语》：教学相长。 */
    ANALECTS("analects", Category.PHILOSOPHY),
    /** 《庄子》：逍遥游。 */
    ZHUANGZI("zhuangzi", Category.PHILOSOPHY, Rarity.EPIC),
    /** 《史记》：秉笔直书。 */
    RECORDS_OF_THE_HISTORIAN("records_of_the_historian", Category.HISTORY, Rarity.EPIC),
    /** 《三十六计》：走为上。 */
    THIRTY_SIX_STRATEGIES("thirty_six_strategies", Category.MILITARY, Rarity.EPIC),
    /** 《战争论》：战争是政治的延续。 */
    ON_WAR("on_war", Category.MILITARY, Rarity.EPIC),
    /** 《天体运行论》：日心。 */
    DE_REVOLUTIONIBUS("de_revolutionibus", Category.SCIENCE, Rarity.EPIC),

    // ==================== 第四批（2026-11-08 只注册，机制未定稿）====================
    /** 《纯粹理性批判》：人为自然立法。 */
    CRITIQUE_OF_PURE_REASON("critique_of_pure_reason", Category.PHILOSOPHY, Rarity.EPIC),
    /** 《尼各马可伦理学》：德性即中道。 */
    NICOMACHEAN_ETHICS("nicomachean_ethics", Category.PHILOSOPHY),
    /** 《神学大全》：信仰寻求理解。 */
    SUMMA_THEOLOGICA("summa_theologica", Category.PHILOSOPHY, Rarity.EPIC),
    /** 《国富论》：看不见的手。 */
    WEALTH_OF_NATIONS("wealth_of_nations", Category.POLITICS),
    /** 《资本论》：商品的秘密。 */
    CAPITAL("capital", Category.POLITICS),
    /** 《黄帝内经》：治未病。 */
    INNER_CANON_OF_THE_YELLOW_EMPEROR("inner_canon_of_the_yellow_emperor", Category.MEDICINE),
    /** 《一千零一夜》：讲故事的活命法。 */
    ONE_THOUSAND_AND_ONE_NIGHTS("one_thousand_and_one_nights", Category.LITERATURE),
    /** 《哈姆雷特》：生存还是毁灭。 */
    HAMLET("hamlet", Category.LITERATURE),
    /** 《基督山伯爵》：等待与希望。 */
    THE_COUNT_OF_MONTE_CRISTO("the_count_of_monte_cristo", Category.LITERATURE);

    /** 三大类：只做内部分组（创造页签排序、日后 JEI 搜索用），不再写进 tooltip。 */
    public enum Category {
        PHILOSOPHY("tooltip.yanscriptures.classic.category.philosophy", ChatFormatting.GOLD),
        LITERATURE("tooltip.yanscriptures.classic.category.literature", ChatFormatting.AQUA),
        SCIENCE("tooltip.yanscriptures.classic.category.science", ChatFormatting.GREEN),
        // 第二批引入的分类：目前只用于创造页签/搜索分组，不写进 tooltip
        MILITARY("tooltip.yanscriptures.classic.category.military", ChatFormatting.RED),
        POLITICS("tooltip.yanscriptures.classic.category.politics", ChatFormatting.YELLOW),
        MEDICINE("tooltip.yanscriptures.classic.category.medicine", ChatFormatting.DARK_GREEN),
        CRAFT("tooltip.yanscriptures.classic.category.craft", ChatFormatting.GOLD),
        PSYCHOLOGY("tooltip.yanscriptures.classic.category.psychology", ChatFormatting.DARK_PURPLE),
        // 第三批引入：《史记》这类史书单独一类
        HISTORY("tooltip.yanscriptures.classic.category.history", ChatFormatting.DARK_AQUA);

        private final String langKey;
        private final ChatFormatting color;

        Category(String langKey, ChatFormatting color) {
            this.langKey = langKey;
            this.color = color;
        }

        public String langKey() {
            return langKey;
        }

        public ChatFormatting color() {
            return color;
        }
    }

    private final String id;
    private final Category category;
    private final Rarity rarity;

    ClassicBook(String id, Category category) {
        this(id, category, Rarity.RARE);
    }

    ClassicBook(String id, Category category, Rarity rarity) {
        this.id = id;
        this.category = category;
        this.rarity = rarity;
    }

    /** 物品注册名，形如 {@code yanscriptures:dao_de_jing}。 */
    public String id() {
        return id;
    }

    public Category category() {
        return category;
    }

    /**
     * 物品稀有度（2026-11-05 作者定，2026-11-09 追加八本）。
     *
     * <p>默认 {@link Rarity#RARE}（青色名字）；作者点名的名著为
     * {@link Rarity#EPIC}（紫色名字）。《如我所书》《圣约·原典》《圣典·戒律》自己重写了
     * {@code getName}，颜色由各自的渐变控制，不走这里。</p>
     */
    public Rarity rarity() {
        return rarity;
    }

    /** tooltip 文案前缀，例如 {@code tooltip.yanscriptures.classic.dao_de_jing.quote}。 */
    public String langPrefix() {
        return "tooltip.yanscriptures.classic." + id;
    }
}
