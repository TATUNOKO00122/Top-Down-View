package com.topdownview.mixin;

import com.topdownview.client.MouseRaycast;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    private static final float VEHICLE_ROTATION_LERP = 0.3f;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean isPassenger = player.isPassenger();
        boolean isFallFlying = player.isFallFlying();

        if (!isPassenger && !isFallFlying) return;

        MouseRaycast.INSTANCE.update(mc, mc.getFrameTime(), MouseRaycast.getCustomReachDistance());

        float[] yawPitch = MouseRaycast.INSTANCE.getMouseTargetYawPitch(mc, mc.getFrameTime());
        if (yawPitch == null) return;

        float mouseTargetYaw = normalizeAngle(yawPitch[0]);

        if (isFallFlying) {
            float targetPitch = Math.max(-90.0f, Math.min(90.0f, yawPitch[1]));
            player.setXRot(targetPitch);
            player.setYRot(mouseTargetYaw);
            player.yHeadRot = mouseTargetYaw;
            player.yBodyRot = mouseTargetYaw;
            player.yRotO = mouseTargetYaw;
            player.yHeadRotO = mouseTargetYaw;
            return;
        }

        // === 乗り物搭乗時 ===
        // プレイヤーはマウス方向を向く（攻撃・インタラクト用）
        player.setYRot(mouseTargetYaw);
        player.yHeadRot = mouseTargetYaw;
        player.yBodyRot = mouseTargetYaw;
        player.yRotO = mouseTargetYaw;
        player.yHeadRotO = mouseTargetYaw;

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        // LivingEntity以外（ボート・トロッコ等）はバニラ操縦を維持
        if (!(vehicle instanceof LivingEntity)) return;

        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        float inputMagnitude = (float) Math.sqrt(forward * forward + strafe * strafe);

        if (inputMagnitude > 0.01f) {
            // カメラ相対入力から目標ヨーを計算
            float cameraYaw = ModState.CAMERA.getYaw();
            float moveAngleDeg = (float) Math.toDegrees(Math.atan2(-strafe, forward));
            float targetVehicleYaw = normalizeAngle(cameraYaw + moveAngleDeg);

            // 滑らかな回転
            float currentYaw = normalizeAngle(vehicle.getYRot());
            float diff = normalizeAngle(targetVehicleYaw - currentYaw);
            float newYaw = normalizeAngle(currentYaw + diff * VEHICLE_ROTATION_LERP);

            vehicle.setYRot(newYaw);
            vehicle.yRotO = newYaw;
            vehicle.setYBodyRot(newYaw);

            // 入力を前方成分のみに設定（乗り物は常に前進）
            player.input.forwardImpulse = inputMagnitude;
            player.input.leftImpulse = 0.0f;
        }
    }

    private static float normalizeAngle(float angle) {
        if (!Float.isFinite(angle)) return 0.0f;
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}
