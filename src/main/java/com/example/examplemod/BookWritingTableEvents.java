package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 著书台的 Shift 右键。
 *
 * <p>为什么不能只写在方块里：玩家按着 Shift 右键时，原版会先尝试「用手里那件物品
 * 对着方块使用」（方块物品直接放成方块、末影珍珠这种走物品自己的 useOn），
 * 轮到方块的 {@code use} 时手里的东西往往已经被原版逻辑吃掉了。
 * 所以在 {@code RightClickBlock} 事件里提前截住：只要对着台子下半部分、
 * 按着 Shift，就由这里完成「整格放入 / 全部退还」，并把物品使用也一并 DENY 掉。</p>
 *
 * <p>两个容易踩的点：</p>
 * <ul>
 *   <li>事件在客户端与服务端都会触发，<b>改库存只允许在服务端做</b>，
 *       否则两边各扣一次、或客户端扣了服务端没扣，就会出现「16 个铁锭变 32 个」；</li>
 *   <li>服务端扣完必须把手里那叠写回去（{@code setItemInHand}），
 *       有些路径拿到的不是同一个对象，只 shrink 不写回等于没扣。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookWritingTableEvents {

    /** 点击高度超过这个值算「上端」。与方块里的阈值保持一致。 */
    private static final double TOP_ZONE = 0.55D;

    /**
     * 每个玩家最后一次处理这个事件的游戏刻。
     *
     * <p>同一个 Shift 右键会被触发不止一次（客户端+服务端、主手+副手、以及方块 use 那条路），
     * 第一次把手里那叠放空之后，第二次就会走进「空手 = 全部退还」的分支，
     * 看起来就像「Shift 右键会清空材料」。这里按玩家 + 刻去重。</p>
     */
    private static final java.util.Map<java.util.UUID, Long> LAST_HANDLED = new java.util.HashMap<>();

    private BookWritingTableEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BookWritingTableBlock)) {
            return;
        }
        if (event.getHitVec().getLocation().y - pos.getY() > TOP_ZONE) {
            return; // 上端：交给方块自己（放书 / 开界面 / 写书）
        }

        // 下半部 + Shift：无论有没有东西可放，都把原版那条路掐断
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);

        if (level.isClientSide) {
            return; // 客户端只负责不再发使用包，库存以服务端为准
        }

        if (!(level.getBlockEntity(pos) instanceof BookWritingTableBlockEntity table)) {
            return;
        }

        long now = level.getGameTime();
        Long last = LAST_HANDLED.put(player.getUUID(), now);
        if (last != null && now - last <= 1L) {
            return;
        }

        ItemStack handStack = player.getItemInHand(event.getHand());
        boolean acted;
        if (handStack.isEmpty()) {
            acted = table.takeAll(player) > 0;
        } else {
            ItemStack held = player.getItemInHand(event.getHand());
            int inserted = table.insertMaterial(held, held.getCount());
            if (inserted > 0) {
                held.shrink(inserted);
                if (held.isEmpty()) {
                    player.setItemInHand(event.getHand(), ItemStack.EMPTY);
                } else {
                    player.setItemInHand(event.getHand(), held);
                }
                player.getInventory().setChanged();
            }
            acted = inserted > 0;
        }

        if (acted && player instanceof ServerPlayer serverPlayer) {
            BookTableMaterialsPacket.syncTo(serverPlayer, table);
        }
    }
}
