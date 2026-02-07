package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
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
            long handle = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
            boolean isAltDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(handle,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) ||
                    com.mojang.blaze3d.platform.InputConstants.isKeyDown(handle,
                            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);

            // ALTキーが押されていない時だけキャンセル（マウスを解放状態に保つ）
            if (!isAltDown) {
                ci.cancel();
            }
        }
    }
}
