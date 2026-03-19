package com.topdownview.culling.geometry;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class CylinderCalculator {

    private CylinderCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    private record CylinderMetrics(double normalizedDistSq, double distXZ, double distY) {
        static final CylinderMetrics INVALID = new CylinderMetrics(-1.0, 0.0, 0.0);

        boolean isInside() {
            return normalizedDistSq <= 1.0;
        }
    }

    private static CylinderMetrics computeCylinderMetrics(
            double blockX, double blockY, double blockZ,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ,
            boolean useShift, double explicitYaw) {

        double shiftedPlayerX = playerX;
        double shiftedPlayerY = playerY;
        double shiftedPlayerZ = playerZ;

        if (useShift) {
            double yawRad = Math.toRadians(explicitYaw);
            double shift = Config.getCylinderForwardShift();
            shiftedPlayerX = playerX + shift * (-Math.sin(yawRad));
            shiftedPlayerZ = playerZ + shift * Math.cos(yawRad);
        }

        double segX = shiftedPlayerX - cameraX;
        double segY = shiftedPlayerY - cameraY;
        double segZ = shiftedPlayerZ - cameraZ;
        double segLengthSq = segX * segX + segY * segY + segZ * segZ;

        if (segLengthSq < 1.0E-8) {
            return CylinderMetrics.INVALID;
        }

        double segLength = Math.sqrt(segLengthSq);

        double toBlockX = blockX - cameraX;
        double toBlockY = blockY - cameraY;
        double toBlockZ = blockZ - cameraZ;

        double t = (toBlockX * segX + toBlockY * segY + toBlockZ * segZ) / segLengthSq;

        double extensionT = 3.0 / segLength;
        if (t < -extensionT) {
            return CylinderMetrics.INVALID;
        }

        t = Math.max(-extensionT, Math.min(t, 1.0));

        double closestX = cameraX + segX * t;
        double closestY = cameraY + segY * t;
        double closestZ = cameraZ + segZ * t;

        double relX = blockX - closestX;
        double relY = blockY - closestY;
        double relZ = blockZ - closestZ;

        double invSegLength = 1.0 / segLength;
        double normDirX = segX * invSegLength;
        double normDirY = segY * invSegLength;
        double normDirZ = segZ * invSegLength;

        double alongAxis = relX * normDirX + relY * normDirY + relZ * normDirZ;

        double perpX = relX - normDirX * alongAxis;
        double perpY = relY - normDirY * alongAxis;
        double perpZ = relZ - normDirZ * alongAxis;

        double distXZ = Math.sqrt(perpX * perpX + perpZ * perpZ);
        double distY = Math.abs(perpY);

        double radiusH = Config.getCylinderRadiusHorizontal();
        double radiusV = Config.getCylinderRadiusVertical();

        double normalizedDistSq = (distXZ * distXZ) / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);

        return new CylinderMetrics(normalizedDistSq, distXZ, distY);
    }

    public static double getNormalizedDistanceSq(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        double yaw = ModState.CAMERA.getYaw();
        CylinderMetrics metrics = computeCylinderMetrics(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                true, yaw);
        return metrics.normalizedDistSq();
    }

    public static boolean isInCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos, double yaw) {
        CylinderMetrics metrics = computeCylinderMetrics(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                true, yaw);
        if (metrics.normalizedDistSq() < 0) {
            return false;
        }
        return metrics.isInside();
    }

    public static boolean isInCylinderForTrapdoor(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        CylinderMetrics metrics = computeCylinderMetrics(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                false, 0.0);
        if (metrics.normalizedDistSq() < 0) {
            return false;
        }
        return metrics.isInside();
    }

    public static boolean isInMiningCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos,
            double radius, double yaw, double forwardShift) {
        double blockX = pos.getX() + 0.5;
        double blockY = pos.getY() + 0.5;
        double blockZ = pos.getZ() + 0.5;

        double shiftedPlayerX = playerPos.x;
        double shiftedPlayerY = playerPos.y;
        double shiftedPlayerZ = playerPos.z;

        if (forwardShift != 0.0) {
            double yawRad = Math.toRadians(yaw);
            shiftedPlayerX = playerPos.x + forwardShift * (-Math.sin(yawRad));
            shiftedPlayerZ = playerPos.z + forwardShift * Math.cos(yawRad);
        }

        double axisX = shiftedPlayerX - cameraPos.x;
        double axisY = shiftedPlayerY - cameraPos.y;
        double axisZ = shiftedPlayerZ - cameraPos.z;
        double axisLengthSq = axisX * axisX + axisY * axisY + axisZ * axisZ;

        if (axisLengthSq < 1.0E-8) {
            return false;
        }

        double toBlockX = blockX - cameraPos.x;
        double toBlockY = blockY - cameraPos.y;
        double toBlockZ = blockZ - cameraPos.z;

        double t = (toBlockX * axisX + toBlockY * axisY + toBlockZ * axisZ) / axisLengthSq;

        double axisLength = Math.sqrt(axisLengthSq);
        double extensionT = 3.0 / axisLength;

        if (t < -extensionT || t > 1.0 + extensionT) {
            return false;
        }

        double clampedT = Math.max(0, Math.min(1.0, t));
        double closestX = cameraPos.x + axisX * clampedT;
        double closestY = cameraPos.y + axisY * clampedT;
        double closestZ = cameraPos.z + axisZ * clampedT;

        double dx = blockX - closestX;
        double dy = blockY - closestY;
        double dz = blockZ - closestZ;
        double distFromAxisSq = dx * dx + dy * dy + dz * dz;

        return distFromAxisSq <= radius * radius;
    }
}