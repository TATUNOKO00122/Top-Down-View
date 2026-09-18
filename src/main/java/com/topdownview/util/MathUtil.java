package com.topdownview.util;

/**
 * 数値クランプと角度正規化・補間の単一情報源。
 */
public final class MathUtil {

    private MathUtil() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 角度を [-180, 180) に正規化する。非有限値は 0 を返す。 */
    public static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) {
            return 0.0f;
        }
        angle = angle % 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }

    /** 最短経路で 2 つの角度を補間する。 */
    public static float lerpAngle(float from, float to, float t) {
        return normalizeAngle(from + normalizeAngle(to - from) * t);
    }
}
