package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.audio.Channel")
public abstract class ChannelMixin {

    @Inject(method = "linearAttenuation(F)V", at = @At("HEAD"))
    private void onLinearAttenuation(float attenuation, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            System.out.println("[ChannelMixin] linearAttenuation called - attenuation (maxDistance): " + attenuation);
        }
    }

    @Inject(method = "setSelfPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void onSetSelfPosition(Vec3 pos, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            Vec3 playerPos = Minecraft.getInstance().player.getEyePosition();
            double distance = playerPos.distanceTo(pos);
            System.out.println("[ChannelMixin] setSelfPosition called - SoundPos: " + pos + ", PlayerPos: " + playerPos + ", Distance: " + distance);
        }
    }
}
