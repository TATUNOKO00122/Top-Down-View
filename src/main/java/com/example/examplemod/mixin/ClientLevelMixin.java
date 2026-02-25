package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void onPlaySeededSoundAtPosition(@Nullable Player pPlayer, double pX, double pY, double pZ, Holder<SoundEvent> pSound, SoundSource pSource, float pVolume, float pPitch, long pSeed, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            boolean willPlay = (pPlayer == Minecraft.getInstance().player);
            System.out.println("[ClientLevelMixin] playSeededSound(position) called - Sound: " + pSound.value().getLocation() + ", pPlayer: " + pPlayer + ", mc.player: " + Minecraft.getInstance().player + ", willPlay: " + willPlay);
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("RETURN"))
    private void onPlaySeededSoundAtPositionReturn(@Nullable Player pPlayer, double pX, double pY, double pZ, Holder<SoundEvent> pSound, SoundSource pSource, float pVolume, float pPitch, long pSeed, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            System.out.println("[ClientLevelMixin] playSeededSound(position) RETURN - Sound: " + pSound.value().getLocation());
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void onPlaySeededSoundAtEntity(@Nullable Player pPlayer, Entity pEntity, Holder<SoundEvent> pSound, SoundSource pSource, float pVolume, float pPitch, long pSeed, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView() && Minecraft.getInstance().player != null) {
            boolean willPlay = (pPlayer == Minecraft.getInstance().player);
            System.out.println("[ClientLevelMixin] playSeededSound(entity) called - Sound: " + pSound.value().getLocation() + ", Entity: " + pEntity.getType() + ", pPlayer: " + pPlayer + ", willPlay: " + willPlay);
        }
    }

    @Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V", at = @At("HEAD"))
    private void onPlaySound(double pX, double pY, double pZ, SoundEvent pSound, SoundSource pSource, float pVolume, float pPitch, boolean pUseDistance, long pSeed, CallbackInfo ci) {
        System.out.println("[ClientLevelMixin] playSound() called - Sound: " + pSound.getLocation() + 
            ", useDistance: " + pUseDistance + 
            ", isTopDownView: " + ClientForgeEvents.isTopDownView() +
            ", mc.player: " + Minecraft.getInstance().player);
    }
}
