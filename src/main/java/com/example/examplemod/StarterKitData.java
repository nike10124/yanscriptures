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
 * 「开局赠礼已发放」的存档级账本。
 *
 * <p>为什么不用玩家自己的持久化数据（{@code player.getPersistentData()}）：
 * 那份数据跟着**玩家实体**走，跨维度传送、死亡重生、克隆时都要靠 Forge 复制，
 * 一旦某个环节没复制上，玩家重进就会再收到一次赠礼——这正是实测里出现的
 * 「只要背包里没有，就又会给一份」。</p>
 *
 * <p>改为按玩家 UUID 记在**存档**里：一个存档内每人只发一次，之后无论玩家
 * 把书扔了、放进箱子、用指令删掉，都不会再补发。换存档/换服务器会重新发一次，
 * 这是有意的——新存档就是新的一轮。</p>
 */
public class StarterKitData extends SavedData {

    /** 存档里的文件名。 */
    private static final String DATA_NAME = "yanscriptures_starter_kit";

    private final Set<UUID> granted = new HashSet<>();

    public static StarterKitData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(StarterKitData::load, StarterKitData::new, DATA_NAME);
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

    public static StarterKitData load(CompoundTag tag) {
        StarterKitData data = new StarterKitData();
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
