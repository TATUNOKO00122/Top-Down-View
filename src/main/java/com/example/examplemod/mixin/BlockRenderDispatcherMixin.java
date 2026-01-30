package com.example.examplemod.mixin;

import com.example.examplemod.client.BlockCuller;
import com.example.examplemod.client.ClientForgeEvents;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockRenderDispatcherにMixinして、個々のブロックのレンダリングを制御
 */
@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    /**
     * レンダリング前にカリング判定を行い、カリング対象ならスキップ
     */
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void onRenderBatched(BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, net.minecraft.world.level.block.state.BlockState state2, net.minecraft.world.level.block.state.BlockState state3, CallbackInfoReturnable<Boolean> cir) {
        // トップダウンビューかつブロックカリングが有効な場合
        if (ClientForgeEvents.isTopDownView && ClientForgeEvents.isBlockCullingEnabled) {
            // カリング対象のブロックならレンダリングをスキップ
            if (BlockCuller.shouldCull(pos)) {
                cir.setReturnValue(false);
            }
        }
    }
}
