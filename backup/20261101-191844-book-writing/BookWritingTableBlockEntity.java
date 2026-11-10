package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 著书台的方块实体：一个「书槽 + 十格材料」的容器。
 *
 * <p>设计要点（作者要求）：</p>
 * <ul>
 *   <li>材料槽不只收本模组的东西——来自原版或任何其他模组的物品都能放，所以不做白名单；</li>
 *   <li>最多十种材料，每种最多一组（64）；</li>
 *   <li>记住放入顺序：空手右键时先退还「最近放进去的那一种」；</li>
 *   <li>上端的书槽只收带 {@code #yanscriptures:books} 标签的物品。</li>
 * </ul>
 *
 * <p>这里直接实现原版的 {@link Container}，而不是包一层 {@code ItemStackHandler}：
 * 菜单（{@link BookWritingTableMenu}）要按槽位同步给客户端，用原版容器最省事，
 * 也免得把「一种材料一格」的规则散落到两个地方。</p>
 */
public class BookWritingTableBlockEntity extends BlockEntity
        implements Container, net.minecraft.world.inventory.ContainerData {

    /** 书槽：上端放书。 */
    public static final int BOOK_SLOT = 0;
    /** 材料槽起点。 */
    public static final int MATERIAL_START = BOOK_SLOT + 1;
    /** 材料槽数量（一种材料占一格）。 */
    public static final int MATERIAL_SLOTS = 10;
    /** 材料槽终点（不含）。 */
    public static final int MATERIAL_END = MATERIAL_START + MATERIAL_SLOTS;
    /** 选择槽：纸 / 墨 / 线，界面里只显示这三个。 */
    public static final int PAPER_SLOT = MATERIAL_END;
    public static final int INK_SLOT = PAPER_SLOT + 1;
    public static final int THREAD_SLOT = INK_SLOT + 1;
    /** 槽位总数：书 1 + 材料 10 + 选择 3。 */
    public static final int SLOT_COUNT = THREAD_SLOT + 1;
    /** 每种材料的上限：一组。 */
    public static final int MAX_STACK = 64;

    /** 能放进书槽的物品。 */
    public static final TagKey<Item> BOOKS_TAG =
            ItemTags.create(new ResourceLocation(ExampleMod.MODID, "books"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /** 材料被放进来的先后顺序（存槽位下标，最近的在末尾）。 */
    private final List<Integer> insertionOrder = new ArrayList<>();
    /** 当前选中的是候选配方里的第几条（界面上的上下箭头改它）。 */
    private int selectedIndex = 0;

    public BookWritingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.BOOK_WRITING_TABLE_BE.get(), pos, state);
    }

    // ==================== 书槽 ====================

    public ItemStack getBook() {
        return items.get(BOOK_SLOT);
    }

    public boolean hasBook() {
        return !getBook().isEmpty();
    }

    /** 放书；已经有书、或拿的不是书，都算失败。 */
    public boolean placeBook(ItemStack stack) {
        if (stack.isEmpty() || !BookWritingRecipes.isWritingTool(stack) || hasBook()) {
            return false;
        }
        setItem(BOOK_SLOT, stack.split(1));
        return true;
    }

    /** 取走书；背包放不下就掉在脚边。 */
    public ItemStack takeBook(Player player) {
        ItemStack book = getBook();
        if (book.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(BOOK_SLOT, ItemStack.EMPTY);
        setChanged();
        giveOrDrop(player, book);
        return book;
    }

    /** 写成一本书之后，台面上那本《书与笔》也随之用完（作者要求，2026-11-01）。 */
    public void consumeBook() {
        items.set(BOOK_SLOT, ItemStack.EMPTY);
        setChanged();
        syncToClients();
    }

    // ==================== 材料 ====================

    /**
     * 放入材料。
     *
     * @param stack  玩家手里那一叠（不会被改动）
     * @param amount 最多放几个；{@code shift + 右键} 传整叠数量
     * @return 实际放进去的数量
     */
    public int insertMaterial(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return 0;
        }
        int remaining = Math.min(amount, stack.getCount());
        int inserted = 0;

        // 先并入同种材料的已有槽位
        for (int slot = MATERIAL_START; slot < MATERIAL_END && remaining > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) {
                continue;
            }
            int room = MAX_STACK - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, remaining);
            existing.grow(move);
            remaining -= move;
            inserted += move;
        }

        // 再占用空格
        for (int slot = MATERIAL_START; slot < MATERIAL_END && remaining > 0; slot++) {
            if (!items.get(slot).isEmpty()) {
                continue;
            }
            int move = Math.min(MAX_STACK, remaining);
            ItemStack placed = stack.copy();
            placed.setCount(move);
            items.set(slot, placed);
            remaining -= move;
            inserted += move;
            rememberSlot(slot);
        }

        if (inserted > 0) {
            setChanged();
            syncToClients();
        }
        return inserted;
    }

    /** 还能再放进去多少个这种物品；0 表示放不下。 */
    public int insertableCount(ItemStack stack) {
        int room = 0;
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                room = room + MAX_STACK;
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                room = room + Math.max(0, MAX_STACK - existing.getCount());
            }
            if (room >= MAX_STACK) {
                return room;
            }
        }
        return room;
    }

    /** 退掉最近放进去的那一种材料。 */
    public ItemStack takeLatest(Player player) {
        while (!insertionOrder.isEmpty()) {
            int slot = insertionOrder.remove(insertionOrder.size() - 1);
            if (slot < MATERIAL_START || slot >= MATERIAL_END) {
                continue;
            }
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            giveOrDrop(player, stack);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 把材料全部退给玩家；返回退掉的种类数。 */
    public int takeAll(Player player) {
        int count = 0;
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            items.set(slot, ItemStack.EMPTY);
            giveOrDrop(player, stack);
            count++;
        }
        insertionOrder.clear();
        if (count > 0) {
            setChanged();
        }
        return count;
    }

    /** 里面一共有多少种材料。 */
    public int materialKinds() {
        int count = 0;
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
            if (!items.get(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // ==================== 纸 / 墨 / 线（选择槽） ====================

    /** 材料池里的东西（按槽位顺序，跳过空槽）。 */
    public List<ItemStack> pool() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    /**
     * 三格放好后能写的所有书：<b>只看档位</b>，不看材料池与份数。
     *
     * <p>作者 2026-10-31：小三角要在「三格放好」时就出现，而不是等材料池凑齐；
     * 缺什么交给界面上的「另需」行提示。</p>
     */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.matching(items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT));
    }

    /** 当前选中的配方；没有就是 null。 */
    public BookWritingRecipes.Recipe selectedRecipe() {
        List<BookWritingRecipes.Recipe> list = candidates();
        if (list.isEmpty()) {
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= list.size()) {
            selectedIndex = 0;
        }
        return list.get(selectedIndex);
    }

    /** 上下箭头：在候选配方里挪一格。 */
    public void cycleSelection(int delta) {
        List<BookWritingRecipes.Recipe> list = candidates();
        if (list.isEmpty()) {
            selectedIndex = 0;
        } else {
            selectedIndex = Math.floorMod(selectedIndex + delta, list.size());
        }
        setChanged();
        syncToClients();
    }

    /** 三格选中的东西能写出什么；凑不成配方就是空。 */
    public ItemStack previewResult() {
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        return recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.result());
    }

    /**
     * 扣一次材料并返回成品（书与笔/空书卷由调用方扣）。
     *
     * <p>扣的顺序：先从下面材料池里找同种物品，池里不够才扣选择槽里的。
     * 额外材料（金胡萝卜之类）只从材料池里扣。</p>
     */
    public ItemStack craft() {
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        // 先确认全都够再动手：否则「纸扣了、墨不够」会把纸白白吃掉
        if (!BookWritingRecipes.canCraft(recipe, items.get(PAPER_SLOT), items.get(INK_SLOT),
                items.get(THREAD_SLOT), pool())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(recipe.result());
        // 用料决定档次：三条材料线各记自己的档位，并各自掷一项永久的额外属性
        int[] tiers = MaterialBonus.tiersOf(items.get(PAPER_SLOT), items.get(INK_SLOT), items.get(THREAD_SLOT));
        String[] attrs = level == null
                ? new String[]{MaterialBonus.ATTR_KEYS[0], MaterialBonus.ATTR_KEYS[0], MaterialBonus.ATTR_KEYS[0]}
                : MaterialBonus.rollAttrs(level.getRandom());
        MaterialBonus.stamp(result, tiers, attrs);

        consume(items.get(PAPER_SLOT).getItem(), recipe.paperCount(), PAPER_SLOT);
        consume(items.get(INK_SLOT).getItem(), recipe.inkCount(), INK_SLOT);
        consume(items.get(THREAD_SLOT).getItem(), recipe.threadCount(), THREAD_SLOT);
        for (ItemStack extra : recipe.extras()) {
            consume(extra.getItem(), extra.getCount(), -1);
        }
        syncToClients();
        return result;
    }

    /**
     * 扣掉指定物品若干个：先从材料池扣，池里不够再扣选择格。
     *
     * @param selectorSlot 选择格下标；{@code -1} 表示只认材料池（额外材料走这条）
     */
    private void consume(net.minecraft.world.item.Item item, int amount, int selectorSlot) {
        int remaining = amount;
        for (int slot = MATERIAL_START; slot < MATERIAL_END && remaining > 0; slot++) {
            ItemStack pooled = items.get(slot);
            if (pooled.isEmpty() || !pooled.is(item)) {
                continue;
            }
            int take = Math.min(remaining, pooled.getCount());
            pooled.shrink(take);
            remaining -= take;
            if (pooled.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
                forgetSlot(slot);
            }
        }
        if (remaining > 0 && selectorSlot >= 0) {
            ItemStack selector = items.get(selectorSlot);
            if (!selector.isEmpty() && selector.is(item)) {
                int take = Math.min(remaining, selector.getCount());
                selector.shrink(take);
                if (selector.isEmpty()) {
                    items.set(selectorSlot, ItemStack.EMPTY);
                }
            }
        }
        setChanged();
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void rememberSlot(int slot) {
        insertionOrder.remove(Integer.valueOf(slot));
        insertionOrder.add(slot);
    }

    private void forgetSlot(int slot) {
        insertionOrder.remove(Integer.valueOf(slot));
    }

    // ==================== 容器 ====================

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            if (items.get(slot).isEmpty()) {
                forgetSlot(slot);
            }
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = ContainerHelper.takeItem(items, slot);
        if (items.get(slot).isEmpty()) {
            forgetSlot(slot);
        }
        return stack;
    }

    /**
     * 把容器内容同步给客户端。
     *
     * <p>方块实体的物品栏不会自己跟客户端走：{@code setChanged()} 只负责“标脏、等待存档”，
     * 客户端那份 BE 还是空的 —— 表现就是书明明放进去了，台面上却不显示。
     * 所以每次内容变化后都要主动发一次方块更新包。</p>
     */
    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean wasEmpty = items.get(slot).isEmpty();
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot > BOOK_SLOT) {
            if (stack.isEmpty()) {
                forgetSlot(slot);
            } else if (wasEmpty) {
                rememberSlot(slot);
            }
        }
        setChanged();
        syncToClients();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BOOK_SLOT) {
            return BookWritingRecipes.isWritingTool(stack);
        }
        if (slot == PAPER_SLOT) {
            return BookWritingRecipes.isPaper(stack);
        }
        if (slot == INK_SLOT) {
            return BookWritingRecipes.isInk(stack);
        }
        if (slot == THREAD_SLOT) {
            return BookWritingRecipes.isThread(stack);
        }
        // 一格只能放一种材料
        ItemStack existing = items.get(slot);
        return existing.isEmpty() || ItemStack.isSameItemSameTags(existing, stack);
    }

    @Override
    public int getMaxStackSize() {
        return MAX_STACK;
    }

    /** 选择槽里只放一个当「样品」。 */
    public int getMaxStackSize(int slot) {
        return slot >= PAPER_SLOT ? 1 : MAX_STACK;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        insertionOrder.clear();
        setChanged();
        syncToClients();
    }

    // ==================== 界面同步的两个整数 ====================

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public int get(int index) {
        return index == 0 ? selectedIndex : candidates().size();
    }

    @Override
    public void set(int index, int value) {
        if (index == 0) {
            selectedIndex = Math.max(0, value);
        }
    }

    public Component getDisplayName() {
        return Component.translatable("container.yanscriptures.book_writing_table");
    }

    // ==================== 存档 ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        ListTag order = new ListTag();
        for (int slot : insertionOrder) {
            order.add(IntTag.valueOf(slot));
        }
        tag.put("InsertOrder", order);
        tag.putInt("SelectedIndex", selectedIndex);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        insertionOrder.clear();
        ListTag order = tag.getList("InsertOrder", Tag.TAG_INT);
        for (int i = 0; i < order.size(); i++) {
            int slot = order.getInt(i);
            if (slot > BOOK_SLOT && slot < SLOT_COUNT && !items.get(slot).isEmpty()) {
                insertionOrder.add(slot);
            }
        }
        selectedIndex = tag.getInt("SelectedIndex");
        // 老存档没有顺序信息时，按槽位顺序兜底
        if (insertionOrder.isEmpty()) {
            for (int slot = MATERIAL_START; slot < MATERIAL_END; slot++) {
                if (!items.get(slot).isEmpty()) {
                    insertionOrder.add(slot);
                }
            }
        }
    }
}
