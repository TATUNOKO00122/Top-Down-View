package com.topdownview.client;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class MountSteeringController {

    private static final float MAX_TURN_RATE = 135.0f;

    private MountSteeringController() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    public static void tick(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity)) return;

        float vehicleYaw = vehicle.getYRot();
        float forward = player.zza;
        float strafe = player.xxa;
        float inputMagnitude = (float) Math.sqrt(forward * forward + strafe * strafe);

        if (inputMagnitude > 0.01f) {
            float cameraYaw = ModState.CAMERA.getYaw();
            float moveAngleDeg = (float) Math.toDegrees(Math.atan2(-strafe, forward));
            float targetYaw = Mth.wrapDegrees(cameraYaw + moveAngleDeg);

            float clampedYaw = vehicleYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - vehicleYaw), -MAX_TURN_RATE, MAX_TURN_RATE);

            player.setYRot(clampedYaw);
            player.yHeadRot = clampedYaw;
            player.yBodyRot = clampedYaw;
            player.yRotO = clampedYaw;
            player.yHeadRotO = clampedYaw;
        } else {
            MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
            float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
            if (yawPitch != null) {
                float mouseTargetYaw = Mth.wrapDegrees(yawPitch[0]);
                float clampedYaw = vehicleYaw + Mth.clamp(Mth.wrapDegrees(mouseTargetYaw - vehicleYaw), -MAX_TURN_RATE, MAX_TURN_RATE);
                player.setYRot(clampedYaw);
                player.yHeadRot = clampedYaw;
                player.yBodyRot = clampedYaw;
                player.yRotO = clampedYaw;
                player.yHeadRotO = clampedYaw;
            }
        }

        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());
        float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
        if (yawPitch != null) {
            player.setXRot(Mth.clamp(yawPitch[1], -90.0f, 90.0f));
        }
    }
}