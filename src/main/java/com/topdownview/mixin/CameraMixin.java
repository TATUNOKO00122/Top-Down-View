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

    private static final float FIXED_PITCH = 45.0f;

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    /**
     * カメラ位置と回転を設定する
     * TAILで実行することで、他MODの変更を上書きする
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached,
            boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        double distance = ModState.CAMERA.getCameraDistance();

        float yaw = ModState.CAMERA.getLerpYaw(partialTick);
        double radPitch = FIXED_PITCH * MathConstants.DEGREES_TO_RADIANS;
        double radYaw = yaw * MathConstants.DEGREES_TO_RADIANS;
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        double cameraX = targetX + Math.sin(radYaw) * offsetH;
        double cameraY = targetY + offsetY;
        double cameraZ = targetZ - Math.cos(radYaw) * offsetH;

        this.setPosition(new Vec3(cameraX, cameraY, cameraZ));
        this.setRotation(yaw, FIXED_PITCH);

        ModState.CAMERA.setCameraPosition(this.getPosition());
    }
}