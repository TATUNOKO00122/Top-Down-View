package com.topdownview.mixin;

import com.topdownview.state.ModState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium(Sodium)用 RenderSectionManager Mixin
 * トップダウンビューでのチャンク欠け・高さ欠け（Occlusion Culling）を回避する
 */
@SuppressWarnings("all")
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.class, remap = false)
public class RenderSectionManagerMixin {

    /**
     * カメラが壁や地面の中に埋まった際に、そこから見える範囲がないと判定され
     * 周囲や別のセクションのチャンク描画がごっそり欠けるバグを修正する。
     * トップダウンビューが有効な間は、EmbeddiumのOcclusion Culling自体を無効化する。
     */
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void onShouldUseOcclusionCulling(net.minecraft.client.Camera camera, boolean spectator,
            CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
