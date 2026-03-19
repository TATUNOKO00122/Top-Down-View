package com.topdownview.culling.cache;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

public final class CullingCacheManager {

    private static final int MAX_CACHE_SIZE = 8000;

    private final Map<BlockPos, Boolean> cache = new HashMap<>(1000);
    private int culledCount = 0;

    public Boolean get(BlockPos pos) {
        return cache.get(pos);
    }

    public void put(BlockPos pos, boolean result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
            culledCount = 0;
        }
        Boolean oldValue = cache.put(pos.immutable(), result);
        if (oldValue != null) {
            if (oldValue && !result) {
                culledCount--;
            } else if (!oldValue && result) {
                culledCount++;
            }
        } else if (result) {
            culledCount++;
        }
    }

    public void clear() {
        cache.clear();
        culledCount = 0;
    }

    public int size() {
        return cache.size();
    }

    public int getCulledCount() {
        return culledCount;
    }
}
