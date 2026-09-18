package com.topdownview.culling;

import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.OcclusionCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * 遮蔽構造（階段・ハシゴ・自然木）のフェード登録を共通化するヘルパー。
 *
 * <p>{@code collectOcclusionBlocks} から毎フレーム呼ばれるため、追加アロケーションを行わない。
 * 呼び出し側が用意したリスト・スクラッチをそのまま走査する。
 */
public final class OcclusionFadeCollector {

    private OcclusionFadeCollector() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * {@code blocks} のうち {@code filter} に該当するもの（{@code filter == null} なら全件）の
     * いずれかが視線を遮るか。
     */
    public static boolean anyOccluding(List<BlockPos> blocks, Set<BlockPos> filter,
            double cX, double cY, double cZ, double pX, double pY, double pZ) {
        for (int i = 0, n = blocks.size(); i < n; i++) {
            BlockPos pos = blocks.get(i);
            if (filter != null && !filter.contains(pos)) {
                continue;
            }
            if (OcclusionCalculator.isOccludingView(pos, cX, cY, cZ, pX, pY, pZ)) {
                return true;
            }
        }
        return false;
    }

    /** (x, z) の y0..y1 のいずれかが視線を遮るか。 */
    public static boolean anyOccludingColumn(int x, int z, int y0, int y1, BlockPos.MutableBlockPos scratch,
            double cX, double cY, double cZ, double pX, double pY, double pZ) {
        for (int y = y0; y <= y1; y++) {
            scratch.set(x, y, z);
            if (OcclusionCalculator.isOccludingView(scratch, cX, cY, cZ, pX, pY, pZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code blocks} のうち {@code filter} に該当するもの（{@code filter == null} なら全件）の
     * 非空気ブロックを {@code alpha} でフェード登録する。
     */
    public static void putBlocks(BlockGetter level, FadeCacheManager fadeCache, List<BlockPos> blocks,
            Set<BlockPos> filter, float alpha) {
        for (int i = 0, n = blocks.size(); i < n; i++) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            BlockPos pos = blocks.get(i);
            if (filter != null && !filter.contains(pos)) {
                continue;
            }
            if (level.getBlockState(pos).isAir()) {
                continue;
            }
            fadeCache.putFadeBlock(pos.asLong(), alpha);
        }
    }

    /**
     * (x, z) の y0..y1 の非空気ブロックを {@code alpha} でフェード登録する。
     *
     * @param includeLongs 登録対象を絞る位置（{@link BlockPos#asLong()}）集合。{@code null} なら全件。
     * @param requireDry   液体ブロックを除外するか（ハシゴ裏の壁用）。
     */
    public static void putColumn(BlockGetter level, FadeCacheManager fadeCache, int x, int z, int y0, int y1,
            BlockPos.MutableBlockPos scratch, Set<Long> includeLongs, boolean requireDry, float alpha) {
        for (int y = y0; y <= y1; y++) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            scratch.set(x, y, z);
            long posLong = scratch.asLong();
            if (includeLongs != null && !includeLongs.contains(posLong)) {
                continue;
            }
            BlockState state = level.getBlockState(scratch);
            if (state.isAir()) {
                continue;
            }
            if (requireDry && !state.getFluidState().isEmpty()) {
                continue;
            }
            fadeCache.putFadeBlock(posLong, alpha);
        }
    }
}
