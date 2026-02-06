package com.example.examplemod.client;

import net.minecraft.util.Mth;

/**
 * 弓の軌道計算クラス
 * 責務：弓の射撃角度計算
 */
public final class BowTrajectoryCalculator {

    // 定数
    private static final double ARROW_SPEED = 3.0 * 20.0; // 60 blocks/tick
    private static final double GRAVITY = 0.05 * 20.0 * 20.0; // 20 blocks/tick^2
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    private static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
    
    // 角度制限
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;

    private BowTrajectoryCalculator() {
        throw new AssertionError("ユーティリティクラスはインスタンス化できません");
    }

    /**
     * 弓の射撃角度を計算
     * @param horizontalDist 水平距離
     * @param verticalDist 垂直距離
     * @return ピッチ角度（度）
     */
    public static float calculateBowPitch(double horizontalDist, double verticalDist) {
        double v2 = ARROW_SPEED * ARROW_SPEED;
        double v4 = v2 * v2;
        double gx = GRAVITY * horizontalDist;
        double discriminant = v4 - GRAVITY * (GRAVITY * horizontalDist * horizontalDist + 2.0 * verticalDist * v2);

        if (discriminant < 0) {
            // 到達不能な場合は直接角度を計算
            return (float) -(Math.atan2(verticalDist, horizontalDist) * RADIANS_TO_DEGREES);
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double tanTheta = (v2 - sqrtDisc) / gx;
        double theta = Math.atan(tanTheta);
        float pitchDegrees = (float) (-theta * RADIANS_TO_DEGREES);

        return Mth.clamp(pitchDegrees, MIN_PITCH, MAX_PITCH);
    }
}
