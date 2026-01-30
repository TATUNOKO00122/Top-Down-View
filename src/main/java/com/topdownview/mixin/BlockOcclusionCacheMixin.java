package com.topdownview.mixin;

import com.topdownview.state.ModState;
import com.topdownview.culling.TopDownCuller;
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
 * Embeddium用ブロック遮蔽キャッシュMixin
 * カリング対象ブロックとの隣接面を強制的に描画する
 */
@SuppressWarnings("all")
@Mixin(value = me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache.class, remap = false)
public class BlockOcclusionCacheMixin {

    private static final TopDownCuller CULLER = TopDownCuller.getInstance();

    // チャンクビルドはバックグラウンドスレッドで並列実行されるため、ThreadLocalでスレッド毎に
    // MutableBlockPosを再用。pos.relative(face) の毎回のBlockPos生成を回避（ブロック6面分/tick）。
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOR_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void onShouldDrawSideHead(
            BlockState selfState,
            BlockGetter view,
            BlockPos pos,
            Direction face,
            CallbackInfoReturnable<Boolean> cir) {

        if (!ModState.STATUS.isEnabled()) {
            return;
        }

        // 隣接ブロック位置をMutableBlockPosで計算（アロケーション回避）
        BlockPos.MutableBlockPos neighborPos = NEIGHBOR_POS.get();
        neighborPos.set(
                pos.getX() + face.getStepX(),
                pos.getY() + face.getStepY(),
                pos.getZ() + face.getStepZ());

        // 隣接ブロックがカリング対象なら、この面を強制的に描画
        if (CULLER.isBlockCulled(neighborPos, view)) {
            cir.setReturnValue(true);
        }
    }
}
