package com.topdownview.culling.geometry;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 楕円シリンダーカリング計算を行うユーティリティクラス。
 * カメラとプレイヤー間の可視領域をシリンダー形状で定義する。
 */
public final class CylinderCalculator {

    private static final double MIN_SEGMENT_LENGTH_SQ = 1.0E-8;
    private static final double EXTENSION_BLOCKS = 3.0;

    private CylinderCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * ブロック位置のシリンダー内正規化距離の二乗を計算する。
     * 
     * @param blockX ブロック中心X座標
     * @param blockY ブロック中心Y座標
     * @param blockZ ブロック中心Z座標
     * @param playerX プレイヤーX座標
     * @param playerY プレイヤーY座標
     * @param playerZ プレイヤーZ座標
     * @param cameraX カメラX座標
     * @param cameraY カメラY座標
     * @param cameraZ カメラZ座標
     * @return 正規化距離の二乗 (1.0以下=シリンダー内, 負値=無効)
     */
    public static double getNormalizedDistanceSq(
            double blockX, double blockY, double blockZ,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        double yaw = ModState.CAMERA.getYaw();
        return computeNormalizedDistSq(blockX, blockY, blockZ,
                playerX, playerY, playerZ,
                cameraX, cameraY, cameraZ,
                true, yaw);
    }

    /**
     * BlockPos版オーバーロード。
     */
    public static double getNormalizedDistanceSq(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        double yaw = ModState.CAMERA.getYaw();
        return computeNormalizedDistSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                true, yaw);
    }

    private static double computeNormalizedDistSq(
            double blockX, double blockY, double blockZ,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ,
            boolean useShift, double explicitYaw) {

        double shiftedPlayerX = playerX;
        double shiftedPlayerZ = playerZ;

        if (useShift) {
            double yawRad = Math.toRadians(explicitYaw);
            double shift = Config.getCylinderForwardShift();
            shiftedPlayerX = playerX + shift * (-Math.sin(yawRad));
            shiftedPlayerZ = playerZ + shift * Math.cos(yawRad);
        }

        double segX = shiftedPlayerX - cameraX;
        double segY = playerY - cameraY;
        double segZ = shiftedPlayerZ - cameraZ;
        double segLengthSq = segX * segX + segY * segY + segZ * segZ;

        if (segLengthSq < MIN_SEGMENT_LENGTH_SQ) {
            return -1.0;
        }

        double toBlockX = blockX - cameraX;
        double toBlockY = blockY - cameraY;
        double toBlockZ = blockZ - cameraZ;

        double t = (toBlockX * segX + toBlockY * segY + toBlockZ * segZ) / segLengthSq;

        double segLength = Math.sqrt(segLengthSq);
        double extensionT = EXTENSION_BLOCKS / segLength;
        if (t < -extensionT || t > 1.0 + extensionT) {
            return -1.0;
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

        return (distXZ * distXZ) / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);
    }

    public static boolean isInCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos, double yaw) {
        double normalizedDistSq = computeNormalizedDistSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                true, yaw);
        return normalizedDistSq >= 0 && normalizedDistSq <= 1.0;
    }

    public static boolean isInCylinderForTrapdoor(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        double normalizedDistSq = computeNormalizedDistSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                false, 0.0);
        return normalizedDistSq >= 0 && normalizedDistSq <= 1.0;
    }

    public static boolean isInMiningCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos,
            double radius, double yaw, double forwardShift) {
        return isInMiningCylinder(pos,
                playerPos.x, playerPos.y, playerPos.z,
                cameraPos.x, cameraPos.y, cameraPos.z,
                radius, yaw, forwardShift);
    }

    /**
     * マイニングモード用円柱判定（double値版、Vec3生成回避）。
     */
    public static boolean isInMiningCylinder(BlockPos pos,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ,
            double radius, double yaw, double forwardShift) {
        double blockX = pos.getX() + 0.5;
        double blockY = pos.getY() + 0.5;
        double blockZ = pos.getZ() + 0.5;

        double shiftedPlayerX = playerX;
        double shiftedPlayerZ = playerZ;

        if (forwardShift != 0.0) {
            double yawRad = Math.toRadians(yaw);
            shiftedPlayerX = playerX + forwardShift * (-Math.sin(yawRad));
            shiftedPlayerZ = playerZ + forwardShift * Math.cos(yawRad);
        }

        double axisX = shiftedPlayerX - cameraX;
        double axisY = playerY - cameraY;
        double axisZ = shiftedPlayerZ - cameraZ;
        double axisLengthSq = axisX * axisX + axisY * axisY + axisZ * axisZ;

        if (axisLengthSq < MIN_SEGMENT_LENGTH_SQ) {
            return false;
        }

        double toBlockX = blockX - cameraX;
        double toBlockY = blockY - cameraY;
        double toBlockZ = blockZ - cameraZ;

        double t = (toBlockX * axisX + toBlockY * axisY + toBlockZ * axisZ) / axisLengthSq;

        double axisLength = Math.sqrt(axisLengthSq);
        double extensionT = EXTENSION_BLOCKS / axisLength;

        if (t < -extensionT || t > 1.0 + extensionT) {
            return false;
        }

        double clampedT = Math.max(0, Math.min(1.0, t));
        double closestX = cameraX + axisX * clampedT;
        double closestY = cameraY + axisY * clampedT;
        double closestZ = cameraZ + axisZ * clampedT;

        double dx = blockX - closestX;
        double dy = blockY - closestY;
        double dz = blockZ - closestZ;
        double distFromAxisSq = dx * dx + dy * dy + dz * dz;

        return distFromAxisSq <= radius * radius;
    }
}