package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ互換: トップダウンビュー有効時にスコープ覗きのワールドFOVズームを無効化する
 * TACZ未導入環境ではこのMixinは適用されない（@Pseudo + required:false）
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public class TacZScopeFovCompatMixin {

    /**
     * スコープ覗き時のワールドFOVズームをキャンセルする
     * TopDownView有効時のみ干渉し、無効時はTACZの通常動作を維持する
     */
    @Inject(method = "applyScopeMagnification", at = @At("HEAD"), cancellable = true)
    private static void topdownview$cancelScopeMagnification(ViewportEvent.ComputeFov event, CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            ci.cancel();
        }
    }
}
