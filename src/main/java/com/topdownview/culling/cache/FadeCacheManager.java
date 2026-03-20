package com.topdownview.culling.cache;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FadeCacheManager {

    private static final int MAX_FADE_ALPHA_CACHE_SIZE = 2000;
    private static final int MAX_FADE_BLOCKS_CACHE_SIZE = 2000;

    private final Map<BlockPos, Float> fadeAlphaCache = new ConcurrentHashMap<>(500);
    private final Map<BlockPos, Float> fadeBlocksCache = new ConcurrentHashMap<>(500);
    private final AtomicInteger fadeAlphaCacheSize = new AtomicInteger(0);

    public Float getFadeAlpha(BlockPos pos) {
        return fadeAlphaCache.get(pos);
    }

    public void putFadeAlpha(BlockPos pos, float alpha) {
        if (fadeAlphaCacheSize.incrementAndGet() > MAX_FADE_ALPHA_CACHE_SIZE) {
            fadeAlphaCache.clear();
            fadeAlphaCacheSize.set(1);
        }
        fadeAlphaCache.put(pos.immutable(), alpha);
    }

    public void putFadeBlock(BlockPos pos, float alpha) {
        fadeBlocksCache.put(pos.immutable(), alpha);
    }

    public Map<BlockPos, Float> getFadeBlocksCache() {
        return fadeBlocksCache;
    }

    public void clearFadeBlocks() {
        fadeBlocksCache.clear();
    }

    public void clear() {
        fadeAlphaCache.clear();
        fadeAlphaCacheSize.set(0);
        fadeBlocksCache.clear();
    }

    public boolean isFadeBlocksFull() {
        return fadeBlocksCache.size() >= MAX_FADE_BLOCKS_CACHE_SIZE;
    }
}
