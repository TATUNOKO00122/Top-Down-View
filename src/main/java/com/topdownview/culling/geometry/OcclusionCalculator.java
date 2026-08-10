package com.topdownview.culling.geometry;

import net.minecraft.core.BlockPos;

/**
 * ブロックがカメラからプレイヤーへの視線を遮るか判定するユーティリティ。
 */
public final class OcclusionCalculator {
    private OcclusionCalculator() {}

    public static boolean isOccludingView(BlockPos pos, double cX, double cY, double cZ, double pX, double pY, double pZ) {
        double minX = pos.getX() - 0.5;
        double minY = pos.getY() - 0.5;
        double minZ = pos.getZ() - 0.5;
        double maxX = pos.getX() + 1.5;
        double maxY = pos.getY() + 1.5;
        double maxZ = pos.getZ() + 1.5;

        double dirX = pX - cX;
        double dirY = pY - cY;
        double dirZ = pZ - cZ;
        double rayLengthSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (rayLengthSq < 1.0E-12) {
            return false;
        }

        double[] t = {0.0, 1.0};
        if (!slabIntersect(cX, dirX, minX, maxX, t)) return false;
        if (!slabIntersect(cY, dirY, minY, maxY, t)) return false;
        if (!slabIntersect(cZ, dirZ, minZ, maxZ, t)) return false;

        return t[1] < 0.999;
    }

    private static boolean slabIntersect(double origin, double dir, double min, double max, double[] t) {
        if (Math.abs(dir) < 1.0E-9) {
            return origin >= min && origin <= max;
        }
        double invDir = 1.0 / dir;
        double t1 = (min - origin) * invDir;
        double t2 = (max - origin) * invDir;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        t[0] = Math.max(t[0], t1);
        t[1] = Math.min(t[1], t2);
        return t[0] <= t[1];
    }
}
