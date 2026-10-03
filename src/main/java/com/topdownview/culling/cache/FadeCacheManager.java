package com.topdownview.culling.cache;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * 遷移フェードの対象となる「今カリングされている位置」の集合を保持する。
 * 走査ごとに作り直されるため α値は保持せず、遷移(消失/復元)の判定は
 * {@link com.topdownview.culling.FadeTransitionController} が行う。
 */
public final class FadeCacheManager {

    /** フェード集合の上限(ハンドラ側の登録打ち切り判定と共有)。 */
    public static final int MAX_FADE_POSITIONS = 4000;

    private final LongOpenHashSet fadePositions = new LongOpenHashSet(500);

    public void addFadePosition(long posLong) {
        if (fadePositions.size() < MAX_FADE_POSITIONS) {
            fadePositions.add(posLong);
        }
    }

    public boolean isFull() {
        return fadePositions.size() >= MAX_FADE_POSITIONS;
    }

    public LongOpenHashSet getFadePositions() {
        return fadePositions;
    }

    public void clear() {
        fadePositions.clear();
    }
}
