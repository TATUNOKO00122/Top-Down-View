package com.topdownview.mixin;

import com.topdownview.culling.CullingManager;
import com.topdownview.state.ModState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium(Sodium)用 BlockRenderer Mixin。
 *
 * <p>カリング対象のブロックは自身のジオメトリを一切出力しない。断面は隣接ブロック側が
 * {@link BlockOcclusionCacheMixin} の遮蔽判定上書きで「自分自身の面」として描く。
 * こうすると断面は隣ブロック本来の向き・テクスチャ・AO・バイオームtint・ライトマップで
 * 通常パイプラインのまま描かれ、見た目が素のワールドと完全に一致する。
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void onRenderModelHead(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            return;
        }
        BlockPos pos = ctx.pos();
        if (pos != null && CullingManager.isBlockCulledForMesh(pos, ctx.world())) {
            ci.cancel();
        }
    }
}
