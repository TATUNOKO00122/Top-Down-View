package com.topdownview.client;

import com.topdownview.util.MathConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

public final class TrajectoryCalculator {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;
    private static final double MIN_HORIZONTAL_DIST = 0.001;
    // 引きがこれ未満の間は軌道が定まらないため、直接照準にフォールバックする
    private static final double MIN_PULL_FACTOR = 0.65;
    // 空気抵抗を含む軌道の反復補正
    private static final int MAX_REFINE_ITERATIONS = 6;
    private static final double REFINE_CONVERGENCE_DEG = 0.02;
    private static final int MAX_SIMULATION_TICKS = 200;

    private TrajectoryCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 使用中の投射武器（弓・クロスボウ・トライデント）の重力補正済み発射角を返す。
     * 投射武器でない、または引きが十分でない場合は null（呼び出し側で直接照準にフォールバック）。
     */
    public static Float calculateTrajectoryPitch(Player player, double horizontalDist, double verticalDist) {
        if (player == null || !player.isUsingItem()) {
            return null;
        }
        ItemStack useItem = player.getUseItem();
        ProjectilePhysics physics = ProjectilePhysics.fromItem(useItem.getItem());
        if (physics == null) {
            return null;
        }
        double pullFactor = calculatePullFactor(useItem.getItem(), player.getTicksUsingItem());
        if (pullFactor < MIN_PULL_FACTOR) {
            return null;
        }
        return calculatePitch(physics, horizontalDist, verticalDist, pullFactor);
    }

    public static float calculatePitch(
        ProjectilePhysics physics,
        double horizontalDist,
        double verticalDist,
        double pullFactor
    ) {
        if (horizontalDist < MIN_HORIZONTAL_DIST) {
            return (float) -(Math.atan2(verticalDist, MIN_HORIZONTAL_DIST) * MathConstants.RADIANS_TO_DEGREES);
        }

        // 空気抵抗なしの解析解を初期値にする
        double pitch = draglessPitch(physics, horizontalDist, verticalDist, pullFactor);

        // 空気抵抗で矢は減速するため、解析解は常に手前に落ちる。実軌道をシミュレートして補正する。
        double speed = physics.baseSpeed() * pullFactor;
        for (int i = 0; i < MAX_REFINE_ITERATIONS; i++) {
            double landedY = simulateLandingY(speed, pitch, horizontalDist, physics.drag(), physics.gravity());
            if (Double.isNaN(landedY)) {
                break;
            }
            double correctionDeg = Math.atan2(verticalDist - landedY, horizontalDist) * MathConstants.RADIANS_TO_DEGREES;
            pitch -= correctionDeg;
            if (Math.abs(correctionDeg) < REFINE_CONVERGENCE_DEG) {
                break;
            }
        }

        return Mth.clamp((float) pitch, MIN_PITCH, MAX_PITCH);
    }

    /**
     * 空気抵抗を無視した弾道の解析解（低い側の解）。
     */
    private static double draglessPitch(ProjectilePhysics physics, double horizontalDist, double verticalDist, double pullFactor) {
        double speed = physics.baseSpeed() * pullFactor * TICKS_PER_SECOND;
        double gravity = physics.gravity() * TICKS_PER_SECOND * TICKS_PER_SECOND;

        double v2 = speed * speed;
        double v4 = v2 * v2;
        double gx = gravity * horizontalDist;
        double discriminant = v4 - gravity * (gravity * horizontalDist * horizontalDist + 2.0 * verticalDist * v2);

        if (discriminant < 0) {
            // 射程外：直線照準にフォールバック
            return -(Math.atan2(verticalDist, horizontalDist) * MathConstants.RADIANS_TO_DEGREES);
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double tanTheta = (v2 - sqrtDisc) / gx;
        return -Math.atan(tanTheta) * MathConstants.RADIANS_TO_DEGREES;
    }

    /**
     * 発射角 pitch（度、Minecraft の XRot 準拠で正が下向き）で放った投射物が
     * 水平距離 h に到達したときの高さを返す。到達しない場合は NaN。
     * バニラの投射物と同じく 1tick ごとに移動→空気抵抗→重力の順で更新する。
     */
    private static double simulateLandingY(double speed, double pitchDeg, double h, double drag, double gravity) {
        double theta = pitchDeg * MathConstants.DEGREES_TO_RADIANS;
        double vx = Math.cos(theta) * speed;
        double vy = -Math.sin(theta) * speed;
        double x = 0.0;
        double y = 0.0;

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            x += vx;
            y += vy;
            if (x >= h) {
                return y;
            }
            vx *= drag;
            vy = vy * drag - gravity;
        }
        return Double.NaN;
    }

    public static double calculatePullFactor(Item item, int useTicks) {
        if (item instanceof BowItem) {
            return Math.max(0.1, BowItem.getPowerForTime(useTicks));
        }
        if (item instanceof CrossbowItem) {
            return 1.0;
        }
        if (item instanceof TridentItem) {
            return useTicks >= TridentItem.THROW_THRESHOLD_TIME ? 1.0 : 0.0;
        }
        return 1.0;
    }
}