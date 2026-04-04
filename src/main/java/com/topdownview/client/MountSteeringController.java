package com.topdownview.client;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MountSteeringController {

    private static final float STEER_LERP = 0.3f;
    private static final float DRIFT_LERP = 0.05f;
    private static final float INPUT_THRESHOLD = 0.01f;
    private static final double VELOCITY_THRESHOLD = 0.01;

    private MountSteeringController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void tick(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
        if (yawPitch == null) return;

        float mouseTargetYaw = normalizeAngle(yawPitch[0]);

        player.setYRot(mouseTargetYaw);
        player.yHeadRot = mouseTargetYaw;
        player.yBodyRot = mouseTargetYaw;
        player.yRotO = mouseTargetYaw;
        player.yHeadRotO = mouseTargetYaw;

        applyPitchToTarget(mc, player);

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        if (vehicle instanceof LivingEntity || vehicle instanceof Boat) {
            steerWithSnapAndDrift(player, vehicle);
        }
    }

    private static void applyPitchToTarget(Minecraft mc, LocalPlayer player) {
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 playerEyePos = player.getEyePosition(mc.getFrameTime());
            Vec3 targetPos = hitResult.getLocation();
            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            float pitch = (float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI));
            player.setXRot(Math.max(-90.0f, Math.min(90.0f, pitch)));
        }
    }

    private static void steerWithSnapAndDrift(LocalPlayer player, Entity vehicle) {
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        float inputMagnitude = (float) Math.sqrt(forward * forward + strafe * strafe);

        if (inputMagnitude > INPUT_THRESHOLD) {
            float cameraYaw = ModState.CAMERA.getYaw();
            float moveAngleDeg = (float) Math.toDegrees(Math.atan2(-strafe, forward));
            float targetYaw = normalizeAngle(cameraYaw + moveAngleDeg);

            float currentYaw = normalizeAngle(vehicle.getYRot());
            float diff = normalizeAngle(targetYaw - currentYaw);
            float newYaw = normalizeAngle(currentYaw + diff * STEER_LERP);

            applyRotation(vehicle, newYaw);

            player.input.forwardImpulse = inputMagnitude;
            player.input.leftImpulse = 0.0f;
        } else {
            applyInertialDrift(vehicle);
        }
    }

    private static void applyInertialDrift(Entity vehicle) {
        Vec3 velocity = vehicle.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalSpeed <= VELOCITY_THRESHOLD) return;

        float velocityYaw = normalizeAngle(
                (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0f
        );
        float currentYaw = normalizeAngle(vehicle.getYRot());
        float diff = normalizeAngle(velocityYaw - currentYaw);
        float newYaw = normalizeAngle(currentYaw + diff * DRIFT_LERP);

        applyRotation(vehicle, newYaw);
    }

    private static void applyRotation(Entity vehicle, float yaw) {
        vehicle.setYRot(yaw);
        vehicle.yRotO = yaw;
        vehicle.setYBodyRot(yaw);
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 0.0f;
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}
