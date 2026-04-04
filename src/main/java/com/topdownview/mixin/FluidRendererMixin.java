package com.topdownview.mixin;

import com.topdownview.culling.TopDownCuller;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium用流体レンダラMixin
 * カリング対象の流体（水、溶岩）を描画しない
 * FluidRendererはBlockRendererとは別パイプラインで動作するため、
 * BlockRendererMixinだけでは流体をキャンセルできない
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer.class, remap = false)
public class FluidRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(
            me.jellysquid.mods.sodium.client.world.WorldSlice world,
            net.minecraft.world.level.material.FluidState fluidState,
            BlockPos blockPos,
            BlockPos offset,
            me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers buffers,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        if (blockPos == null) {
            return;
        }

        if (CULLER.isBlockCulled(blockPos, world)) {
            ci.cancel();
        }
    }
}
