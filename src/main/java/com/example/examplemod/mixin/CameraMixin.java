package com.example.examplemod.mixin;

import com.example.examplemod.state.ModState;
import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

    // 定数
    private static final float FIXED_PITCH = 45.0f;
    private static final float FIXED_YAW = 0.0f;
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

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
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        // ターゲット（プレイヤー）の補間位置を取得
        double targetX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double targetY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double targetZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());

        // カメラ距離
        double distance = ClientForgeEvents.getCameraDistance();

        // カメラ位置オフセット計算
        double radPitch = FIXED_PITCH * DEGREES_TO_RADIANS;
        double radYaw = FIXED_YAW * DEGREES_TO_RADIANS;
        double offsetY = Math.sin(radPitch) * distance;
        double offsetH = Math.cos(radPitch) * distance;

        // ヨー角度に基づいてカメラ位置を計算
        double cameraX = targetX + Math.sin(radYaw) * offsetH;
        double cameraY = targetY + offsetY;
        double cameraZ = targetZ - Math.cos(radYaw) * offsetH;

        // カメラ位置と角度を設定
        this.setPosition(new Vec3(cameraX, cameraY, cameraZ));
        this.setRotation(FIXED_YAW, FIXED_PITCH);

        // カメラ位置を状態に保存
        ModState.CAMERA.setCameraPosition(this.getPosition());

        // 時間管理
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && ModState.TIME.getStartTime() == 0) {
            ModState.TIME.setStartTime(mc.level.getGameTime());
        }
        ModState.TIME.setEndTime(0);
    }
}