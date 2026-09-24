package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

/**
 * カリング結果をキャッシュするマネージャ。
 * アロケーション削減のため、BlockPos の代わりに long (pos.asLong()) をキーとして使用します。
 * 値は byte で持ち、未登録は defaultReturnValue で表現する(単一プローブ・ボクシング無し)。
 */
public final class CullingCacheManager extends EpochCache<Long2ByteOpenHashMap> {

    /** キャッシュ未登録を表す番兵値。0=false, 1=true と重複しない。 */
    public static final byte UNKNOWN = 2;

    private static final int MAX_CACHE_SIZE = 8000;
    private static final int INITIAL_CAPACITY = 1000;

    public CullingCacheManager() {
        super(() -> {
            Long2ByteOpenHashMap map = new Long2ByteOpenHashMap(INITIAL_CAPACITY);
            map.defaultReturnValue(UNKNOWN);
            return map;
        });
    }

    /** 0=false, 1=true, {@link #UNKNOWN}=未登録。 */
    public byte get(long posLong) {
        return map().get(posLong);
    }

    public void put(long posLong, boolean result) {
        Long2ByteOpenHashMap cache = map();
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
        }
        cache.put(posLong, result ? (byte) 1 : (byte) 0);
    }

    @Override
    protected void clearMap(Long2ByteOpenHashMap cache) {
        cache.clear();
    }
}
