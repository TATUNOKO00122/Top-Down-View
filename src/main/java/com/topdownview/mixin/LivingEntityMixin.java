package com.topdownview.mixin;

import com.topdownview.Config;
import com.topdownview.state.ModState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer player)) return;
        if (!player.isPassenger()) return;

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity) {
            if (Config.isIndependentMountAim()) {
                player.yBodyRot = vehicle.getYRot();
            }
        }
    }
}
