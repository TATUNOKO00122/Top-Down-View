package com.topdownview.culling;

import com.topdownview.spatial.RoomSegmentation;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 屋内用の天井スライスカリング。
 *
 * <p>検出された空間の天井高さを求め、その水平バウンディングボックス (正方形範囲) 内で、
 * 天井高さ以上の非空気ブロックをまとめてカリングする。天井以外のブロック (壁・家具・上階) も
 * 同じ高さ以上ならすべて消す水平スライスで、屋根の形状や分類結果に依存しない。
 *
 * <p>カリングは各列の地表高さ (WORLD_SURFACE) まで行い、それ以上は空気なので打ち切る。
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
     * 検出空間から天井高さを求め、検出範囲 (正方形) 内の天井高さ以上を一括でカリングする。
     *
     * @param minPos       検出空間の最小座標
     * @param maxPos       検出空間の最大座標
     * @param airCells     検出空間の空気セル (packed long)。列ごとの天井候補の集計に使う。
     * @param segmentation 階・部屋分割結果。プレイヤーの階の天井だけを集計するために使う。
     * @param playerStorey プレイヤーがいる階インデックス。負値なら階で絞らない。
     */
    public void update(LevelReader level, BlockPos minPos, BlockPos maxPos, LongSet airCells,
            RoomSegmentation.Result segmentation, int playerStorey) {
        LongOpenHashSet next = new LongOpenHashSet();
        if (level == null || minPos == null || maxPos == null) {
            apply(next);
            return;
        }

        int ceilingY = findDominantCeilingY(airCells, segmentation, playerStorey);
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

    /**
     * 検出空間の各列について「最上部の空気 Y の1つ上」を天井候補とし、最も多い Y を返す。
     *
     * <p>列ごとの天井ではなく最頻値を使うことで、背の高い部屋へ入るたびにスライス位置が
     * 変わるのを防ぐ。階が分かる場合はプレイヤーの階のセルだけを集計するため、2階建てでも
     * 上階や屋根ではなく「今いる階の天井」が選ばれる。
     *
     * <p>梁の下でも、上に空気が続いていれば最上部は梁より上になるため、梁を天井と誤認しない。
     * 最頻値が同数の場合は低い方 (より多くの視界を開く方) を選ぶ。
     */
    private int findDominantCeilingY(LongSet airCells, RoomSegmentation.Result segmentation,
            int playerStorey) {
        if (airCells == null || airCells.isEmpty()) {
            return NO_CEILING;
        }
        boolean useStorey = segmentation != null && segmentation.isValid() && playerStorey >= 0;
        Long2IntOpenHashMap topByColumn = new Long2IntOpenHashMap();
        topByColumn.defaultReturnValue(Integer.MIN_VALUE);
        for (long cell : airCells) {
            if (useStorey && segmentation.getStoreyOf(cell) != playerStorey) {
                continue;
            }
            long column = BlockPos.asLong(BlockPos.getX(cell), 0, BlockPos.getZ(cell));
            int y = BlockPos.getY(cell);
            if (y > topByColumn.get(column)) {
                topByColumn.put(column, y);
            }
        }
        if (topByColumn.isEmpty()) {
            return NO_CEILING;
        }

        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (var entry : topByColumn.long2IntEntrySet()) {
            counts.addTo((long) entry.getIntValue() + 1, 1);
        }

        int bestY = NO_CEILING;
        int bestCount = 0;
        for (var entry : counts.long2IntEntrySet()) {
            int y = (int) entry.getLongKey();
            int count = entry.getIntValue();
            if (count > bestCount || (count == bestCount && y < bestY)) {
                bestCount = count;
                bestY = y;
            }
        }
        return bestY;
    }

    private void apply(LongOpenHashSet next) {
        slicePositions = next;
        generation++;
    }
}
