package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YanBookCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("yanbook")
                // 原有的清除冷却指令
                .then(Commands.literal("clearcd")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                return 0;
                            }
                            int cleared = clearAllCooldowns(player);
                            source.sendSuccess(() -> Component.literal("§a已清除 §e" + cleared + " §a项冷却。"), true);
                            return cleared;
                        })
                )
                // 清除所有状态数据指令
                .then(Commands.literal("clearall")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("只有玩家可以执行此命令。"));
                                return 0;
                            }
                            int count = 0;
                            CompoundTag data = player.getPersistentData();
                            List<String> toRemove = new ArrayList<>();
                            for (String key : data.getAllKeys()) {
                                if (key.startsWith("Fate") || key.startsWith("BookOfMe") ||
                                        key.startsWith("Ego") || key.startsWith("Pillar") || key.startsWith("Creation")) {
                                    toRemove.add(key);
                                }
                            }
                            for (String key : toRemove) {
                                data.remove(key);
                                count++;
                            }
                            final int finalCount = count;
                            source.sendSuccess(() -> Component.literal("§a已清理 §e" + finalCount + " §a项状态数据。"), true);
                            return count;
                        })
                )
                // 🚨 新增：圣典相关的调试指令（多加一层 scripture）
                .then(Commands.literal("scripture")
                        // /yanbook scripture curse <1-10>
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
                                            source.sendSuccess(() -> Component.literal("§4已强制触发第 §e" + index + " §4条诅咒。"), true);
                                            return 1;
                                        })
                                )
                        )
                        // /yanbook scripture bless <1-10>
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
                                            source.sendSuccess(() -> Component.literal("§b已清除第 §e" + index + " §b条诅咒。"), true);
                                            return 1;
                                        })
                                )
                        )
                        // /yanbook scripture reset —— 清除所有十诫诅咒
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
                                    }
                                    final int finalCount = count;
                                    source.sendSuccess(() -> Component.literal("§a已重置 §e" + finalCount + " §a条诅咒。"), true);
                                    return count;
                                })
                        )
                )
        );
    }

    // 从玩家饰品栏或背包中找到圣典
    private static ItemStack getScripture(ServerPlayer player) {
        // 1. 先查饰品栏
        var curiosResult = CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inv -> inv.findFirstCurio(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get()));
        if (curiosResult.isPresent()) {
            return curiosResult.get().stack();
        }
        // 2. 再查背包
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ExampleMod.SCRIPTURE_OF_COMMANDMENTS.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    // 清除玩家所有冷却
    public static int clearAllCooldowns(Player player) {
        int count = 0;
        CompoundTag persistentData = player.getPersistentData();
        List<String> keysToRemove = new ArrayList<>();
        for (String key : persistentData.getAllKeys()) {
            if (key.startsWith("TitanEpic_") || key.contains("Cooldown")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            persistentData.remove(key);
            count++;
        }
        return count;
    }
}