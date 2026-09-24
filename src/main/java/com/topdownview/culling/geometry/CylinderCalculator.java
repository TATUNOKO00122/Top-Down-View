package com.topdownview.culling.geometry;

import com.topdownview.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 楕円シリンダーカリング計算を行うユーティリティクラス。
 * カメラとプレイヤー間の可視領域をシリンダー形状で定義する。
 */
public final class CylinderCalculator {

    private static final double MIN_SEGMENT_LENGTH_SQ = 1.0E-8;
    private static final double EXTENSION_BLOCKS = 3.0;

    /**
     * フレーム毎に不変なシリンダー軸(カメラ→シフト後プレイヤー)の情報。
     * {@link #updateCache} が1度だけ構築し volatile で公開するため、チャンク構築ワーカーからも
     * 一貫したスナップショットを読める。ブロック毎の計算から sqrt と軸の再導出を排除する。
     */
    private static volatile Axis axis = Axis.INVALID;

    private record Axis(double segX, double segY, double segZ, double segLengthSq,
                        double invSegLength, double extensionT) {
        static final Axis INVALID = new Axis(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private CylinderCalculator() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * フレーム毎のシリンダー軸を事前計算します。
     * プレイヤー/カメラ座標は {@code TopDownCuller.update()} で確定した値と一致させること。
     */
    public static void updateCache(double yaw, double forwardShift,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        double yawRad = Math.toRadians(yaw);
        double shiftedPlayerX = playerX + forwardShift * (-Math.sin(yawRad));
        double shiftedPlayerZ = playerZ + forwardShift * Math.cos(yawRad);
        double segX = shiftedPlayerX - cameraX;
        double segY = playerY - cameraY;
        double segZ = shiftedPlayerZ - cameraZ;
        double segLengthSq = segX * segX + segY * segY + segZ * segZ;
        if (segLengthSq < MIN_SEGMENT_LENGTH_SQ) {
            axis = Axis.INVALID;
            return;
        }
        double segLength = Math.sqrt(segLengthSq);
        axis = new Axis(segX, segY, segZ, segLengthSq, 1.0 / segLength, EXTENSION_BLOCKS / segLength);
    }

    /**
     * ブロック位置のシリンダー内正規化距離の二乗を計算する。
     * 軸は {@link #updateCache} の値を用いるため、プレイヤー座標は受け取らない。
     *
     * @param blockX ブロック中心X座標
     * @param blockY ブロック中心Y座標
     * @param blockZ ブロック中心Z座標
     * @param cameraX カメラX座標(updateCache と同一値)
     * @param cameraY カメラY座標
     * @param cameraZ カメラZ座標
     * @return 正規化距離の二乗 (1.0以下=シリンダー内, 負値=無効)
     */
    public static double getNormalizedDistanceSq(
            double blockX, double blockY, double blockZ,
            double cameraX, double cameraY, double cameraZ) {
        Axis a = axis;
        if (a.segLengthSq() < MIN_SEGMENT_LENGTH_SQ) {
            return -1.0;
        }
        return cylinderValue(blockX, blockY, blockZ, cameraX, cameraY, cameraZ,
                a.segX(), a.segY(), a.segZ(), a.segLengthSq(), a.invSegLength(), a.extensionT());
    }

    /**
     * シリンダー内正規化距離の二乗を計算する共通実装。軸情報を明示的に受け取る。
     * 事前正規化済みの {@code invSegLength}/{@code extensionT} を受け取ることで sqrt を排除する。
     */
    private static double cylinderValue(
            double blockX, double blockY, double blockZ,
            double cameraX, double cameraY, double cameraZ,
            double segX, double segY, double segZ, double segLengthSq,
            double invSegLength, double extensionT) {
        double toBlockX = blockX - cameraX;
        double toBlockY = blockY - cameraY;
        double toBlockZ = blockZ - cameraZ;

        double t = (toBlockX * segX + toBlockY * segY + toBlockZ * segZ) / segLengthSq;
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

        double normDirX = segX * invSegLength;
        double normDirY = segY * invSegLength;
        double normDirZ = segZ * invSegLength;

        double alongAxis = relX * normDirX + relY * normDirY + relZ * normDirZ;

        double perpX = relX - normDirX * alongAxis;
        double perpY = relY - normDirY * alongAxis;
        double perpZ = relZ - normDirZ * alongAxis;

        double distXZSq = perpX * perpX + perpZ * perpZ; // Math.sqrt() を排除して二乗値のまま処理
        double distY = Math.abs(perpY);

        double radiusH = Config.getCylinderRadiusHorizontal();
        double radiusV = Config.getCylinderRadiusVertical();

        return distXZSq / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);
    }

    public static boolean isInCylinderForTrapdoor(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        return isInCylinderForTrapdoor(pos, playerPos.x, playerPos.y, playerPos.z, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static boolean isInCylinderForTrapdoor(BlockPos pos,
            double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ) {
        // トラップドアはシフト無しの軸で判定するため、フレームキャッシュを使わずここで軸を求める。
        double segX = playerX - cameraX;
        double segY = playerY - cameraY;
        double segZ = playerZ - cameraZ;
        double segLengthSq = segX * segX + segY * segY + segZ * segZ;
        if (segLengthSq < MIN_SEGMENT_LENGTH_SQ) {
            return false;
        }
        double segLength = Math.sqrt(segLengthSq);
        double normalizedDistSq = cylinderValue(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                cameraX, cameraY, cameraZ,
                segX, segY, segZ, segLengthSq, 1.0 / segLength, EXTENSION_BLOCKS / segLength);
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
