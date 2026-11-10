package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
    public static final String MODID = "examplemod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    // 注册饰品
    public static final RegistryObject<Item> BOOK_OF_PEACE = ITEMS.register("book_of_peace",
            () -> new BookOfPeaceItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PRINCIPIA = ITEMS.register("principia",
            () -> new BookOfPrinciplesItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EMERALD_TABLET = ITEMS.register("emerald_tablet",
            () -> new BookOfEmeraldTabletItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLANK_PAGE = ITEMS.register("blank_page",
            () -> new BlankPageItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EPIC_FATE = ITEMS.register("epic_fate",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1), "fate", 300000));
    public static final RegistryObject<Item> EPIC_CREATION = ITEMS.register("epic_creation",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1), "creation", 120000));
    public static final RegistryObject<Item> EPIC_PILLAR = ITEMS.register("epic_pillar",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1), "pillar", 180000));
    public static final RegistryObject<Item> EPIC_CALAMITY = ITEMS.register("epic_calamity",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1), "calamity", 180000));
    public static final RegistryObject<Item> EPIC_EGO = ITEMS.register("epic_ego",
            () -> new TitanEpicItem(new Item.Properties().stacksTo(1), "ego", 300000));
    public static final RegistryObject<Item> BOOK_OF_ME = ITEMS.register("book_of_me",
            () -> new BookOfMeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SCRIPTURE_OF_COMMANDMENTS = ITEMS.register("scripture_of_commandments",
            () -> new ScriptureOfCommandmentsItem(new Item.Properties().stacksTo(1)));

    // ==================== 十诫刻蚀残卷（2026-10-22）====================
    // 十张残卷各对应一条诫，另一张「启示残卷」不属十诫。
    // 当前只做注册与文案，诅咒反转机制尚未接入（见 ScriptureScrollItem 类注释）。
    private static RegistryObject<Item> scroll(String id, String key) {
        return ITEMS.register(id, () -> new ScriptureScrollItem(new Item.Properties().stacksTo(1), key));
    }

    public static final RegistryObject<Item> SCROLL_SHEMA      = scroll("scripture_of_shema", "shema");
    public static final RegistryObject<Item> SCROLL_SILENCE    = scroll("scripture_of_silence", "silence");
    public static final RegistryObject<Item> SCROLL_THORNS     = scroll("scripture_of_thorns", "thorns");
    public static final RegistryObject<Item> SCROLL_SABBATH    = scroll("scripture_of_sabbath", "sabbath");
    public static final RegistryObject<Item> SCROLL_REBELLION  = scroll("scripture_of_rebellion", "rebellion");
    public static final RegistryObject<Item> SCROLL_BLOOD      = scroll("scripture_of_blood", "blood");
    public static final RegistryObject<Item> SCROLL_HARP       = scroll("scripture_of_harp", "harp");
    public static final RegistryObject<Item> SCROLL_SHINAR     = scroll("scripture_of_shinar", "shinar");
    public static final RegistryObject<Item> SCROLL_TONGUE     = scroll("scripture_of_tongue", "tongue");
    public static final RegistryObject<Item> SCROLL_VANITY     = scroll("scripture_of_vanity", "vanity");
    public static final RegistryObject<Item> SCROLL_REVELATION = scroll("scripture_of_revelation", "revelation");

    /** 十张诫卷，按第一诫到第十诫的顺序；创造页签按此顺序排列。 */
    public static final java.util.List<RegistryObject<Item>> SCROLLS = java.util.List.of(
            SCROLL_SHEMA, SCROLL_SILENCE, SCROLL_THORNS, SCROLL_SABBATH, SCROLL_REBELLION,
            SCROLL_BLOOD, SCROLL_HARP, SCROLL_SHINAR, SCROLL_TONGUE, SCROLL_VANITY);



    // 🚨 新增：空白的书页的快捷键定义
    public static final KeyMapping BLANK_PAGE_KEY = new KeyMapping(
            "key.examplemod.blank_page",           // 语言键
            InputConstants.Type.KEYSYM,            // 按键类型
            GLFW.GLFW_KEY_V,                       // 默认 V 键
            "key.categories.examplemod"            // 分类
    );
    public static final KeyMapping BOOK_OF_ME_Z_KEY = new KeyMapping(
            "key.examplemod.book_of_me_z",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.examplemod"
    );
    public static final KeyMapping BOOK_OF_ME_L_KEY = new KeyMapping(
            "key.examplemod.book_of_me_l",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.examplemod"
    );
    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BLANK_PAGE.get().getDefaultInstance())
            // 新增这一行，显式指定标题
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.examplemod.example_tab"))
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
            }).build());

    public ExampleMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(ExampleMod::registerCuriosSlots);
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

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
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(EXAMPLE_BLOCK_ITEM);
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
                        .icon(ResourceLocation.tryParse("examplemod:item/book_slot"))
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

        }

        ;
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}