package com.topdownview.mixin;

import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Boat.class, priority = 1000)
public abstract class BoatEntityMixin {

    @Inject(method = "clampRotation(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onClampRotation(Entity entity, CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;
        if (entity instanceof LocalPlayer) {
            ci.cancel();
        }
    }

    @Inject(method = "onPassengerTurned(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onPassengerTurned(Entity passenger, CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;
        if (passenger instanceof LocalPlayer) {
            ci.cancel();
        }
    }
}