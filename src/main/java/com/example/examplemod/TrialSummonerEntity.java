package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 试炼召唤物 —— 安息仪式的产物，一个「一次性的演出触发器」。
 *
 * <p>借用 Goety「召唤暴风」的做法（{@code goety:storm_util}）：仪式本身只负责把一个
 * 工具实体放到世界里，真正的复杂演出由这个实体自己开场。
 *
 * <p>行为：落地后等 10 tick（让仪式特效播完），找到最近的玩家，交给
 * {@link TrialManager} 开场，然后自毁。它<b>不参与战斗、不可被攻击、不会移动</b>。
 */
public class TrialSummonerEntity extends Entity {

    public TrialSummonerEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        // 没有需要同步的数据
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            return;
        }
        // 等仪式动画播完再开场，避免玩家还没看清就开打
        if (this.tickCount < 10) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();
        List<Player> nearby = serverLevel.getEntitiesOfClass(
                Player.class, this.getBoundingBox().inflate(16.0D), Player::isAlive);
        Player caster = nearby.isEmpty() ? null : nearby.get(0);

        // ==================== 前置条件 ====================
        // 一、必须在冥界。启示录的主线在那里展开；sabbath 仪式本身没有维度概念，
        //     而 Goety 自带的 expert_nether 除了要求下界还要求特定结构，太严。
        // 二、必须击败过使徒。这是「先胜后约」的叙事门槛。
        // 任一条件不满足都退还素材 —— 仪式已经把东西收走了，不能让玩家白亏七件稀有物品。
        if (this.level().dimension() != Level.NETHER) {
            deny(caster, "message.yanscriptures.trial.denied.overworld");
            this.discard();
            return;
        }
        if (caster != null && !hasKilledApostle(caster)) {
            deny(caster, "message.yanscriptures.trial.denied.no_apostle");
            this.discard();
            return;
        }

        if (caster != null) {
            TrialManager.start(serverLevel, caster, this.blockPosition());
        }

        this.discard();
    }

    /** 拒绝开场：退还素材并给出经文风格的理由。 */
    private void deny(Player caster, String reasonKey) {
        if (caster == null) {
            return;
        }
        refundIngredients(caster);
        caster.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(reasonKey), false);
        caster.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.yanscriptures.trial.refund")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
    }

    /** 玩家是否击杀过使徒（用原版击杀统计，不依赖任何模组的进度系统）。 */
    private static boolean hasKilledApostle(Player player) {
        // getStats() 只声明在 ServerPlayer 上，Player 没有这个方法
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        var apostle = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getValue(new net.minecraft.resources.ResourceLocation("goety:apostle"));
        if (apostle == null) {
            return true; // 没装 Goety 就没有使徒，不该卡住流程
        }
        return serverPlayer.getStats().getValue(
                net.minecraft.stats.Stats.ENTITY_KILLED.get(apostle)) > 0;
    }

    /**
     * 退还安息仪式的素材。
     * 清单与 {@code rite_of_revelation.json} 的 ingredients 保持一致 ——
     * 改配方时记得同步这里，否则退还的物品会和实际消耗对不上。
     */
    private static void refundIngredients(Player player) {
        give(player, "goety:undeath_potion", null);
        give(player, "goety:heart_of_the_night", null);
        give(player, "goety:heart_of_the_night", null);
        give(player, "goety:night_beacon", null);
        // 至纯之血 = 不洁之血 + {Pure:1b}
        give(player, "goety:unholy_blood", "{Pure:1b}");
        give(player, "goety:forbidden_fragment", null);
        give(player, "goety_revelation:ascension_halo", null);
    }

    private static void give(Player player, String id, String nbt) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new net.minecraft.resources.ResourceLocation(id));
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (nbt != null) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putBoolean("Pure", true);
            stack.setTag(tag);
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
