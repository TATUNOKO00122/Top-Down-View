package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 地下カリング用に、ブロック列ごとの地表Yをキャッシュする。
 * チャンクビルドはSodiumのワーカースレッドから並列に呼ばれるため、
 * ThreadLocalでスレッドごとに保持しロック競合を避ける。
 */
public final class SurfaceHeightCache {

    /** 未ロード、またはHeightmap未生成の列。有効なY（-64以上）と重複しないセンチネル。 */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    private static final int MAX_CACHE_SIZE = 1 << 15;

    private volatile int epoch = 0;

    private static final class LocalCache {
        final Long2IntOpenHashMap heights = new Long2IntOpenHashMap(1024);
        int epoch = -1;
    }

    private final ThreadLocal<LocalCache> threadLocalCache = ThreadLocal.withInitial(LocalCache::new);

    /**
     * 指定列の最上位ブロックYを返す。未ロード場合は {@link #UNKNOWN}。
     */
    public int getSurfaceY(int x, int z) {
        LocalCache local = threadLocalCache.get();
        int globalEpoch = epoch;
        if (local.epoch != globalEpoch) {
            local.heights.clear();
            local.epoch = globalEpoch;
        }

        long key = ((long) z << 32) | (x & 0xFFFFFFFFL);
        if (local.heights.containsKey(key)) {
            return local.heights.get(key);
        }

        int surfaceY = compute(x, z);
        if (surfaceY != UNKNOWN) {
            if (local.heights.size() >= MAX_CACHE_SIZE) {
                local.heights.clear();
            }
            local.heights.put(key, surfaceY);
        }
        return surfaceY;
    }

    private static int compute(int x, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return UNKNOWN;
        }
        ChunkAccess chunk = level.getChunk(
                SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z),
                ChunkStatus.FULL, false);
        if (chunk == null || !chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE)) {
            // 未ロード列やHeightmap未生成をワーカースレッドから生成しない（安全側でカリング無効）
            return UNKNOWN;
        }
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    /** 既存キャッシュを無効化する（ディメンション変更・設定変更時）。 */
    public void clear() {
        epoch++;
    }
}
