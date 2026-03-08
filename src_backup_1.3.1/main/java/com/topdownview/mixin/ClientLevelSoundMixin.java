package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = net.minecraft.client.multiplayer.ClientLevel.class)
public class ClientLevelSoundMixin {

    @Redirect(method = "playSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectCameraPositionForSound(Camera camera) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return camera.getPosition();
    }
}
