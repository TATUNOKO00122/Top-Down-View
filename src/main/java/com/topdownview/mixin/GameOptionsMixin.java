package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.culling.CullingManager;
import com.topdownview.state.ModState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class GameOptionsMixin {

    private static final OptionInstance<Boolean> FORCE_AUTO_JUMP = OptionInstance.createBoolean("options.autoJump", true);

    @Inject(method = "autoJump", at = @At("HEAD"), cancellable = true)
    public void topdownview$getAutoJump(CallbackInfoReturnable<OptionInstance<Boolean>> cir) {
        if (ModState.STATUS.isEnabled() && Config.isForceAutoJump() && ModState.CLICK_TO_MOVE.isMoving()) {
            cir.setReturnValue(FORCE_AUTO_JUMP);
        }
    }

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void topdownview$onSetCameraType(CameraType type, CallbackInfo ci) {
        if (ModState.STATUS.isInternalCameraChange()) {
            return;
        }

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (Config.isLockedTopDown()) {
            ci.cancel();
            return;
        }

        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        ModState.STATUS.setEnabled(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.mouseHandler.releaseMouse();
        mc.mouseHandler.grabMouse();
        ModState.resetAll();
        CullingManager.forceChunkRebuild(mc);
    }
}