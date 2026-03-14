package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class GameOptionsMixin {

    private static final OptionInstance<Boolean> FORCE_AUTO_JUMP = OptionInstance.createBoolean("options.autoJump", true);

    @Inject(method = "autoJump", at = @At("HEAD"), cancellable = true)
    public void topdownview$getAutoJump(CallbackInfoReturnable<OptionInstance<Boolean>> cir) {
        if (ModState.STATUS.isEnabled() && Config.isClickToMoveEnabled() && Config.isForceAutoJump()) {
            cir.setReturnValue(FORCE_AUTO_JUMP);
        }
    }
}