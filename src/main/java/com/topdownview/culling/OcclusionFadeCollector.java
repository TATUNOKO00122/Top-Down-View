package com.topdownview.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * 遮蔽構造（階段・ハシゴ・自然木）のカリング位置登録を共有するヘルパー。
 *
 * <p>フェード判定 ({@code TopDownCuller.collectCullSet}) から移動時にのみ呼ばれるため、
 * 追加アロケーションを行わない。呼び出し側が用意した集合・スクラッチをそのまま走査する。
 */
public final class OcclusionFadeCollector {

    private static final int MAX_FADE_POSITIONS = 4000;

    private OcclusionFadeCollector() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * {@code blocks} のうち {@code filter} に該当するもの（{@code filter == null} なら全件）の
     * 非空気ブロックを {@code out} に登録する。
     */
    public static void addBlocks(BlockGetter level, LongOpenHashSet out, List<BlockPos> blocks,
            Set<BlockPos> filter) {
        for (int i = 0, n = blocks.size(); i < n; i++) {
            if (out.size() >= MAX_FADE_POSITIONS) {
                return;
            }
            BlockPos pos = blocks.get(i);
            if (filter != null && !filter.contains(pos)) {
                continue;
            }
            if (level.getBlockState(pos).isAir()) {
                continue;
            }
            out.add(pos.asLong());
        }
    }

    /**
     * (x, z) の y0..y1 の非空気ブロックを {@code out} に登録する。
     *
     * @param includeLongs 登録対象を絞る位置（{@link BlockPos#asLong()}）集合。{@code null} なら全件。
     * @param requireDry   液体ブロックを除外するか（ハシゴ裏の壁用）。
     */
    public static void addColumn(BlockGetter level, LongOpenHashSet out, int x, int z, int y0, int y1,
            BlockPos.MutableBlockPos scratch, Set<Long> includeLongs, boolean requireDry) {
        for (int y = y0; y <= y1; y++) {
            if (out.size() >= MAX_FADE_POSITIONS) {
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
            out.add(posLong);
        }
    }
}
