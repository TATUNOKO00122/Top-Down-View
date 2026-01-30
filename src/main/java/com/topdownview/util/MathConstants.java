package com.topdownview.util;

/**
 * 数学定数ユーティリティ
 */
public final class MathConstants {
    public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
    
    /**
     * ピッチ角が真上（90度）に近いと判定する閾値
     * 90度に非常に近い場合、水平方向のオフセットが存在しないため特別扱いが必要
     */
    public static final float PITCH_NEAR_VERTICAL = 89.9f;
    
    /**
     * ドット積の閾値（ベクトルが平行に近いかどうかの判定用）
     * 0.1より小さい場合はほぼ直交、大きい場合は平行に近い
     */
    public static final double DOT_PRODUCT_THRESHOLD = 0.1;

    private MathConstants() {
        throw new IllegalStateException("ユーティリティクラス");
    }
}
