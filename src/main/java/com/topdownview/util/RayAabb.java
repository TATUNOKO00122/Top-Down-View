package com.topdownview.util;

/**
 * レイと AABB のスラブ（1軸分）交差計算の単一情報源。
 */
public final class RayAabb {

    private static final double PARALLEL_EPSILON = 1.0E-8;

    private RayAabb() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 1軸分のスラブ交差で {@code range[0] = 入口, range[1] = 出口} を更新する。
     * 方向成分が平行とみなせる場合は原点がスラブ内かを判定するだけ。
     *
     * @return 交差範囲が空でない場合 true
     */
    public static boolean clip(double origin, double dir, double min, double max, double[] range) {
        if (Math.abs(dir) < PARALLEL_EPSILON) {
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
        if (t1 > range[0]) {
            range[0] = t1;
        }
        if (t2 < range[1]) {
            range[1] = t2;
        }
        return range[0] <= range[1];
    }
}
