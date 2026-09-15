package com.topdownview.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 空間検出パイプラインの簡易プロファイラ。
 *
 * <p>各フェーズの合計・最大所要時間を蓄積し、5秒ごとに平均/最大をログ出力する。
 * どこが重いかを切り分けるための計測用。呼び出しは 1 tick = 1 スレッドから行うこと
 * (フィールドは同期しない)。
 */
public final class SpaceProfiler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long WINDOW_NANOS = 5_000_000_000L;

    /** 空間判定全体 (flood + segment + 壁距離スキャン)。 */
    public static final Timer PROBE = new Timer();
    /** RoomFloodFill.compute。 */
    public static final Timer FLOOD = new Timer();
    /** RoomSegmentation.analyze。 */
    public static final Timer SEGMENT = new Timer();
    /** BuildingClassifier.classify。 */
    public static final Timer CLASSIFY = new Timer();
    /** StairAnalyzer.detect を含む階段ハンドラー更新。 */
    public static final Timer STAIR = new Timer();
    /** CeilingCullingHandler.update。 */
    public static final Timer CEILING = new Timer();
    /** LadderCullingHandler.scan。 */
    public static final Timer LADDER = new Timer();
    /** WallCullingHandler.updateClassification。 */
    public static final Timer WALL = new Timer();
    /** CoverCullingHandler.update。 */
    public static final Timer COVER = new Timer();

    private static long windowStart = System.nanoTime();

    private SpaceProfiler() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** ウィンドウ経過時に集計をログ出力する。毎tick呼び出す。 */
    public static void tick() {
        long now = System.nanoTime();
        if (now - windowStart < WINDOW_NANOS) {
            return;
        }
        if (PROBE.count > 0) {
            LOGGER.info("[TopDownView][SpacePerf] calls={} (avg/max ms) | probe={} flood={} segment={} "
                            + "classify={} stair={} ceiling={} ladder={} wall={} cover={}",
                    PROBE.count, PROBE, FLOOD, SEGMENT, CLASSIFY, STAIR, CEILING, LADDER, WALL, COVER);
        }
        PROBE.reset();
        FLOOD.reset();
        SEGMENT.reset();
        CLASSIFY.reset();
        STAIR.reset();
        CEILING.reset();
        LADDER.reset();
        WALL.reset();
        COVER.reset();
        windowStart = now;
    }

    /** 単一フェーズの集計。 */
    public static final class Timer {
        private long totalNanos;
        private long maxNanos;
        private int count;

        public void add(long nanos) {
            totalNanos += nanos;
            count++;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        void reset() {
            totalNanos = 0L;
            maxNanos = 0L;
            count = 0;
        }

        @Override
        public String toString() {
            if (count == 0) {
                return "-";
            }
            return String.format("%.2f/%.2f", totalNanos / 1.0E6 / count, maxNanos / 1.0E6);
        }
    }
}
