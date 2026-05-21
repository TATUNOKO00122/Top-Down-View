package com.topdownview.mixin;

import com.topdownview.client.ClickActionHandler;
import com.topdownview.state.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled())
            return;
        this.missTime = 0;
    }
}
