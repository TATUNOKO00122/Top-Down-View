package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;

/**
 * フェード用のアルファ値キャッシュおよびフェード表示対象ブロックリストを管理するマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 */
public final class FadeCacheManager extends EpochCache<Long2FloatOpenHashMap> {

    private static final int MAX_FADE_ALPHA_CACHE_SIZE = 2000;
    private static final int MAX_FADE_BLOCKS_CACHE_SIZE = 4000;

    // 描画ブロックキャッシュ（メイン/レンダスレッドのみからアクセスされるため synchronized なしで直接保持）
    private final Long2FloatOpenHashMap fadeBlocksCache = new Long2FloatOpenHashMap(500);

    public FadeCacheManager() {
        super(() -> new Long2FloatOpenHashMap(500));
        fadeBlocksCache.defaultReturnValue(-1.0f);
    }

    public Float getFadeAlpha(long posLong) {
        Long2FloatOpenHashMap cache = map();
        if (cache.containsKey(posLong)) {
            return cache.get(posLong);
        }
        return null;
    }

    public void putFadeAlpha(long posLong, float alpha) {
        Long2FloatOpenHashMap cache = map();
        cache.put(posLong, alpha);
        if (cache.size() > MAX_FADE_ALPHA_CACHE_SIZE) {
            cache.clear();
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

    public boolean isFadeBlocksFull() {
        return fadeBlocksCache.size() >= MAX_FADE_BLOCKS_CACHE_SIZE;
    }

    @Override
    protected void clearMap(Long2FloatOpenHashMap cache) {
        cache.clear();
    }

    @Override
    protected void onClear() {
        fadeBlocksCache.clear();
    }
}
