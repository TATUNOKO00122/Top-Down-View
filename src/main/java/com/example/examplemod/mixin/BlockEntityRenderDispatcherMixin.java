package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.example.examplemod.culling.TopDownCuller;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void onRender(E pBlockEntity, float pPartialTick, PoseStack pPoseStack,
            MultiBufferSource pBufferSource, CallbackInfo ci) {
        if (ClientForgeEvents.isTopDownView()) {
            if (CULLER.isBlockCulled(pBlockEntity.getBlockPos())) {
                ci.cancel();
            }
        }
    }
}
