package com.topdownview.mixin;

import com.topdownview.state.ModState;
import io.socol.betterthirdperson.api.adapter.IPlayerAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = io.socol.betterthirdperson.api.CustomCameraManager.class,
    remap = false
)
public class CustomCameraManagerCompatMixin {

    @Inject(method = "hasCustomCamera", at = @At("HEAD"), cancellable = true)
    private void topdownview$onHasCustomCamera(CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mustHaveCustomCamera", at = @At("HEAD"), cancellable = true)
    private void topdownview$onMustHaveCustomCamera(IPlayerAdapter player, CallbackInfoReturnable<Boolean> cir) {
        if (ModState.STATUS.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
