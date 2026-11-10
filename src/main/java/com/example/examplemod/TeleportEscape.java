package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 脱困传送：**沿着视线方向**找第一个站得下的位置。
 *
 * <h2>为什么是视线方向</h2>
 * 方向由玩家自己决定，可预测、有掌控感 —— 你看向哪边，就往哪边脱身。
 * 早先版本只有「沿视线挪 1.2 格」，遇到有厚度的结界出不去；
 * 后来改成随机方向，虽然能出去，却丢了「我说了算」的手感。
 *
 * <h2>现在的做法</h2>
 * 沿视线方向<b>由近及远</b>试距离（2 格起，每次 +1，直到上限）：
 * <ul>
 *   <li>近处能站 → 只挪一点点，不破坏走位</li>
 *   <li>近处被结界占住 → 同一方向上继续找更远的位置，直到穿出去</li>
 *   <li>正前方实在没地方 → 左右各偏 1~2 格再试，仍然优先「你朝的那一侧」</li>
 * </ul>
 * 高度依次试 0 / +1 / -1，避免半砖或台阶把手感弄坏。
 */
public final class TeleportEscape {

    /** 左右偏移的尝试顺序（格）。 */
    private static final double[] LATERAL = {0.0D, 1.0D, -1.0D, 2.0D, -2.0D};
    /** 高度偏移的尝试顺序。 */
    private static final int[] VERTICAL = {0, 1, -1};

    private TeleportEscape() {
    }

    /**
     * 沿视线方向搜索安全落点。
     *
     * @param entity    要传送的实体
     * @param minRadius 起始距离：至少跳这么远（避免「挪一格还在结界里」）
     * @param maxRadius 最远搜索多少格
     * @return 安全坐标；找不到返回 {@code null}
     */
    @Nullable
    public static Vec3 findSafeSpot(Entity entity, double minRadius, double maxRadius) {
        Level level = entity.level();
        Vec3 origin = entity.position();
        AABB base = entity.getBoundingBox();

        // 水平视线；几乎垂直时退化为正前方，避免除零
        Vec3 look = entity.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        // 从 minRadius 起，由近及远
        double start = Math.max(2.0D, minRadius);
        for (double distance = start; distance <= maxRadius; distance += 1.0D) {
            for (double lateral : LATERAL) {
                for (int dy : VERTICAL) {
                    Vec3 spot = origin
                            .add(forward.scale(distance))
                            .add(right.scale(lateral))
                            .add(0.0D, dy, 0.0D);

                    AABB target = base.move(spot.x - origin.x, spot.y - origin.y, spot.z - origin.z);
                    if (!level.noCollision(entity, target)) {
                        continue;
                    }
                    if (isLava(level, spot)) {
                        continue;
                    }
                    return spot;
                }
            }
        }
        return null;
    }

    private static boolean isLava(Level level, Vec3 spot) {
        var fluid = level.getFluidState(BlockPos.containing(spot.x, spot.y, spot.z));
        return !fluid.isEmpty() && fluid.getType() == Fluids.LAVA;
    }

    /**
     * 直接传送到视线方向的第一个安全落点。
     *
     * @return 是否成功传送
     */
    public static boolean escape(Entity entity, double maxRadius) {
        return escape(entity, 2.0D, maxRadius);
    }

    /** 指定起始距离的脱困传送：一次跳够远。 */
    public static boolean escape(Entity entity, double minRadius, double maxRadius) {
        Vec3 spot = findSafeSpot(entity, minRadius, maxRadius);
        if (spot == null) {
            return false;
        }
        entity.teleportTo(spot.x, spot.y, spot.z);
        return true;
    }
}
