package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * 著书台：照原版讲台做的方块——上半是可以放书、可以点开的台面，下半是基座；
 * 里面还有十格材料。
 *
 * <p>形状与模型直接沿用原版讲台（模型 {@code parent: minecraft:block/lectern}），
 * 所以碰撞箱、朝向、外观都跟讲台一致；书由 {@code BookWritingTableRenderer}
 * 画在台面上。</p>
 *
 * <p>交互（作者定的规格）：</p>
 * <ul>
 *   <li>上端（方块上半）：手里拿着书 → 放进书槽；空手 → 打开界面；</li>
 *   <li>下端（方块下半）：手里拿着材料 → 放 1 个；{@code shift + 右键} → 整叠放进去；</li>
 *   <li>下端空手右键 → 退还最近放入的那一种材料；{@code shift + 空手右键} → 材料全部退还。</li>
 * </ul>
 */
public class BookWritingTableBlock extends BaseEntityBlock {

    /** 朝向：与讲台一致。 */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 点击高度超过这个值就算「上端」。 */
    private static final double TOP_ZONE = 0.55D;

    /** 碰撞箱：基座 + 台面，跟讲台手工拼出来的体积接近。 */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(0.0D, 2.0D, 2.0D, 16.0D, 10.0D, 14.0D),
            Block.box(0.0D, 10.0D, 0.0D, 16.0D, 16.0D, 16.0D));

    public BookWritingTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookWritingTableBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BookWritingTableBlockEntity table)) {
            return InteractionResult.PASS;
        }

        boolean top = hit.getLocation().y - pos.getY() > TOP_ZONE;
        ItemStack held = player.getItemInHand(hand);

        if (top) {
            return useTop(player, table, held, hand);
        }
        return useBottom(player, table, held, hand);
    }

    /**
     * 上端：书槽里有书与笔时，右键直接打开界面；没有时，手里拿着书与笔就放上去。
     */
    private InteractionResult useTop(Player player, BookWritingTableBlockEntity table,
                                     ItemStack held, InteractionHand hand) {
        if (table.hasBook()) {
            // 手里拿着空书卷 → 试着写书；否则一律开界面
            if (held.is(Materials.BLANK_SCROLL.get())) {
                return craft(player, table, held);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                BookTableMaterialsPacket.syncTo(serverPlayer, table);
                NetworkHooks.openScreen(serverPlayer,
                        new net.minecraft.world.SimpleMenuProvider(
                                (id, inv, p) -> new BookWritingTableMenu(id, inv, table, table.getBlockPos()),
                                table.getDisplayName()));
            }
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_table.need_tool"), true);
            return InteractionResult.CONSUME;
        }

        if (!BookWritingRecipes.isWritingTool(held)) {
            // 玩家对着台面塞材料是常事：书槽不收就转进材料池，不要甩一句“这不是书”把人挡回去。
            String name = held.getHoverName().getString();
            int inserted = table.insertMaterial(held, held.getCount());
            if (inserted > 0) {
                held.shrink(inserted);
                player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
                player.getInventory().setChanged();
                player.displayClientMessage(
                        Component.translatable("message.yanscriptures.book_table.material_placed",
                                name, String.valueOf(inserted)), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.yanscriptures.book_table.full"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (table.placeBook(held)) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_table.book_placed"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** 上端 + 空书卷：按三格选中的纸墨线写出一本书。 */
    private InteractionResult craft(Player player, BookWritingTableBlockEntity table, ItemStack scroll) {
        if (!table.hasBook()) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_table.need_tool"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack result = table.craft();
        if (result.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_table.no_recipe"), true);
            return InteractionResult.CONSUME;
        }
        scroll.shrink(1);
        // 台上的《书与笔》写一本就少一本（作者要求，2026-11-01）。
        table.consumeBook();
        // 先把名字取下来：原版 Inventory#add 放得下时会把 result 直接缩到 0 个，
        // 之后再取 getHoverName() 拿到的就成了「空气」（2026-10-31 实测）。
        Component resultName = result.getHoverName().copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            YanEventTrigger.INSTANCE.trigger(serverPlayer, "book_written");
        }
        player.displayClientMessage(
                Component.translatable("message.yanscriptures.book_table.crafted", resultName),
                true);
        return InteractionResult.CONSUME;
    }

    /** 下端：放材料 / 退还材料。 */
    private InteractionResult useBottom(Player player, BookWritingTableBlockEntity table,
                                        ItemStack held, InteractionHand hand) {
        // Shift 的全部行为交给 BookWritingTableEvents（那里能拦住原版对物品的使用）；
        // 这里再处理一次会在同一刻被调用，出现「放进去又被全部退回来」。
        if (player.isShiftKeyDown()) {
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                int kinds = table.takeAll(player);
                if (kinds == 0) {
                    player.displayClientMessage(
                            Component.translatable("message.yanscriptures.book_table.empty"), true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.yanscriptures.book_table.taken_all", kinds), true);
                }
            } else {
                ItemStack taken = table.takeLatest(player);
                if (taken.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.yanscriptures.book_table.empty"), true);
                }
            }
            if (player instanceof ServerPlayer sp) {
                BookTableMaterialsPacket.syncTo(sp, table);
            }
            return InteractionResult.CONSUME;
        }

        int want = player.isShiftKeyDown() ? held.getCount() : 1;
        int inserted = table.insertMaterial(held, want);
        if (inserted > 0 && player instanceof ServerPlayer serverPlayer) {
            BookTableMaterialsPacket.syncTo(serverPlayer, table);
        }
        if (inserted <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.yanscriptures.book_table.full"), true);
        } else {
            held.shrink(inserted);
            if (held.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BookWritingTableBlockEntity table) {
                Containers.dropContents(level, pos, table);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
