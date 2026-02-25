package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void onPlay(SoundInstance sound, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            System.out.println("[SoundManagerMixin] play() called - Sound: " + sound.getLocation() + 
                ", Pos: (" + sound.getX() + ", " + sound.getY() + ", " + sound.getZ() + ")");
        }
    }
}
