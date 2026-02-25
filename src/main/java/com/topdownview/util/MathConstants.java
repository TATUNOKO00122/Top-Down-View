package com.topdownview.util;

/**
 * 数学定数ユーティリティ
 */
public final class MathConstants {
    public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;

    public static final float HALF_ANGLE = 180.0f;

    public static final float FULL_ANGLE = 360.0f;

    private MathConstants() {
        throw new IllegalStateException("ユーティリティクラス");
    }
}
