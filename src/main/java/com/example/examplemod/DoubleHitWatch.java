package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「一次攻击打出两次伤害」的定位工具。
 *
 * <p>背景（作者 m12414）：拿着〈断曜流光〉时，偶尔一刀打出两次伤害，本该打不死的怪被打死了，
 * 玩家会怀疑是我们这边某个机制在偷偷补刀。我们自己确实有两处「同一 tick 两笔」的正当机制：
 * 《理想国》影子转伤、《易经》乾卦 50% 补刀 —— 这两种不算异常，本工具会把它们过滤掉，
 * 只把**剩下那些解释不了的**两次伤害写进日志。</p>
 *
 * <p>日志形如（`logs/latest.log` 搜 `两次伤害`）：</p>
 * <pre>
 * [CHAT] 两次伤害：Skeleton 同一 tick 挨了 2 笔（玩家 Steve，伤害源 player，8.0 → 12.0）
 * </pre>
 *
 * <p>如果日志里出现的是**别的模组**的伤害源（例如 `revelationfix` 之类的 id），
 * 那就与我们无关，是那把武器自己打了两下；如果压根没有这行，说明两次伤害来自同一个
 * 伤害源、只是被别的东西放大了（那应当是暴击，而不是两笔）。</p>
 */
@Mod.EventBusSubscriber
public final class DoubleHitWatch {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 上一次「玩家 → 生物」的伤害记账：key = 玩家 uuid + 目标 uuid。 */
    private static final Map<String, Integer> LAST_TICK = new HashMap<>();
    private static final Map<String, Float> LAST_AMOUNT = new HashMap<>();

    private DoubleHitWatch() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (ClassicBooksHandler.isRepublicShadow(target)) {
            return;
        }
        // 我们自己那两处「同一 tick 两笔」：正当机制，不记
        if (ClassicBooksHandler.isTransferring() || IChingHandler.isExtraHit()) {
            return;
        }

        String key = player.getUUID() + "→" + target.getUUID();
        Integer last = LAST_TICK.get(key);
        if (last != null && last == player.tickCount) {
            Float before = LAST_AMOUNT.get(key);
            LOGGER.warn("两次伤害：{} 同一 tick 挨了 2 笔（玩家 {}，伤害源 {}，{} → {}）",
                    target.getName().getString(), player.getName().getString(),
                    event.getSource().getMsgId(),
                    before == null ? "?" : String.format("%.1f", before),
                    String.format("%.1f", event.getAmount()));
        }
        LAST_TICK.put(key, player.tickCount);
        LAST_AMOUNT.put(key, event.getAmount());
        if (LAST_TICK.size() > 128) {
            LAST_TICK.clear();
            LAST_AMOUNT.clear();
        }
    }
}
