package com.example.examplemod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 本模组的全部调试指令子树。
 *
 * <p>根节点由 {@link YanCommandRoot} 统一注册（`/ys`），
 * 本类只负责把子命令挂上去。
 *
 * <pre>
 * /ys trial start|stop
 * /ys scripture curse|bless|uncomplete|reset
 * /ys escape
 * /ys debug clearcd|clearall|info|dummy
 * </pre>
 */
public final class YanBookCommand {

    private YanBookCommand() {
    }

    /** 把本类负责的子树挂到根节点上。 */
    public static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> root) {

        return root
                // ==================== 启示试炼 ====================
                .then(Commands.literal("trial")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                                        source.sendFailure(Component.literal("只能在服务端世界执行。"));
                                        return 0;
                                    }
                                    if (TrialManager.isRunning(player)) {
                                        source.sendFailure(Component.literal("§7你已经在试炼里了。"));
                                        return 0;
                                    }
                                    TrialManager.start(level, player, player.blockPosition());
                                    source.sendSuccess(() -> Component.literal(
                                            "§6启示试炼已开启（四波，间隔 30 秒）。"), true);
                                    return 1;
                                }))
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                                        source.sendFailure(Component.literal("只能在服务端世界执行。"));
                                        return 0;
                                    }
                                    boolean stopped = TrialManager.stop(level, player);
                                    if (stopped) {
                                        source.sendSuccess(() -> Component.literal(
                                                "§a试炼已终止，场地已清空，限伤与生物破坏已还原。"), true);
                                        return 1;
                                    }
                                    source.sendFailure(Component.literal("你当前不在试炼中。"));
                                    return 0;
                                })))

                // ==================== 十诫圣典 ====================
                .then(Commands.literal("scripture")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§6用法：§f/ys scripture curseall §7| §fcurse <1-10> §7| "
                                            + "§fbless <1-10> §7| §funcomplete <1-10> §7| §freset"), false);
                            return 1;
                        })
                        .then(Commands.literal("curseall")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    ItemStack scripture = getScripture(player);
                                    if (scripture.isEmpty()) {
                                        source.sendFailure(Component.literal("你身上没有圣典。"));
                                        return 0;
                                    }
                                    CompoundTag tag = scripture.getOrCreateTag();
                                    for (int i = 1; i <= 10; i++) {
                                        tag.putBoolean("Curse_" + i, true);
                                    }
                                    syncScripture(player);
                                    source.sendSuccess(() -> Component.literal(
                                            "§4已一次性触发全部 §e10 §4条诅咒。"), true);
                                    return 10;
                                }))
                        .then(Commands.literal("curse")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                                return 0;
                                            }
                                            int index = IntegerArgumentType.getInteger(context, "index");
                                            ItemStack scripture = getScripture(player);
                                            if (scripture.isEmpty()) {
                                                source.sendFailure(Component.literal("你身上没有圣典。"));
                                                return 0;
                                            }
                                            scripture.getOrCreateTag().putBoolean("Curse_" + index, true);
                                            syncScripture(player);
                                            source.sendSuccess(() -> Component.literal(
                                                    "§4已强制触发第 §e" + index + " §4条诅咒。"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("bless")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                                return 0;
                                            }
                                            int index = IntegerArgumentType.getInteger(context, "index");
                                            ItemStack scripture = getScripture(player);
                                            if (scripture.isEmpty()) {
                                                source.sendFailure(Component.literal("你身上没有圣典。"));
                                                return 0;
                                            }
                                            scripture.getOrCreateTag().remove("Curse_" + index);
                                            syncScripture(player);
                                            source.sendSuccess(() -> Component.literal(
                                                    "§b已清除第 §e" + index + " §b条诅咒。"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("uncomplete")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                                return 0;
                                            }
                                            int index = IntegerArgumentType.getInteger(context, "index");
                                            ItemStack scripture = getScripture(player);
                                            if (scripture.isEmpty()) {
                                                source.sendFailure(Component.literal("你身上没有圣典。"));
                                                return 0;
                                            }
                                            scripture.getOrCreateTag().remove("Completed_" + index);
                                            syncScripture(player);
                                            source.sendSuccess(() -> Component.literal(
                                                    "§b已撤销第 §e" + index + " §b诫的补全。"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    ItemStack scripture = getScripture(player);
                                    if (scripture.isEmpty()) {
                                        source.sendFailure(Component.literal("你身上没有圣典。"));
                                        return 0;
                                    }
                                    CompoundTag tag = scripture.getOrCreateTag();
                                    int count = 0;
                                    for (int i = 1; i <= 10; i++) {
                                        if (tag.getBoolean("Curse_" + i)) {
                                            tag.remove("Curse_" + i);
                                            count++;
                                        }
                                        if (tag.getBoolean("Completed_" + i)) {
                                            tag.remove("Completed_" + i);
                                            count++;
                                        }
                                    }
                                    syncScripture(player);
                                    final int finalCount = count;
                                    source.sendSuccess(() -> Component.literal(
                                            "§a已重置 §e" + finalCount + " §a项诅咒/补全。"), true);
                                    return count;
                                })))

                // ==================== 脱困 ====================
                .then(Commands.literal("escape")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                return 0;
                            }
                            boolean ok = EscapeController.forceEscape(player);
                            source.sendSuccess(() -> Component.literal(ok
                                    ? "§a已沿视线方向强制脱困。"
                                    : "§c脱困失败：范围内找不到可站立的位置。"), false);
                            return ok ? 1 : 0;
                        }))

                // ==================== 易经：手动定卦（测试用，不要求权限） ====================
                .then(Commands.literal("iching")
                        .then(Commands.argument("hexagram",
                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(IChingHandler.COMMAND_NAMES, builder))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    String name = com.mojang.brigadier.arguments.StringArgumentType
                                            .getString(context, "hexagram");
                                    int hex;
                                    if (name.equals("random")) {
                                        hex = player.getRandom().nextInt(IChingHandler.NAMES.length);
                                    } else {
                                        hex = java.util.Arrays.asList(IChingHandler.NAMES).indexOf(name);
                                        if (hex < 0) {
                                            source.sendFailure(Component.literal(
                                                    "§c不认识这个卦：" + name + "（可用：qian kun zhen kan li gen random）"));
                                            return 0;
                                        }
                                    }
                                    IChingHandler.setHexagram(player, hex);
                                    return 1;
                                })))

                // ==================== 直接生成一本带材料档位的书（整合包/测试用） ====================
                .then(Commands.literal("book")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("book", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(ExampleMod.CLASSIC_BOOKS.stream()
                                                .map(holder -> holder.getId().getPath())
                                                .toArray(String[]::new), builder))
                                .then(Commands.argument("paper", IntegerArgumentType.integer(1, 4))
                                        .then(Commands.argument("ink", IntegerArgumentType.integer(1, 3))
                                                .then(Commands.argument("thread", IntegerArgumentType.integer(1, 3))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                                                return 0;
                                                            }
                                                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                                                    .getString(context, "book");
                                                            net.minecraft.resources.ResourceLocation key =
                                                                    net.minecraft.resources.ResourceLocation.tryParse(
                                                                            name.contains(":")
                                                                                    ? name
                                                                                    : ExampleMod.MODID + ":" + name);
                                                            net.minecraft.world.item.Item item = key == null
                                                                    ? null
                                                                    : net.minecraftforge.registries.ForgeRegistries.ITEMS
                                                                            .getValue(key);
                                                            if (item == null) {
                                                                source.sendFailure(Component.literal(
                                                                        "§c没有这个物品：" + name));
                                                                return 0;
                                                            }
                                                            ItemStack stack = new ItemStack(item);
                                                            if (!stack.is(BookWritingTableBlockEntity.BOOKS_TAG)) {
                                                                source.sendFailure(Component.literal(
                                                                        "§c" + name + " 不是书（不在 #yanscriptures:books 里）。"));
                                                                return 0;
                                                            }
                                                            int[] tiers = {
                                                                    IntegerArgumentType.getInteger(context, "paper"),
                                                                    IntegerArgumentType.getInteger(context, "ink"),
                                                                    IntegerArgumentType.getInteger(context, "thread")
                                                            };
                                                            MaterialBonus.stamp(stack, tiers,
                                                                    MaterialBonus.rollAttrs(player.getRandom()));
                                                            if (!player.getInventory().add(stack)) {
                                                                player.drop(stack, false);
                                                            }
                                                            String shown = stack.getHoverName().getString();
                                                            source.sendSuccess(() -> Component.literal(
                                                                    "§a已生成 §e" + shown + "§a（纸 " + tiers[0]
                                                                            + " / 墨 " + tiers[1] + " / 线 " + tiers[2]
                                                                            + "，额外属性随机）"), true);
                                                            return 1;
                                                        })))))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§7用法：/ys book <书> <纸档 1-4> <墨档 1-3> <线档 1-3>"), false);
                            return 0;
                        }))

                // ==================== 调整《资本论》的资本（测试/整合包用） ====================
                .then(Commands.literal("capital")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                                return 0;
                                            }
                                            int delta = IntegerArgumentType.getInteger(context, "delta");
                                            CapitalHandler.addCapital(player, delta);
                                            long now = CapitalHandler.capitalOf(player);
                                            source.sendSuccess(() -> Component.literal(
                                                    "§a资本 " + (delta >= 0 ? "+" : "") + delta
                                                            + " → §e" + now
                                                            + "§a（攻击 +"
                                                            + String.format("%.0f%%",
                                                                    CapitalHandler.bonusOf(player) * 100.0D)
                                                            + "）"), true);
                                            return 1;
                                        })))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    CapitalHandler.setCapital(player, value);
                                    source.sendSuccess(() -> Component.literal(
                                            "§a资本已设为 §e" + CapitalHandler.capitalOf(player)
                                                    + "§a（攻击 +"
                                                    + String.format("%.0f%%",
                                                            CapitalHandler.bonusOf(player) * 100.0D)
                                                    + "）"), true);
                                    return 1;
                                }))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§7用法：/ys capital <数值>　或　/ys capital add <增减量>"), false);
                            return 0;
                        }))

                // ==================== 调试杂项 ====================
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("clearcd")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    int cleared = clearAllCooldowns(player);
                                    source.sendSuccess(() -> Component.literal(
                                            "§a已清除 §e" + cleared + " §a项冷却。"), true);
                                    return cleared;
                                }))
                        .then(Commands.literal("clearall")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    int count = clearAllState(player);
                                    final int finalCount = count;
                                    source.sendSuccess(() -> Component.literal(
                                            "§a已清理 §e" + finalCount + " §a项状态数据。"), true);
                                    return count;
                                }))
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    source.sendSuccess(() -> Component.literal(
                                            EscapeController.debugInfo(player)), false);
                                    return 1;
                                }))
                        .then(Commands.literal("flight")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    source.sendSuccess(() -> Component.literal(
                                            CritiqueOfPureReasonHandler.debugFlight(player)), false);
                                    return 1;
                                }))
                        .then(Commands.literal("boss")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    var candidates = player.level().getEntitiesOfClass(
                                            net.minecraft.world.entity.LivingEntity.class,
                                            player.getBoundingBox().inflate(16.0D),
                                            e -> e != player && e.isAlive());
                                    if (candidates.isEmpty()) {
                                        source.sendFailure(Component.literal(
                                                "§7附近 16 格内没有生物。"));
                                        return 0;
                                    }
                                    candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
                                    source.sendSuccess(() -> Component.literal(
                                            "§6===== 首领判定（最近 16 格，最多 8 只） ====="), false);
                                    int shown = 0;
                                    for (var entity : candidates) {
                                        if (shown++ >= 8) break;
                                        source.sendSuccess(() -> Component.literal(
                                                BossDetection.describe(entity)), false);
                                    }
                                    return shown;
                                }))
                        .then(Commands.literal("curio")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                        return 0;
                                    }
                                    source.sendSuccess(() -> Component.literal(
                                            "§6===== 饰品栏分类 ====="), false);
                                    for (String line : CurioExemption.audit(player)) {
                                        source.sendSuccess(() -> Component.literal(line), false);
                                    }
                                    boolean shema = MilestoneTracker.shemaCurioOk(player);
                                    source.sendSuccess(() -> Component.literal(
                                            "§6===== 示马判定 ====="), false);
                                    source.sendSuccess(() -> Component.literal(
                                            "§f除豁免表外"
                                                    + (shema ? "§a只有本模组饰品 → 通过"
                                                    : "§c还混有别家饰品 → 不通过")), false);
                                    return 1;
                                }))
                        .then(Commands.literal("dummy")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    var level = source.getLevel();
                                    var pos = net.minecraft.core.BlockPos.containing(source.getPosition());
                                    var dummy = TrainingDummy.spawn(level, pos, "训练假人");
                                    if (dummy == null) {
                                        source.sendFailure(Component.literal("生成失败。"));
                                        return 0;
                                    }
                                    source.sendSuccess(() -> Component.literal(
                                            "§a已生成训练假人（打不死；§f/ys debug dummy clear§a 直接清除）"), false);
                                    return 1;
                                })
                                .then(Commands.literal("clear")
                                        .executes(context -> clearDummies(context.getSource(), 32.0D))
                                        .then(Commands.argument("range",
                                                        com.mojang.brigadier.arguments.DoubleArgumentType
                                                                .doubleArg(1.0D, 256.0D))
                                                .executes(context -> clearDummies(context.getSource(),
                                                        com.mojang.brigadier.arguments.DoubleArgumentType
                                                                .getDouble(context, "range")))))));
    }

    /** 从玩家饰品栏或背包中找到圣典。 */
    private static ItemStack getScripture(ServerPlayer player) {
        var curiosResult = CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()));
        if (curiosResult.isPresent()) {
            return curiosResult.get().stack();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 直接清除附近的训练假人（discard，不走死亡）。 */
    private static int clearDummies(CommandSourceStack source, double radius) {
        int removed = TrainingDummy.clear(source.getLevel(), source.getPosition(), radius);
        if (removed == 0) {
            source.sendFailure(Component.literal("§7" + (int) radius + " 格内没有训练假人。"));
            return 0;
        }
        final int n = removed;
        source.sendSuccess(() -> Component.literal(
                "§a已直接清除 §e" + n + " §a只训练假人（未触发死亡）。"), true);
        return n;
    }

    /** 改完圣典 NBT 后让客户端与饰品栏看到新状态。 */
    private static void syncScripture(ServerPlayer player) {
        player.getInventory().setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }

    /** 清除玩家所有冷却。 */
    public static int clearAllCooldowns(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        List<String> keysToRemove = new ArrayList<>();
        for (String key : persistentData.getAllKeys()) {
            if (isCooldownKey(key)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            persistentData.remove(key);
        }
        return keysToRemove.size();
    }

    /**
     * 判断一个 player persistent data 键是不是「冷却 / 到点才可用」的计时器。
     *
     * <p>旧的实现只看 {@code TitanEpic_} 前缀和 {@code Cooldown} 字样，
     * 于是 {@code ComedyReadyAt}（神曲）、{@code Covenant_Judgement_CD}、
     * {@code BookOfMe_Rational_CD}、{@code EucResetAt} 这些全都漏掉了。
     */
    private static boolean isCooldownKey(String key) {
        return key.startsWith("TitanEpic_")
                || key.contains("Cooldown")
                || key.endsWith("_CD")
                || key.endsWith("ReadyAt")
                || key.endsWith("_LastTrigger")
                || key.endsWith("_ImmuneUntil")
                || key.equals("EucResetAt");
    }

    /** 玩家身上属于本模组的状态键前缀（供 {@code debug clearall} 使用）。 */
    private static final String[] STATE_PREFIXES = {
            "Fate", "BookOfMe", "Ego", "Pillar", "Creation", "Calamity", "TitanEpic_",
            "Dao", "Euc", "Origin", "Nine", "Comedy", "Republic", "Yan", "Covenant",
            "principia_", "brewed_", "drunk_"
    };

    /** 清掉本模组写在玩家 persistent data 里的全部状态（含九本书的进度与冷却）。 */
    public static int clearAllState(Player player) {
        CompoundTag data = player.getPersistentData();
        List<String> toRemove = new ArrayList<>();
        for (String key : data.getAllKeys()) {
            for (String prefix : STATE_PREFIXES) {
                if (key.startsWith(prefix)) {
                    toRemove.add(key);
                    break;
                }
            }
        }
        for (String key : toRemove) {
            data.remove(key);
        }
        return toRemove.size();
    }
}
