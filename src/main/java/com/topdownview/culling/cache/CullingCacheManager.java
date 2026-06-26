package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * カリング結果をキャッシュするマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 * Embeddium のマルチスレッドアクセスに対応するため、synchronized で同期化を行います。
 */
public final class CullingCacheManager {

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;

    private final Long2BooleanOpenHashMap cache = new Long2BooleanOpenHashMap(INITIAL_CAPACITY);

    public CullingCacheManager() {
    }

    public synchronized Boolean get(long posLong) {
        if (cache.containsKey(posLong)) {
            return cache.get(posLong);
        }
        return null;
    }

    public synchronized void put(long posLong, boolean result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
        }
        cache.put(posLong, result);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public int getCulledCount() {
        return 0; // パフォーマンス悪化を防ぐため AtomicInteger 追跡は廃止（ダミー値を返却）
    }
}