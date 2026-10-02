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

    /** チャンク構築ワーカーから読まれるため、集合は volatile 参照ごと差し替える。 */
    private volatile Set<Long> protectedTreeLogPositions = Set.of();
    private final List<ProtectedTreeTrunk> protectedTreeTrunks = new ArrayList<>();
    private volatile Set<Long> occludedTreeTrunkColumns = Set.of();

    public void clearCache() {
        protectedTreeLogPositions = Set.of();
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns = Set.of();
    }

    public boolean isOccludedLog(long posLong, BlockPos pos) {
        Set<Long> occluded = occludedTreeTrunkColumns;
        Set<Long> logs = protectedTreeLogPositions;
        if (!occluded.isEmpty() && logs.contains(posLong)) {
            long columnKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            return occluded.contains(columnKey);
        }
        return false;
    }

    public boolean isProtectedLog(long posLong) {
        return Config.isProtectNaturalTreeLogs() && protectedTreeLogPositions.contains(posLong);
    }

    public void updateLogs() {
        protectedTreeLogPositions = new HashSet<>(NaturalTreeDetector.getNaturalTreeLogs());

        if (Config.isProtectNaturalTreeLogs() && Config.isTreeOccludeEnabled()) {
            buildProtectedTreeTrunks();
        } else {
            protectedTreeTrunks.clear();
            occludedTreeTrunkColumns = Set.of();
        }
    }

    private void buildProtectedTreeTrunks() {
        protectedTreeTrunks.clear();
        occludedTreeTrunkColumns = Set.of();
        Set<Long> logPositions = protectedTreeLogPositions;
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
        Set<Long> occluded = new HashSet<>();
        if (Config.isTreeOccludeEnabled() && !protectedTreeTrunks.isEmpty()) {
            Set<Long> logs = protectedTreeLogPositions;
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
                boolean anyOccluding = false;
                for (int y = trunk.bottomY; y <= trunk.topY; y++) {
                    mutablePos.set(trunk.x, y, trunk.z);
                    long posLong = mutablePos.asLong();
                    if (!logs.contains(posLong)) continue;
                    if (OcclusionCalculator.isOccludingView(mutablePos, cX, cY, cZ, pX, pY, pZ)) {
                        anyOccluding = true;
                        break;
                    }
                }
                if (anyOccluding) {
                    occluded.add(BlockPos.asLong(trunk.x, 0, trunk.z));
                }
            }
        }
        occludedTreeTrunkColumns = occluded;
    }

    public void collectOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, FadeCacheManager fadeCache) {
        Set<Long> occluded = occludedTreeTrunkColumns;
        if (occluded.isEmpty() || protectedTreeTrunks.isEmpty()) {
            return;
        }

        float occludeAlpha = (float) Config.getTreeOccludeAlpha();
        Set<Long> logs = protectedTreeLogPositions;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (ProtectedTreeTrunk trunk : protectedTreeTrunks) {
            long columnKey = BlockPos.asLong(trunk.x, 0, trunk.z);
            if (!occluded.contains(columnKey)) {
                continue;
            }
            OcclusionFadeCollector.putColumn(level, fadeCache, trunk.x, trunk.z, trunk.bottomY, trunk.topY,
                    mutablePos, logs, false, occludeAlpha);
        }
    }
}
