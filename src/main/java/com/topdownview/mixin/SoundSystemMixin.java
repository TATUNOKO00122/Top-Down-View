package com.topdownview.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.topdownview.client.ClientForgeEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {

    @Redirect(method = "updateSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectCameraPosition(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return camera.getPosition();
    }

    @Redirect(method = "updateSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getLookVector()Lorg/joml/Vector3f;"))
    private Vector3f redirectCameraLookVector(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 viewVector = Minecraft.getInstance().player.getViewVector(1.0F);
            return new Vector3f((float) viewVector.x, (float) viewVector.y, (float) viewVector.z);
        }
        return camera.getLookVector();
    }

    @Redirect(method = "updateSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getUpVector()Lorg/joml/Vector3f;"))
    private Vector3f redirectCameraUpVector(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 upVector = Minecraft.getInstance().player.getUpVector(1.0F);
            return new Vector3f((float) upVector.x, (float) upVector.y, (float) upVector.z);
        }
        return camera.getUpVector();
    }

    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/audio/Listener;getListenerPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectListenerPosition(Listener listener) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return listener.getListenerPosition();
    }
}
