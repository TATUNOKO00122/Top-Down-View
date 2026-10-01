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
 *
 * <p>呼び出しは最内ループで毎面ぶん走るため、直前に判定した隣接座標の結果をスレッドローカルに
 * メモし、同一ブロックの6面で同じ座標を再問い合わせしない。
 */
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache.class, remap = false)
public class BlockOcclusionCacheMixin {

    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    // 直前に判定した隣接座標と結果。shouldDrawSide は同一ブロックの面ごとに隣接座標だけが
    // 変わるので、6面中で重複する座標(角・辺の共有)をここで1回にまとめられる。
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> MEMO_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    @Unique
    private static final ThreadLocal<boolean[]> MEMO = ThreadLocal.withInitial(() -> new boolean[1]);

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

        boolean culled;
        BlockPos.MutableBlockPos memoPos = MEMO_POS.get();
        boolean[] memo = MEMO.get();
        if (memoPos.equals(neighborPos)) {
            culled = memo[0];
        } else {
            culled = CullingManager.isBlockCulled(neighborPos, view);
            memoPos.set(neighborPos);
            memo[0] = culled;
        }

        if (culled) {
            cir.setReturnValue(true);
        }
    }
}
