package com.example.examplemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /ys milestone ...} 调试指令。
 *
 * <p>由 {@link YanCommandRoot} 统一挂到根节点上，本类不再自己注册指令。
 *
 * <ul>
 *   <li>{@code list} —— 列出十四条可获取项的完成状态</li>
 *   <li>{@code grant <id>} —— 无视条件直接发放</li>
 *   <li>{@code reset} —— 清空全部标记，方便重测</li>
 * </ul>
 */
public final class MilestoneCommand {

    private MilestoneCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(MilestoneTracker.ALL, builder);

    /** 把 {@code milestone} 子树挂到根节点上。 */
    public static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("milestone")
                .then(Commands.literal("list")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(
                                    () -> Component.literal("§6===== 获取进度 ====="), false);
                            for (String line : MilestoneTracker.statusLines(player)) {
                                context.getSource().sendSuccess(
                                        () -> Component.literal(line), false);
                            }
                            return MilestoneTracker.ALL.size();
                        }))
                .then(Commands.literal("grantall")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int count = MilestoneTracker.forceGrantAll(player);
                            context.getSource().sendSuccess(
                                    () -> Component.literal(count > 0
                                            ? "§a已补发 §e" + count + " §a件获取物（含残卷，跳过违逆门槛）"
                                            : "§7没有可补发的东西——已经全都拿过了。"), true);
                            return count;
                        }))
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(IDS)
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (MilestoneTracker.itemOf(id) == null) {
                                        context.getSource().sendFailure(
                                                Component.literal("未知的 id：" + id));
                                        return 0;
                                    }
                                    if (!MilestoneTracker.forceGrant(player, id)) {
                                        context.getSource().sendFailure(Component.literal(
                                                "§e" + id + " §7已经拥有过了。"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("§a已补发 " + id), true);
                                    return 1;
                                })))
                .then(Commands.literal("selftest")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            List<MilestoneSelfTest.Result> results =
                                    MilestoneSelfTest.sorted(MilestoneSelfTest.run(player));
                            context.getSource().sendSuccess(
                                    () -> Component.literal("§6===== 获取判定自检 ====="), false);
                            int pass = 0;
                            int fail = 0;
                            int skip = 0;
                            for (MilestoneSelfTest.Result result : results) {
                                String head = switch (result.status()) {
                                    case PASS -> "§a✔ ";
                                    case FAIL -> "§c✘ ";
                                    case SKIP -> "§e～ ";
                                };
                                if (result.status() == MilestoneSelfTest.Status.PASS) pass++;
                                if (result.status() == MilestoneSelfTest.Status.FAIL) fail++;
                                if (result.status() == MilestoneSelfTest.Status.SKIP) skip++;
                                String line = head + "§f" + result.label()
                                        + " §8(" + result.id() + ") " + result.detail();
                                context.getSource().sendSuccess(
                                        () -> Component.literal(line), false);
                            }
                            String tail = "§7通过 §a" + pass + " §7· 失败 §c" + fail
                                    + " §7· 跳过 §e" + skip
                                    + " §8（自检不发放物品，存档标记已回滚）";
                            context.getSource().sendSuccess(
                                    () -> Component.literal(tail), false);
                            return pass;
                        }))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            MilestoneTracker.reset(player);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("§a已清空全部获取标记。"), true);
                            return 1;
                        })));
    }
}
