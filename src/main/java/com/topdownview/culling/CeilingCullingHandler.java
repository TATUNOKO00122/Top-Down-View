package com.topdownview.culling;

import com.topdownview.spatial.RoomFloodFill;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

/**
 * 屋内空間として判定された領域の天井ブロックのカリング処理を担うハンドラー。
 */
public final class CeilingCullingHandler {
    private final LongOpenHashSet ceilingCullPositions = new LongOpenHashSet();

    public void clearCache() {
        ceilingCullPositions.clear();
    }

    public boolean isCeilingBlock(long posLong) {
        return !ceilingCullPositions.isEmpty() && ceilingCullPositions.contains(posLong);
    }

    public void update(boolean currentSpaceEnclosed, RoomFloodFill.Result roomResult) {
        ceilingCullPositions.clear();
        if (!currentSpaceEnclosed || roomResult == null || !roomResult.isEnclosed()) {
            return;
        }
        
        LongSet airCells = roomResult.getAirCells();
        LongSet shellCells = roomResult.getShellCells();
        if (airCells.isEmpty() || shellCells.isEmpty()) {
            return;
        }
        
        LongIterator it = shellCells.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            long below = BlockPos.asLong(x, y - 1, z);
            if (airCells.contains(below)) {
                ceilingCullPositions.add(packed);
            }
        }
    }
}
