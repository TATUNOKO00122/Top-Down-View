package com.topdownview.state;

import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.RoomFloodFill;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 空間判定デバッグ状態。
 *
 * <p>SpaceProbe（天井+壁スキャン）と StairAnalyzer の動作確認用。
 * SpaceDebugRenderer からの参照のみ。
 */
public final class SpaceDebugState {

    public static final SpaceDebugState INSTANCE = new SpaceDebugState();

    /** 階段として認定する最小段数 */
    public static final int MIN_STAIRCASE_STEPS = 3;
    /** 階段検出の水平スキャン半径（プレイヤーを中心とした XZ の片側幅） */
    public static final int STAIR_SCAN_RADIUS = 16;

    private boolean enabled = false;
    private SpaceProbe.Result currentResult = null;
    private BuildingClassifier.Result currentClassification = BuildingClassifier.Result.EMPTY;
    private RoomSegmentation.Result currentSegmentation = RoomSegmentation.Result.EMPTY;
    private List<Staircase> currentStaircases = List.of();
    private BlockPos currentSeed = null;
    private long lastProbeTimeMs = 0;
    private final RoomFloodFill.Scratch debugScratch = new RoomFloodFill.Scratch();

    private SpaceDebugState() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            currentResult = null;
            currentClassification = BuildingClassifier.Result.EMPTY;
            currentSegmentation = RoomSegmentation.Result.EMPTY;
            currentStaircases = List.of();
            currentSeed = null;
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public SpaceProbe.Result getCurrentResult() {
        return currentResult;
    }

    public BuildingClassifier.Result getCurrentClassification() {
        return currentClassification;
    }

    public RoomSegmentation.Result getCurrentSegmentation() {
        return currentSegmentation;
    }

    public List<Staircase> getCurrentStaircases() {
        return currentStaircases;
    }

    public BlockPos getCurrentSeed() {
        return currentSeed;
    }

    public long getLastProbeTimeMs() {
        return lastProbeTimeMs;
    }

    /**
     * 指定シードで空間を判定し、結果を保持する。
     * disabled の場合はクリアして何もしない。
     */
    public void update(BlockGetter level, BlockPos seed) {
        if (!enabled || !com.topdownview.Config.isStaircaseExclusionEnabled() || level == null || seed == null) {
            currentResult = null;
            currentClassification = BuildingClassifier.Result.EMPTY;
            currentSegmentation = RoomSegmentation.Result.EMPTY;
            currentStaircases = List.of();
            currentSeed = null;
            return;
        }
        long start = System.currentTimeMillis();
        currentSeed = seed.immutable();
        currentResult = SpaceProbe.probe(level, seed, debugScratch);
        currentClassification = BuildingClassifier.classify(
                currentResult.getRoomResult(), debugScratch.getBlockMap());
        currentSegmentation = currentResult.getSegmentation();
        int feetY = seed.getY() - 1;
        currentStaircases = StairAnalyzer.detect(seed, STAIR_SCAN_RADIUS, MIN_STAIRCASE_STEPS,
                StairAnalyzer.scanMinY(feetY, MIN_STAIRCASE_STEPS),
                StairAnalyzer.scanMaxY(feetY, com.topdownview.Config.getStaircaseExclusionHeight(),
                        MIN_STAIRCASE_STEPS),
                debugScratch.getBlockMap());
        lastProbeTimeMs = System.currentTimeMillis() - start;
    }

    public void reset() {
        enabled = false;
        currentResult = null;
        currentClassification = BuildingClassifier.Result.EMPTY;
        currentSegmentation = RoomSegmentation.Result.EMPTY;
        currentStaircases = List.of();
        currentSeed = null;
        lastProbeTimeMs = 0;
    }
}
