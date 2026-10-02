package com.topdownview.culling;

import com.topdownview.Config;
import com.topdownview.culling.cache.FadeCacheManager;
import com.topdownview.spatial.BlockMap;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import com.topdownview.state.SpaceDebugState;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 階段ブロックの走査、保護、および視線遮蔽判定を担うハンドラー。
 */
public final class StairCullingHandler {
    /** チャンク構築ワーカーから読まれるため、集合は volatile 参照ごと差し替える。 */
    private volatile Set<BlockPos> excludedStairBlocks = Set.of();
    private List<Staircase> detectedStaircases = List.of();

    public void clearCache() {
        excludedStairBlocks = Set.of();
        detectedStaircases = List.of();
    }

    /** デバッグ表示用: 直近の update で検出した階段一覧。 */
    public List<Staircase> getDetectedStaircases() {
        return detectedStaircases;
    }

    public void update(Minecraft mc, int blockY, boolean currentSpaceEnclosed, RoomFloodFill.Result roomResult,
            BlockMap blockMap) {
        Set<BlockPos> excluded = new HashSet<>();
        List<Staircase> detected = new ArrayList<>();

        if (mc.level != null && mc.player != null && Config.isStaircaseExclusionEnabled() && currentSpaceEnclosed
                && blockMap != null) {
            BlockPos seed = mc.player.blockPosition();
            int minSteps = SpaceDebugState.MIN_STAIRCASE_STEPS;
            int playerFeetY = blockY - 1;
            int exclusionHeight = Config.getStaircaseExclusionHeight();
            // 除外対象の段 (playerFeetY..playerFeetY+exclusionHeight) と、その段を含む階段を
            // minSteps 段として認定できる下限/上限のみ走査する。それ以外のYは結果に寄与しない。
            List<Staircase> staircases = StairAnalyzer.detect(seed,
                    SpaceDebugState.STAIR_SCAN_RADIUS,
                    minSteps,
                    StairAnalyzer.scanMinY(playerFeetY, minSteps),
                    StairAnalyzer.scanMaxY(playerFeetY, exclusionHeight, minSteps),
                    blockMap);

            int minY = playerFeetY;
            int maxY = playerFeetY + exclusionHeight;
            LongSet airCells = roomResult != null ? roomResult.getAirCells() : null;

            for (Staircase stair : staircases) {
                if (stair.getBottomPos().getY() <= playerFeetY + 1) {
                    boolean anyStepInRange = false;
                    for (BlockPos step : stair.getSteps()) {
                        if (step.getY() >= minY && step.getY() <= maxY) {
                            if (isIndoorStairStep(airCells, step)) {
                                excluded.add(step.immutable());
                                anyStepInRange = true;
                            }
                        }
                    }
                    if (anyStepInRange) {
                        detected.add(stair);
                    }
                }
            }
        }

        // 描画スレッドで完成した集合に差し替えてから公開する(ワーカーは中途状態を見ない)。
        excludedStairBlocks = excluded;
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
        Set<BlockPos> excluded = excludedStairBlocks;
        return !excluded.isEmpty() && excluded.contains(pos);
    }

    public void collectOcclusionBlocks(BlockGetter level, double pX, double pY, double pZ,
            double cX, double cY, double cZ, FadeCacheManager fadeCache) {
        Set<BlockPos> excluded = excludedStairBlocks;
        if (detectedStaircases.isEmpty() || !Config.isStaircaseExclusionEnabled() || !Config.isStaircaseOccludeEnabled()) {
            return;
        }

        float occludeAlpha = (float) Config.getStaircaseOccludeAlpha();

        for (Staircase stair : detectedStaircases) {
            if (fadeCache.isFadeBlocksFull()) {
                return;
            }
            List<BlockPos> steps = stair.getSteps();
            boolean anyOccluding = OcclusionFadeCollector.anyOccluding(steps, excluded,
                    cX, cY, cZ, pX, pY, pZ);
            float alpha = anyOccluding ? occludeAlpha : 1.0f;
            OcclusionFadeCollector.putBlocks(level, fadeCache, steps, excluded, alpha);
        }
    }
}
