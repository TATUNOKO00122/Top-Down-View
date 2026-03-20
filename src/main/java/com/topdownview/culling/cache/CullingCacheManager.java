package com.topdownview.culling.cache;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CullingCacheManager {

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;

    private final Map<BlockPos, Boolean> cache = new ConcurrentHashMap<>(INITIAL_CAPACITY);
    private final AtomicInteger culledCount = new AtomicInteger(0);

    public CullingCacheManager() {
    }

    public Boolean get(BlockPos pos) {
        return cache.get(pos);
    }

    public void put(BlockPos pos, boolean result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
            culledCount.set(0);
        }
        Boolean oldValue = cache.put(pos.immutable(), result);
        if (oldValue != null) {
            if (oldValue && !result) {
                culledCount.decrementAndGet();
            } else if (!oldValue && result) {
                culledCount.incrementAndGet();
            }
        } else if (result) {
            culledCount.incrementAndGet();
        }
    }

    public void clear() {
        cache.clear();
        culledCount.set(0);
    }

    public int size() {
        return cache.size();
    }

    public int getCulledCount() {
        return culledCount.get();
    }
}