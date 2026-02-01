package com.example.examplemod.mixin;

import com.example.examplemod.api.cullers.BlockCullingLogic;
import com.example.examplemod.client.ClientForgeEvents;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium BlockRenderer Mixin
 * dungeons-perspectiveと同様のブロック全体カリングを実装
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public class EmbeddiumBlockRendererMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onRenderModel(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        // MODが有効でなければ何もしない
        if (!ClientForgeEvents.isTopDownView || !ClientForgeEvents.isBlockCullingEnabled) {
            return;
        }

        // ブロックカリング判定
        if (BlockCullingLogic.shouldCull(ctx.pos())) {
            ci.cancel();
        }
    }
}
