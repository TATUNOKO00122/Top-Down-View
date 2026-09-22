package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
import com.topdownview.culling.Cullable;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;

/**
 * LevelRenderer Mixin
 * 
 * 1. エンティティカリング（トップダウン視点）
 * 2. Entity Culling MOD対応（プレイヤー保護）
 * 3. ターゲットアウトライン色変更
 */
@Mixin(value = LevelRenderer.class, priority = 1000)
public class LevelRendererMixin {

    @Shadow
    private RenderBuffers renderBuffers;

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void onRenderEntityHead(Entity entity, double camX, double camY, double camZ,
            float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) return;

        Minecraft mc = Minecraft.getInstance();

        if (entity instanceof Player && entity == mc.player) {
            return;
        }

        if (entity instanceof Cullable && ((Cullable) entity).topdownview_isCulled()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OutlineBufferSource;setColor(IIII)V",
            shift = At.Shift.AFTER
        )
    )
    private void onRenderLevelOutline(CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity target = ModState.TARGET_HIGHLIGHT.getCurrentTarget();
        if (target == null) return;

        int[] color = ModState.TARGET_HIGHLIGHT.getOutlineColor();
        this.renderBuffers.outlineBufferSource().setColor(color[0], color[1], color[2], color[3]);
    }

    @Redirect(
        method = "tickRain",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"),
        require = 0
    )
    private Vec3 redirectCameraPositionForRain(Camera camera) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return camera.getPosition();
    }

    /**
     * トップダウンビューではカメラが最大50ブロック離れるため、バニラの
     * addParticleInternal の距離判定（カメラから32ブロック超で生成を破棄、
     * 1024.0 = 32^2）がプレイヤー周辺のパーティクル生成まで弾いてしまう。
     * 判定基準をプレイヤー目線位置へ置き換え、視点を離してもパーティクルが出るようにする。
     */
    @Redirect(
        method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(DDD)D"),
        require = 0
    )
    private double redirectParticleDistance(Vec3 cameraPos, double x, double y, double z) {
        if (ModState.STATUS.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                return mc.player.getEyePosition().distanceToSqr(x, y, z);
            }
        }
        return cameraPos.distanceToSqr(x, y, z);
    }
}
