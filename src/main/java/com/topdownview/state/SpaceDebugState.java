package com.topdownview.state;

import com.topdownview.spatial.BlockMap;
import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.RoomSegmentation;
import com.topdownview.spatial.SpaceProbe;
import com.topdownview.spatial.Staircase;
import java.util.List;
import net.minecraft.core.BlockPos;

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
    private SpaceProbe.Result currentRawResult = null;
    private BuildingClassifier.Result currentClassification = BuildingClassifier.Result.EMPTY;
    private RoomSegmentation.Result currentSegmentation = RoomSegmentation.Result.EMPTY;
    private List<Staircase> currentStaircases = List.of();
    private BlockPos currentSeed = null;

    private SpaceDebugState() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            currentResult = null;
            currentRawResult = null;
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

    /** ヒステリシス適用前の生のプローブ結果 (カリングに未反映の値)。未プローブなら null。 */
    public SpaceProbe.Result getRawResult() {
        return currentRawResult;
    }

    /**
     * TopDownCuller が受理した空間プローブ結果を採り込む。
     *
     * <p>デバッグ独自のプローブを持たず、カリング実体と同じ結果・同じヒステリシスを
     * 表示するための単一化ポイント。結果オブジェクトはプローブの受理ごとに新しくなるため、
     * 参照一致で変化を検出し、変化のないフレームの再分類を避ける。
     *
     * @param rawResult     ヒステリシス適用前の生のプローブ結果
     * @param appliedResult カリングが採用した結果 (ヒステリシス適用後)。未確定なら null
     * @param blockMap      appliedResult のプローブで構築されたブロック判定キャッシュ
     * @param staircases    階段ハンドラが検出した階段一覧
     */
    public void adopt(SpaceProbe.Result rawResult, SpaceProbe.Result appliedResult,
            BlockMap blockMap, List<Staircase> staircases) {
        if (!enabled) {
            return;
        }
        currentRawResult = rawResult;
        if (appliedResult == currentResult) {
            return;
        }
        currentResult = appliedResult;
        currentSeed = appliedResult != null ? appliedResult.getOrigin() : null;
        currentClassification = (appliedResult != null)
                ? BuildingClassifier.classify(appliedResult.getRoomResult(), blockMap)
                : BuildingClassifier.Result.EMPTY;
        currentSegmentation = appliedResult != null
                ? appliedResult.getSegmentation()
                : RoomSegmentation.Result.EMPTY;
        currentStaircases = staircases != null ? List.copyOf(staircases) : List.of();
    }

    public void reset() {
        enabled = false;
        currentResult = null;
        currentRawResult = null;
        currentClassification = BuildingClassifier.Result.EMPTY;
        currentSegmentation = RoomSegmentation.Result.EMPTY;
        currentStaircases = List.of();
        currentSeed = null;
    }
}
