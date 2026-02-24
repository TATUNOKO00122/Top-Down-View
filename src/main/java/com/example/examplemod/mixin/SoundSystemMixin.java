package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {

    @Redirect(method = "updateSource(Lnet/minecraft/client/Camera;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectCameraPosition(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 pos = Minecraft.getInstance().player.getEyePosition();
            System.out.println("[SoundSystemMixin] Redirecting Camera position to player: " + pos);
            return pos;
        }
        return camera.getPosition();
    }

    @Redirect(method = "updateSource(Lnet/minecraft/client/Camera;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getLookVector()Lorg/joml/Vector3f;"))
    private Vector3f redirectCameraLookVector(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 viewVector = Minecraft.getInstance().player.getViewVector(1.0F);
            return new Vector3f((float) viewVector.x, (float) viewVector.y, (float) viewVector.z);
        }
        return camera.getLookVector();
    }

    @Redirect(method = "updateSource(Lnet/minecraft/client/Camera;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getUpVector()Lorg/joml/Vector3f;"))
    private Vector3f redirectCameraUpVector(Camera camera) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 upVector = Minecraft.getInstance().player.getUpVector(1.0F);
            return new Vector3f((float) upVector.x, (float) upVector.y, (float) upVector.z);
        }
        return camera.getUpVector();
    }
}
