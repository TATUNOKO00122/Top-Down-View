package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityRenderDispatcher Mixin
 * 
 * 優先度500: 他の軽量化MOD（Entity Culling, Sodium等）より先に実行し、
 * プレイヤーエンティティを確実に描画対象にする
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 500)
public class EntityRenderDispatcherMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void onRender(E pEntity, double pX, double pY, double pZ, float pRotationYaw,
            float pPartialTicks, PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, CallbackInfo ci) {
        if (ModState.STATUS.isEnabled()) {
            if (CULLER.isBlockCulled(pEntity.blockPosition(), pEntity.level())) {
                ci.cancel();
            }
        }
    }
}
