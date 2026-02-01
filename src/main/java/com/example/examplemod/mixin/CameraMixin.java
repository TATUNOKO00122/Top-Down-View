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

        // 固定されたピッチと可変ヨー
        float fixedPitch = 45.0f;
        float cameraYaw = ClientForgeEvents.cameraYaw; // 可変のヨー角度

        // ターゲット（プレイヤー）の補間位置を取得
        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        // カメラ距離
        double distance = ClientForgeEvents.cameraDistance;

        // カメラ位置オフセット計算（ヨー角度を考慮）
        double radPitch = Math.toRadians(fixedPitch);
        double radYaw = Math.toRadians(cameraYaw);
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        // ヨー角度に基づいてカメラ位置を計算
        double cameraX = targetX + Math.sin(radYaw) * offsetH;
        double cameraY = targetY + offsetY;
        double cameraZ = targetZ - Math.cos(radYaw) * offsetH;

        // カメラ位置と角度を設定
        this.setPosition(new Vec3(cameraX, cameraY, cameraZ));
        this.setRotation(cameraYaw, fixedPitch);
    }
}
