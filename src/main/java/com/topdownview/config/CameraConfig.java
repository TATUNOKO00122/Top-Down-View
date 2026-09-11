package com.topdownview.config;

/**
 * カメラに関する設定項目とキャッシュ状態を保持する構成クラス。
 */
public final class CameraConfig {

    private int rotateAngleMode = 1;
    private double cameraSnapRotationSpeed = 0.2;
    private double cameraPitch = 40.0;
    private double miningModePitch = 45.0;
    private double maxCameraDistance = 50.0;
    private double defaultCameraDistance = 30.0;
    private boolean cameraYFollowDelayEnabled = true;
    private double cameraYFollowDelay = 1.0;
    private boolean cameraXFollowDelayEnabled = false;
    private double cameraXFollowDelay = 1.0;
    private boolean cameraZFollowDelayEnabled = false;
    private double cameraZFollowDelay = 1.0;
    private boolean followDelayWhileMounted = false;
    private double playerScreenOffset = 0.5;
    private boolean headBodyRotationEnabled = true;
    private boolean waterMovementControlEnabled = true;
    private boolean independentMountAim = true;
    private int mountAimMaxTwist = 90;
    private double mountTurnSmoothing = 0.25;
    private int boatHeadMaxTwist = 60;
    private int boatBodyMaxTwist = 45;
    private int topDownFov = 30;
    private boolean lockedTopDown = false;
    private boolean scrollOnlyZoomEnabled = false;
    private boolean cameraZoomSmoothingEnabled = true;
    private double cameraZoomSmoothing = 0.15;
    private boolean mousePanEnabled = false;
    private double mousePanMaxDistance = 5.0;
    private double mousePanSmoothing = 0.1;

    public int getRotateAngleMode() { return rotateAngleMode; }
    public void setRotateAngleMode(int value) { this.rotateAngleMode = clamp(value, 0, 2); }

    public double getCameraSnapRotationSpeed() { return cameraSnapRotationSpeed; }
    public void setCameraSnapRotationSpeed(double value) { this.cameraSnapRotationSpeed = clamp(value, 0.05, 0.5); }

    public double getCameraPitch() { return cameraPitch; }
    public void setCameraPitch(double value) { this.cameraPitch = clamp(value, 10.0, 90.0); }

    public double getMiningModePitch() { return miningModePitch; }
    public void setMiningModePitch(double value) { this.miningModePitch = clamp(value, 10.0, 90.0); }

    public double getMaxCameraDistance() { return maxCameraDistance; }
    public void setMaxCameraDistance(double value) { this.maxCameraDistance = clamp(value, 0.0, 200.0); }

    public double getDefaultCameraDistance() { return defaultCameraDistance; }
    public void setDefaultCameraDistance(double value) { this.defaultCameraDistance = Math.min(clamp(value, 0.0, 200.0), maxCameraDistance); }

    public boolean isCameraYFollowDelayEnabled() { return cameraYFollowDelayEnabled; }
    public void setCameraYFollowDelayEnabled(boolean value) { this.cameraYFollowDelayEnabled = value; }

    public double getCameraYFollowDelay() { return cameraYFollowDelay; }
    public void setCameraYFollowDelay(double value) { this.cameraYFollowDelay = clamp(value, 0.0, 4.0); }

    public boolean isCameraXFollowDelayEnabled() { return cameraXFollowDelayEnabled; }
    public void setCameraXFollowDelayEnabled(boolean value) { this.cameraXFollowDelayEnabled = value; }

    public double getCameraXFollowDelay() { return cameraXFollowDelay; }
    public void setCameraXFollowDelay(double value) { this.cameraXFollowDelay = clamp(value, 0.0, 4.0); }

    public boolean isCameraZFollowDelayEnabled() { return cameraZFollowDelayEnabled; }
    public void setCameraZFollowDelayEnabled(boolean value) { this.cameraZFollowDelayEnabled = value; }

    public double getCameraZFollowDelay() { return cameraZFollowDelay; }
    public void setCameraZFollowDelay(double value) { this.cameraZFollowDelay = clamp(value, 0.0, 4.0); }

    public boolean isFollowDelayWhileMounted() { return followDelayWhileMounted; }
    public void setFollowDelayWhileMounted(boolean value) { this.followDelayWhileMounted = value; }

    public double getPlayerScreenOffset() { return playerScreenOffset; }
    public void setPlayerScreenOffset(double value) { this.playerScreenOffset = clamp(value, -10.0, 10.0); }

    public boolean isHeadBodyRotationEnabled() { return headBodyRotationEnabled; }
    public void setHeadBodyRotationEnabled(boolean value) { this.headBodyRotationEnabled = value; }

    public boolean isWaterMovementControlEnabled() { return waterMovementControlEnabled; }
    public void setWaterMovementControlEnabled(boolean value) { this.waterMovementControlEnabled = value; }

    public boolean isIndependentMountAim() { return independentMountAim; }
    public void setIndependentMountAim(boolean value) { this.independentMountAim = value; }

    public int getMountAimMaxTwist() { return mountAimMaxTwist; }
    public void setMountAimMaxTwist(int value) { this.mountAimMaxTwist = clamp(value, 45, 360); }

    public double getMountTurnSmoothing() { return mountTurnSmoothing; }
    public void setMountTurnSmoothing(double value) { this.mountTurnSmoothing = clamp(value, 0.05, 1.0); }

    public int getBoatHeadMaxTwist() { return boatHeadMaxTwist; }
    public void setBoatHeadMaxTwist(int value) { this.boatHeadMaxTwist = clamp(value, 30, 180); }

    public int getBoatBodyMaxTwist() { return boatBodyMaxTwist; }
    public void setBoatBodyMaxTwist(int value) { this.boatBodyMaxTwist = clamp(value, 15, 90); }

    public int getTopDownFov() { return topDownFov; }
    public void setTopDownFov(int value) { this.topDownFov = clamp(value, 30, 110); }

    public boolean isLockedTopDown() { return lockedTopDown; }
    public void setLockedTopDown(boolean value) { this.lockedTopDown = value; }

    public boolean isScrollOnlyZoomEnabled() { return scrollOnlyZoomEnabled; }
    public void setScrollOnlyZoomEnabled(boolean value) { this.scrollOnlyZoomEnabled = value; }

    public boolean isCameraZoomSmoothingEnabled() { return cameraZoomSmoothingEnabled; }
    public void setCameraZoomSmoothingEnabled(boolean value) { this.cameraZoomSmoothingEnabled = value; }

    public double getCameraZoomSmoothing() { return cameraZoomSmoothing; }
    public void setCameraZoomSmoothing(double value) { this.cameraZoomSmoothing = clamp(value, 0.0, 1.0); }

    public boolean isMousePanEnabled() { return mousePanEnabled; }
    public void setMousePanEnabled(boolean value) { this.mousePanEnabled = value; }

    public double getMousePanMaxDistance() { return mousePanMaxDistance; }
    public void setMousePanMaxDistance(double value) { this.mousePanMaxDistance = clamp(value, 0.0, 20.0); }

    public double getMousePanSmoothing() { return mousePanSmoothing; }
    public void setMousePanSmoothing(double value) { this.mousePanSmoothing = clamp(value, 0.01, 0.2); }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
