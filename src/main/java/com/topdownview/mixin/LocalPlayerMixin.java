package com.topdownview.mixin;

import com.topdownview.client.MouseRaycast;
import com.topdownview.client.MountSteeringController;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

        MountSteeringController.tick(player);
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
