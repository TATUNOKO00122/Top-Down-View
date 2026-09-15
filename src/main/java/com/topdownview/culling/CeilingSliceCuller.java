package com.topdownview.culling;

import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.WallAnalyzer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 屋内用の天井スライスカリング。
 *
 * <p>プレイヤーの天井高さを求め、検出された空間の水平バウンディングボックス (正方形範囲) 内で、
 * 天井高さ以上の非空気ブロックをまとめてカリングする。天井以外のブロック (壁・家具・上階) も
 * 同じ高さ以上ならすべて消す水平スライスで、屋根の形状や分類結果に依存しない。
 *
 * <p>カリングは各列の地表高さ (WORLD_SURFACE) まで行い、それより上は空気なので打ち切る。
 * チャンクビルドワーカーから読まれるため、集合は volatile 参照ごと差し替える。
 */
public final class CeilingSliceCuller {

    private static final int NO_CEILING = Integer.MIN_VALUE;

    /** 検出範囲は空気セルの AABB なので、外壁の頂部も含めるため壁厚分マージンする。 */
    private static final int RANGE_MARGIN = 1;

    private volatile LongOpenHashSet slicePositions = new LongOpenHashSet();
    private volatile long generation = 0;

    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public void clearCache() {
        if (!slicePositions.isEmpty()) {
            slicePositions = new LongOpenHashSet();
        }
        generation++;
    }

    public boolean isCeilingSliceBlock(long posLong) {
        LongOpenHashSet set = slicePositions;
        return !set.isEmpty() && set.contains(posLong);
    }

    /** 天井スライス集合の世代番号。値が変わればカリング結果が変わった可能性がある。 */
    public long getGeneration() {
        return generation;
    }

    /**
     * プレイヤーの天井高さを求め、検出範囲 (正方形) 内の天井高さ以上を一括でカリングする。
     *
     * @param minPos      検出空間の最小座標
     * @param maxPos      検出空間の最大座標
     * @param playerFeetY プレイヤー足元ブロック Y
     */
    public void update(LevelReader level, BlockPos minPos, BlockPos maxPos,
            int playerX, int playerFeetY, int playerZ) {
        LongOpenHashSet next = new LongOpenHashSet();
        if (level == null || minPos == null || maxPos == null) {
            apply(next);
            return;
        }

        int ceilingY = findCeilingY(level, playerX, playerFeetY + 1, playerZ);
        if (ceilingY == NO_CEILING) {
            apply(next);
            return;
        }

        int minX = minPos.getX() - RANGE_MARGIN;
        int minZ = minPos.getZ() - RANGE_MARGIN;
        int maxX = maxPos.getX() + RANGE_MARGIN;
        int maxZ = maxPos.getZ() + RANGE_MARGIN;
        int maxBuildHeight = level.getMaxBuildHeight() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // 天井より上を全部消す。列の地表高さまでで打ち切る (その上は空気)。
                int top = Math.min(maxBuildHeight, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                if (top < ceilingY) {
                    top = ceilingY;
                }
                for (int y = ceilingY; y <= top; y++) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    next.add(mutablePos.asLong());
                }
            }
        }
        apply(next);
    }

    /** プレイヤー列を上方走査し、最初の固体ブロックを天井とする。 */
    private int findCeilingY(LevelReader level, int x, int startY, int z) {
        int limit = startY + RoomFloodFill.CEILING_SCAN_HEIGHT;
        for (int y = startY; y <= limit; y++) {
            mutablePos.set(x, y, z);
            if (WallAnalyzer.isSolid(level, mutablePos)) {
                return y;
            }
        }
        return NO_CEILING;
    }

    private void apply(LongOpenHashSet next) {
        slicePositions = next;
        generation++;
    }
}
