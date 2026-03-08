package com.topdownview.culling.geometry;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class PyramidProtectionCalc {

    private static final double MAX_PROTECTION_HEIGHT = 3.0;
    private static final double FADE_BOUNDARY_THICKNESS = 1.5;

    private PyramidProtectionCalc() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static double calculateProtectionFactor(BlockPos pos, Vec3 playerPos) {
        float yaw = ModState.CAMERA.getYaw();
        double yawRad = Math.toRadians(yaw);

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double dx = pos.getX() + 0.5 - playerPos.x;
        double dz = pos.getZ() + 0.5 - playerPos.z;

        double dot = dx * forwardX + dz * forwardZ;
        if (dot < 0) {
            return 0.0;
        }

        double distXZ = Math.sqrt(dx * dx + dz * dz);

        double protectedHeight = Math.min(distXZ, MAX_PROTECTION_HEIGHT);

        double groundY = Math.floor(playerPos.y) - 1.0;
        double targetY = groundY + protectedHeight;

        double blockY = pos.getY() + 0.5;

        double diff = targetY - blockY;

        if (diff >= 0.0) {
            return 1.0;
        }

        double fadeThickness = FADE_BOUNDARY_THICKNESS;
        if (diff >= -fadeThickness) {
            double t = (fadeThickness + diff) / fadeThickness;
            double fadeNearAlpha = Config.fadeNearAlpha;
            return fadeNearAlpha + t * (1.0 - fadeNearAlpha);
        }

        return 0.0;
    }
}
