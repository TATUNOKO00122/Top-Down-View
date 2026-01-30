package com.topdownview.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Listener.class)
public class ListenerMixin {

    @ModifyVariable(method = "setListenerPosition", at = @At("HEAD"), argsOnly = true)
    private Vec3 modifyListenerPosition(Vec3 original) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getEyePosition();
        }
        return original;
    }

    @ModifyVariable(method = "setListenerOrientation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Vector3f modifyListenerLookVector(Vector3f original) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            Vec3 viewVector = Minecraft.getInstance().player.getViewVector(1.0F);
            return new Vector3f((float) viewVector.x, (float) viewVector.y, (float) viewVector.z);
        }
        return original;
    }

    @ModifyVariable(method = "setListenerOrientation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private Vector3f modifyListenerUpVector(Vector3f original) {
        if (ModState.STATUS.isEnabled() && Minecraft.getInstance().player != null) {
            Vec3 upVector = Minecraft.getInstance().player.getUpVector(1.0F);
            return new Vector3f((float) upVector.x, (float) upVector.y, (float) upVector.z);
        }
        return original;
    }
}
