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
        static CylinderMetrics invalid() {
            return new CylinderMetrics(-1.0, 0.0, 0.0);
        }

        boolean isInside() {
            return normalizedDistSq <= 1.0;
        }
    }

    private static CylinderMetrics computeCylinderMetrics(BlockPos pos, Vec3 playerPos, Vec3 cameraPos,
            boolean useShift, double explicitYaw) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        Vec3 shiftedPlayerPos;
        if (useShift) {
            double yaw = explicitYaw;
            double yawRad = Math.toRadians(yaw);
            double shift = Config.getCylinderForwardShift();
            shiftedPlayerPos = new Vec3(
                    playerPos.x + shift * (-Math.sin(yawRad)),
                    playerPos.y,
                    playerPos.z + shift * Math.cos(yawRad));
        } else {
            shiftedPlayerPos = playerPos;
        }

        Vec3 dir = shiftedPlayerPos.subtract(cameraPos);
        double dirLengthSq = dir.lengthSqr();
        if (dirLengthSq < 1.0E-8) {
            return CylinderMetrics.invalid();
        }

        Vec3 normDir = dir.normalize();

        double radiusH = Config.getCylinderRadiusHorizontal();
        double radiusV = Config.getCylinderRadiusVertical();

        Vec3 segVec = shiftedPlayerPos.subtract(cameraPos);
        double segLengthSq = segVec.lengthSqr();

        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(segVec) / segLengthSq;

        double segLength = Math.sqrt(segLengthSq);
        double extensionT = 3.0 / segLength;
        if (t < -extensionT) {
            return CylinderMetrics.invalid();
        }

        t = Math.max(-extensionT, Math.min(t, 1.0));

        Vec3 closestPoint = cameraPos.add(segVec.scale(t));
        Vec3 relPos = blockCenter.subtract(closestPoint);
        double alongAxis = relPos.dot(normDir);
        Vec3 perpPos = relPos.subtract(normDir.scale(alongAxis));

        double distXZ = Math.sqrt(perpPos.x * perpPos.x + perpPos.z * perpPos.z);
        double distY = Math.abs(perpPos.y);

        double normalizedDistSq = (distXZ * distXZ) / (radiusH * radiusH)
                + (distY * distY) / (radiusV * radiusV);

        return new CylinderMetrics(normalizedDistSq, distXZ, distY);
    }

    public static double getNormalizedDistanceSq(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        double yaw = ModState.CAMERA.getYaw();
        CylinderMetrics metrics = computeCylinderMetrics(pos, playerPos, cameraPos, true, yaw);
        return metrics.normalizedDistSq();
    }

    public static boolean isInCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos, double yaw) {
        CylinderMetrics metrics = computeCylinderMetrics(pos, playerPos, cameraPos, true, yaw);
        if (metrics.normalizedDistSq() < 0) {
            return false;
        }
        return metrics.isInside();
    }

    public static boolean isInCylinderForTrapdoor(BlockPos pos, Vec3 playerPos, Vec3 cameraPos) {
        CylinderMetrics metrics = computeCylinderMetrics(pos, playerPos, cameraPos, false, 0.0);
        if (metrics.normalizedDistSq() < 0) {
            return false;
        }
        return metrics.isInside();
    }

    /**
     * マイニングモード用の真円柱判定
     * 水平・垂直ともに同じ半径の真円柱
     * 
     * @param pos ブロック位置
     * @param playerPos プレイヤー位置
     * @param cameraPos カメラ位置
     * @param radius 円柱の半径（ブロック数）
     * @param yaw 前方シフト用のヨー角度（度）
     * @param forwardShift 前方シフト距離（ブロック数）
     * @return 円柱内ならtrue
     */
    public static boolean isInMiningCylinder(BlockPos pos, Vec3 playerPos, Vec3 cameraPos, 
            double radius, double yaw, double forwardShift) {
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // 前方シフトを適用したプレイヤー位置を計算
        Vec3 shiftedPlayerPos;
        if (forwardShift != 0.0) {
            double yawRad = Math.toRadians(yaw);
            shiftedPlayerPos = new Vec3(
                    playerPos.x + forwardShift * (-Math.sin(yawRad)),
                    playerPos.y,
                    playerPos.z + forwardShift * Math.cos(yawRad));
        } else {
            shiftedPlayerPos = playerPos;
        }

        // カメラ→シフト後プレイヤーのベクトル（円柱の軸）
        Vec3 axis = shiftedPlayerPos.subtract(cameraPos);
        double axisLengthSq = axis.lengthSqr();
        if (axisLengthSq < 1.0E-8) {
            return false;
        }

        // 軸に沿った位置を計算（t=0がカメラ、t=1がシフト後プレイヤー）
        Vec3 toBlock = blockCenter.subtract(cameraPos);
        double t = toBlock.dot(axis) / axisLengthSq;

        // 軸の範囲外（カメラより後ろ、またはシフト後プレイヤーより先）は円柱外
        double axisLength = Math.sqrt(axisLengthSq);
        double extensionT = 3.0 / axisLength;
        if (t < -extensionT || t > 1.0 + extensionT) {
            return false;
        }

        // 軸上の最近傍点
        Vec3 closestPoint = cameraPos.add(axis.scale(Math.max(0, Math.min(1.0, t))));

        // 3D距離（軸からの距離）
        double dx = blockCenter.x - closestPoint.x;
        double dy = blockCenter.y - closestPoint.y;
        double dz = blockCenter.z - closestPoint.z;
        double distFromAxis = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 真円柱判定：軸からの3D距離が半径以内
        return distFromAxis <= radius;
    }
}
