package com.topdownview.client;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MountSteeringController {

    private static final float MAX_TURN_RATE = 135.0f;

    private static float smoothMountTargetYaw = Float.NaN;

    private MountSteeringController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void reset() {
        smoothMountTargetYaw = Float.NaN;
    }

    public static float getSmoothMountTargetYaw() {
        return smoothMountTargetYaw;
    }

    public static void tick(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity)) {
            smoothMountTargetYaw = Float.NaN;
            return;
        }

        float mountYaw = vehicle.getYRot();

        if (Float.isNaN(smoothMountTargetYaw)) {
            smoothMountTargetYaw = mountYaw;
        }

        float forward = player.zza;
        float strafe = player.xxa;
        float inputMagnitude = (float) Math.sqrt(forward * forward + strafe * strafe);

        if (inputMagnitude > 0.01f) {
            float cameraYaw = ModState.CAMERA.getYaw();
            float moveAngleDeg = (float) Math.toDegrees(Math.atan2(-strafe, forward));
            float targetYaw = Mth.wrapDegrees(cameraYaw + moveAngleDeg);
            float turnSpeed = (float) Config.getMountTurnSmoothing();
            smoothMountTargetYaw = lerpAngleDegrees(smoothMountTargetYaw, targetYaw, turnSpeed);
        } else {
            smoothMountTargetYaw = mountYaw;
        }

        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());

        float[] aim = computeAimFromPlayerEye(mc, player);

        if (Config.isIndependentMountAim()) {
            tickIndependentAim(player, aim[0], aim[1], mountYaw);
        } else {
            tickLinkedAim(player, aim[0], aim[1], mc, mountYaw);
        }
    }

    private static float lerpAngleDegrees(float from, float to, float t) {
        float diff = Mth.wrapDegrees(to - from);
        return Mth.wrapDegrees(from + diff * t);
    }

    private static float[] computeAimFromPlayerEye(Minecraft mc, LocalPlayer player) {
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 targetPos = hitResult.getLocation();

            if (hitResult instanceof EntityHitResult entityHit) {
                Entity hitEntity = entityHit.getEntity();
                if (ModState.TARGET_LOCK.isLockedTo(hitEntity)) {
                    targetPos = hitEntity.getPosition(1.0f).add(0, hitEntity.getEyeHeight() * 0.8, 0);
                }
            }

            Vec3 playerEyePos = player.getEyePosition(mc.getFrameTime());

            double dx = targetPos.x - playerEyePos.x;
            double dy = targetPos.y - playerEyePos.y;
            double dz = targetPos.z - playerEyePos.z;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            float aimYaw = (float) (Math.atan2(dz, dx) * MathConstants.RADIANS_TO_DEGREES) - 90.0f;

            Float trajectoryPitch = calculatePitch(mc, player, horizontalDist, dy);
            float aimPitch;
            if (trajectoryPitch != null) {
                aimPitch = trajectoryPitch;
            } else {
                aimPitch = (float) -(Math.atan2(dy, horizontalDist) * MathConstants.RADIANS_TO_DEGREES);
                aimPitch = Mth.clamp(aimPitch, -90.0f, 90.0f);
            }

            return new float[]{aimYaw, aimPitch};
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
            if (yawPitch == null) {
                return new float[]{player.getYRot(), player.getXRot()};
            }
            float aimYaw = Mth.wrapDegrees(yawPitch[0]);
            float aimPitch = Mth.clamp(yawPitch[1], -90.0f, 90.0f);

            Float trajectoryPitch = calculatePitchFromRaycast(mc, player);
            if (trajectoryPitch != null) {
                aimPitch = trajectoryPitch;
            }

            return new float[]{aimYaw, aimPitch};
        }
    }

    private static void tickIndependentAim(LocalPlayer player, float aimYaw, float aimPitch, float mountYaw) {
        int maxTwist = Config.getMountAimMaxTwist();
        float twist = Mth.wrapDegrees(aimYaw - mountYaw);

        if (maxTwist < 360) {
            twist = Mth.clamp(twist, -maxTwist, maxTwist);
        }

        float constrainedAimYaw = Mth.wrapDegrees(mountYaw + twist);

        player.setYRot(constrainedAimYaw);
        player.setXRot(aimPitch);
        player.yHeadRot = constrainedAimYaw;
        player.yHeadRotO = constrainedAimYaw;
        player.yBodyRot = mountYaw;
        player.yRotO = constrainedAimYaw;
    }

    private static void tickLinkedAim(LocalPlayer player, float aimYaw, float aimPitch, Minecraft mc, float mountYaw) {
        float forward = player.zza;
        float strafe = player.xxa;
        float inputMagnitude = (float) Math.sqrt(forward * forward + strafe * strafe);

        float targetYaw;
        if (inputMagnitude > 0.01f) {
            targetYaw = smoothMountTargetYaw;
        } else {
            targetYaw = aimYaw;
        }

        float clampedYaw = mountYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - mountYaw), -MAX_TURN_RATE, MAX_TURN_RATE);

        player.setYRot(clampedYaw);
        player.yHeadRot = clampedYaw;
        player.yBodyRot = clampedYaw;
        player.yRotO = clampedYaw;
        player.yHeadRotO = clampedYaw;
        player.setXRot(aimPitch);
    }

    private static final double MIN_PULL_FACTOR = 0.65;

    private static Float calculatePitch(Minecraft mc, LocalPlayer player, double horizontalDist, double verticalDist) {
        ItemStack useItem = player.getUseItem();
        Item item = useItem.getItem();
        ProjectilePhysics physics = ProjectilePhysics.fromItem(item);

        if (physics != null && player.isUsingItem()) {
            int useTicks = player.getTicksUsingItem();
            double pullFactor = TrajectoryCalculator.calculatePullFactor(item, useTicks);
            if (pullFactor >= MIN_PULL_FACTOR) {
                return TrajectoryCalculator.calculatePitch(physics, horizontalDist, verticalDist, pullFactor);
            }
        }
        return null;
    }

    private static Float calculatePitchFromRaycast(Minecraft mc, LocalPlayer player) {
        ItemStack useItem = player.getUseItem();
        Item item = useItem.getItem();
        ProjectilePhysics physics = ProjectilePhysics.fromItem(item);

        if (physics != null && player.isUsingItem()) {
            int useTicks = player.getTicksUsingItem();
            double pullFactor = TrajectoryCalculator.calculatePullFactor(item, useTicks);
            if (pullFactor >= MIN_PULL_FACTOR) {
                var hitResult = MouseRaycast.INSTANCE.getLastHitResult();
                if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                    var targetPos = hitResult.getLocation();
                    var playerEyePos = player.getEyePosition(mc.getFrameTime());
                    double dx = targetPos.x - playerEyePos.x;
                    double dy = targetPos.y - playerEyePos.y;
                    double dz = targetPos.z - playerEyePos.z;
                    double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                    return TrajectoryCalculator.calculatePitch(physics, horizontalDist, dy, pullFactor);
                }
            }
        }
        return null;
    }
}