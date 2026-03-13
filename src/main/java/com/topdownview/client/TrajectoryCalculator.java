package com.topdownview.client;

import com.topdownview.util.MathConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TridentItem;

public final class TrajectoryCalculator {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;
    private static final double MIN_HORIZONTAL_DIST = 0.001;

    private TrajectoryCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static float calculatePitch(
        ProjectilePhysics physics,
        double horizontalDist,
        double verticalDist,
        double pullFactor
    ) {
        double speed = physics.baseSpeed() * pullFactor * TICKS_PER_SECOND;
        double gravity = physics.gravity() * TICKS_PER_SECOND * TICKS_PER_SECOND;

        if (horizontalDist < MIN_HORIZONTAL_DIST) {
            return (float) -(Math.atan2(verticalDist, MIN_HORIZONTAL_DIST) * MathConstants.RADIANS_TO_DEGREES);
        }

        double v2 = speed * speed;
        double v4 = v2 * v2;
        double gx = gravity * horizontalDist;
        double discriminant = v4 - gravity * (gravity * horizontalDist * horizontalDist + 2.0 * verticalDist * v2);

        if (discriminant < 0) {
            return (float) -(Math.atan2(verticalDist, horizontalDist) * MathConstants.RADIANS_TO_DEGREES);
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double tanTheta = (v2 - sqrtDisc) / gx;
        double theta = Math.atan(tanTheta);
        float pitchDegrees = (float) (-theta * MathConstants.RADIANS_TO_DEGREES);

        return Mth.clamp(pitchDegrees, MIN_PITCH, MAX_PITCH);
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