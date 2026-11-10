package com.example.examplemod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;

/**
 * 创造飞行的「多方申请」表。
 *
 * <p>起因（2026-11-08）：本模组里同时有几个东西会动 {@code abilities.mayfly}——
 * 《如我所书》（没戴就每 tick 关掉）、《圣约·原典》（F 键禁用时每 tick 关掉）、
 * 《纯粹理性批判》（条件达成才给）。它们互相不知道对方存在，于是后 tick 的那个
 * 会把前一个刚打开的飞行直接抹平，表现就是「条件达成了却飞不起来 / 一飞就掉」。
 *
 * <p>现在改成：每个来源只声明「我要不要飞」，由这里汇总成唯一的最终答案。
 * 谁撤掉自己那一份都不影响别人。
 */
public final class FlightPermissions {

    /** 来源名 → 玩家。 */
    public static final String BOOK_OF_ME = "book_of_me";
    public static final String COVENANT = "covenant_codex";
    public static final String CRITIQUE = "critique_of_pure_reason";

    private static final Map<UUID, Set<String>> SOURCES = new HashMap<>();

    /**
     * 一票否决表：某些来源不仅要「不申请」，还要**压住别人给的飞行**
     * （目前只有《圣约·原典》的 F 键：作者明确要求按了就是飞不了，哪怕同时还戴着别的会飞的书）。
     */
    private static final Map<UUID, Set<String>> VETOES = new HashMap<>();

    private FlightPermissions() {
    }

    /**
     * 声明某个来源是否需要飞行。
     *
     * <p>重复声明同一状态不会重复发包；只有当「总开关」真的变了才同步给客户端。
     */
    public static void set(Player player, String source, boolean active) {
        UUID id = player.getUUID();
        Set<String> sources = SOURCES.computeIfAbsent(id, key -> new HashSet<>());
        boolean changed = active ? sources.add(source) : sources.remove(source);
        if (!changed) {
            return;
        }
        apply(player, effective(id));
        if (sources.isEmpty()) {
            SOURCES.remove(id);
        }
    }

    /**
     * 一票否决：{@code active} 为真时，不管别人怎么申请，这个人都不许飞。
     *
     * <p>用于《圣约·原典》的 F 键——作者要的是「按了就是飞不了」，
     * 而不是「只是这本不给你飞」。
     */
    public static void veto(Player player, String source, boolean active) {
        UUID id = player.getUUID();
        Set<String> vetoes = VETOES.computeIfAbsent(id, key -> new HashSet<>());
        boolean changed = active ? vetoes.add(source) : vetoes.remove(source);
        if (!changed) {
            return;
        }
        apply(player, effective(id));
        if (vetoes.isEmpty()) {
            VETOES.remove(id);
        }
    }

    /** 汇总：有申请且没人否决，才允许飞。 */
    private static boolean effective(UUID id) {
        Set<String> sources = SOURCES.get(id);
        Set<String> vetoes = VETOES.get(id);
        if (sources == null || sources.isEmpty()) {
            return false;
        }
        return vetoes == null || vetoes.isEmpty();
    }

    /** 是否有任意一个来源正在申请（且没有被否决）。 */
    public static boolean granted(Player player) {
        return effective(player.getUUID());
    }

    /** 玩家离线/重生时清账（飞行能力本身由原版重置）。 */
    public static void clear(UUID playerId) {
        SOURCES.remove(playerId);
        VETOES.remove(playerId);
    }

    private static void apply(Player player, boolean allow) {
        if (player == null) {
            return;
        }
        // 创造 / 旁观自带飞行，不归我们管：作者 2026-11-09 报「创造模式不能飞了」，
        // 原因就是这里在「没有任何来源申请」时把创造玩家的 mayfly 也按成了 false。
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        boolean changed = false;
        if (player.getAbilities().mayfly != allow) {
            player.getAbilities().mayfly = allow;
            changed = true;
        }
        if (!allow && player.getAbilities().flying) {
            // 只在「收回」时清 flying；给权限时绝不碰它，否则会把正在飞的玩家拽下来。
            player.getAbilities().flying = false;
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    static {
        SessionCleanup.register(FlightPermissions::clear);
    }
}
