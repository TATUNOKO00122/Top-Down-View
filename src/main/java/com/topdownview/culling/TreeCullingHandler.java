package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.OcclusionCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自然木のログ保護および視線遮蔽判定を担うハンドラー。
 */
public final class TreeCullingHandler {

    private static final class ProtectedTreeTrunk {
        final int x;
        final int z;
        final int bottomY;
        final int topY;

        ProtectedTreeTrunk(int x, int z, int bottomY, int topY) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
        }
    }

    private final Set<Long> protectedTreeLogPositions = new HashSet<>();
    private final List<ProtectedTreeTrunk> protectedTreeTrunks = new ArrayList<>();
    private final Set<Long> occludedTreeTrunkColumns = new HashSet<>();

    public void clearCache() {
        protectedTreeLogPositions.clear();
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns.clear();
    }

    public boolean isOccludedLog(long posLong, BlockPos pos) {
        if (!occludedTreeTrunkColumns.isEmpty() && protectedTreeLogPositions.contains(posLong)) {
            long columnKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            return occludedTreeTrunkColumns.contains(columnKey);
        }
        return false;
    }

    public boolean isProtectedLog(long posLong) {
        return Config.isProtectNaturalTreeLogs() && protectedTreeLogPositions.contains(posLong);
    }

    public void updateLogs() {
        protectedTreeLogPositions.clear();
        protectedTreeLogPositions.addAll(NaturalTreeDetector.getNaturalTreeLogs());
        
        if (Config.isProtectNaturalTreeLogs() && Config.isTreeOccludeEnabled()) {
            buildProtectedTreeTrunks();
        } else {
            protectedTreeTrunks.clear();
            occludedTreeTrunkColumns.clear();
        }
    }

    private void buildProtectedTreeTrunks() {
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns.clear();
        Set<Long> logPositions = NaturalTreeDetector.getNaturalTreeLogs();
        if (logPositions.isEmpty()) {
            return;
        }

        Map<Long, int[]> columns = new HashMap<>();
        for (long posLong : logPositions) {
            int x = BlockPos.getX(posLong);
            int z = BlockPos.getZ(posLong);
            int y = BlockPos.getY(posLong);
            long columnKey = BlockPos.asLong(x, 0, z);
            int[] range = columns.get(columnKey);
            if (range == null) {
                range = new int[]{y, y};
                columns.put(columnKey, range);
            } else {
                if (y < range[0]) range[0] = y;
                if (y > range[1]) range[1] = y;
            }
        }

        for (Map.Entry<Long, int[]> entry : columns.entrySet()) {
            long columnKey = entry.getKey();
            int[] range = entry.getValue();
            protectedTreeTrunks.add(new ProtectedTreeTrunk(
                    BlockPos.getX(columnKey), BlockPos.getZ(columnKey),
                    range[0], range[1]));
        }
    }

    public void updateOcclusion(double pX, double pY, double pZ, double cX, double cY, double cZ) {
        occludedTreeTrunkColumns.clear();
        if (!Config.isTreeOccludeEnabled() || protectedTreeTrunks.isEmpty()) {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
            boolean anyOccluding = false;
            for (int y = trunk.bottomY; y <= trunk.topY; y++) {
                mutablePos.set(trunk.x, y, trunk.z);
                long posLong = mutablePos.asLong();
                if (!protectedTreeLogPositions.contains(posLong)) continue;
                if (OcclusionCalculator.isOccludingView(mutablePos, cX, cY, cZ, pX, pY, pZ)) {
                    anyOccluding = true;
                    break;
                }
            }
            if (anyOccluding) {
                occludedTreeTrunkColumns.add(BlockPos.asLong(trunk.x, 0, trunk.z));
            }
        }
    }

    public void collectOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, FadeCacheManager fadeCache) {
        if (occludedTreeTrunkColumns.isEmpty() || protectedTreeTrunks.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getTreeOccludeAlpha();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
            long columnKey = BlockPos.asLong(trunk.x, 0, trunk.z);
            if (!occludedTreeTrunkColumns.contains(columnKey)) {
                continue;
            }
            OcclusionFadeCollector.putColumn(level, fadeCache, trunk.x, trunk.z, trunk.bottomY, trunk.topY,
                    mutablePos, protectedTreeLogPositions, false, occludeAlpha);
        }
    }
}
