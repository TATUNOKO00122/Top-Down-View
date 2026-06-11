package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.client.MouseRaycast;
import com.topdownview.client.MountSteeringController;
import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean isPassenger = player.isPassenger();
        boolean isFallFlying = player.isFallFlying();

        if (!isPassenger && !isFallFlying) return;

        if (isFallFlying) {
            handleElytraFlight(player, mc);
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Boat) {
            setBoatPlayerRotationToMouse(player, mc);
            return;
        }

        MountSteeringController.tick(player);
    }

    private void setBoatPlayerRotationToMouse(LocalPlayer player, Minecraft mc) {
        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        HitResult hitResult = MouseRaycast.INSTANCE.getLastHitResult();

        float aimYaw;
        float aimPitch;

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

            aimYaw = (float) (Math.atan2(dz, dx) * MathConstants.RADIANS_TO_DEGREES) - 90.0f;
            aimPitch = Mth.clamp((float) -(Math.atan2(dy, horizontalDist) * MathConstants.RADIANS_TO_DEGREES), -90.0f, 90.0f);
        } else {
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
            if (yawPitch == null) return;
            aimYaw = Mth.wrapDegrees(yawPitch[0]);
            aimPitch = Mth.clamp(yawPitch[1], -90.0f, 90.0f);
        }

        float boatYaw = player.getVehicle().getYRot();
        int maxTwist = Config.getMountAimMaxTwist();
        float twist = Mth.wrapDegrees(aimYaw - boatYaw);
        twist = Mth.clamp(twist, -maxTwist, maxTwist);
        float constrainedYaw = Mth.wrapDegrees(boatYaw + twist);

        player.setYRot(constrainedYaw);
        player.yHeadRot = constrainedYaw;
        player.yBodyRot = constrainedYaw;
        player.yRotO = constrainedYaw;
        player.yHeadRotO = constrainedYaw;
        player.setXRot(aimPitch);
    }

    private void handleElytraFlight(LocalPlayer player, Minecraft mc) {
        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
        if (yawPitch == null) return;

        float mouseTargetYaw = normalizeAngle(yawPitch[0]);
        float targetPitch = Math.max(-90.0f, Math.min(90.0f, yawPitch[1]));

        player.setXRot(targetPitch);
        player.setYRot(mouseTargetYaw);
        player.yHeadRot = mouseTargetYaw;
        player.yBodyRot = mouseTargetYaw;
        player.yRotO = mouseTargetYaw;
        player.yHeadRotO = mouseTargetYaw;
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 0.0f;
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}