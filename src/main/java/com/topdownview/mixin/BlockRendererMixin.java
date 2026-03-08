package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium用ブロックレンダラMixin
 * カリング対象のブロックを描画しない
 * マイニングモード時は円柱内のスライス範囲外をカリング
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void onRenderModelHead(
            me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext ctx,
            me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers buffers,
            CallbackInfo ci) {

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        BlockPos pos = ctx.pos();
        if (pos == null) {
            return;
        }

        if (CULLER.isBlockCulled(pos, ctx.world())) {
            ci.cancel();
        }
    }
}
