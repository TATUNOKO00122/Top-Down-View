package com.topdownview.state;

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
    /** 階段検出のスキャン半径（プレイヤーを中心とした立方体の辺 = 2*radius+1） */
    public static final int STAIR_SCAN_RADIUS = 16;

    private boolean enabled = false;
    private SpaceProbe.Result currentResult = null;
    private List<Staircase> currentStaircases = List.of();
    private BlockPos currentSeed = null;
    private long lastProbeTimeMs = 0;

    private SpaceDebugState() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            currentResult = null;
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
            currentStaircases = List.of();
            currentSeed = null;
            return;
        }
        long start = System.currentTimeMillis();
        currentSeed = seed.immutable();
        currentResult = SpaceProbe.probe(level, seed);
        currentStaircases = StairAnalyzer.detect(level, seed,
                STAIR_SCAN_RADIUS, MIN_STAIRCASE_STEPS);
        lastProbeTimeMs = System.currentTimeMillis() - start;
    }

    public void reset() {
        enabled = false;
        currentResult = null;
        currentStaircases = List.of();
        currentSeed = null;
        lastProbeTimeMs = 0;
    }
}
