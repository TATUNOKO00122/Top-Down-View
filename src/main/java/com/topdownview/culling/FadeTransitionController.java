package com.topdownview.culling;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * フェードブロック状態のタイマー引き継ぎ(Transition Handoff)を管理するコントローラー。
 */
public final class FadeTransitionController {
    private final LongOpenHashSet previousFadedPositions = new LongOpenHashSet();
    private final LongOpenHashSet currentFadedPositions = new LongOpenHashSet();
    private final Long2IntMap transitionHandoffBlocks = new Long2IntOpenHashMap();
    private static final int HANDOFF_TICKS = 3;

    public void clearCache() {
        previousFadedPositions.clear();
        currentFadedPositions.clear();
        transitionHandoffBlocks.clear();
    }

    public boolean hasActiveHandoffs() {
        return !transitionHandoffBlocks.isEmpty();
    }

    public void onStartCollection() {
        currentFadedPositions.clear();
    }

    public void onBlockFaded(long posLong) {
        currentFadedPositions.add(posLong);
    }

    public boolean isHandoffActive(long posLong) {
        return previousFadedPositions.contains(posLong) || transitionHandoffBlocks.containsKey(posLong);
    }

    public void activateHandoff(long posLong) {
        if (!transitionHandoffBlocks.containsKey(posLong)) {
            transitionHandoffBlocks.put(posLong, HANDOFF_TICKS);
        }
    }

    public void onEndCollection() {
        if (!transitionHandoffBlocks.isEmpty()) {
            var iterator = transitionHandoffBlocks.long2IntEntrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                long posLong = entry.getLongKey();
                if (currentFadedPositions.contains(posLong)) {
                    iterator.remove();
                    continue;
                }
                int remaining = entry.getIntValue() - 1;
                if (remaining <= 0) {
                    iterator.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        previousFadedPositions.clear();
        previousFadedPositions.addAll(currentFadedPositions);
        currentFadedPositions.clear();
    }
}
