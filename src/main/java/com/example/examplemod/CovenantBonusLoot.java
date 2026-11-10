package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「圣约专属额外掉落」的数据表。
 *
 * <p>某些物品本来由别的模组以极低概率产出（或者根本不再产出），
 * 而毕业饰品希望它们「必定到手」。直接把这种东西写进代码会带来两个问题：
 * 一是硬编码别人的实体 ID，二是会变成全局生效、影响所有玩家。
 *
 * <p>所以改成数据驱动 + 佩戴者限定：
 * <ul>
 *   <li>数据文件放在 {@code data/yanscriptures/covenant_bonus_loot/*.json}</li>
 *   <li>只有<b>佩戴《圣约·原典》</b>的玩家击杀该生物时才会追加</li>
 *   <li>不加载任何全局战利品修饰器，未佩戴者完全不受影响</li>
 * </ul>
 *
 * <p>文件格式：
 * <pre>
 * {
 *   "entity": "goety:apostle",
 *   "items": ["goety_revelation:broken_halo"]
 * }
 * </pre>
 */
public final class CovenantBonusLoot extends SimplePreparableReloadListener<Void> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "covenant_bonus_loot";

    /** 实体类型 → 追加掉落。 */
    private static final Map<ResourceLocation, List<ItemStack>> TABLE = new HashMap<>();
    private static boolean loaded = false;

    public CovenantBonusLoot() {
        super();
    }

    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
        load(manager);
    }

    /**
     * 惰性加载兜底。
     *
     * <p>重载监听器在某些启动路径下可能没有触发（例如直接从存档列表进入世界时），
     * 那样这张表就会一直是空的，表现为「本模组一件额外掉落都不给」。
     * 所以第一次被查询时再主动读一次，保证一定会加载。
     */
    public static synchronized void ensureLoaded(net.minecraft.server.MinecraftServer server) {
        if (loaded || server == null) {
            return;
        }
        loaded = true;
        try {
            load(server.getResourceManager());
        } catch (Throwable t) {
            LOGGER.warn("[圣约] 惰性加载额外掉落表失败：{}", t.toString());
        }
    }

    private static synchronized void load(ResourceManager manager) {
        TABLE.clear();
        for (Map.Entry<ResourceLocation, Resource> entry
                : manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (var stream = entry.getValue().open();
                 var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null || !json.has("entity") || !json.has("items")) {
                    continue;
                }
                ResourceLocation entity = new ResourceLocation(json.get("entity").getAsString());
                List<ItemStack> stacks = new ArrayList<>();
                for (var element : json.getAsJsonArray("items")) {
                    ResourceLocation itemId = new ResourceLocation(element.getAsString());
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item == null) {
                        LOGGER.warn("[圣约] 额外掉落表引用了不存在的物品：{}", itemId);
                        continue;
                    }
                    stacks.add(new ItemStack(item));
                }
                if (!stacks.isEmpty()) {
                    TABLE.put(entity, stacks);
                }
            } catch (Exception e) {
                LOGGER.warn("[圣约] 读取额外掉落表 {} 失败：{}", entry.getKey(), e.toString());
            }
        }
        loaded = true;
        LOGGER.info("[圣约] 额外掉落表已加载：{} 条", TABLE.size());
    }

    /** 该生物是否有为佩戴者准备的额外掉落。 */
    public static List<ItemStack> forEntity(net.minecraft.world.entity.Entity entity) {
        ensureLoaded(entity.getServer());
        return TABLE.getOrDefault(
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()),
                List.of());
    }
}
