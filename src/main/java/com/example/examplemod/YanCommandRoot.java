package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 本模组唯一的指令根：{@code /ys}。
 *
 * <p>所有子命令由各功能类通过 {@code build(root)} 挂上来，避免出现多个根、
 * 也避免子命令互相嵌套错位。曾经还有一个 {@code /yanscriptures} 全名别名，
 * 作者要求「完整版和缩写版只留一个」（2026-11-01），已删除，只保留 {@code /ys}。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YanCommandRoot {

    private YanCommandRoot() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ys");
        root = YanBookCommand.build(root);
        root = MilestoneCommand.build(root);
        root = EmptyBookshelfItem.buildCommand(root);
        root = PerfProbe.build(root);

        dispatcher.register(root);
    }
}
