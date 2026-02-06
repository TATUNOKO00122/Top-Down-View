package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.example.examplemod.culling.TopDownCuller;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium BlockRenderer Mixin
 * ブロック単位でのカリング（面単位より効率的）
 * 
 * Xray対策：
 * - ブロックを描画しない場合、隣接ブロックの面は自動的に描画される
 * - BlockOcclusionCacheが隣接関係をチェックし、必要な面を描画する
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(
        method = "renderModel",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderModelHead(
            me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext ctx,
            me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers buffers,
            CallbackInfo ci) {
        
        // トップダウンビューでない場合は何もしない
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        // BlockRenderContextからブロック位置を取得
        BlockPos pos = ctx.pos();
        if (pos == null) {
            return;
        }

        // ブロックがカリング対象かチェック
        // Dungeons Perspective方式：毎tick更新なので即座に反映
        if (CULLER.isBlockCulled(pos)) {
            // カリング対象ブロックは描画をスキップ
            ci.cancel();
        }
    }
}
