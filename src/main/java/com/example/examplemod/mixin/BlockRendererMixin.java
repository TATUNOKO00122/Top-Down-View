package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.example.examplemod.culling.TopDownCuller;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Embeddium用ブロックレンダラMixin
 * カリング対象のブロックを描画しない
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();
    private static final Logger LOGGER = LogManager.getLogger();
    private static int callCount = 0;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void onRenderModelHead(
            me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext ctx,
            me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers buffers,
            CallbackInfo ci) {

        callCount++;
        if (callCount % 1000 == 0) {
            LOGGER.info("[TopDownView] BlockRendererMixin called {} times, isTopDownView={}", 
                    callCount, ClientForgeEvents.isTopDownView());
        }

        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        BlockPos pos = ctx.pos();
        if (pos == null) {
            return;
        }

        boolean culled = CULLER.isBlockCulled(pos, ctx.world());
        if (culled && callCount % 100 == 0) {
            LOGGER.info("[TopDownView] Culled block at {}", pos);
        }
        
        if (culled) {
            ci.cancel();
        }
    }
}
