package com.example.examplemod.client;

import com.example.examplemod.ClassicBook;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.NetworkHandler;
import com.example.examplemod.WornCache;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第二批名著里两本「按 Shift + 右键才发动」的书的按键检测。
 *
 * <p>为什么在客户端做：右键的按下沿只有客户端知道（{@code mc.options.keyUse} 是每帧状态），
 * 服务端的 {@code PlayerInteractEvent} 里区分不了「按住不放」和「刚按下」。
 * 所以这里只负责「报键」，真正能不能发动（戴着没有、冷却到没有、生命够不够）
 * 全在 {@code FaustHandler} / {@code OdysseyHandler} 里判 —— 客户端永远是不可信的。
 *
 * <p><b>2026-11-01 修「浮士德/奥德赛没反应」</b>：第一版要求准星是
 * {@code HitResult.Type.MISS}（必须对着空气）才发包，而站在地上往脚下、往墙上按
 * 都会被判成对着方块，于是几乎永远发不出去。现在改成「只跳过右键真有事做的方块」：
 * 箱子/工作台/门/按钮/床/铁砧这些照旧让给原版，地面、山体、空气则放行。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BookRightClickHandler {

    private static boolean wasUseDown;

    private BookRightClickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            wasUseDown = false;
            return;
        }
        boolean useDown = mc.options.keyUse.isDown();
        boolean shiftDown = mc.options.keyShift.isDown();
        boolean pressed = useDown && !wasUseDown;
        wasUseDown = useDown;

        if (!pressed || !shiftDown) {
            return;
        }
        if (rightClickIsTaken(mc)) {
            return;
        }
        if (WornCache.wears(mc.player, ClassicBook.FAUST)) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FaustPactPacket());
        } else if (WornCache.wears(mc.player, ClassicBook.ODYSSEY)) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.OdysseyReturnPacket());
        }
    }

    /**
     * 这一下右键是不是已经被原版/方块占用了。
     *
     * <p>对着空气、地面、石头这类「右键没反应」的方块一律返回 {@code false}
     * ——那正是我们要的空闲右键。手持方块物品时也算被占用（Shift + 右键是放置方块）。
     */
    private static boolean rightClickIsTaken(Minecraft mc) {
        if (mc.level == null || mc.hitResult == null) {
            return false;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof BlockItem) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BedBlock
                || block instanceof CakeBlock || block instanceof CauldronBlock || block instanceof AnvilBlock
                || block instanceof NoteBlock || block instanceof JukeboxBlock || block instanceof ComposterBlock) {
            return true;
        }
        return state.getMenuProvider(mc.level, pos) != null;
    }
}
