package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.culling.geometry.OcclusionCalculator;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 階段ブロックの走査、保護、および視線遮蔽判定を担うハンドラー。
 */
public final class StairCullingHandler {
    private final Set<BlockPos> excludedStairBlocks = new HashSet<>();
    private List<Staircase> detectedStaircases = List.of();
    
    public void clearCache() {
        excludedStairBlocks.clear();
        detectedStaircases = List.of();
    }

    public void update(Minecraft mc, int blockY, boolean currentSpaceEnclosed, RoomFloodFill.Result roomResult) {
        excludedStairBlocks.clear();
        detectedStaircases = List.of();

        if (mc.level == null || mc.player == null || !Config.isStaircaseExclusionEnabled() || !currentSpaceEnclosed) {
            return;
        }

        BlockPos seed = mc.player.blockPosition();
        List<Staircase> staircases = StairAnalyzer.detect(mc.level, seed,
                com.topdownview.state.SpaceDebugState.STAIR_SCAN_RADIUS,
                com.topdownview.state.SpaceDebugState.MIN_STAIRCASE_STEPS);
        
        if (staircases.isEmpty()) {
            return;
        }

        int playerFeetY = blockY - 1;
        int exclusionHeight = Config.getStaircaseExclusionHeight();
        int minY = playerFeetY;
        int maxY = playerFeetY + exclusionHeight;
        LongSet airCells = roomResult != null ? roomResult.getAirCells() : null;

        List<Staircase> detected = new ArrayList<>();
        for (Staircase stair : staircases) {
            if (stair.getBottomPos().getY() <= playerFeetY + 1) {
                boolean anyStepInRange = false;
                for (BlockPos step : stair.getSteps()) {
                    if (step.getY() >= minY && step.getY() <= maxY) {
                        if (isIndoorStairStep(airCells, step)) {
                            excludedStairBlocks.add(step.immutable());
                            anyStepInRange = true;
                        }
                    }
                }
                if (anyStepInRange) {
                    detected.add(stair);
                }
            }
        }
        detectedStaircases = detected;
    }

    private boolean isIndoorStairStep(LongSet airCells, BlockPos step) {
        if (airCells == null || airCells.isEmpty()) {
            return false;
        }
        int x = step.getX();
        int y = step.getY();
        int z = step.getZ();
        long posAbove1 = BlockPos.asLong(x, y + 1, z);
        long posAbove2 = BlockPos.asLong(x, y + 2, z);
        return airCells.contains(posAbove1) || airCells.contains(posAbove2);
    }

    public boolean isExcludedStairBlock(BlockPos pos) {
        return !excludedStairBlocks.isEmpty() && excludedStairBlocks.contains(pos);
    }

    public void collectOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, FadeCacheManager fadeCache) {
        if (detectedStaircases.isEmpty() || !Config.isStaircaseExclusionEnabled() || !Config.isStaircaseOccludeEnabled()) {
            return;
        }

        float occludeAlpha = (float) Config.getStaircaseOccludeAlpha();

        for (Staircase stair : detectedStaircases) {
            if (fadeCache.isFadeBlocksFull()) return;
            boolean anyOccluding = false;
            for (BlockPos step : stair.getSteps()) {
                if (!excludedStairBlocks.contains(step)) continue;
                if (OcclusionCalculator.isOccludingView(step, cX, cY, cZ, pX, pY, pZ)) {
                    anyOccluding = true;
                    break;
                }
            }
            float alpha = anyOccluding ? occludeAlpha : 1.0f;
            for (BlockPos step : stair.getSteps()) {
                if (fadeCache.isFadeBlocksFull()) return;
                if (!excludedStairBlocks.contains(step)) continue;
                BlockState state = level.getBlockState(step);
                if (state.isAir()) continue;
                fadeCache.putFadeBlock(step.asLong(), alpha);
            }
        }
    }
}
