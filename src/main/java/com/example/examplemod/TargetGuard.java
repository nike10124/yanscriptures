package com.example.examplemod;

import net.minecraft.world.entity.Mob;

/**
 * 清空生物目标的安全封装。
 *
 * <h2>为什么需要它</h2>
 * {@code Mob#setTarget} 内部会触发 {@code LivingChangeTargetEvent}。
 * 如果在**该事件的处理器里**直接调用 {@code setTarget(null)}，事件会再次派发、
 * 又进入同一个处理器、再次调用……形成无限递归，最终 {@code StackOverflowError}。
 * 而 Forge 的事件总线在记录这个 Error 时还会撞上 log4j 的加载器冲突，
 * 把一次调用写错放大成整局崩溃。
 *
 * <p>这里用一个 ThreadLocal 重入标记：递归进来的那一层直接放过，不做任何拦截，
 * 让 {@code setTarget(null)} 正常完成。
 */
public final class TargetGuard {

    private static final ThreadLocal<Boolean> CLEARING = ThreadLocal.withInitial(() -> false);

    private TargetGuard() {
    }

    /** 当前是否正处在「我们主动清空目标」的调用里。事件处理器应当据此放弃拦截。 */
    public static boolean isClearing() {
        return CLEARING.get();
    }

    /**
     * 安全清空目标：会触发一次重入的事件派发，但那一层不会再次清空，递归到此为止。
     */
    public static void clearTarget(Mob mob) {
        if (CLEARING.get()) {
            return;
        }
        CLEARING.set(true);
        try {
            mob.setTarget(null);
        } finally {
            CLEARING.set(false);
        }
    }

    /**
     * 在「放行」状态下设置目标。
     *
     * <h2>为什么需要它</h2>
     * 「万邦止戈」会拦截生物锁定佩戴者。但有些 Boss 的伤害逻辑要求「它正在锁定你」才肯掉血 ——
     * 于是一旦玩家主动攻击它，就要反过来把它的目标设回玩家，让它能正常还手。
     * 这个设置同样会触发目标变更事件，必须走放行通道，否则会被自己的拦截器吃掉。
     */
    public static void setTargetBypassingGuard(Mob mob, net.minecraft.world.entity.LivingEntity target) {
        if (CLEARING.get()) {
            return;
        }
        CLEARING.set(true);
        try {
            mob.setTarget(target);
        } finally {
            CLEARING.set(false);
        }
    }
}
