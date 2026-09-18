package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * カリング結果をキャッシュするマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 */
public final class CullingCacheManager extends EpochCache<Long2BooleanOpenHashMap> {

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;

    public CullingCacheManager() {
        super(() -> new Long2BooleanOpenHashMap(INITIAL_CAPACITY));
    }

    public Boolean get(long posLong) {
        Long2BooleanOpenHashMap cache = map();
        if (cache.containsKey(posLong)) {
            return cache.get(posLong);
        }
        return null;
    }

    public void put(long posLong, boolean result) {
        Long2BooleanOpenHashMap cache = map();
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
        }
        cache.put(posLong, result);
    }

    @Override
    protected void clearMap(Long2BooleanOpenHashMap cache) {
        cache.clear();
    }
}
