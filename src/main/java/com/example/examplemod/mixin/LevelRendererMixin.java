package com.example.examplemod.mixin;

import com.example.examplemod.client.BlockCuller;
import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRendererにMixinして、レンダリング前にブロックカリング情報を更新
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * renderLevelメソッドの最初にフックして、カリング情報を更新
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void beforeRenderLevel(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, com.mojang.blaze3d.vertex.PoseStack.Pose pose, CallbackInfo ci) {
        // トップダウンビューかつブロックカリングが有効な場合
        if (ClientForgeEvents.isTopDownView && ClientForgeEvents.isBlockCullingEnabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                // カメラ位置とプレイヤーの目位置を取得
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 playerEyePos = mc.player.getEyePosition(partialTick);

                // カリング情報を更新
                BlockCuller.updateCulling(cameraPos, playerEyePos);
            }
        }
    }

    /**
     * renderLevelメソッドの最後にフックして、カリング情報をクリア
     */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void afterRenderLevel(CallbackInfo ci) {
        // レンダリング終了後にカリング情報をクリア
        BlockCuller.clearCulling();
    }
}
