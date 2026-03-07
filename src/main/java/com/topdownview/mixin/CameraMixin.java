package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.util.MathConstants;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
     * TAILでカメラを上書き
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", 
            at = @At("TAIL"))
    private void onSetupTail(BlockGetter level, Entity entity, boolean detached,
            boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {
        
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        // entityがnullの場合は早期リターン
        if (entity == null) {
            return;
        }

        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        double distance = ModState.CAMERA.getCameraDistance();
        float pitch = (float) com.topdownview.Config.cameraPitch;
        float yaw = ModState.CAMERA.getLerpYaw(partialTick);
        
        double radPitch = pitch * MathConstants.DEGREES_TO_RADIANS;
        double radYaw = yaw * MathConstants.DEGREES_TO_RADIANS;
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        double cameraX = targetX + Math.sin(radYaw) * offsetH;
        double cameraY = targetY + offsetY;
        double cameraZ = targetZ - Math.cos(radYaw) * offsetH;

        this.setPosition(new Vec3(cameraX, cameraY, cameraZ));
        this.setRotation(yaw, pitch);

        ModState.CAMERA.setPitch(pitch);
        ModState.CAMERA.setCameraPosition(this.getPosition());
    }
}
