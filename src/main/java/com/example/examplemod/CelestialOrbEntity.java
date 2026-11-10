package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 《天体运行论》绕身旋转的三颗天体（太阳 / 月亮 / 地球）。
 *
 * <p>作者 2026-11-07：「最好不用粒子特效」——所以这不是粒子，是真正的实体：
 * 每颗球都是 {@link CelestialOrbEntity}，由 {@link DeRevolutionibusHandler} 每刻摆到轨道上，
 * 客户端用 {@link com.example.examplemod.client.CelestialOrbRenderer} 画成一张朝向镜头的球面贴图。
 *
 * <p>它不进世界存档（玩家不戴书就立刻被移除），所以不写任何持久数据；
 * 只有「种类」走同步数据，客户端才知道该染成金色、银色还是蓝色。
 */
public class CelestialOrbEntity extends Entity {

    /** 太阳：最大、金色，靠近正午最烫。 */
    public static final int KIND_SUN = 0;

    /** 月亮：次大、银色，越接近深夜负面效果等级越高。 */
    public static final int KIND_MOON = 1;

    /** 地球：最小、蓝色，碰到怪物给佩戴者正面效果。 */
    public static final int KIND_EARTH = 2;

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(CelestialOrbEntity.class, EntityDataSerializers.INT);

    public CelestialOrbEntity(EntityType<? extends CelestialOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, KIND_SUN);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_KIND, tag.getInt("YanOrbKind"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("YanOrbKind", kind());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /**
     * 只跟着轨道走，不受重力、不受流体推动。
     *
     * <p>{@code super.tick()} 负责着火、进入流体这类基础逻辑；
     * 位置由 Handler 每刻 {@code setPos} 直接给定，所以这里把速度清零即可。
     */
    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        this.setOldPosAndRot();
    }

    /** 0 太阳 / 1 月亮 / 2 地球。 */
    public int kind() {
        return this.entityData.get(DATA_KIND);
    }

    /**
     * 三颗天体各自的中文名（作者 2026-11-07：「三个星球依旧采用的是内部代码的名称，给他们改成中文」）。
     *
     * <p>默认名会走 {@code entity.yanscriptures.celestial_orb}，三颗球长得一样名字也一样，
     * 所以这里按种类各给一个键：金太阳 / 银月亮 / 蓝地球。
     */
    @Override
    public Component getName() {
        return Component.translatable(switch (kind()) {
            case KIND_SUN -> "entity.yanscriptures.celestial_orb.sun";
            case KIND_MOON -> "entity.yanscriptures.celestial_orb.moon";
            default -> "entity.yanscriptures.celestial_orb.earth";
        });
    }

    public void setKind(int kind) {
        this.entityData.set(DATA_KIND, kind);
    }

    /**
     * 显示半径（格）。
     *
     * <p>按真实比例排大小：太阳远大于地球，地球大于月亮（作者 2026-11-07 指出「大小不太对」）。
     * 真实比例是 109 : 1 : 0.27，游戏里没法照搬，所以取「太阳最大、地球居中、月亮最小」的相对关系。
     */
    public float displaySize() {
        return switch (kind()) {
            case KIND_SUN -> 0.90F;
            case KIND_MOON -> 0.28F;
            default -> 0.45F;
        };
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 不参与任何持久化：玩家一摘书就该消失。 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
