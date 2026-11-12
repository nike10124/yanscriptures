package com.example.examplemod;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 轻量性能探针（2026-11-12，作者报「戴多本书掉帧」时加的）。
 *
 * <p>只回答两个问题：每个 tick 花在这套事件里的时间是多少、每帧花在 HUD 上的时间是多少。
 * 做法很土但不需要装 profiler：在 {@code PlayerTickEvent.END} 与 {@code RenderGuiEvent.Post}
 * 上各挂一头一尾两个监听器（HIGHEST / LOWEST），差值就是「所有监听器加起来的耗时」。
 *
 * <p>注意：这个差值包含别的模组的监听器，不全是我们的。单机里 tick 与渲染同线程，
 * tick 变慢会直接吃掉帧时间，所以两个数要一起看。
 *
 * <p>用 {@code /ys perf} 读，或用 {@code /ys perf reset} 清零重新量。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerfProbe {

    private static long tickStart = 0L;
    private static long tickNanos = 0L;
    private static long tickSamples = 0L;

    private static long renderStart = 0L;
    private static long renderNanos = 0L;
    private static long renderSamples = 0L;

    /** 单个 tick 超过这个值（毫秒）就记一次「慢 tick」，用于判断是不是偶发卡顿。 */
    private static final double SLOW_TICK_MS = 8.0D;
    private static long slowTicks = 0L;
    private static double worstTickMs = 0.0D;

    private PerfProbe() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickStart = System.nanoTime();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tickStart == 0L) {
            return;
        }
        double ms = (System.nanoTime() - tickStart) / 1_000_000.0D;
        tickStart = 0L;
        tickNanos += (long) (ms * 1_000_000.0D);
        tickSamples++;
        if (ms > SLOW_TICK_MS) {
            slowTicks++;
        }
        if (ms > worstTickMs) {
            worstTickMs = ms;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderStart(RenderGuiEvent.Post event) {
        renderStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderEnd(RenderGuiEvent.Post event) {
        if (renderStart == 0L) {
            return;
        }
        renderNanos += System.nanoTime() - renderStart;
        renderStart = 0L;
        renderSamples++;
    }

    /** 挂到 {@code /ys} 根上。 */
    public static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("perf")
                .executes(ctx -> report(ctx.getSource()))
                .then(Commands.literal("reset").executes(ctx -> {
                    reset();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a性能计数已清零"), false);
                    return 1;
                })));
    }

    private static void reset() {
        tickNanos = 0L;
        tickSamples = 0L;
        renderNanos = 0L;
        renderSamples = 0L;
        slowTicks = 0L;
        worstTickMs = 0.0D;
        tickStart = 0L;
        renderStart = 0L;
    }

    private static int report(CommandSourceStack source) {
        double tickAvg = tickSamples == 0 ? 0.0D : tickNanos / 1_000_000.0D / tickSamples;
        double frameAvg = renderSamples == 0 ? 0.0D : renderNanos / 1_000_000.0D / renderSamples;
        int modifiers = modifierCount(source);
        int effects = source.getPlayer() == null ? 0 : source.getPlayer().getActiveEffects().size();

        source.sendSuccess(() -> Component.literal("§6[性能] §f样本 "
                + tickSamples + " tick / " + renderSamples + " 帧"), false);
        source.sendSuccess(() -> Component.literal(
                "§7每 tick 事件耗时 §f" + String.format("%.3f", tickAvg) + " ms"
                        + "§7（最慢 §f" + String.format("%.2f", worstTickMs) + " §7ms，超 "
                        + String.format("%.0f", SLOW_TICK_MS) + "ms 的有 §f" + slowTicks + " §7次）"), false);
        source.sendSuccess(() -> Component.literal(
                "§7每帧 HUD 耗时 §f" + String.format("%.3f", frameAvg) + " ms"), false);
        source.sendSuccess(() -> Component.literal(
                "§7属性修饰符 §f" + modifiers + " §7个 / 状态效果 §f" + effects + " §7个"), false);
        return 1;
    }

    /** 玩家身上挂了多少条属性修饰符——这个数会随佩戴的书线性增长。 */
    private static int modifierCount(CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }
        AttributeMap map = source.getPlayer().getAttributes();
        int total = 0;
        for (var attribute : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = map.getInstance(attribute);
            if (instance != null) {
                total += instance.getModifiers().size();
            }
        }
        return total;
    }
}
