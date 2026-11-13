package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 「作者后门：至理之书已发放」的存档级账本。
 *
 * <p>和 {@link StarterKitData} 同样的理由：玩家自己的持久化数据跨重生/克隆要靠 Forge 复制，
 * 一旦漏掉就会重复发放；这里按玩家 UUID 记在存档里，一个存档内每个 ID 只发一次。</p>
 *
 * <p>只记 UUID，不记名字：名字可以改，UUID 是稳定的。</p>
 */
public class AuthorGiftData extends SavedData {

    /** 存档里的文件名。 */
    private static final String DATA_NAME = "yanscriptures_author_gift";

    private final Set<UUID> granted = new HashSet<>();

    public static AuthorGiftData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(AuthorGiftData::load, AuthorGiftData::new, DATA_NAME);
    }

    /** 这个玩家是否已经领过。 */
    public boolean has(UUID playerId) {
        return this.granted.contains(playerId);
    }

    /** 标记为已领，返回 true 表示这次是首次（之前没记过）。 */
    public boolean mark(UUID playerId) {
        if (!this.granted.add(playerId)) {
            return false;
        }
        this.setDirty();
        return true;
    }

    public static AuthorGiftData load(CompoundTag tag) {
        AuthorGiftData data = new AuthorGiftData();
        ListTag list = tag.getList("Granted", Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            try {
                data.granted.add(UUID.fromString(list.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // 坏数据跳过，不影响其它玩家
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : this.granted) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("Granted", list);
        return tag;
    }
}
