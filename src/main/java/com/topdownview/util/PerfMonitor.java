package com.topdownview.util;

import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;

/**
 * カリング・描画パイプラインの簡易プロファイラ。
 *
 * <p>フレーム時間と各フェーズの合計/最大所要時間を蓄積し、5秒ごとにサマリを出力する。
 * 単発のスパイク(カクつき)は検出時に即時ログするので、FPS低下が描画(mod render)由来か、
 * ロジック(ティック/空間判定)由来か、チャンク再構築由来かを切り分けられる。
 *
 * <p>タイマーは記録側スレッドから単純加算するだけなので同期しない。1フェーズにつき
 * 1スレッド(通常はティックまたは描画スレッド)からの記録に限定すること。チャンク構築
 * ワーカーから呼ばれる {@link #IS_BLOCK_CULLED} 等のカウンタのみ {@link LongAdder} を使う。
 */
public final class PerfMonitor {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** サマリの出力間隔。 */
    private static final long WINDOW_NANOS = 5_000_000_000L;
    /** これ以上のフレームを「カクつき」として即時ログする。 */
    private static final long SPIKE_NANOS = 40_000_000L;
    /** スパイクログの最短間隔(ログI/O自体の負荷を避ける)。 */
    private static final long SPIKE_LOG_INTERVAL_NANOS = 500_000_000L;
    /** 40FPSを下回るフレーム。 */
    private static final long FRAME_DROP_NANOS = 25_000_000L;
    /** 100ms超えのプチフリ。 */
    private static final long FRAME_FREEZE_NANOS = 100_000_000L;

    // ==================== フェーズ別タイマー ====================
    /** TopDownCuller.update 全体。 */
    public static final Timer CULL_UPDATE = new Timer();
    /** TopDownCuller.getFadeBlocks (フェード集合の収集)。 */
    public static final Timer FADE_COLLECT = new Timer();
    /** updateEntityCulling。 */
    public static final Timer ENTITY_CULL = new Timer();
    /** TranslucentBlockRenderer.renderFadeBlocks。 */
    public static final Timer FADE_RENDER = new Timer();
    /** ハイライト/設置プレビュー等の3Dオーバーレイ描画。 */
    public static final Timer OVERLAY_RENDER = new Timer();
    /** CullingManager の Embeddium 再構築呼び出し。 */
    public static final Timer CHUNK_REBUILD = new Timer();

    /** 空間判定全体 (flood + segment + 壁距離スキャン)。 */
    public static final Timer PROBE = new Timer();
    /** RoomFloodFill.compute。 */
    public static final Timer FLOOD = new Timer();
    /** RoomSegmentation.analyze。 */
    public static final Timer SEGMENT = new Timer();
    /** StairAnalyzer.detect を含む階段ハンドラー更新。 */
    public static final Timer STAIR = new Timer();
    /** CeilingSliceCuller.update。 */
    public static final Timer CEILING = new Timer();
    /** LadderCullingHandler.scan。 */
    public static final Timer LADDER = new Timer();
    /** CoverCullingHandler.update。 */
    public static final Timer COVER = new Timer();

    // ==================== カウンタ ====================
    /** isBlockCulled の呼び出し回数(チャンク構築ワーカー含む)。 */
    public static final LongAdder IS_BLOCK_CULLED = new LongAdder();
    /** Embeddium へ要求したチャンク再構築回数。 */
    public static final LongAdder CHUNK_REBUILDS = new LongAdder();
    /** うち探索キャッシュ全域へ広げた(コーン変化/差分不明)再構築の回数。 */
    public static final LongAdder CHUNK_REBUILDS_WIDE = new LongAdder();
    /** 再構築ボックスの合計セクション数(再構築規模の目安)。 */
    public static final LongAdder CHUNK_REBUILD_SECTIONS = new LongAdder();
    /** フェード描画対象ブロック数の合計。 */
    private static final LongAdder FADE_BLOCKS = new LongAdder();

    // ==================== フレーム統計(ウィンドウ内) ====================
    private static long windowStart = System.nanoTime();
    private static long lastFrameNanos = 0L;
    private static long lastSpikeLogNanos = 0L;
    private static int frameCount;
    private static long frameTotalNanos;
    private static long frameMaxNanos;
    private static int dropFrames;
    private static int freezeFrames;

    private PerfMonitor() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /** 毎フレーム先頭で呼ぶ。前フレームとの間隔を計測し、スパイク/サマリを判定する。 */
    public static void onFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            long dt = now - lastFrameNanos;
            frameCount++;
            frameTotalNanos += dt;
            if (dt > frameMaxNanos) {
                frameMaxNanos = dt;
            }
            if (dt > FRAME_DROP_NANOS) {
                dropFrames++;
            }
            if (dt > FRAME_FREEZE_NANOS) {
                freezeFrames++;
            }
            if (dt > SPIKE_NANOS && now - lastSpikeLogNanos >= SPIKE_LOG_INTERVAL_NANOS) {
                lastSpikeLogNanos = now;
                LOGGER.info("[TopDownView][Perf] SPIKE frame={}ms | render fade={}ms overlay={}ms | "
                                + "tick cull={}ms probe={}ms | chunk rebuild={}ms ({} calls) | isBlockCulled={}",
                        f1(dt / 1.0E6), FADE_RENDER, OVERLAY_RENDER, CULL_UPDATE, PROBE,
                        CHUNK_REBUILD, CHUNK_REBUILDS.sum(), IS_BLOCK_CULLED.sum());
            }
        }
        lastFrameNanos = now;

        if (now - windowStart >= WINDOW_NANOS) {
            logAndReset(now);
        }
    }

    private static void logAndReset(long now) {
        double elapsedSec = (now - windowStart) / 1.0E9;
        double fps = elapsedSec > 0.0 ? frameCount / elapsedSec : 0.0;
        double avgFrameMs = frameCount == 0 ? 0.0 : frameTotalNanos / 1.0E6 / frameCount;

        LOGGER.info("[TopDownView][Perf] fps={} frame avg={}ms max={}ms | drop(>25ms)={} freeze(>100ms)={} frames={}",
                f1(fps), f1(avgFrameMs), f1(frameMaxNanos / 1.0E6), dropFrames, freezeFrames, frameCount);
        LOGGER.info("[TopDownView][Perf] render fade={} collect={} overlay={}ms | tick cull={} entity={}ms | "
                        + "space probe={} flood={} seg={} ceiling={} stair={} ladder={} cover={} | "
                        + "chunk rebuild={} (wide={}) ({}) sections={} | isBlockCulled={} fadeBlocks={}",
                FADE_RENDER, FADE_COLLECT, OVERLAY_RENDER, CULL_UPDATE, ENTITY_CULL,
                PROBE, FLOOD, SEGMENT, CEILING, STAIR, LADDER, COVER,
                CHUNK_REBUILDS.sum(), CHUNK_REBUILDS_WIDE.sum(), CHUNK_REBUILD, CHUNK_REBUILD_SECTIONS.sum(),
                IS_BLOCK_CULLED.sum(), FADE_BLOCKS.sum());

        resetAll();
        windowStart = now;
    }

    private static void resetAll() {
        CULL_UPDATE.reset();
        FADE_COLLECT.reset();
        ENTITY_CULL.reset();
        FADE_RENDER.reset();
        OVERLAY_RENDER.reset();
        CHUNK_REBUILD.reset();
        PROBE.reset();
        FLOOD.reset();
        SEGMENT.reset();
        STAIR.reset();
        CEILING.reset();
        LADDER.reset();
        COVER.reset();
        IS_BLOCK_CULLED.reset();
        CHUNK_REBUILDS.reset();
        CHUNK_REBUILDS_WIDE.reset();
        CHUNK_REBUILD_SECTIONS.reset();
        FADE_BLOCKS.reset();
        frameCount = 0;
        frameTotalNanos = 0L;
        frameMaxNanos = 0L;
        dropFrames = 0;
        freezeFrames = 0;
    }

    /** フェード描画対象数(overlay/ログ用)。 */
    public static void recordFadeBlocks(int count) {
        FADE_BLOCKS.add(count);
    }

    // ==================== オーバーレイ用の読み取り ====================

    public static double getWindowFps() {
        long elapsed = System.nanoTime() - windowStart;
        return elapsed > 0 ? frameCount / (elapsed / 1.0E9) : 0.0;
    }

    public static double getWindowAvgFrameMs() {
        return frameCount == 0 ? 0.0 : frameTotalNanos / 1.0E6 / frameCount;
    }

    public static double getWindowMaxFrameMs() {
        return frameMaxNanos / 1.0E6;
    }

    public static int getDropFrames() {
        return dropFrames;
    }

    public static int getFreezeFrames() {
        return freezeFrames;
    }

    public static long getCulledCallCount() {
        return IS_BLOCK_CULLED.sum();
    }

    public static long getChunkRebuildCount() {
        return CHUNK_REBUILDS.sum();
    }

    private static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 単一フェーズの集計。記録側スレッドからのみ触る。 */
    public static final class Timer {
        private long totalNanos;
        private long maxNanos;
        private long lastNanos;
        private int count;

        public void add(long nanos) {
            totalNanos += nanos;
            lastNanos = nanos;
            count++;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        public double avgMs() {
            return count == 0 ? 0.0 : totalNanos / 1.0E6 / count;
        }

        public double maxMs() {
            return maxNanos / 1.0E6;
        }

        public double lastMs() {
            return lastNanos / 1.0E6;
        }

        public int count() {
            return count;
        }

        void reset() {
            totalNanos = 0L;
            maxNanos = 0L;
            lastNanos = 0L;
            count = 0;
        }

        @Override
        public String toString() {
            return count == 0 ? "-" : String.format(Locale.ROOT, "%.2f/%.2f", avgMs(), maxMs());
        }
    }
}
