package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家下线时的统一清理。
 *
 * <p>各处理器里有不少「按 UUID 记账」的静态 Map（生命值基线、入场演出标记、
 * 背包快照、脱困采样、佩戴缓存等）。这些 Map 原来只增不减，单人游戏里看不出来，
 * 但开服务器时会长住已离线玩家的数据 —— 既是内存泄漏，也会让「重新进服」的
 * 玩家继承上一轮的残留状态。
 *
 * <p><b>2026-10-30 改法</b>：从「记得来这里加一行」改成注册表。
 * 任何按玩家记账的类都在自己的静态初始化里写一行 {@link #register(Consumer)}，
 * 这个类不需要再被改动。漏掉注册的风险仍在（类不初始化就不会注册），
 * 但至少不必再跨文件改同一处；下面的静态块把现有已知的清理器全部登记，
 * 新增的类照着抄一行即可。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SessionCleanup {

    /** 每个清理器拿到「下线玩家的 UUID」。 */
    private static final Logger LOG = LogUtils.getLogger();

    private static final List<Consumer<UUID>> CLEANERS = new ArrayList<>();

    static {
        register(CovenantCodexHandler::cleanupPlayer);
        register(CommandmentsHandler::cleanupPlayer);
        register(DpsMeter::cleanupPlayer);
        register(WornCache::cleanup);
        register(EscapeController::cleanupPlayer);
        // 2026-11-10（D 批「状态残留」）：下面这几个表的玩家条目原来从不清理
        register(ClassicBooksHandler::cleanupPlayer);
        register(IChingHandler::cleanupPlayer);
        register(ThePrinceHandler::cleanupPlayer);
        register(ThirtySixStrategiesHandler::cleanupPlayer);
        register(OneThousandAndOneNightsHandler::cleanupPlayer);
        register(CapitalHandler::cleanupPlayer);
        register(JudgementController::cleanupPlayer);
    }

    private SessionCleanup() {
    }

    /**
     * 登记一个清理器。
     *
     * <p>约定：清理器必须能容忍「这个玩家本来就没有数据」，
     * 一律用 {@code Map.remove} / {@code Set.remove}，不要假设存在。
     */
    public static void register(Consumer<UUID> cleaner) {
        if (cleaner != null && !CLEANERS.contains(cleaner)) {
            CLEANERS.add(cleaner);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        for (Consumer<UUID> cleaner : CLEANERS) {
            try {
                cleaner.accept(id);
            } catch (Exception e) {
                LOG.warn("[yanscriptures] 清理玩家 {} 时出错：{}", id, e.toString());
            }
        }
    }
}
