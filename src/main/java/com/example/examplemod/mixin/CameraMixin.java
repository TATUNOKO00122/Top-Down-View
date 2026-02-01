package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
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
 * Vivecraft等の他のMODによるカメラ変更を上書きするため、優先度を高めに設定
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraMixin {

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract void setRotation(float yRot, float xRot);

    /**
     * カメラ位置と回転を強制的に設定する
     * TAILで実行することで、他MODの変更を上書きする
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached,
            boolean thirdPersonReverse, float partialTick,
            CallbackInfo ci) {
        if (!ClientForgeEvents.isTopDownView) {
            return;
        }

        // 固定されたピッチとヨー
        float fixedPitch = 45.0f;
        float fixedYaw = 0.0f; // 南向き

        // ターゲット（プレイヤー）の補間位置を取得
        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        // カメラ距離
        double distance = ClientForgeEvents.cameraDistance;

        // カメラ位置オフセット計算
        double radPitch = Math.toRadians(fixedPitch);
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        // Yaw=0（南向き）の場合、カメラはターゲットの北（-Z）にある
        double cameraX = targetX;
        double cameraY = targetY + offsetY;
        double cameraZ = targetZ - offsetH;

        // カメラ位置と角度を設定
        this.setPosition(new Vec3(cameraX, cameraY, cameraZ));
        this.setRotation(fixedYaw, fixedPitch);
    }
}
