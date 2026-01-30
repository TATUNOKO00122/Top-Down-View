package com.topdownview.state;

import com.topdownview.spatial.SpaceAnalyzer;
import com.topdownview.spatial.SpaceExplorer;
import com.topdownview.spatial.SpaceRegion;
import com.topdownview.spatial.StairAnalyzer;
import com.topdownview.spatial.Staircase;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 空間探索デバッグ状態。
 *
 * <p>SpaceExplorer/SpaceAnalyzer の動作確認用。
 * 既存のCulling等には統合せず、SpaceDebugRenderer からの参照のみ。
 *
 * <p>パラメータは定数として保持。後で Config 化可能。
 */
public final class SpaceDebugState {

    public static final SpaceDebugState INSTANCE = new SpaceDebugState();

    // 探索パラメータ（後でConfig化）
    public static final int MAX_EXPLORE_BLOCKS = 1000;
    public static final int MAX_WALL_THICKNESS = 3;
    public static final int MAX_HOLE_SIZE = 2;
    public static final int MIN_ROOM_VOLUME = 8;
    /** 階段として認定する最小段数 */
    public static final int MIN_STAIRCASE_STEPS = 3;

    private boolean enabled = false;
    private SpaceRegion currentRegion = null;
    private List<Staircase> currentStaircases = List.of();
    private BlockPos currentSeed = null;
    private long lastExploreTimeMs = 0;

    private SpaceDebugState() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            currentRegion = null;
            currentStaircases = List.of();
            currentSeed = null;
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public SpaceRegion getCurrentRegion() {
        return currentRegion;
    }

    public List<Staircase> getCurrentStaircases() {
        return currentStaircases;
    }

    public BlockPos getCurrentSeed() {
        return currentSeed;
    }

    public long getLastExploreTimeMs() {
        return lastExploreTimeMs;
    }

    /**
     * 指定シードで空間を再探索し、結果を保持する。
     * disabled の場合はクリアして何もしない。
     */
    public void update(BlockGetter level, BlockPos seed) {
        if (!enabled || !com.topdownview.Config.isStaircaseExclusionEnabled() || level == null || seed == null) {
            currentRegion = null;
            currentStaircases = List.of();
            currentSeed = null;
            return;
        }
        long start = System.currentTimeMillis();
        currentSeed = seed.immutable();
        SpaceRegion raw = SpaceExplorer.explore(level, seed,
                MAX_EXPLORE_BLOCKS, MAX_WALL_THICKNESS, MAX_HOLE_SIZE);
        currentRegion = SpaceAnalyzer.classify(level, raw, MIN_ROOM_VOLUME);
        currentStaircases = StairAnalyzer.detect(level, currentRegion, MIN_STAIRCASE_STEPS);
        lastExploreTimeMs = System.currentTimeMillis() - start;
    }

    public void reset() {
        enabled = false;
        currentRegion = null;
        currentStaircases = List.of();
        currentSeed = null;
        lastExploreTimeMs = 0;
    }
}
