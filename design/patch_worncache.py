# -*- coding: utf-8 -*-
"""一次性补丁：影子 O(1) 查找、wears 读缓存、属性修正器幂等。"""
from pathlib import Path

p = Path("src/main/java/com/example/examplemod/ClassicBooksHandler.java")
s = p.read_text(encoding="utf-8")
orig = s


def sub(old, new):
    global s
    if s.count(old) != 1:
        raise SystemExit("MATCH COUNT %d: %s" % (s.count(old), old[:70]))
    s = s.replace(old, new, 1)


# 1) 影子去重：查表替代 8 格扫描
sub(
    """        boolean already = !level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(8.0D),
                e -> isRepublicShadow(e) && target.getUUID().equals(e.getPersistentData().getUUID(SHADOW_OF))).isEmpty();
        if (already) {
            return;
        }
""",
    """        // 同一本体最多一份影子：查表（O(1)）而不是在 8 格内扫一遍实体
        UUID tracked = SHADOW_BY_TARGET.get(target.getUUID());
        if (tracked != null) {
            var existing = level.getEntity(tracked);
            if (existing != null && existing.isAlive()) {
                return;
            }
            SHADOW_BY_TARGET.remove(target.getUUID());
        }
""",
)

sub(
    """        data.putInt(SHADOW_LIFE, SHADOW_LIFETIME);
        level.addFreshEntity(shadow);""",
    """        data.putInt(SHADOW_LIFE, SHADOW_LIFETIME);
        level.addFreshEntity(shadow);
        SHADOW_BY_TARGET.put(target.getUUID(), shadow.getUUID());""",
)

# 2) 受伤转移：UUID 直取
sub(
    """        UUID id = shadow.getPersistentData().getUUID(SHADOW_OF);
        LivingEntity original = null;
        for (LivingEntity candidate : shadow.level().getEntitiesOfClass(LivingEntity.class,
                shadow.getBoundingBox().inflate(64.0D), e -> e.getUUID().equals(id) && !isRepublicShadow(e))) {
            original = candidate;
            break;
        }
        if (original != null && original.isAlive()) {""",
    """        LivingEntity original = ownerOf(shadow);
        if (original != null && original.isAlive()) {""",
)

# 3) 影子每 tick 判断本体存活
sub(
    """        UUID id = data.getUUID(SHADOW_OF);
        boolean alive = !shadow.level().getEntitiesOfClass(LivingEntity.class,
                shadow.getBoundingBox().inflate(64.0D), e -> e.getUUID().equals(id)).isEmpty();
        if (!alive) {
            shadow.discard();
        }""",
    """        if (ownerOf(shadow) == null) {
            SHADOW_BY_TARGET.remove(data.getUUID(SHADOW_OF));
            shadow.discard();
        }""",
)

# 4) 影子索引表 + ownerOf
sub(
    """    /** 每条被禁的诅咒：全属性 -3%。 */""",
    """    /** 「本体 UUID -> 影子 UUID」。只用来去重与清理，不参与任何掉落/计数。 */
    private static final Map<UUID, UUID> SHADOW_BY_TARGET = new HashMap<>();

    /**
     * 影子的本体。
     *
     * <p>{@code ServerLevel#getEntity(UUID)} 是哈希查找，比在 64 格里扫实体便宜得多；
     * 影子与本体永远在同一个维度（本体换维度时影子会自己消散）。
     */
    private static LivingEntity ownerOf(LivingEntity shadow) {
        if (!(shadow.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID id = shadow.getPersistentData().getUUID(SHADOW_OF);
        if (id == null) {
            return null;
        }
        var entity = level.getEntity(id);
        return entity instanceof LivingEntity living && !isRepublicShadow(living) ? living : null;
    }

    /** 每条被禁的诅咒：全属性 -3%。 */""",
)

# 5) wears 读缓存
sub(
    """    /** 玩家身上是否戴着指定的那本书。 */
    public static boolean wears(Player player, ClassicBook book) {
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                var slotHandler = entry.getValue();
                for (int i = 0; i < slotHandler.getSlots(); i++) {
                    ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                    if (!stack.isEmpty()
                            && stack.getItem() instanceof ClassicBookItem item
                            && item.book() == book) {
                        return true;
                    }
                }
            }
            return false;
        }).orElse(false);
    }""",
    """    /**
     * 玩家身上是否戴着指定的那本书。
     *
     * <p>真实扫描在 {@link WornCache#refresh(Player)} 里每 tick 做一次，
     * 这里只是查表——以前一个 tick 要被问六七遍，每遍都遍历整张饰品栏。
     */
    public static boolean wears(Player player, ClassicBook book) {
        return WornCache.wears(player, book);
    }""",
)

# 6) tick 开头刷新缓存
sub(
    """        tickDao(player);
        tickRepublic(player);""",
    """        WornCache.refresh(player);

        tickDao(player);
        tickRepublic(player);""",
)

# 7) 属性修正器幂等
sub(
    """    private static void setModifier(Player player, Attribute attribute, UUID id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(id, "classic_book", amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }""",
    """    private static void setModifier(Player player, Attribute attribute, UUID id, double amount) {
        applyModifier(player.getAttribute(attribute), id, "classic_book", amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /**
     * 把修正器调整到目标状态，已经是目标状态就什么都不做。
     *
     * <p>旧写法每 tick {@code removeModifier} + {@code addTransientModifier} 一次，
     * 属性表每 tick 都被标脏、缓存被冲掉；数值其实绝大多数 tick 都没变。
     */
    private static void applyModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance instance,
                                      UUID id, String name, double amount,
                                      AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) <= 1.0E-4) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current != null
                && current.getOperation() == operation
                && Math.abs(current.getAmount() - amount) <= 1.0E-6) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }""",
)

sub(
    """    private static void setAddition(Player player, Attribute attribute, UUID id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(id, "classic_book",
                    amount, AttributeModifier.Operation.ADDITION));
        }
    }""",
    """    private static void setAddition(Player player, Attribute attribute, UUID id, double amount) {
        applyModifier(player.getAttribute(attribute), id, "classic_book", amount,
                AttributeModifier.Operation.ADDITION);
    }""",
)

# 8) 欧几里得距离修正器
sub(
    """            var blockReach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
            var entityReach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (blockReach != null) {
                blockReach.removeModifier(EUCLID_BLOCK_REACH);
                if (active) {
                    blockReach.addTransientModifier(new AttributeModifier(EUCLID_BLOCK_REACH,
                            "classic_euc_reach", 1.0D, AttributeModifier.Operation.ADDITION));
                }
            }""",
    """            applyModifier(player.getAttribute(ForgeMod.BLOCK_REACH.get()), EUCLID_BLOCK_REACH,
                    "classic_euc_reach", active ? 1.0D : 0.0D, AttributeModifier.Operation.ADDITION);""",
)

idx = s.index("            if (entityReach != null) {")
end = s.index("            }\n", idx) + len("            }\n")
s = s[:idx] + """            applyModifier(player.getAttribute(ForgeMod.ENTITY_REACH.get()), EUCLID_ENTITY_REACH,
                    "classic_euc_reach", active ? 1.0D : 0.0D, AttributeModifier.Operation.ADDITION);
""" + s[end:]

# 9) import
if "import java.util.HashMap;" not in s:
    s = s.replace("import java.util.List;",
                  "import java.util.HashMap;\nimport java.util.List;\nimport java.util.Map;", 1)

p.write_text(s, encoding="utf-8")
print("patched, delta lines:", s.count("\n") - orig.count("\n"))
