package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "getPickRange", at = @At("HEAD"), cancellable = true)
    private void onGetPickRange(CallbackInfoReturnable<Float> cir) {
        if (!ModState.STATUS.isEnabled() || !Config.isScreenReachEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.getSingleplayerServer() == null && !Config.hasSyncedServerReach()) return;
        cir.setReturnValue((float) Config.getEffectiveReachDistance());
    }
}