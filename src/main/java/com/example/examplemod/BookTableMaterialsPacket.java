package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 著书台：把「材料池里存了什么」推给打开界面的玩家。
 *
 * <p>材料池不在界面里显示槽位，所以没有原版的槽位同步可用；这一条包只服务于
 * 界面上那行文字列表。发包时机：打开界面、放入材料、退还材料、写出成品之后。</p>
 */
public class BookTableMaterialsPacket {

    /** 客户端保存的最近一次材料列表：方块坐标 → 列表。 */
    private static final java.util.Map<BlockPos, List<ItemStack>> CLIENT_CACHE = new java.util.HashMap<>();
    /**
     * 最近一次收到的清单。
     *
     * <p>客户端的菜单是用 {@code (id, inv)} 构造的，坐标是 {@code BlockPos.ZERO}，
     * 按坐标查缓存一定查不到，所以再留一份「最后收到的」给界面用。</p>
     */
    private static List<ItemStack> lastReceived = List.of();

    private final BlockPos pos;
    private final List<ItemStack> stacks;

    public BookTableMaterialsPacket(BlockPos pos, List<ItemStack> stacks) {
        this.pos = pos;
        this.stacks = stacks;
    }

    /** 从方块实体收集当前材料池内容。 */
    public static BookTableMaterialsPacket of(BookWritingTableBlockEntity table) {
        List<ItemStack> list = new ArrayList<>();
        for (int slot = BookWritingTableBlockEntity.MATERIAL_START;
             slot < BookWritingTableBlockEntity.MATERIAL_END; slot++) {
            ItemStack stack = table.getItem(slot);
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
        return new BookTableMaterialsPacket(table.getBlockPos(), list);
    }

    /** 服务端用：把这个方块的材料列表发给指定玩家。 */
    public static void syncTo(ServerPlayer player, BookWritingTableBlockEntity table) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), of(table));
    }

    /** 客户端用：界面按坐标取缓存，查不到就退回「最后收到的」。 */
    public static List<ItemStack> cached(BlockPos pos) {
        List<ItemStack> byPos = CLIENT_CACHE.get(pos);
        return byPos != null ? byPos : lastReceived;
    }

    public static void encode(BookTableMaterialsPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(msg.pos);
        buffer.writeVarInt(msg.stacks.size());
        for (ItemStack stack : msg.stacks) {
            buffer.writeItem(stack);
        }
    }

    public static BookTableMaterialsPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int size = buffer.readVarInt();
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readItem());
        }
        return new BookTableMaterialsPacket(pos, list);
    }

    public static void handle(BookTableMaterialsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CLIENT_CACHE.put(msg.pos, msg.stacks);
            lastReceived = msg.stacks;
        });
        ctx.get().setPacketHandled(true);
    }
}
