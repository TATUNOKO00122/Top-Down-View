package com.example.examplemod.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onGrabMouse(CallbackInfo ci) {
        if (com.example.examplemod.client.ClientForgeEvents.isTopDownView()) {
            // トップダウンビュー時は常にマウスを解放した状態に保つため、キャプチャをキャンセルする
            ci.cancel();
        }
    }
}
