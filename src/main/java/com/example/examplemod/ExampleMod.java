package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
// import net.minecraft.world.item.BlockItem; // 占位方块移除后不再需要
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
// import net.minecraft.world.level.block.Block; // 占位方块移除后不再需要
import net.minecraft.world.level.block.Blocks;
// import net.minecraft.world.level.block.state.BlockBehaviour; // 占位方块移除后不再需要
// import net.minecraft.world.level.material.MapColor; // 占位方块移除后不再需要
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.SlotTypeMessage;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "yanscriptures";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // 模组当前只有饰品，没有方块：模板自带的 example_block 占位方块已移除
    // （它没有任何 blockstate / 模型 / 贴图，只会在日志里刷三条模型缺失警告，
    //  还在创造模式标签页里挂一个没有名字的石方块）。
    // Create a Deferred Register to hold Items which will all be registered under the "yanscriptures" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "yanscriptures" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** 实体类型。目前只有一个不参与战斗的「试炼召唤物」。 */
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    // ==================== 方块 / 方块实体 / 界面（著书台，2026-10-28）====================
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    /** 锻造台「换料重铸」配方的序列化器（2026-10-31 新增）。 */
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);

    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<?>> MATERIAL_UPGRADE_SERIALIZER =
            RECIPE_SERIALIZERS.register("material_upgrade", MaterialUpgradeRecipe.Serializer::new);

    /** 著书台配方的序列化器（2026-11-01 数据化，供 KubeJS 用 {@code event.custom} 增删改）。 */
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<?>> BOOK_WRITING_SERIALIZER =
            RECIPE_SERIALIZERS.register("book_writing", BookWritingRecipe.Serializer::new);

    /** 著书台：上端放书、下端塞材料的容器方块。 */
    public static final RegistryObject<net.minecraft.world.level.block.Block> BOOK_WRITING_TABLE =
            BLOCKS.register("book_writing_table", () -> new BookWritingTableBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties
                            .copy(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                            .noOcclusion()));

    public static final RegistryObject<Item> BOOK_WRITING_TABLE_ITEM =
            ITEMS.register("book_writing_table",
                    () -> new net.minecraft.world.item.BlockItem(BOOK_WRITING_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<BookWritingTableBlockEntity>>
            BOOK_WRITING_TABLE_BE = BLOCK_ENTITY_TYPES.register("book_writing_table",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(BookWritingTableBlockEntity::new, BOOK_WRITING_TABLE.get())
                            .build(null));

    /**
     * 著书台的界面类型。
     *
     * <p>客户端不需要方块坐标：槽位内容由服务端同步，所以只用最简的
     * {@code (id, inventory) -> menu} 工厂即可（服务端另外用带容器的那套构造器）。</p>
     */
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BookWritingTableMenu>>
            BOOK_WRITING_TABLE_MENU = MENUS.register("book_writing_table",
                    () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                            (id, inv, data) -> new BookWritingTableMenu(id, inv, data.readBlockPos())));

    // 注册饰品
    public static final RegistryObject<Item> BOOK_OF_PEACE = ITEMS.register("book_of_peace",
            () -> new BookOfPeaceItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> PRINCIPIA = ITEMS.register("principia",
            () -> new BookOfPrinciplesItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> EMERALD_TABLET = ITEMS.register("emerald_tablet",
            () -> new BookOfEmeraldTabletItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> BLANK_PAGE = ITEMS.register("blank_page",
            () -> new BlankPageItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** 著书笔：手持对着原版讲台 Shift + 右键，把讲台改造成著书台。 */
    public static final RegistryObject<Item> WRITING_QUILL = ITEMS.register("writing_quill",
            () -> new WritingQuillItem(new Item.Properties().stacksTo(1)));
    /** 空书架：护符栏饰品，戴上后多两个书籍栏位。 */
    public static final RegistryObject<Item> EMPTY_BOOKSHELF = ITEMS.register("empty_bookshelf",
            () -> new EmptyBookshelfItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> EPIC_FATE = ITEMS.register("epic_fate",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC), "fate", 300000));
    public static final RegistryObject<Item> EPIC_CREATION = ITEMS.register("epic_creation",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC), "creation", 120000));
    public static final RegistryObject<Item> EPIC_PILLAR = ITEMS.register("epic_pillar",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC), "pillar", 180000));
    public static final RegistryObject<Item> EPIC_CALAMITY = ITEMS.register("epic_calamity",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC), "calamity", 180000));
    public static final RegistryObject<Item> EPIC_EGO = ITEMS.register("epic_ego",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC), "ego", 300000));
    public static final RegistryObject<Item> BOOK_OF_ME = ITEMS.register("book_of_me",
            () -> new BookOfMeItem(new Item.Properties().stacksTo(1)));
    /**
     * 《十诫》的名字颜色由 {@code ScriptureOfCommandmentsItem#getName} 自己算（暗红 → 暖金，
     * 与大书页标题同源），所以它和《如我所书》《圣约·原典》一样**不设稀有度**——
     * 设了也会被组件级颜色覆盖，只会让两处颜色对不上。
     */
    public static final RegistryObject<Item> SCRIPTURE_OF_COMMANDMENTS = ITEMS.register("scripture_of_commandments",
            () -> new ScriptureOfCommandmentsItem(new Item.Properties().stacksTo(1)));

    // ==================== 十诫刻蚀残卷（2026-10-22）====================
    // 十张残卷各对应一条诫，另一张「启示残卷」不属十诫。
    // 当前只做注册与文案，诅咒反转机制尚未接入（见 ScriptureScrollItem 类注释）。
    // 稀有度（2026-11-05 作者定）：十张诫卷 = 少见（黄色）；启示残卷自己有深红名字
    // （见 ScriptureScrollItem#getName），名字颜色会被组件级颜色覆盖，故不设稀有度。
    private static RegistryObject<Item> scroll(String id, String key, int commandment) {
        return ITEMS.register(id, () -> {
            Item.Properties properties = new Item.Properties().stacksTo(1);
            if (commandment > 0) {
                properties.rarity(net.minecraft.world.item.Rarity.UNCOMMON);
            }
            return new ScriptureScrollItem(properties, key, commandment);
        });
    }

    public static final RegistryObject<Item> SCROLL_SHEMA      = scroll("scripture_of_shema", "shema", 1);
    public static final RegistryObject<Item> SCROLL_SILENCE    = scroll("scripture_of_silence", "silence", 2);
    public static final RegistryObject<Item> SCROLL_THORNS     = scroll("scripture_of_thorns", "thorns", 3);
    public static final RegistryObject<Item> SCROLL_SABBATH    = scroll("scripture_of_sabbath", "sabbath", 4);
    public static final RegistryObject<Item> SCROLL_REBELLION  = scroll("scripture_of_rebellion", "rebellion", 5);
    public static final RegistryObject<Item> SCROLL_BLOOD      = scroll("scripture_of_blood", "blood", 6);
    public static final RegistryObject<Item> SCROLL_HARP       = scroll("scripture_of_harp", "harp", 7);
    public static final RegistryObject<Item> SCROLL_SHINAR     = scroll("scripture_of_shinar", "shinar", 8);
    public static final RegistryObject<Item> SCROLL_TONGUE     = scroll("scripture_of_tongue", "tongue", 9);
    public static final RegistryObject<Item> SCROLL_VANITY     = scroll("scripture_of_vanity", "vanity", 10);
    public static final RegistryObject<Item> SCROLL_REVELATION = scroll("scripture_of_revelation", "revelation", 0);

    // ==================== 九本名著（哲学 / 文学 / 理科）====================
    // 统一用一次性 Helper 注册，避免九段重复代码；机制全部在 ClassicBooksHandler 里。
    private static RegistryObject<Item> classic(ClassicBook book) {
        return ITEMS.register(book.id(),
                () -> new ClassicBookItem(new Item.Properties().stacksTo(1).rarity(book.rarity()), book));
    }

    /** 《道德经》—— 生命越低，越难被杀死 */
    public static final RegistryObject<Item> BOOK_DAO_DE_JING = classic(ClassicBook.DAO_DE_JING);
    /** 《理想国》—— 免疫失明/黑暗，令隐形者现形 */
    public static final RegistryObject<Item> BOOK_REPUBLIC = classic(ClassicBook.REPUBLIC);
    /** 《查拉图斯特拉如是说》—— 对同一类生物越打越重 */
    public static final RegistryObject<Item> BOOK_ZARATHUSTRA = classic(ClassicBook.ZARATHUSTRA);
    /** 《神曲》—— 地狱增伤、炼狱减伤、天堂大幅减伤 */
    public static final RegistryObject<Item> BOOK_DIVINE_COMEDY = classic(ClassicBook.DIVINE_COMEDY);
    /** 《堂吉诃德》—— 伤害随机在 50%~250% 之间 */
    public static final RegistryObject<Item> BOOK_DON_QUIXOTE = classic(ClassicBook.DON_QUIXOTE);
    /** 《百年孤独》—— 独行者增伤且减伤 */
    public static final RegistryObject<Item> BOOK_HUNDRED_YEARS = classic(ClassicBook.HUNDRED_YEARS);
    /** 《几何原本》—— 站定叠层减伤 */
    public static final RegistryObject<Item> BOOK_EUCLID = classic(ClassicBook.EUCLID);
    /** 《物种起源》—— 适应反复受到的伤害类型 */
    public static final RegistryObject<Item> BOOK_ORIGIN = classic(ClassicBook.ORIGIN);
    /** 《九章算术》—— 重击拆成持续小额伤害 */
    public static final RegistryObject<Item> BOOK_NINE_CHAPTERS = classic(ClassicBook.NINE_CHAPTERS);

    // ==================== 第二批名著（2026-11-01 只注册，机制未定稿）====================
    /** 《孙子兵法》 */
    public static final RegistryObject<Item> BOOK_ART_OF_WAR = classic(ClassicBook.ART_OF_WAR);
    /** 《君主论》 */
    public static final RegistryObject<Item> BOOK_THE_PRINCE = classic(ClassicBook.THE_PRINCE);
    /** 《罪与罚》 */
    public static final RegistryObject<Item> BOOK_CRIME_AND_PUNISHMENT = classic(ClassicBook.CRIME_AND_PUNISHMENT);
    /** 《浮士德》 */
    public static final RegistryObject<Item> BOOK_FAUST = classic(ClassicBook.FAUST);
    /** 《奥德赛》 */
    public static final RegistryObject<Item> BOOK_ODYSSEY = classic(ClassicBook.ODYSSEY);
    /** 《本草纲目》 */
    public static final RegistryObject<Item> BOOK_MATERIA_MEDICA = classic(ClassicBook.MATERIA_MEDICA);
    /** 《天工开物》 */
    public static final RegistryObject<Item> BOOK_TIANGONG_KAIWU = classic(ClassicBook.TIANGONG_KAIWU);
    /** 《梦的解析》 */
    public static final RegistryObject<Item> BOOK_INTERPRETATION_OF_DREAMS = classic(ClassicBook.INTERPRETATION_OF_DREAMS);
    /** 《易经》 */
    public static final RegistryObject<Item> BOOK_I_CHING = classic(ClassicBook.I_CHING);

    // ==================== 第三批名著（2026-11-06 只注册，机制未定稿）====================
    // 作者已交付九张贴图；机制等定稿后再写，这里先保证能拿到物品、进饰品栏、进 JEI。
    /** 《伊利亚特》 */
    public static final RegistryObject<Item> BOOK_ILIAD = classic(ClassicBook.ILIAD);
    /** 《埃涅阿斯纪》 */
    public static final RegistryObject<Item> BOOK_AENEID = classic(ClassicBook.AENEID);
    /** 《吉尔伽美什史诗》 */
    public static final RegistryObject<Item> BOOK_GILGAMESH = classic(ClassicBook.GILGAMESH);
    /** 《论语》 */
    public static final RegistryObject<Item> BOOK_ANALECTS = classic(ClassicBook.ANALECTS);
    /** 《庄子》 */
    public static final RegistryObject<Item> BOOK_ZHUANGZI = classic(ClassicBook.ZHUANGZI);
    /** 《史记》 */
    public static final RegistryObject<Item> BOOK_RECORDS_OF_THE_HISTORIAN =
            classic(ClassicBook.RECORDS_OF_THE_HISTORIAN);
    /** 《三十六计》 */
    public static final RegistryObject<Item> BOOK_THIRTY_SIX_STRATEGIES = classic(ClassicBook.THIRTY_SIX_STRATEGIES);
    /** 《战争论》 */
    public static final RegistryObject<Item> BOOK_ON_WAR = classic(ClassicBook.ON_WAR);
    /** 《天体运行论》 */
    public static final RegistryObject<Item> BOOK_DE_REVOLUTIONIBUS = classic(ClassicBook.DE_REVOLUTIONIBUS);

    // ==================== 第四批名著（2026-11-08 只注册，机制未定稿）====================
    // 作者已交付九张贴图；机制等定稿后再写，这里先保证能拿到物品、进饰品栏、进 JEI。
    /** 《纯粹理性批判》 */
    public static final RegistryObject<Item> BOOK_CRITIQUE_OF_PURE_REASON =
            classic(ClassicBook.CRITIQUE_OF_PURE_REASON);
    /** 《尼各马可伦理学》 */
    public static final RegistryObject<Item> BOOK_NICOMACHEAN_ETHICS = classic(ClassicBook.NICOMACHEAN_ETHICS);
    /** 《神学大全》 */
    public static final RegistryObject<Item> BOOK_SUMMA_THEOLOGICA = classic(ClassicBook.SUMMA_THEOLOGICA);
    /** 《国富论》 */
    public static final RegistryObject<Item> BOOK_WEALTH_OF_NATIONS = classic(ClassicBook.WEALTH_OF_NATIONS);
    /** 《资本论》 */
    public static final RegistryObject<Item> BOOK_CAPITAL = classic(ClassicBook.CAPITAL);
    /** 《黄帝内经》 */
    public static final RegistryObject<Item> BOOK_INNER_CANON_OF_THE_YELLOW_EMPEROR =
            classic(ClassicBook.INNER_CANON_OF_THE_YELLOW_EMPEROR);
    /** 《一千零一夜》 */
    public static final RegistryObject<Item> BOOK_ONE_THOUSAND_AND_ONE_NIGHTS =
            classic(ClassicBook.ONE_THOUSAND_AND_ONE_NIGHTS);
    /** 《哈姆雷特》 */
    public static final RegistryObject<Item> BOOK_HAMLET = classic(ClassicBook.HAMLET);
    /** 《基督山伯爵》 */
    public static final RegistryObject<Item> BOOK_THE_COUNT_OF_MONTE_CRISTO =
            classic(ClassicBook.THE_COUNT_OF_MONTE_CRISTO);

    /** 名著，按「哲学 → 文学 → 理科 → 第二批 → 第三批 → 第四批」的顺序。 */
    public static final java.util.List<RegistryObject<Item>> CLASSIC_BOOKS = java.util.List.of(
            BOOK_DAO_DE_JING, BOOK_REPUBLIC, BOOK_ZARATHUSTRA,
            BOOK_DIVINE_COMEDY, BOOK_DON_QUIXOTE, BOOK_HUNDRED_YEARS,
            BOOK_EUCLID, BOOK_ORIGIN, BOOK_NINE_CHAPTERS,
            BOOK_ART_OF_WAR, BOOK_THE_PRINCE, BOOK_CRIME_AND_PUNISHMENT,
            BOOK_FAUST, BOOK_ODYSSEY, BOOK_MATERIA_MEDICA,
            BOOK_TIANGONG_KAIWU, BOOK_INTERPRETATION_OF_DREAMS, BOOK_I_CHING,
            BOOK_ILIAD, BOOK_AENEID, BOOK_GILGAMESH,
            BOOK_ANALECTS, BOOK_ZHUANGZI, BOOK_RECORDS_OF_THE_HISTORIAN,
            BOOK_THIRTY_SIX_STRATEGIES, BOOK_ON_WAR, BOOK_DE_REVOLUTIONIBUS,
            BOOK_CRITIQUE_OF_PURE_REASON, BOOK_NICOMACHEAN_ETHICS, BOOK_SUMMA_THEOLOGICA,
            BOOK_WEALTH_OF_NATIONS, BOOK_CAPITAL, BOOK_INNER_CANON_OF_THE_YELLOW_EMPEROR,
            BOOK_ONE_THOUSAND_AND_ONE_NIGHTS, BOOK_HAMLET, BOOK_THE_COUNT_OF_MONTE_CRISTO);

    /** 十张诫卷，按第一诫到第十诫的顺序；创造页签按此顺序排列。 */
    public static final java.util.List<RegistryObject<Item>> SCROLLS = java.util.List.of(
            SCROLL_SHEMA, SCROLL_SILENCE, SCROLL_THORNS, SCROLL_SABBATH, SCROLL_REBELLION,
            SCROLL_BLOOD, SCROLL_HARP, SCROLL_SHINAR, SCROLL_TONGUE, SCROLL_VANITY);

    // ==================== 圣约·原典 ====================
    // 十诫路线的毕业饰品（book 槽位）。
    // 目前【只是注册】：仪式、试炼、效果都还没接，先占位。
    // 后续阶段：安息仪式 → 四波试炼 → 第二次仪式（圣典 + 天启七印级素材）→ 本物品。
    public static final RegistryObject<Item> COVENANT_CODEX = ITEMS.register(
            "covenant_codex",
            () -> new ScriptureOfCovenantItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)
                    .fireResistant()));

    // ==================== 试炼召唤物 ====================
    // 安息仪式完成后由 Goety 的召唤仪式生成（见 data/yanscriptures/recipes/rite_of_revelation.json）。
    // 它不参与战斗，落地后第一 tick 就开启试炼并自毁 —— 借用「召唤暴风」那套
    // 「用一次性的工具实体来触发复杂演出」的做法。
    public static final RegistryObject<net.minecraft.world.entity.EntityType<TrialSummonerEntity>> TRIAL_SUMMONER =
            ENTITY_TYPES.register("trial_summoner",
                    () -> net.minecraft.world.entity.EntityType.Builder
                            .of(TrialSummonerEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(8)
                            .build(new ResourceLocation(MODID, "trial_summoner").toString()));

    /**
     * 《天体运行论》绕身旋转的三颗天体。
     *
     * <p>作者 2026-11-07 要求「不用粒子、用实体」——这三颗球是真的实体，
     * 由 {@link DeRevolutionibusHandler} 每刻摆到轨道上，客户端由
     * {@code client/CelestialOrbRenderer} 画成球面。必须登记渲染器，否则客户端一出现就崩。
     */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<CelestialOrbEntity>> CELESTIAL_ORB =
            ENTITY_TYPES.register("celestial_orb",
                    () -> net.minecraft.world.entity.EntityType.Builder
                            .of(CelestialOrbEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                            .sized(0.8F, 0.8F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(new ResourceLocation(MODID, "celestial_orb").toString()));

    // 🚨 新增：空白的书页的快捷键定义
    public static final KeyMapping BLANK_PAGE_KEY = new KeyMapping(
            "key.yanscriptures.blank_page",           // 语言键
            InputConstants.Type.KEYSYM,            // 按键类型
            GLFW.GLFW_KEY_V,                       // 默认 V 键
            "key.categories.yanscriptures"            // 分类
    );
    public static final KeyMapping BOOK_OF_ME_Z_KEY = new KeyMapping(
            "key.yanscriptures.book_of_me_z",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.yanscriptures"
    );
    public static final KeyMapping BOOK_OF_ME_L_KEY = new KeyMapping(
            "key.yanscriptures.book_of_me_l",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.yanscriptures"
    );

    /** 主动脱困：沿视线方向传出去（两个毕业饰品通用）。 */
    public static final KeyMapping ESCAPE_KEY = new KeyMapping(
            "key.yanscriptures.escape",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.yanscriptures"
    );

    /** 七碗倾倒（末日审判）：圣约·原典的主动攻击权柄。 */
    public static final KeyMapping COVENANT_JUDGEMENT_KEY = new KeyMapping(
            "key.yanscriptures.covenant_judgement",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.yanscriptures"
    );

    /** 《神曲》穿越维度：Shift + T。按住 Shift 才生效，避免和聊天键打架。 */
    public static final KeyMapping COMEDY_TELEPORT_KEY = new KeyMapping(
            "key.yanscriptures.comedy_teleport",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            "key.categories.yanscriptures"
    );

    /** 《梦的解析》入梦：Shift + D。同样按住 Shift 才生效。 */
    public static final KeyMapping DREAM_ENTER_KEY = new KeyMapping(
            "key.yanscriptures.dream_enter",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_D,
            "key.categories.yanscriptures"
    );

    /** 《吉尔伽美什史诗》Enuma Elish：按住 R 持续五秒才释放（作者 2026-11-07 补充）。 */
    public static final KeyMapping ENUMA_KEY = new KeyMapping(
            "key.yanscriptures.enuma",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.yanscriptures"
    );

    /** 飞行开关：双击空格在某些整合包里会被拦掉，给一个确定可用的入口。 */
    public static final KeyMapping COVENANT_FLIGHT_KEY = new KeyMapping(
            "key.yanscriptures.covenant_flight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.yanscriptures"
    );
    // Creates a creative tab with the id "yanscriptures:example_tab" for the example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BLANK_PAGE.get().getDefaultInstance())
            // 新增这一行，显式指定标题
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.yanscriptures.example_tab"))
            .displayItems((parameters, output) -> {
                output.accept(BOOK_OF_PEACE.get());
                output.accept(PRINCIPIA.get());
                output.accept(EMERALD_TABLET.get());
                output.accept(BLANK_PAGE.get());
                output.accept(EPIC_FATE.get());
                output.accept(EPIC_CREATION.get());
                output.accept(EPIC_PILLAR.get());
                output.accept(EPIC_CALAMITY.get());
                output.accept(EPIC_EGO.get());
                output.accept(BOOK_OF_ME.get());
                output.accept(SCRIPTURE_OF_COMMANDMENTS.get());
                // 十诫刻蚀残卷：先按诫条顺序排十张，最后放不属于十诫的启示残卷
                SCROLLS.forEach(s -> output.accept(s.get()));
                output.accept(SCROLL_REVELATION.get());
                // 十诫路线的终点（占位，效果未启用）
                output.accept(COVENANT_CODEX.get());
                // 九本名著：哲学三本、文学三本、理科三本
                CLASSIC_BOOKS.forEach(b -> output.accept(b.get()));
                // 制书材料：纸 → 墨 → 装订 → 空书卷
                Materials.ALL.forEach(m -> output.accept(m.get()));
                // 著书台（材料在这里投入实际使用）
                output.accept(BOOK_WRITING_TABLE_ITEM.get());
                output.accept(WRITING_QUILL.get());
                // 空书架（护符）：戴上后书籍栏 +2
                output.accept(EMPTY_BOOKSHELF.get());
            }).build());

    public ExampleMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(ExampleMod::registerCuriosSlots);
        // 炼药锅配方在 DataReloadHandler 里随 FORGE 的 AddReloadListenerEvent 注册
        // （AddReloadListenerEvent 不在 mod 总线上，挂错会在构造期直接崩）。
        modEventBus.addListener(this::commonSetup);

        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);

        // 制书材料的物品写在 Materials 的静态字段里，必须在这里先把那个类初始化，
        // 否则会等到创造页签列举时才加载，RegisterEvent 已经结束 → 崩溃。
        Materials.init();

        // ==================== [修复 P0-4 2026-10-21] 去掉重复注册 ====================
        // 下面这些类每一个头上都有 @Mod.EventBusSubscriber(bus = Bus.FORGE)，Forge 已经自动注册过一次。
        // 手工再 register 一次会让同一事件被处理两遍，而这些处理器都是非幂等的数值改写
        // （例如 CombatHandler 里的 ×0.7 / ×1.2），重复执行会导致战斗数值翻倍。
        // 原代码保留在下方注释中，便于回滚。
        //
        // MinecraftForge.EVENT_BUS.register(this);
        // MinecraftForge.EVENT_BUS.register(CombatHandler.class); // 战斗事件
        //
        // MinecraftForge.EVENT_BUS.register(CreationEpicHandler.class);
        //
        // MinecraftForge.EVENT_BUS.register(PillarEpicHandler.class);
        //
        // MinecraftForge.EVENT_BUS.register(CalamityEpicHandler.class);
        //
        // MinecraftForge.EVENT_BUS.register(EgoEpicHandler.class);

        modEventBus.addListener(this::addCreative);

        NetworkHandler.register();

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // [修复 P0-4] 同上，FateEpicHandler 已由注解自动注册。
        // MinecraftForge.EVENT_BUS.register(FateEpicHandler.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // 进度/成就用的自定义触发器：必须在通用初始化阶段登记，两边（客户端/服务端）都要有
        event.enqueueWork(() -> net.minecraft.advancements.CriteriaTriggers.register(YanEventTrigger.INSTANCE));
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // 占位方块已移除，创造模式标签页由 EXAMPLE_TAB 自己声明内容，这里不再挂东西
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }

    // 注册一个名为 "book" 的饰品槽，专门用来放书
    @SubscribeEvent
    public static void registerCuriosSlots(final InterModEnqueueEvent event) {
        InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE, () ->
                new SlotTypeMessage.Builder("book")
                        .icon(ResourceLocation.tryParse("yanscriptures:item/book_slot"))
                        .size(4)
                        .build()
        );
    }

    // 客户端专属事件（按键注册和客户端初始化）
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        // 注册按键映射
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(BLANK_PAGE_KEY);
            event.register(BOOK_OF_ME_Z_KEY);
            event.register(BOOK_OF_ME_L_KEY);
            event.register(ESCAPE_KEY);
            event.register(COVENANT_JUDGEMENT_KEY);
            event.register(COVENANT_FLIGHT_KEY);
            event.register(COMEDY_TELEPORT_KEY);
            event.register(DREAM_ENTER_KEY);
            event.register(ENUMA_KEY);

        }

        /**
         * 实体渲染器注册。
         *
         * <p>凡是我们自己注册了实体类型，就<b>必须</b>给它一个客户端渲染器。
         * 否则实体一出现在世界里，{@code EntityRenderDispatcher.shouldRender}
         * 会因为拿不到渲染器而抛 {@code NullPointerException} —— 整个游戏客户端直接崩溃。
         * 试炼启动时召唤的 {@code trial_summoner} 就是这样把客户端搞崩的。
         */
        @SubscribeEvent
        public static void registerRenderers(
                net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ExampleMod.TRIAL_SUMMONER.get(),
                    net.minecraft.client.renderer.entity.NoopRenderer::new);
            event.registerEntityRenderer(ExampleMod.CELESTIAL_ORB.get(),
                    com.example.examplemod.client.CelestialOrbRenderer::new);
            event.registerBlockEntityRenderer(BOOK_WRITING_TABLE_BE.get(),
                    com.example.examplemod.client.BookWritingTableRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // 1.20.1 没有 RegisterMenuScreensEvent，界面必须在客户端初始化时注册
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    BOOK_WRITING_TABLE_MENU.get(),
                    com.example.examplemod.client.BookWritingTableScreen::new));
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}