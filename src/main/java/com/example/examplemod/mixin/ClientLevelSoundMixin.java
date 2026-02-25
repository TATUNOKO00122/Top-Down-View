package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel")
public class ClientLevelSoundMixin {

    @Redirect(method = "playSound", 
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectCameraPositionForSound(Camera camera) {
        Vec3 cameraPos = camera.getPosition();
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 playerPos = Minecraft.getInstance().player.getEyePosition();
            System.out.println("[ClientLevelSoundMixin] Redirecting camera position: " + cameraPos + " -> " + playerPos);
            return playerPos;
        }
        System.out.println("[ClientLevelSoundMixin] NOT redirecting (isTopDownView=" + ClientForgeEvents.isTopDownView() + "), cameraPos=" + cameraPos);
        return cameraPos;
    }
}
