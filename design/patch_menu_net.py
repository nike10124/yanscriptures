# -*- coding: utf-8 -*-
"""著书台：候选配方切换（数据槽同步 + 网络包 + 界面箭头）。"""
from pathlib import Path

# ==================== 1. NetworkHandler：新增 BookTableCyclePacket ====================
nh = Path("src/main/java/com/example/examplemod/NetworkHandler.java")
src = nh.read_text(encoding="utf-8")

old = """        CHANNEL.registerMessage(id++, ComedyTeleportPacket.class,
                ComedyTeleportPacket::encode,
                ComedyTeleportPacket::decode,
                ComedyTeleportPacket::handle);
    }"""
new = """        CHANNEL.registerMessage(id++, ComedyTeleportPacket.class,
                ComedyTeleportPacket::encode,
                ComedyTeleportPacket::decode,
                ComedyTeleportPacket::handle);
        CHANNEL.registerMessage(id++, BookTableCyclePacket.class,
                BookTableCyclePacket::encode,
                BookTableCyclePacket::decode,
                BookTableCyclePacket::handle);
    }"""
assert old in src
src = src.replace(old, new)

anchor = "    // ==================== 全局 DPS ===================="
packet = '''    /**
     * 著书台界面上的上下箭头：让方块实体在候选配方之间挪一格。
     *
     * <p>只做「挪一格」这一件事——候选列表由服务端现算，
     * 客户端算出来的列表可能因为同步延迟和服务端不一致。</p>
     */
    public static class BookTableCyclePacket {
        private final BlockPos pos;
        private final int delta;

        public BookTableCyclePacket(BlockPos pos, int delta) {
            this.pos = pos;
            this.delta = delta;
        }

        public static void encode(BookTableCyclePacket msg, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(msg.pos);
            buffer.writeVarInt(msg.delta);
        }

        public static BookTableCyclePacket decode(FriendlyByteBuf buffer) {
            return new BookTableCyclePacket(buffer.readBlockPos(), buffer.readVarInt());
        }

        public static void handle(BookTableCyclePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                int delta = msg.delta == 1 ? 1 : -1;
                if (player.level().getBlockEntity(msg.pos) instanceof BookWritingTableBlockEntity table) {
                    if (player.distanceToSqr(msg.pos.getX() + 0.5D, msg.pos.getY() + 0.5D,
                            msg.pos.getZ() + 0.5D) <= 64.0D) {
                        table.cycleSelection(delta);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

'''
assert anchor in src
src = src.replace(anchor, packet + anchor, 1)

if "import net.minecraft.core.BlockPos;" not in src:
    src = src.replace("import net.minecraft.network.FriendlyByteBuf;",
                      "import net.minecraft.core.BlockPos;\nimport net.minecraft.network.FriendlyByteBuf;")
nh.write_text(src, encoding="utf-8")
print("NetworkHandler ok")

# ==================== 2. Menu：候选列表 + 数据槽 ====================
mp = Path("src/main/java/com/example/examplemod/BookWritingTableMenu.java")
src = mp.read_text(encoding="utf-8")

src = src.replace("import net.minecraft.world.inventory.AbstractContainerMenu;",
                  "import net.minecraft.world.inventory.AbstractContainerMenu;\n"
                  "import net.minecraft.world.inventory.ContainerData;\n"
                  "import net.minecraft.world.inventory.SimpleContainerData;")

src = src.replace("""    private final Container container;
    private final BlockPos pos;""",
"""    private final Container container;
    private final BlockPos pos;
    /** 0 = 当前选中的第几条候选配方，1 = 候选总数。用原版 data slot 同步。 */
    private final ContainerData data;""")

src = src.replace("""        this.container = container;
        this.pos = pos;

        addSlot(new Slot(container, BookWritingTableBlockEntity.PAPER_SLOT, PAPER_X, PAPER_Y) {""",
"""        this.container = container;
        this.pos = pos;
        this.data = container instanceof ContainerData containerData
                ? containerData
                : new SimpleContainerData(2);

        addSlot(new Slot(container, BookWritingTableBlockEntity.PAPER_SLOT, PAPER_X, PAPER_Y) {""")

src = src.replace("""        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 178));
        }
    }""",
"""        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 178));
        }

        addDataSlots(this.data);
    }""")

old = """    /** 界面左栏用：当前三格能不能写出一本书。 */
    public ItemStack previewResult() {
        return BookWritingRecipes.find(container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
                container.getItem(BookWritingTableBlockEntity.INK_SLOT),
                container.getItem(BookWritingTableBlockEntity.THREAD_SLOT));
    }"""
new = """    /** 三格 + 材料池现在能写出哪几本书（材料池里东西多时会有好几条）。 */
    public List<BookWritingRecipes.Recipe> candidates() {
        return BookWritingRecipes.findAll(container.getItem(BookWritingTableBlockEntity.PAPER_SLOT),
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
    public BookWritingRecipes.Recipe selectedRecipe() {
        List<BookWritingRecipes.Recipe> list = candidates();
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
        BookWritingRecipes.Recipe recipe = selectedRecipe();
        return recipe == null ? ItemStack.EMPTY : new ItemStack(recipe.result());
    }"""
assert old in src
src = src.replace(old, new)
mp.write_text(src, encoding="utf-8")
print("Menu ok")
