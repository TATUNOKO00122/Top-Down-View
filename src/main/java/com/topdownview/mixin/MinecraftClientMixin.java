package com.topdownview.mixin;

import com.topdownview.client.ClickActionHandler;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public HitResult hitResult;

    @Shadow
    protected int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled())
            return;
        this.missTime = 0;

        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null && this.hitResult != null) {
            Vec3 eyePos = mc.player.getEyePosition(1.0f);
            if (this.hitResult.getType() == HitResult.Type.BLOCK) {
                double blockReach = mc.gameMode != null ? mc.gameMode.getPickRange() : 4.5;
                double distSqr = eyePos.distanceToSqr(this.hitResult.getLocation());
                if (distSqr > blockReach * blockReach) {
                    cir.setReturnValue(false);
                }
            } else if (this.hitResult.getType() == HitResult.Type.ENTITY) {
                double entityReach = mc.player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
                double distSqr = eyePos.distanceToSqr(this.hitResult.getLocation());
                if (distSqr > entityReach * entityReach) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled())
            return;

        Minecraft mc = (Minecraft) (Object) this;
        if (leftClick && mc.player != null && this.hitResult != null) {
            Vec3 eyePos = mc.player.getEyePosition(1.0f);
            if (this.hitResult.getType() == HitResult.Type.BLOCK) {
                double blockReach = mc.gameMode != null ? mc.gameMode.getPickRange() : 4.5;
                double distSqr = eyePos.distanceToSqr(this.hitResult.getLocation());
                if (distSqr > blockReach * blockReach) {
                    ci.cancel();
                }
            } else if (this.hitResult.getType() == HitResult.Type.ENTITY) {
                double entityReach = mc.player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
                double distSqr = eyePos.distanceToSqr(this.hitResult.getLocation());
                if (distSqr > entityReach * entityReach) {
                    ci.cancel();
                }
            }
        }
    }
}
