package com.example.examplemod;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 每 tick 只查一次饰品栏，其余地方读缓存。
 *
 * <p>为什么要有这个类：{@link ClassicBooksHandler} 一个 tick 里会问好几次「戴着哪本书」，
 * 旧写法每次问都遍历一遍 Curios 的所有槽位（{@code getCurios()} → 每个 slot handler → 每格）。
 * 一本书一 tick 六次、二十个玩家就是上百次全量遍历。查询本身不重，但重复得没有必要。
 *
 * <p>这个类不挪动任何玩法逻辑，只把「同一份事实」算一次：{@link #refresh(Player)} 扫一遍，
 * 记下「戴着哪些书 + 这些书的物品栈」，之后 {@link #wears(Player, ClassicBook)} 是 O(1)。
 *
 * <p>刻意保留的兜底：如果某个玩家这一 tick 还没 refresh 过（刚登录、刚进维度、
 * 客户端侧查询），{@link #snapshot(Player)} 会现场扫一次而不是返回空，
 * 免得「缓存未命中」被误当成「没戴」。
 */
public final class WornCache {

    /** 一个玩家这一 tick 的佩戴快照。 */
    public static final class Snapshot {
        private final Set<ClassicBook> books;
        private final int signature;

        private Snapshot(Set<ClassicBook> books, int signature) {
            this.books = books;
            this.signature = signature;
        }

        public boolean wears(ClassicBook book) {
            return books.contains(book);
        }

        /** 佩戴情况的指纹，用来判断「和上一 tick 一样、不用重建属性修正器」。 */
        public int signature() {
            return signature;
        }

        public Set<ClassicBook> books() {
            return books;
        }
    }

    private static final Snapshot EMPTY = new Snapshot(EnumSet.noneOf(ClassicBook.class), 0);

    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();

    private WornCache() {
    }

    /**
     * 重算这个玩家的佩戴快照。每个 tick 的玩家事件里调用一次即可。
     *
     * <p>指纹 = 每本书的 {@code 物品 hashCode} 混进 {@code book.ordinal()}，
     * 换书、换附魔后的同一本书（物品栈变了）都会让指纹变化。
     */
    public static void refresh(Player player) {
        SNAPSHOTS.put(player.getUUID(), scan(player));
    }

    public static Snapshot snapshot(Player player) {
        Snapshot cached = SNAPSHOTS.get(player.getUUID());
        return cached != null ? cached : scan(player);
    }

    public static boolean wears(Player player, ClassicBook book) {
        return player != null && book != null && snapshot(player).wears(book);
    }

    /** 玩家下线时清掉，避免旧 UUID 一直占内存。 */
    public static void cleanup(UUID playerId) {
        SNAPSHOTS.remove(playerId);
    }

    public static void cleanupAll() {
        SNAPSHOTS.clear();
    }

    private static Snapshot scan(Player player) {
        Set<ClassicBook> books = EnumSet.noneOf(ClassicBook.class);
        int signature = 17;
        var handler = CuriosApi.getCuriosInventory(player).resolve();
        if (handler.isEmpty()) {
            return EMPTY;
        }
        for (var entry : handler.get().getCurios().entrySet()) {
            var slots = entry.getValue();
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStacks().getStackInSlot(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof ClassicBookItem item)) {
                    continue;
                }
                ClassicBook book = item.book();
                if (books.add(book)) {
                    signature = signature * 31 + book.ordinal();
                    signature = signature * 31 + stack.getItem().hashCode();
                }
            }
        }
        return books.isEmpty() ? EMPTY : new Snapshot(books, signature);
    }
}
