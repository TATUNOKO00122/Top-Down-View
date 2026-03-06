package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
    private void onBobView(PoseStack pPoseStack, float pPartialTicks, CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "pick", at = @At("TAIL"))
    private void onPick(float partialTicks, CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            com.topdownview.client.MouseRaycast.INSTANCE.update(mc, partialTicks,
                    com.topdownview.client.MouseRaycast.getCustomReachDistance());
            mc.hitResult = com.topdownview.client.MouseRaycast.INSTANCE.getLastHitResult();

            HitResult result = mc.hitResult;
            if (result instanceof EntityHitResult entityHit) {
                mc.crosshairPickEntity = entityHit.getEntity();
            } else {
                mc.crosshairPickEntity = null;
            }
        }
    }
}
