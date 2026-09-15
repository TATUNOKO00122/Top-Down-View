package com.topdownview.culling;

import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.BuildingClassifier.Label;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * 屋内空間の屋根(天井)ブロックのカリング処理を担うハンドラー。
 *
 * <p>{@link BuildingClassifier} が {@link Label#ROOF} と分類したセルを全てカリング対象にする。
 * 空気直上の1層だけでなく厚い屋根の上層や、連結空間内の別部屋の屋根も含めて消えるため、
 * トップダウン視点から建物内部まで見通せる。
 */
public final class CeilingCullingHandler {
    private final LongOpenHashSet ceilingCullPositions = new LongOpenHashSet();

    public void clearCache() {
        ceilingCullPositions.clear();
    }

    public boolean isCeilingBlock(long posLong) {
        return !ceilingCullPositions.isEmpty() && ceilingCullPositions.contains(posLong);
    }

    public void update(boolean currentSpaceEnclosed, BuildingClassifier.Result classification) {
        ceilingCullPositions.clear();
        if (!currentSpaceEnclosed || classification == null || !classification.isValid()) {
            return;
        }

        var it = classification.getLabels().long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() == Label.ROOF) {
                ceilingCullPositions.add(entry.getLongKey());
            }
        }
    }
}
