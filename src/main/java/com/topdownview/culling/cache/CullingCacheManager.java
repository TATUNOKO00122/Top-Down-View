package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * カリング結果をキャッシュするマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 * マルチスレッドでのロック競合を防ぐため、ThreadLocal とエポックベース無効化を使用します。
 */
public final class CullingCacheManager {

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;

    // キャッシュ無効化用エポック（世代）
    private volatile int currentEpoch = 0;

    // スレッドごとのキャッシュ
    private static class ThreadLocalCache {
        final Long2BooleanOpenHashMap cache = new Long2BooleanOpenHashMap(INITIAL_CAPACITY);
        int epoch = -1;
    }

    private final ThreadLocal<ThreadLocalCache> threadLocalCache = ThreadLocal.withInitial(ThreadLocalCache::new);

    public CullingCacheManager() {
    }

    private ThreadLocalCache getLocalCache() {
        ThreadLocalCache local = threadLocalCache.get();
        int globalEpoch = currentEpoch;
        if (local.epoch != globalEpoch) {
            local.cache.clear();
            local.epoch = globalEpoch;
        }
        return local;
    }

    public Boolean get(long posLong) {
        ThreadLocalCache local = getLocalCache();
        if (local.cache.containsKey(posLong)) {
            return local.cache.get(posLong);
        }
        return null;
    }

    public void put(long posLong, boolean result) {
        ThreadLocalCache local = getLocalCache();
        if (local.cache.size() >= MAX_CACHE_SIZE) {
            local.cache.clear();
        }
        local.cache.put(posLong, result);
    }

    public void clear() {
        // エポックをインクリメントして全スレッドのキャッシュを無効化
        currentEpoch++;
    }

    public int size() {
        return threadLocalCache.get().cache.size();
    }

    public int getCulledCount() {
        return 0;
    }
}