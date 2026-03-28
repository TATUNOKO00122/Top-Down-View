package com.topdownview.state;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class PlayerRotationState {

    public static final PlayerRotationState INSTANCE = new PlayerRotationState();

    public static final float MAX_HEAD_BODY_DIFF = 45.0f;
    public static final float DEFAULT_LERP_SPEED = 0.2f;
    public static final float HEAD_LERP_SPEED = 0.3f;

    private float targetHeadYaw = 0.0f;
    private float currentHeadYaw = 0.0f;
    private float prevHeadYaw = 0.0f;

    private float targetBodyYaw = 0.0f;
    private float currentBodyYaw = 0.0f;
    private float prevBodyYaw = 0.0f;

    private boolean isUsingItem = false;
    private boolean hasMovementInput = false;

    private float bodyLerpSpeed = DEFAULT_LERP_SPEED;
    private float headLerpSpeed = HEAD_LERP_SPEED;

    private PlayerRotationState() {}

    public void updateTargetHeadYaw(Vec3 playerEyePos, Vec3 targetPos) {
        if (playerEyePos == null || targetPos == null) return;

        double dx = targetPos.x - playerEyePos.x;
        double dz = targetPos.z - playerEyePos.z;
        targetHeadYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        targetHeadYaw = normalizeAngle(targetHeadYaw);
    }

    public void updateTargetHeadYawDirect(float yaw) {
        targetHeadYaw = normalizeAngle(yaw);
    }

    public void updateTargetBodyYaw(float movementYaw, boolean hasInput) {
        hasMovementInput = hasInput;
        if (hasInput) {
            targetBodyYaw = normalizeAngle(movementYaw);
        }
    }

    public void tick() {
        prevHeadYaw = currentHeadYaw;
        prevBodyYaw = currentBodyYaw;

        if (isUsingItem) {
            currentHeadYaw = targetHeadYaw;
            currentBodyYaw = targetHeadYaw;
            return;
        }

        currentHeadYaw = lerpAngle(currentHeadYaw, targetHeadYaw, headLerpSpeed);

        if (hasMovementInput) {
            float constrainedBodyYaw = constrainBodyToHead(targetBodyYaw, currentHeadYaw);
            currentBodyYaw = lerpAngle(currentBodyYaw, constrainedBodyYaw, bodyLerpSpeed);
        } else {
            currentBodyYaw = lerpAngle(currentBodyYaw, currentHeadYaw, bodyLerpSpeed);
        }
    }

    public void syncAngles() {
        currentBodyYaw = currentHeadYaw;
        targetBodyYaw = currentHeadYaw;
    }

    public float getLerpHeadYaw(float partialTick) {
        float diff = normalizeAngle(currentHeadYaw - prevHeadYaw);
        return prevHeadYaw + partialTick * diff;
    }

    public float getLerpBodyYaw(float partialTick) {
        float diff = normalizeAngle(currentBodyYaw - prevBodyYaw);
        return prevBodyYaw + partialTick * diff;
    }

    public float getCurrentHeadYaw() {
        return currentHeadYaw;
    }

    public float getCurrentBodyYaw() {
        return currentBodyYaw;
    }

    public float getPrevHeadYaw() {
        return prevHeadYaw;
    }

    public float getPrevBodyYaw() {
        return prevBodyYaw;
    }

    public boolean isUsingItem() {
        return isUsingItem;
    }

    public void setUsingItem(boolean value) {
        isUsingItem = value;
        if (value) {
            syncAngles();
        }
    }

    public void setBodyLerpSpeed(float speed) {
        bodyLerpSpeed = Mth.clamp(speed, 0.01f, 1.0f);
    }

    public void setHeadLerpSpeed(float speed) {
        headLerpSpeed = Mth.clamp(speed, 0.01f, 1.0f);
    }

    public void reset() {
        targetHeadYaw = 0.0f;
        currentHeadYaw = 0.0f;
        prevHeadYaw = 0.0f;
        targetBodyYaw = 0.0f;
        currentBodyYaw = 0.0f;
        prevBodyYaw = 0.0f;
        isUsingItem = false;
        hasMovementInput = false;
    }

    public void initializeFromPlayer(float headYaw, float bodyYaw) {
        targetHeadYaw = normalizeAngle(headYaw);
        currentHeadYaw = targetHeadYaw;
        prevHeadYaw = targetHeadYaw;
        targetBodyYaw = normalizeAngle(bodyYaw);
        currentBodyYaw = targetBodyYaw;
        prevBodyYaw = targetBodyYaw;
    }

    private float constrainBodyToHead(float bodyYaw, float headYaw) {
        float diff = normalizeAngle(bodyYaw - headYaw);
        if (diff > MAX_HEAD_BODY_DIFF) {
            return normalizeAngle(headYaw + MAX_HEAD_BODY_DIFF);
        } else if (diff < -MAX_HEAD_BODY_DIFF) {
            return normalizeAngle(headYaw - MAX_HEAD_BODY_DIFF);
        }
        return bodyYaw;
    }

    private static float lerpAngle(float from, float to, float speed) {
        float diff = normalizeAngle(to - from);
        return normalizeAngle(from + diff * speed);
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 0.0f;
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}