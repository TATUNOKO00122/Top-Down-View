package com.topdownview.client;

import com.topdownview.util.MathConstants;
import net.minecraft.util.Mth;

/**
 * 弓の軌道計算クラス
 */
public final class BowTrajectoryCalculator {

    private static final double ARROW_SPEED = 3.0 * 20.0;
    private static final double GRAVITY = 0.05 * 20.0 * 20.0;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;

    private BowTrajectoryCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static float calculateBowPitch(double horizontalDist, double verticalDist) {
        double v2 = ARROW_SPEED * ARROW_SPEED;
        double v4 = v2 * v2;
        double gx = GRAVITY * horizontalDist;
        double discriminant = v4 - GRAVITY * (GRAVITY * horizontalDist * horizontalDist + 2.0 * verticalDist * v2);

        if (discriminant < 0) {
            return (float) -(Math.atan2(verticalDist, horizontalDist) * MathConstants.RADIANS_TO_DEGREES);
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double tanTheta = (v2 - sqrtDisc) / gx;
        double theta = Math.atan(tanTheta);
        float pitchDegrees = (float) (-theta * MathConstants.RADIANS_TO_DEGREES);

        return Mth.clamp(pitchDegrees, MIN_PITCH, MAX_PITCH);
    }
}
