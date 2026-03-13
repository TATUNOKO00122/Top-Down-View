package com.topdownview.state;

import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

/**
 * カメラ状態管理
 * カメラの位置、角度、ズームレベルを管理
 * カメラ距離の単一の真実のソース（Single Source of Truth）
 */
public final class CameraState {

    // 角度計算定数
    private static final float ANGLE_RANGE = 360.0f;
    private static final float HALF_ANGLE_RANGE = 180.0f;
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;

    // デフォルト値定数
    public static final float DEFAULT_PITCH = 45.0f;
    public static final float DEFAULT_YAW = 0.0f;
    public static final float DEFAULT_ZOOM = 5.0f;
    public static final Vec3 DEFAULT_POSITION = Vec3.ZERO;

    // カメラ距離定数（Single Source of Truth）
    public static final double MIN_CAMERA_DISTANCE = 5.0;
    public static final double MAX_CAMERA_DISTANCE = 50.0;
    public static final double DEFAULT_CAMERA_DISTANCE = 9.0;

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
    private double cameraDistance = DEFAULT_CAMERA_DISTANCE;
    private Vec3 cameraPosition = DEFAULT_POSITION;
    private CameraType previousCameraType = null;
    private long lastAutoAlignTick = 0;
    private float lastMovementDirection = 0.0f;
    private int stableDirectionTicks = 0;
    private boolean isAutoAlignAnimation = false;

    // ドラッグ回転用状態
    private boolean isDragging = false;
    private float dragStartYaw = DEFAULT_YAW;
    private double dragStartMouseX = 0.0;

    // カメラY軸遅延追従用状態
    private double currentCameraY = 0.0;
    private double targetCameraY = 0.0;
    private boolean cameraYInitialized = false;

    // カメラXZ軸遅延追従用状態
    private double currentCameraX = 0.0;
    private double currentCameraZ = 0.0;
    private double targetCameraX = 0.0;
    private double targetCameraZ = 0.0;
    private boolean cameraXInitialized = false;
    private boolean cameraZInitialized = false;

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
        return cameraDistance;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public CameraType getPreviousCameraType() {
        return previousCameraType;
    }

    public long getLastAutoAlignTick() {
        return lastAutoAlignTick;
    }

    public float getLastMovementDirection() {
        return lastMovementDirection;
    }

    public int getStableDirectionTicks() {
        return stableDirectionTicks;
    }

    public boolean isAutoAlignAnimation() {
        return isAutoAlignAnimation;
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
        double maxDistance = getEffectiveMaxCameraDistance();
        if (value < MIN_CAMERA_DISTANCE || value > maxDistance) {
            throw new IllegalArgumentException(
                    String.format("Camera distance must be between %.1f and %.1f: %.1f",
                            MIN_CAMERA_DISTANCE, maxDistance, value));
        }
        cameraDistance = value;
    }

    public void setCameraPosition(Vec3 value) {
        if (value == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException("Position coordinates must be finite");
        }
        cameraPosition = value;
    }

    public void setPreviousCameraType(CameraType type) {
        previousCameraType = type;
    }

    public void setLastAutoAlignTick(long tick) {
        lastAutoAlignTick = tick;
    }

    public void setLastMovementDirection(float direction) {
        lastMovementDirection = normalizeAngle(direction);
    }

    public void setStableDirectionTicks(int ticks) {
        stableDirectionTicks = ticks;
    }

    public void setAutoAlignAnimation(boolean value) {
        isAutoAlignAnimation = value;
    }

    // ==================== Drag Rotation Getters ====================

    public boolean isDragging() {
        return isDragging;
    }

    public float getDragStartYaw() {
        return dragStartYaw;
    }

    public double getDragStartMouseX() {
        return dragStartMouseX;
    }

    // ==================== Camera Y Follow Delay Getters ====================

    public double getCurrentCameraY() {
        return currentCameraY;
    }

    public double getTargetCameraY() {
        return targetCameraY;
    }

    public boolean isCameraYInitialized() {
        return cameraYInitialized;
    }

    // ==================== Camera XZ Follow Delay Getters ====================

    public double getCurrentCameraX() {
        return currentCameraX;
    }

    public double getCurrentCameraZ() {
        return currentCameraZ;
    }

    public double getTargetCameraX() {
        return targetCameraX;
    }

    public double getTargetCameraZ() {
        return targetCameraZ;
    }

    public boolean isCameraXInitialized() {
        return cameraXInitialized;
    }

    public boolean isCameraZInitialized() {
        return cameraZInitialized;
    }

    // ==================== Drag Rotation Setters ====================

    public void setDragging(boolean value) {
        isDragging = value;
    }

    public void setDragStartYaw(float value) {
        dragStartYaw = normalizeAngle(value);
    }

    public void setDragStartMouseX(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Mouse X must be finite: " + value);
        }
        dragStartMouseX = value;
    }

    // ==================== Camera Y Follow Delay Setters ====================

    public void setCurrentCameraY(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Camera Y must be finite: " + value);
        }
        currentCameraY = value;
    }

    public void setTargetCameraY(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Target Camera Y must be finite: " + value);
        }
        targetCameraY = value;
    }

    public void setCameraYInitialized(boolean value) {
        cameraYInitialized = value;
    }

    // ==================== Camera XZ Follow Delay Setters ====================

    public void setCurrentCameraX(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Camera X must be finite: " + value);
        }
        currentCameraX = value;
    }

    public void setCurrentCameraZ(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Camera Z must be finite: " + value);
        }
        currentCameraZ = value;
    }

    public void setTargetCameraX(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Target Camera X must be finite: " + value);
        }
        targetCameraX = value;
    }

    public void setTargetCameraZ(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Target Camera Z must be finite: " + value);
        }
        targetCameraZ = value;
    }

    public void setCameraXInitialized(boolean value) {
        cameraXInitialized = value;
    }

    public void setCameraZInitialized(boolean value) {
        cameraZInitialized = value;
    }

    /**
     * カメラ距離を増加
     */
    public double increaseCameraDistance(double delta) {
        double maxDistance = getEffectiveMaxCameraDistance();
        double newDistance = Math.min(maxDistance, cameraDistance + delta);
        setCameraDistance(newDistance);
        return newDistance;
    }

    /**
     * カメラ距離を減少
     */
    public double decreaseCameraDistance(double delta) {
        double newDistance = Math.max(MIN_CAMERA_DISTANCE, cameraDistance - delta);
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
        cameraDistance = getEffectiveDefaultCameraDistance();
        cameraPosition = DEFAULT_POSITION;
        previousCameraType = null;
        lastAutoAlignTick = 0;
        lastMovementDirection = 0.0f;
        stableDirectionTicks = 0;
        isAutoAlignAnimation = false;
        isDragging = false;
        dragStartYaw = DEFAULT_YAW;
        dragStartMouseX = 0.0;
        currentCameraY = 0.0;
        targetCameraY = 0.0;
        cameraYInitialized = false;
        currentCameraX = 0.0;
        currentCameraZ = 0.0;
        targetCameraX = 0.0;
        targetCameraZ = 0.0;
        cameraXInitialized = false;
        cameraZInitialized = false;
    }

    /**
     * 有効なデフォルトカメラ距離を取得（Configからキャッシュ済み値を取得）
     */
    public static double getEffectiveDefaultCameraDistance() {
        double configValue = com.topdownview.Config.getDefaultCameraDistance();
        double maxDistance = com.topdownview.Config.getMaxCameraDistance();
        // Config値が範囲外の場合はフォールバック
        if (configValue < MIN_CAMERA_DISTANCE || configValue > maxDistance) {
            return DEFAULT_CAMERA_DISTANCE;
        }
        return configValue;
    }
    
    /**
     * 有効な最大カメラ距離を取得（Configからキャッシュ済み値を取得）
     */
    public static double getEffectiveMaxCameraDistance() {
        return com.topdownview.Config.getMaxCameraDistance();
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
        float diff = this.yaw - this.prevYaw;
        diff = normalizeAngle(diff);
        return this.prevYaw + partialTicks * diff;
    }

    /**
     * カメラの視線方向ベクトルを取得
     */
    public Vec3 getLookVector() {
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vec3(x, y, z).normalize();
    }

    private static float normalizeAngle(float angle) {
        if (Float.isNaN(angle) || Float.isInfinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite: " + angle);
        }

        float result = angle % ANGLE_RANGE;
        if (result > HALF_ANGLE_RANGE) {
            result -= ANGLE_RANGE;
        } else if (result < -HALF_ANGLE_RANGE) {
            result += ANGLE_RANGE;
        }

        return result;
    }

    private static float clampPitch(float pitch) {
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("Pitch must be finite: " + pitch);
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private static void validateFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
