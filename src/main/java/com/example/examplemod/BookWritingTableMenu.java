package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 著书台的界面：<b>只有纸、墨、线三个选择格</b>，再下面是玩家背包。
 *
 * <p>十格材料池故意不在界面里显示——材料是「从下面塞进去、界面只挑三样用」，
 * 所以界面里只放三个样品格。槽位坐标必须和
 * {@code textures/gui/book_writing_table.png} 一一对应。</p>
 */
public class BookWritingTableMenu extends AbstractContainerMenu {

    /** 三个选择格在界面里的坐标（物品左上角）。
     *  贴图上的格子框是 18×18、外框在 62/84/106，物品要落在框「里面」那一圈，
     *  所以物品坐标整体 +1（原版也是这么错的：{@code Slot} 给的是物品位置，框画在 x-1）。 */
    public static final int PAPER_X = 63;
    public static final int PAPER_Y = 21;
    public static final int SLOT_STEP = 22;

    /** 材料池在界面里不显示，但必须被同步：这里挂 10 个不可点、不可放的“影子槽”，
     *  位置远在界面外，只为了让原版的槽位同步机制把材料列表自动送到客户端。 */
    public static final int POOL_SLOTS = 10;

    private final Container container;
    private final BlockPos pos;
    /** 0 = 当前选中的第几条候选配方，1 = 候选总数。用原版 data slot 同步。 */
    private final ContainerData data;

    /** 服务端构造：绑定到真实方块实体。 */
    public BookWritingTableMenu(int id, Inventory playerInventory, Container container, BlockPos pos) {
        super(ExampleMod.BOOK_WRITING_TABLE_MENU.get(), id);
        this.container = container;
        this.pos = pos;
        this.data = container instanceof ContainerData containerData
                ? containerData
                : new SimpleContainerData(2);

        addSlot(new Slot(container, BookWritingTableBlockEntity.PAPER_SLOT, PAPER_X, PAPER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BookWritingRecipes.isPaper(stack);
            }
        });
        addSlot(new Slot(container, BookWritingTableBlockEntity.INK_SLOT, PAPER_X + SLOT_STEP, PAPER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BookWritingRecipes.isInk(stack);
            }
        });
        addSlot(new Slot(container, BookWritingTableBlockEntity.THREAD_SLOT, PAPER_X + SLOT_STEP * 2, PAPER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BookWritingRecipes.isThread(stack);
            }
        });

        // 材料池的影子槽（界面外，不可点不可放，仅用于同步）
        for (int i = 0; i < POOL_SLOTS; i++) {
            addSlot(new Slot(container, BookWritingTableBlockEntity.MATERIAL_START + i, -10000 - i * 20, -10000) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        // 玩家背包（3 × 9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 178));
        }

        addDataSlots(this.data);
    }

    /**
     * 客户端构造：真实内容由服务端同步，但<b>方块坐标必须从开界面的数据里读</b>——
     * 作者 2026-11-08 报「选中的书退出就重置、右键永远写第一本」，根因就是这里写死了
     * {@code BlockPos.ZERO}：点箭头时客户端发的包带着 (0,0,0)，服务端找不到方块实体，
     * 选择只在客户端改了，服务端一直是第一条。
     */
    public BookWritingTableMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, new SimpleContainer(BookWritingTableBlockEntity.SLOT_COUNT), pos);
    }

    public BlockPos getPos() {
        return pos;
    }

    /** 材料池里现有的材料（按槽位顺序，跳过空槽）。 */
    public List<ItemStack> poolItems() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < POOL_SLOTS; i++) {
            ItemStack stack = container.getItem(BookWritingTableBlockEntity.MATERIAL_START + i);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    /** 三格放好后能写哪几本书：只看档位，不看材料池与份数（作者 2026-10-31）。 */
    public List<BookWritingRecipe> candidates() {
        return BookWritingRecipes.matching(recipeLevel(), container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT));
    }

    /**
     * 配方要从世界的配方表里查。
     *
     * <p>服务端：容器就是方块实体，直接拿它的世界。</p>
     * <p>客户端：{@link #BookWritingTableMenu(int, Inventory)} 用的是占位空容器，
     * 拿不到世界，退回客户端世界（配方表已由服务端同步下来）。
     * 用 {@code DistExecutor} 包一层，服务端永远不会碰到 {@code Minecraft} 这个类。</p>
     */
    private net.minecraft.world.level.Level recipeLevel() {
        if (container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                && blockEntity.getLevel() != null) {
            return blockEntity.getLevel();
        }
        return net.minecraftforge.fml.DistExecutor.unsafeRunForDist(
                () -> () -> net.minecraft.client.Minecraft.getInstance().level,
                () -> () -> null);
    }

    /** 当前选中的这本书还缺哪些材料（空表 = 材料齐了，可以写）。 */
    public List<ItemStack> missing() {
        BookWritingRecipe recipe = selectedRecipe();
        if (recipe == null) {
            return List.of();
        }
        return BookWritingRecipes.missing(recipe,
                container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT),
                poolItems());
    }

    /** 候选里选中了第几条。 */
    public int selectedIndex() {
        int size = candidates().size();
        if (size == 0) {
            return 0;
        }
        int index = Math.max(0, this.data.get(0));
        return index >= size ? 0 : index;
    }

    /** 当前选中的那一条配方；没有就是 null。 */
    public BookWritingRecipe selectedRecipe() {
        List<BookWritingRecipe> list = candidates();
        return list.isEmpty() ? null : list.get(selectedIndex());
    }

    /** 候选数量（界面用来显示「2/3」和箭头的可用状态）。 */
    public int candidateCount() {
        return candidates().size();
    }

    /** 点上下箭头：先本地挪一格（立刻有反馈），再告诉服务端。 */
    public void cycle(int delta) {
        int size = candidateCount();
        if (size <= 1) {
            return;
        }
        this.data.set(0, Math.floorMod(selectedIndex() + delta, size));
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BookTableCyclePacket(this.pos, delta));
    }

    /** 界面左栏用：当前三格能写出什么。 */
    public ItemStack previewResult() {
        BookWritingRecipe recipe = selectedRecipe();
        return recipe == null ? ItemStack.EMPTY : recipe.result();
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int guiSlots = 3;

        if (index < guiSlots) {
            if (!moveItemStackTo(stack, guiSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 选择格：只放得进对应标签的格子，放不进就算了
            if (BookWritingRecipes.isPaper(stack)) {
                moveItemStackTo(stack, 0, 1, false);
            } else if (BookWritingRecipes.isInk(stack)) {
                moveItemStackTo(stack, 1, 2, false);
            } else if (BookWritingRecipes.isThread(stack)) {
                moveItemStackTo(stack, 2, 3, false);
            } else {
                return ItemStack.EMPTY;
            }
            if (ItemStack.matches(stack, original)) {
                return ItemStack.EMPTY; // 一个也没挪动：别把副本当成“已搬运”返回，否则会多出一份
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
