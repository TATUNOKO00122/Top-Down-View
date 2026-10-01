package com.topdownview.mixin;

import com.topdownview.culling.CullingManager;
import com.topdownview.state.ModState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium(Sodium)用 BlockOcclusionCache Mixin。
 *
 * <p>隣接ブロックがカリング対象だと、その面は通常「遮蔽されている」と判定され描かれない。ここで
 * {@code shouldDrawSide} を true に上書きし、消えるブロックに接する面を隣ブロック自身の面として
 * 描画させる。断面は隣ブロック本来の向き・テクスチャ・AO・バイオームtintで描かれるため、
 * 独自レンダラや頂点反転は不要になる。
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache.class, remap = false)
public class BlockOcclusionCacheMixin {

    // チャンクビルドはワーカースレッドで並列実行されるため、スレッド毎に MutableBlockPos を再用して
    // ブロック6面ぶんの BlockPos 生成を避ける。
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void onShouldDrawSide(BlockState selfState, BlockGetter view, BlockPos pos, Direction face,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.STATUS.isEnabled() || !ModState.STATUS.isCullingEnabled()) {
            return;
        }

        BlockPos.MutableBlockPos neighborPos = NEIGHBOR_POS.get();
        neighborPos.set(
                pos.getX() + face.getStepX(),
                pos.getY() + face.getStepY(),
                pos.getZ() + face.getStepZ());

        if (CullingManager.isBlockCulled(neighborPos, view)) {
            cir.setReturnValue(true);
        }
    }
}
