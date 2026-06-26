package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;

/**
 * フェード用のアルファ値キャッシュおよびフェード表示対象ブロックリストを管理するマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 */
public final class FadeCacheManager {

    private static final int MAX_FADE_ALPHA_CACHE_SIZE = 2000;
    private static final int MAX_FADE_BLOCKS_CACHE_SIZE = 4000;

    private final Long2FloatOpenHashMap fadeAlphaCache = new Long2FloatOpenHashMap(500);
    private final Long2FloatOpenHashMap fadeBlocksCache = new Long2FloatOpenHashMap(500);

    public FadeCacheManager() {
        fadeBlocksCache.defaultReturnValue(-1.0f);
    }

    public synchronized Float getFadeAlpha(long posLong) {
        if (fadeAlphaCache.containsKey(posLong)) {
            return fadeAlphaCache.get(posLong);
        }
        return null;
    }

    public synchronized void putFadeAlpha(long posLong, float alpha) {
        fadeAlphaCache.put(posLong, alpha);
        if (fadeAlphaCache.size() > MAX_FADE_ALPHA_CACHE_SIZE) {
            fadeAlphaCache.clear();
        }
    }

    public synchronized void putFadeBlock(long posLong, float alpha) {
        fadeBlocksCache.put(posLong, alpha);
    }

    public synchronized Long2FloatMap getFadeBlocksCache() {
        return fadeBlocksCache;
    }

    public synchronized void clearFadeBlocks() {
        fadeBlocksCache.clear();
    }

    public synchronized void clear() {
        fadeAlphaCache.clear();
        fadeBlocksCache.clear();
    }

    public synchronized boolean isFadeBlocksFull() {
        return fadeBlocksCache.size() >= MAX_FADE_BLOCKS_CACHE_SIZE;
    }
}
