package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;

/**
 * フェード用のアルファ値キャッシュおよびフェード表示対象ブロックリストを管理するマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 * マルチスレッドでのロック競合を防ぐため、ThreadLocal とエポックベース無効化を使用します。
 */
public final class FadeCacheManager {

    private static final int MAX_FADE_ALPHA_CACHE_SIZE = 2000;
    private static final int MAX_FADE_BLOCKS_CACHE_SIZE = 4000;

    // アルファ値キャッシュ用エポック（世代）
    private volatile int alphaEpoch = 0;

    // スレッドごとのアルファ値キャッシュ
    private static class ThreadLocalAlphaCache {
        final Long2FloatOpenHashMap cache = new Long2FloatOpenHashMap(500);
        int epoch = -1;
    }

    private final ThreadLocal<ThreadLocalAlphaCache> threadLocalAlphaCache = ThreadLocal.withInitial(ThreadLocalAlphaCache::new);

    // 描画ブロックキャッシュ（メイン/レンダスレッドのみからアクセスされるため synchronized なしで直接保持）
    private final Long2FloatOpenHashMap fadeBlocksCache = new Long2FloatOpenHashMap(500);

    public FadeCacheManager() {
        fadeBlocksCache.defaultReturnValue(-1.0f);
    }

    private ThreadLocalAlphaCache getLocalAlphaCache() {
        ThreadLocalAlphaCache local = threadLocalAlphaCache.get();
        int globalEpoch = alphaEpoch;
        if (local.epoch != globalEpoch) {
            local.cache.clear();
            local.epoch = globalEpoch;
        }
        return local;
    }

    public Float getFadeAlpha(long posLong) {
        ThreadLocalAlphaCache local = getLocalAlphaCache();
        if (local.cache.containsKey(posLong)) {
            return local.cache.get(posLong);
        }
        return null;
    }

    public void putFadeAlpha(long posLong, float alpha) {
        ThreadLocalAlphaCache local = getLocalAlphaCache();
        local.cache.put(posLong, alpha);
        if (local.cache.size() > MAX_FADE_ALPHA_CACHE_SIZE) {
            local.cache.clear();
        }
    }

    public void putFadeBlock(long posLong, float alpha) {
        fadeBlocksCache.put(posLong, alpha);
    }

    public Long2FloatMap getFadeBlocksCache() {
        return fadeBlocksCache;
    }

    public void clearFadeBlocks() {
        fadeBlocksCache.clear();
    }

    public void clear() {
        // エポックをインクリメントして全スレッドのキャッシュを無効化
        alphaEpoch++;
        fadeBlocksCache.clear();
    }

    public boolean isFadeBlocksFull() {
        return fadeBlocksCache.size() >= MAX_FADE_BLOCKS_CACHE_SIZE;
    }
}
