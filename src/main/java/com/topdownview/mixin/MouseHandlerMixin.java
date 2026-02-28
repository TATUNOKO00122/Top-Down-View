package com.topdownview.mixin;

import com.topdownview.client.ClickActionHandler;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onGrabMouse(CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void onMousePress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ModState.STATUS.isEnabled()) {
            ClickActionHandler.onInput(button, action, mc);
        }
    }
}
