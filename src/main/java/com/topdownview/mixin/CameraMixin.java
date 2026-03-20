package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Camera.class, priority = 1000)
public abstract class CameraMixin {

    private static final double DT = 0.05;
    private static final double MIN_DELAY = 0.001;

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    private double cachedDelayY = -1;
    private double cachedLerpFactorY = 0;
    private double cachedDelayX = -1;
    private double cachedLerpFactorX = 0;
    private double cachedDelayZ = -1;
    private double cachedLerpFactorZ = 0;

    private static double computeLerpFactor(double delaySeconds) {
        if (delaySeconds <= MIN_DELAY) return 1.0;
        return 1.0 - Math.exp(-DT / delaySeconds);
    }

    @Inject(method = "getBlockPosition", at = @At("HEAD"), cancellable = true)
    private void onGetBlockPosition(CallbackInfoReturnable<BlockPos> cir) {
        if (ModState.STATUS.isEnabled()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                cir.setReturnValue(mc.player.blockPosition());
            }
        }
    }

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void onSetupTail(BlockGetter level, Entity entity, boolean detached,
            boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (entity == null) {
            return;
        }

        updateFreeCameraMode();

        if (ModState.CAMERA.isFreeCameraMode()) {
            ModState.CAMERA.updatePrevFreeCameraPitch();
        }

        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        double cameraY = calculateCameraY(targetY);
        double cameraBaseX = calculateCameraX(targetX);
        double cameraBaseZ = calculateCameraZ(targetZ);

        double distance = ModState.CAMERA.getCameraDistance();
        float pitch;
        if (ModState.CAMERA.isFreeCameraMode()) {
            pitch = ModState.CAMERA.getLerpFreeCameraPitch(partialTick);
        } else if (ModState.CAMERA.isFreeCameraPitchAdjusted()) {
            pitch = ModState.CAMERA.getFreeCameraPitch();
        } else if (ModState.STATUS.isMiningMode()) {
            pitch = (float) com.topdownview.Config.getMiningModePitch();
        } else {
            pitch = (float) com.topdownview.Config.getCameraPitch();
        }
        float yaw = ModState.CAMERA.getLerpYaw(partialTick);

        double radPitch = pitch * MathConstants.DEGREES_TO_RADIANS;
        double radYaw = yaw * MathConstants.DEGREES_TO_RADIANS;
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        double cameraX = cameraBaseX + Math.sin(radYaw) * offsetH;
        double cameraZ = cameraBaseZ - Math.cos(radYaw) * offsetH;

        this.setPosition(new Vec3(cameraX, cameraY + offsetY, cameraZ));
        this.setRotation(yaw, pitch);

        ModState.CAMERA.setPitch(pitch);
        ModState.CAMERA.setCameraPosition(this.getPosition());
    }

    private void updateFreeCameraMode() {
        if (!ModState.CAMERA.isFreeCameraMode()) {
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        double currentMouseX = mc.mouseHandler.xpos();
        double currentMouseY = mc.mouseHandler.ypos();

        if (!ModState.CAMERA.isFreeCameraMouseInitialized()) {
            ModState.CAMERA.setLastMouseX(currentMouseX);
            ModState.CAMERA.setLastMouseY(currentMouseY);
            ModState.CAMERA.setFreeCameraMouseInitialized(true);
            ModState.CAMERA.updatePrevFreeCameraPitch();
            return;
        }

        double lastX = ModState.CAMERA.getLastMouseX();
        double lastY = ModState.CAMERA.getLastMouseY();

        double deltaX = currentMouseX - lastX;
        double deltaY = currentMouseY - lastY;

        double sensitivity = mc.options.sensitivity().get();
        double f = sensitivity * 0.6 + 0.2;
        double multiplier = f * f * f * 8.0;
        double guiScale = mc.getWindow().getGuiScale();

        float currentYaw = ModState.CAMERA.getYaw();
        float currentPitch = ModState.CAMERA.getFreeCameraPitch();

        float newYaw = currentYaw + (float) (deltaX * multiplier / guiScale);
        float newPitch = currentPitch + (float) (deltaY * multiplier / guiScale);

        newPitch = Math.max(0.0f, Math.min(90.0f, newPitch));

        ModState.CAMERA.setYaw(newYaw);
        ModState.CAMERA.setFreeCameraPitch(newPitch);
        ModState.CAMERA.setFreeCameraPitchAdjusted(true);

        ModState.CAMERA.setLastMouseX(currentMouseX);
        ModState.CAMERA.setLastMouseY(currentMouseY);
    }

    private double calculateCameraY(double targetY) {
        if (!com.topdownview.Config.isCameraYFollowDelayEnabled()) {
            ModState.CAMERA.setTargetCameraY(targetY);
            ModState.CAMERA.setCurrentCameraY(targetY);
            ModState.CAMERA.setCameraYInitialized(true);
            return targetY;
        }

        if (!ModState.CAMERA.isCameraYInitialized()) {
            ModState.CAMERA.setTargetCameraY(targetY);
            ModState.CAMERA.setCurrentCameraY(targetY);
            ModState.CAMERA.setCameraYInitialized(true);
            return targetY;
        }

        ModState.CAMERA.setTargetCameraY(targetY);

        double currentY = ModState.CAMERA.getCurrentCameraY();
        double delaySeconds = com.topdownview.Config.getCameraYFollowDelay();

        if (delaySeconds <= 0.0) {
            ModState.CAMERA.setCurrentCameraY(targetY);
            return targetY;
        }

        if (cachedDelayY != delaySeconds) {
            cachedDelayY = delaySeconds;
            cachedLerpFactorY = computeLerpFactor(delaySeconds);
        }

        double newY = currentY + (targetY - currentY) * cachedLerpFactorY;
        ModState.CAMERA.setCurrentCameraY(newY);

        return newY;
    }

    private double calculateCameraX(double targetX) {
        if (!com.topdownview.Config.isCameraXFollowDelayEnabled()) {
            ModState.CAMERA.setTargetCameraX(targetX);
            ModState.CAMERA.setCurrentCameraX(targetX);
            ModState.CAMERA.setCameraXInitialized(true);
            return targetX;
        }

        if (!ModState.CAMERA.isCameraXInitialized()) {
            ModState.CAMERA.setTargetCameraX(targetX);
            ModState.CAMERA.setCurrentCameraX(targetX);
            ModState.CAMERA.setCameraXInitialized(true);
            return targetX;
        }

        ModState.CAMERA.setTargetCameraX(targetX);

        double currentX = ModState.CAMERA.getCurrentCameraX();
        double delaySeconds = com.topdownview.Config.getCameraXFollowDelay();

        if (delaySeconds <= 0.0) {
            ModState.CAMERA.setCurrentCameraX(targetX);
            return targetX;
        }

        if (cachedDelayX != delaySeconds) {
            cachedDelayX = delaySeconds;
            cachedLerpFactorX = computeLerpFactor(delaySeconds);
        }

        double newX = currentX + (targetX - currentX) * cachedLerpFactorX;
        ModState.CAMERA.setCurrentCameraX(newX);

        return newX;
    }

    private double calculateCameraZ(double targetZ) {
        if (!com.topdownview.Config.isCameraZFollowDelayEnabled()) {
            ModState.CAMERA.setTargetCameraZ(targetZ);
            ModState.CAMERA.setCurrentCameraZ(targetZ);
            ModState.CAMERA.setCameraZInitialized(true);
            return targetZ;
        }

        if (!ModState.CAMERA.isCameraZInitialized()) {
            ModState.CAMERA.setTargetCameraZ(targetZ);
            ModState.CAMERA.setCurrentCameraZ(targetZ);
            ModState.CAMERA.setCameraZInitialized(true);
            return targetZ;
        }

        ModState.CAMERA.setTargetCameraZ(targetZ);

        double currentZ = ModState.CAMERA.getCurrentCameraZ();
        double delaySeconds = com.topdownview.Config.getCameraZFollowDelay();

        if (delaySeconds <= 0.0) {
            ModState.CAMERA.setCurrentCameraZ(targetZ);
            return targetZ;
        }

        if (cachedDelayZ != delaySeconds) {
            cachedDelayZ = delaySeconds;
            cachedLerpFactorZ = computeLerpFactor(delaySeconds);
        }

        double newZ = currentZ + (targetZ - currentZ) * cachedLerpFactorZ;
        ModState.CAMERA.setCurrentCameraZ(newZ);

        return newZ;
    }
}