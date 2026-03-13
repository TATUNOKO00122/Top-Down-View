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

/**
 * CameraMixin
 * トップダウンビューのカメラ位置と回転を制御
 */
@Mixin(value = Camera.class, priority = 1000)
public abstract class CameraMixin {

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    /**
     * カメラがブロックに埋もれた際に起こるチャンクのOcclusion Cullingバグを防止するため、
     * カメラのブロック位置としてプレイヤーの位置を返すよう偽装する。
     */
    @Inject(method = "getBlockPosition", at = @At("HEAD"), cancellable = true)
    private void onGetBlockPosition(CallbackInfoReturnable<BlockPos> cir) {
        if (ModState.STATUS.isEnabled()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                cir.setReturnValue(mc.player.blockPosition());
            }
        }
    }

    /**
     * TAILでカメラを上書き
     */
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

        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        // Y軸遅延追従処理
        double cameraY = calculateCameraY(targetY, partialTick);

        // X軸・Z軸遅延追従処理
        double cameraBaseX = calculateCameraX(targetX, partialTick);
        double cameraBaseZ = calculateCameraZ(targetZ, partialTick);

        double distance = ModState.CAMERA.getCameraDistance();
        float pitch = ModState.STATUS.isMiningMode() ? (float) com.topdownview.Config.getMiningModePitch() : (float) com.topdownview.Config.getCameraPitch();
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

    /**
     * Y軸の遅延追従を計算
     * 遅延時間に基づいて指数減衰で滑らかに追従
     */
    private double calculateCameraY(double targetY, float partialTick) {
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

        // 指数減衰による遅延追従
        // dtは約0.05秒（20TPS）、フレームレートに依存しないよう固定値を使用
        double dt = 0.05;
        double lerpFactor = 1.0 - Math.exp(-dt / delaySeconds);

        double newY = currentY + (targetY - currentY) * lerpFactor;
        ModState.CAMERA.setCurrentCameraY(newY);

        return newY;
    }

    /**
     * X軸の遅延追従を計算
     * 遅延時間に基づいて指数減衰で滑らかに追従
     */
    private double calculateCameraX(double targetX, float partialTick) {
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

        double dt = 0.05;
        double lerpFactor = 1.0 - Math.exp(-dt / delaySeconds);

        double newX = currentX + (targetX - currentX) * lerpFactor;
        ModState.CAMERA.setCurrentCameraX(newX);

        return newX;
    }

    /**
     * Z軸の遅延追従を計算
     * 遅延時間に基づいて指数減衰で滑らかに追従
     */
    private double calculateCameraZ(double targetZ, float partialTick) {
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

        double dt = 0.05;
        double lerpFactor = 1.0 - Math.exp(-dt / delaySeconds);

        double newZ = currentZ + (targetZ - currentZ) * lerpFactor;
        ModState.CAMERA.setCurrentCameraZ(newZ);

        return newZ;
    }
}
