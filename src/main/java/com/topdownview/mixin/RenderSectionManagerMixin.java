package com.topdownview.mixin;

import com.topdownview.state.ModState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Embeddium(Sodium)用 RenderSectionManager Mixin
 * トップダウンビューでのチャンク欠け・高さ欠け（Occlusion Culling）を回避しつつ、パフォーマンスを最大化する
 */
@SuppressWarnings("all")
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.class, remap = false)
public class RenderSectionManagerMixin {

    /**
     * カメラが固体ブロック（岩盤、石、土など）の中に埋まった際に、
     * そこから周囲が見えないと判定され、周囲や別のセクションのチャンク描画がごっそり欠けるバグを修正する。
     * カメラが固体ブロックに埋まっている場合のみオクルージョンカリングを無効化し、
     * 空中や開けた場所にカメラがある場合はオクルージョンカリングを有効に保つことでFPSを向上させる。
     */
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void onShouldUseOcclusionCulling(Camera camera, boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockPos camPos = camera.getBlockPosition();
                BlockState state = mc.level.getBlockState(camPos);
                
                // カメラ位置が固体かつ光を遮蔽するブロックに埋まっているかどうか判定
                boolean isCameraOccluded = !state.isAir() && state.isSolidRender(mc.level, camPos);
                if (isCameraOccluded) {
                    cir.setReturnValue(false);
                }
            } else {
                cir.setReturnValue(false);
            }
        }
    }
}
