package com.example.examplemod.mixin;

import com.example.examplemod.client.ClientForgeEvents;
import com.example.examplemod.culling.TopDownCuller;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium BlockOcclusionCache Mixin
 * Xray対策：カリング対象ブロックに隣接する面を強制描画
 * 
 * 重要：このMixinはパフォーマンスのため軽量に保つ
 * - 計算はTopDownCullerのキャッシュを使用
 * - 面単位の判定は最小限に
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache.class, remap = false)
public class BlockOcclusionCacheMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    @Inject(
        method = "shouldDrawSide",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldDrawSideHead(
            BlockState selfState,
            BlockGetter view,
            BlockPos pos,
            Direction face,
            CallbackInfoReturnable<Boolean> cir) {
        
        // トップダウンビューでない場合は何もしない
        if (!ClientForgeEvents.isTopDownView()) {
            return;
        }

        // 隣接ブロックを取得
        BlockPos neighborPos = pos.relative(face);
        
        // 隣接ブロックがカリング対象かチェック
        // キャッシュになくても計算する（上下の面描画のため）
        if (CULLER.isBlockCulled(neighborPos)) {
            // カリング対象ブロックに隣接する面は強制的に描画する（Xray対策）
            cir.setReturnValue(true);
        }
    }
}
