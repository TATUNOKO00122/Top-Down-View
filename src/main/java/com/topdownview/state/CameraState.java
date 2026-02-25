package com.topdownview.state;

import net.minecraft.world.phys.Vec3;

/**
 * カメラ状態管理
 * カメラの位置、角度、ズームレベルを管理
 * Minecraftはシングルスレッドなので同期処理は不要
 */
public final class CameraState {

    // 角度計算定数
    private static final float ANGLE_RANGE = 360.0f;
    private static final float HALF_ANGLE_RANGE = 180.0f;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;
    private static final int MAX_NORMALIZE_ITERATIONS = 100;

    // デフォルト値定数
    public static final float DEFAULT_PITCH = 45.0f;
    public static final float DEFAULT_YAW = 0.0f;
    public static final float DEFAULT_ZOOM = 5.0f;
    public static final Vec3 DEFAULT_POSITION = Vec3.ZERO;

    // Runtime values from Config
    public static double get_default_camera_distance() {
        return com.topdownview.client.ClientForgeEvents.getDefaultCameraDistance();
    }

    public static double get_min_camera_distance() {
        return com.topdownview.client.ClientForgeEvents.getMinCameraDistance();
    }

    public static double get_max_camera_distance() {
        return com.topdownview.client.ClientForgeEvents.getMaxCameraDistance();
    }

    // シングルトンインスタンス
    public static final CameraState INSTANCE = new CameraState();

    // カメラ制御
    private float yaw = DEFAULT_YAW;
    private float prevYaw = DEFAULT_YAW;
    private float targetYaw = DEFAULT_YAW;
    private boolean isAnimating = false;
    private float pitch = DEFAULT_PITCH;
    private double x = 0.0;
    private double z = 0.0;
    private float zoom = DEFAULT_ZOOM;
    private double cameraDistance = -1.0; // -1 indicates uninitialized
    private Vec3 cameraPosition = DEFAULT_POSITION;

    private CameraState() {
    }

    // ==================== Getters ====================

    public float getYaw() {
        return yaw;
    }

    public float getPrevYaw() {
        return prevYaw;
    }

    public float getTargetYaw() {
        return targetYaw;
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    public float getPitch() {
        return pitch;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public float getZoom() {
        return zoom;
    }

    public double getCameraDistance() {
        if (cameraDistance < 0) {
            cameraDistance = get_default_camera_distance();
        }
        return cameraDistance;
    }

    /**
     * カメラ位置の防御的コピーを返す
     * Vec3はイミュータブルなので新しいインスタンスを生成
     */
    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    // ==================== Setters with Validation ====================

    public void setYaw(float value) {
        yaw = normalizeAngle(value);
    }

    public void setTargetYaw(float value) {
        targetYaw = normalizeAngle(value);
    }

    public void setAnimating(boolean value) {
        isAnimating = value;
    }

    public void setPitch(float value) {
        pitch = clampPitch(value);
    }

    public void setX(double value) {
        validateFinite(value, "X coordinate");
        x = value;
    }

    public void setZ(double value) {
        validateFinite(value, "Z coordinate");
        z = value;
    }

    public void setZoom(float value) {
        if (value < 0.0f || Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Zoom must be non-negative and finite: " + value);
        }
        zoom = value;
    }

    public void setCameraDistance(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Camera distance must be finite: " + value);
        }
        if (value < get_min_camera_distance() || value > get_max_camera_distance()) {
            throw new IllegalArgumentException(
                    String.format("Camera distance must be between %.1f and %.1f: %.1f",
                            get_min_camera_distance(), get_max_camera_distance(), value));
        }
        cameraDistance = value;
    }

    /**
     * カメラ位置を設定（防御的コピー）
     */
    public void setCameraPosition(Vec3 value) {
        if (value == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException("Position coordinates must be finite");
        }
        cameraPosition = new Vec3(value.x, value.y, value.z);
    }

    /**
     * カメラ距離を増加
     */
    public double increaseCameraDistance(double delta) {
        double newDistance = Math.min(get_max_camera_distance(), cameraDistance + delta);
        setCameraDistance(newDistance);
        return newDistance;
    }

    /**
     * カメラ距離を減少
     */
    public double decreaseCameraDistance(double delta) {
        double newDistance = Math.max(get_min_camera_distance(), cameraDistance - delta);
        setCameraDistance(newDistance);
        return newDistance;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        yaw = DEFAULT_YAW;
        prevYaw = DEFAULT_YAW;
        targetYaw = DEFAULT_YAW;
        isAnimating = false;
        pitch = DEFAULT_PITCH;
        x = 0.0;
        z = 0.0;
        zoom = DEFAULT_ZOOM;
        cameraDistance = get_default_camera_distance();
        cameraPosition = DEFAULT_POSITION;
    }

    // ==================== Utility Methods ====================

    /**
     * 現在の yaw を prevYaw に保存する
     */
    public void updatePrevYaw() {
        this.prevYaw = this.yaw;
    }

    /**
     * 最短距離での角度補間 (Lerp) を行う
     */
    public float getLerpYaw(float partialTicks) {
        float f = this.yaw - this.prevYaw;
        while (f < -180.0F) {
            f += 360.0F;
        }
        while (f >= 180.0F) {
            f -= 360.0F;
        }
        return this.prevYaw + partialTicks * f;
    }

    /**
     * カメラの視線方向ベクトルを取得
     * yawとpitchから計算（FloodCullerと同じ計算方式）
     */
    public Vec3 getLookVector() {
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z).normalize();
    }

    /**
     * 角度を -180 ~ 180 の範囲に正規化
     * 無限ループ防止のため最大イテレーション数を設定
     */
    private static float normalizeAngle(float angle) {
        if (Float.isNaN(angle) || Float.isInfinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite: " + angle);
        }

        float result = angle;
        int iterations = 0;

        while (result > HALF_ANGLE_RANGE && iterations < MAX_NORMALIZE_ITERATIONS) {
            result -= ANGLE_RANGE;
            iterations++;
        }
        while (result < -HALF_ANGLE_RANGE && iterations < MAX_NORMALIZE_ITERATIONS) {
            result += ANGLE_RANGE;
            iterations++;
        }

        if (iterations >= MAX_NORMALIZE_ITERATIONS) {
            // 数学的な方法で正規化
            result = ((result + HALF_ANGLE_RANGE) % ANGLE_RANGE) - HALF_ANGLE_RANGE;
            if (result < -HALF_ANGLE_RANGE) {
                result += ANGLE_RANGE;
            }
        }

        return result;
    }

    /**
     * ピッチを -90 ~ 90 の範囲に制限
     */
    private static float clampPitch(float pitch) {
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("Pitch must be finite: " + pitch);
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    /**
     * 値が有限数か検証
     */
    private static void validateFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
