package com.topdownview.state;

import com.topdownview.util.MathUtil;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

/**
 * カメラ状態管理
 * カメラの位置、角度、ズームレベルを管理
 * カメラ距離の単一の真実のソース（Single Source of Truth）
 */
public final class CameraState {

    // 角度計算定数
    private static final float MIN_PITCH = -90.0f;
    private static final float MAX_PITCH = 90.0f;

    // デフォルト値定数
    public static final float DEFAULT_PITCH = 45.0f;
    public static final float DEFAULT_YAW = 0.0f;
    public static final Vec3 DEFAULT_POSITION = Vec3.ZERO;

    // カメラ距離定数（Single Source of Truth）
    public static final double MIN_CAMERA_DISTANCE = 0.0;
    public static final double MAX_CAMERA_DISTANCE = 50.0;
    public static final double DEFAULT_CAMERA_DISTANCE = 9.0;

    public static final CameraState INSTANCE = new CameraState();

    private volatile float yaw = DEFAULT_YAW;
    private volatile float prevYaw = DEFAULT_YAW;
    private volatile float targetYaw = DEFAULT_YAW;
    private volatile boolean isAnimating = false;
    private volatile float pitch = DEFAULT_PITCH;
    private volatile double x = 0.0;
    private volatile double z = 0.0;
    private volatile double cameraDistance = DEFAULT_CAMERA_DISTANCE;
    // レンダリング用補間済み距離（スムージング有効時は target=cameraDistance へ追従）
    private volatile double renderCameraDistance = DEFAULT_CAMERA_DISTANCE;
    private volatile Vec3 cameraPosition = DEFAULT_POSITION;
    private volatile CameraType previousCameraType = null;
    private volatile long lastAutoAlignTick = 0;
    private volatile float lastMovementDirection = 0.0f;
    private volatile int stableDirectionTicks = 0;
    private volatile boolean isAutoAlignAnimation = false;
    // 自動回転の加速モード用ランプ係数（0.0〜1.0）
    private volatile float autoAlignAnimationRamp = 0.0f;

    private volatile boolean isDragging = false;
    private volatile float dragStartYaw = DEFAULT_YAW;
    private volatile double dragStartMouseX = 0.0;

    private volatile double currentCameraY = 0.0;
    private volatile double targetCameraY = 0.0;
    private volatile boolean cameraYInitialized = false;

    private volatile double currentCameraX = 0.0;
    private volatile double currentCameraZ = 0.0;
    private volatile double targetCameraX = 0.0;
    private volatile double targetCameraZ = 0.0;
    private volatile boolean cameraXInitialized = false;
    private volatile boolean cameraZInitialized = false;

    private volatile boolean isFreeCameraMode = false;
    private volatile float freeCameraPitch = DEFAULT_PITCH;
    private volatile float prevFreeCameraPitch = DEFAULT_PITCH;
    private volatile double lastMouseX = 0.0;
    private volatile double lastMouseY = 0.0;
    private volatile boolean freeCameraMouseInitialized = false;
    private volatile boolean freeCameraPitchAdjusted = false;

    private volatile double currentMousePanX = 0.0;
    private volatile double currentMousePanZ = 0.0;

    private CameraState() {
    }

    public static boolean isPositionValid(Vec3 pos) {
        return pos != null && pos != DEFAULT_POSITION;
    }

    // ==================== Getters ====================

    public float getYaw() {
        return yaw;
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

    public double getCameraDistance() {
        return cameraDistance;
    }

    public double getRenderCameraDistance() {
        return renderCameraDistance;
    }

    public void setRenderCameraDistance(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Render camera distance must be finite: " + value);
        }
        renderCameraDistance = value;
    }

    /**
     * 補間済み距離を target（cameraDistance）へ1フレーム分線形補間で近づける
     * @param lerpFactor 0.0〜1.0 の補間係数（1.0なら即座に一致）
     */
    public void stepRenderCameraDistance(double lerpFactor) {
        if (lerpFactor >= 1.0 || lerpFactor <= 0.0) {
            renderCameraDistance = cameraDistance;
            return;
        }
        renderCameraDistance += (cameraDistance - renderCameraDistance) * lerpFactor;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public double getCameraX() {
        return cameraPosition.x;
    }

    public double getCameraY() {
        return cameraPosition.y;
    }

    public double getCameraZ() {
        return cameraPosition.z;
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

    public float getAutoAlignAnimationRamp() {
        return autoAlignAnimationRamp;
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
        // スムージング無効時は補間済み距離も即座に同期する（操作感の整合性担保）
        if (!com.topdownview.Config.isCameraZoomSmoothingEnabled()) {
            renderCameraDistance = value;
        }
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

    public void setAutoAlignAnimationRamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Auto align animation ramp must be finite: " + value);
        }
        autoAlignAnimationRamp = Math.max(0.0f, Math.min(1.0f, value));
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

    // ==================== Free Camera Mode Getters ====================

    public boolean isFreeCameraMode() {
        return isFreeCameraMode;
    }

    public float getFreeCameraPitch() {
        return freeCameraPitch;
    }

    public float getLerpFreeCameraPitch(float partialTicks) {
        return prevFreeCameraPitch + (freeCameraPitch - prevFreeCameraPitch) * partialTicks;
    }

    // ==================== Free Camera Mode Setters ====================

    public void setFreeCameraMode(boolean value) {
        isFreeCameraMode = value;
    }

    public void setFreeCameraPitch(float value) {
        freeCameraPitch = clampPitch(value);
    }

    public void updatePrevFreeCameraPitch() {
        prevFreeCameraPitch = freeCameraPitch;
    }

    public double getLastMouseX() {
        return lastMouseX;
    }

    public double getLastMouseY() {
        return lastMouseY;
    }

    public boolean isFreeCameraMouseInitialized() {
        return freeCameraMouseInitialized;
    }

    public void setLastMouseX(double value) {
        lastMouseX = value;
    }

    public void setLastMouseY(double value) {
        lastMouseY = value;
    }

    public void setFreeCameraMouseInitialized(boolean value) {
        freeCameraMouseInitialized = value;
    }

    public boolean isFreeCameraPitchAdjusted() {
        return freeCameraPitchAdjusted;
    }

    public void setFreeCameraPitchAdjusted(boolean value) {
        freeCameraPitchAdjusted = value;
    }

    public double getCurrentMousePanX() {
        return currentMousePanX;
    }

    public double getCurrentMousePanZ() {
        return currentMousePanZ;
    }

    public void setCurrentMousePanX(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Mouse Pan X must be finite: " + value);
        }
        currentMousePanX = value;
    }

    public void setCurrentMousePanZ(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Mouse Pan Z must be finite: " + value);
        }
        currentMousePanZ = value;
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
        cameraDistance = getEffectiveDefaultCameraDistance();
        renderCameraDistance = cameraDistance;
        cameraPosition = DEFAULT_POSITION;
        previousCameraType = null;
        lastAutoAlignTick = 0;
        lastMovementDirection = 0.0f;
        stableDirectionTicks = 0;
        isAutoAlignAnimation = false;
        autoAlignAnimationRamp = 0.0f;
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
        isFreeCameraMode = false;
        freeCameraPitch = DEFAULT_PITCH;
        prevFreeCameraPitch = DEFAULT_PITCH;
        lastMouseX = 0.0;
        lastMouseY = 0.0;
        freeCameraMouseInitialized = false;
        freeCameraPitchAdjusted = false;
        currentMousePanX = 0.0;
        currentMousePanZ = 0.0;
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

    public static float normalizeAngle(float angle) {
        if (Float.isNaN(angle) || Float.isInfinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite: " + angle);
        }

        return MathUtil.normalizeAngle(angle);
    }

    private static float clampPitch(float pitch) {
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("Pitch must be finite: " + pitch);
        }
        return MathUtil.clamp(pitch, MIN_PITCH, MAX_PITCH);
    }

    private static void validateFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }
}
