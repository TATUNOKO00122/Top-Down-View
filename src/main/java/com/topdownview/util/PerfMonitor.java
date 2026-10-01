package com.topdownview.util;

import com.mojang.logging.LogUtils;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
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
    /** これ以上フレームが空いたら計測を仕切り直す(初回/ロード明け/モニター復帰)。 */
    private static final long RESUME_GAP_NANOS = 1_000_000_000L;
    /** 高頻度のブロック判定回数は1/256を標本計数して推定する。 */
    private static final int BLOCK_CULL_SAMPLE_SHIFT = 8;
    private static final long BLOCK_CULL_SAMPLE_MASK = (1L << BLOCK_CULL_SAMPLE_SHIFT) - 1L;
    private static final long BLOCK_CULL_SAMPLE_MIX = 0x9E3779B97F4A7C15L;

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
    /** Minecraft.tick 全体(描画スレッド)。スパイクがtick/描画どちら由来かの切り分け用。 */
    public static final Timer TICK_TOTAL = new Timer();
    /** RenderSectionManager.uploadChunks (メッシュのGPUアップロード)。 */
    public static final Timer CHUNK_UPLOAD = new Timer();
    /** RenderSectionManager.processChunkBuildResults (ビルド結果の反映/保留処理)。 */
    public static final Timer CHUNK_PROCESS = new Timer();

    // ==================== カウンタ ====================
    /** isBlockCulled の推定呼び出し回数(チャンク構築ワーカー含む)。 */
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
    /** GC の累積コレクション時間(ms)。前フレームからの増分でそのフレームのGC時間を推定する。 */
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private static long lastGcTimeMs = totalGcTimeMs();
    private static long lastGcDeltaMs;
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

        // 初回・ロード画面明け・モニター無効化からの復帰は、離散的な巨大フレームを統計に混ぜないよう仕切り直す
        if (lastFrameNanos == 0L || now - lastFrameNanos > RESUME_GAP_NANOS) {
            resetAll();
            windowStart = now;
            lastFrameNanos = now;
            return;
        }

        long dt = now - lastFrameNanos;
        long gcNow = totalGcTimeMs();
        lastGcDeltaMs = gcNow - lastGcTimeMs;
        lastGcTimeMs = gcNow;
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
            LOGGER.info("[TopDownView][Perf] SPIKE frame={}ms gc={}ms | tick total={}ms cull={}ms probe={}ms | "
                            + "render fade={}ms overlay={}ms | chunk upload={}ms process={}ms rebuild={}ms ({} calls) "
                            + "| isBlockCulled~={}",
                    f1(dt / 1.0E6), lastGcDeltaMs, TICK_TOTAL, CULL_UPDATE, PROBE,
                    FADE_RENDER, OVERLAY_RENDER, CHUNK_UPLOAD, CHUNK_PROCESS, CHUNK_REBUILD,
                    CHUNK_REBUILDS.sum(), IS_BLOCK_CULLED.sum());
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
        LOGGER.info("[TopDownView][Perf] render fade={} collect={} overlay={}ms | tick total={} cull={} entity={}ms | "
                        + "space probe={} flood={} seg={} ceiling={} stair={} ladder={} cover={} | "
                        + "chunk rebuild={} (wide={}) ({}) sections={} upload={} process={} "
                        + "| isBlockCulled~={} fadeBlocks={}",
                FADE_RENDER, FADE_COLLECT, OVERLAY_RENDER, TICK_TOTAL, CULL_UPDATE, ENTITY_CULL,
                PROBE, FLOOD, SEGMENT, CEILING, STAIR, LADDER, COVER,
                CHUNK_REBUILDS.sum(), CHUNK_REBUILDS_WIDE.sum(), CHUNK_REBUILD, CHUNK_REBUILD_SECTIONS.sum(),
                CHUNK_UPLOAD, CHUNK_PROCESS, IS_BLOCK_CULLED.sum(), FADE_BLOCKS.sum());

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
        TICK_TOTAL.reset();
        CHUNK_UPLOAD.reset();
        CHUNK_PROCESS.reset();
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

    /** GC の累積コレクション時間(ms)。 */
    private static long totalGcTimeMs() {
        long total = 0L;
        for (int i = 0, n = GC_BEANS.size(); i < n; i++) {
            total += GC_BEANS.get(i).getCollectionTime();
        }
        return total;
    }

    /**
     * 高頻度カリング問い合わせを標本計数する。位置ハッシュを使い、ブロック単位の出力は変えず
     * LongAdder への更新回数を減らす。
     */
    public static void recordBlockCullSample(long posLong) {
        long sampleKey = posLong * BLOCK_CULL_SAMPLE_MIX;
        sampleKey ^= sampleKey >>> 32;
        sampleKey ^= sampleKey >>> 16;
        if ((sampleKey & BLOCK_CULL_SAMPLE_MASK) == 0L) {
            IS_BLOCK_CULLED.add(1L << BLOCK_CULL_SAMPLE_SHIFT);
        }
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

    /** 標本計数から算出した isBlockCulled 呼び出し回数の推定値。 */
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
