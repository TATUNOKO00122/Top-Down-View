package com.topdownview.culling.cache;

import net.minecraft.core.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CullingCacheManager {

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;
    private static final float LOAD_FACTOR = 0.75f;

    private final Map<BlockPos, Boolean> cache;
    private int culledCount = 0;

    public CullingCacheManager() {
        cache = new LinkedHashMap<BlockPos, Boolean>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                if (size() > MAX_CACHE_SIZE) {
                    if (eldest.getValue()) {
                        culledCount--;
                    }
                    return true;
                }
                return false;
            }
        };
    }

    public Boolean get(BlockPos pos) {
        return cache.get(pos);
    }

    public void put(BlockPos pos, boolean result) {
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