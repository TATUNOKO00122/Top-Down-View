package com.topdownview.mixin;

import com.topdownview.client.MountSteeringController;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseEntityMixin {

    @Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true)
    private void onGetRiddenInput(Player player, Vec3 movementInput, CallbackInfoReturnable<Vec3> cir) {
        if (!ModState.STATUS.isEnabled()) return;
        if (!(player instanceof LocalPlayer)) return;

        LocalPlayer localPlayer = (LocalPlayer) player;
        float forward = localPlayer.zza;
        float strafe = localPlayer.xxa;
        float inputLength = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength < 0.01f) return;

        cir.setReturnValue(new Vec3(0.0, 0.0, Math.min(inputLength, 1.0)));
    }

    @Inject(method = "getRiddenRotation", at = @At("RETURN"), cancellable = true)
    private void onGetRiddenRotation(LivingEntity controllingPassenger, CallbackInfoReturnable<Vec2> cir) {
        if (!ModState.STATUS.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer)) return;

        LocalPlayer localPlayer = (LocalPlayer) mc.player;
        float forward = localPlayer.zza;
        float strafe = localPlayer.xxa;
        float inputLength = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength < 0.01f) return;

        float smoothYaw = MountSteeringController.getSmoothMountTargetYaw();
        if (Float.isNaN(smoothYaw)) return;

        cir.setReturnValue(new Vec2(0.0f, smoothYaw));
    }
}