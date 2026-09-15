package com.topdownview.culling;

import com.topdownview.spatial.BlockMap;
import com.topdownview.spatial.BuildingClassifier;
import com.topdownview.spatial.BuildingClassifier.Label;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * 屋内空間の屋根(天井)ブロックのカリング処理を担うハンドラー。
 *
 * <p>{@link BuildingClassifier} が {@link Label#ROOF} と分類したセルを全てカリング対象にする。
 * 空気直上の1層だけでなく厚い屋根の上層や、連結空間内の別部屋の屋根も含めて消えるため、
 * トップダウン視点から建物内部まで見通せる。さらに屋根に上下方向で連続する固体ブロックを
 * 1層ずつ取り込み、切り抜きの縁に残る段差を取り除く。
 */
public final class CeilingCullingHandler {
    private final LongOpenHashSet ceilingCullPositions = new LongOpenHashSet();
    private final LongArrayList roofCells = new LongArrayList();

    public void clearCache() {
        ceilingCullPositions.clear();
        roofCells.clear();
    }

    public boolean isCeilingBlock(long posLong) {
        return !ceilingCullPositions.isEmpty() && ceilingCullPositions.contains(posLong);
    }

    public void update(boolean currentSpaceEnclosed, BuildingClassifier.Result classification, BlockMap blockMap) {
        ceilingCullPositions.clear();
        roofCells.clear();
        if (!currentSpaceEnclosed || classification == null || !classification.isValid()) {
            return;
        }

        var it = classification.getLabels().long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() == Label.ROOF) {
                long pos = entry.getLongKey();
                ceilingCullPositions.add(pos);
                roofCells.add(pos);
            }
        }

        // 分類は内部空気に接する面から厚さ T_MAX までしか届かず、屋根の最上層/最下層に接する
        // 固体1ブロックが残ると切り抜きの縁に段差が見える。上下へ1層だけ連続固体を取り込む。
        if (blockMap == null) {
            return;
        }
        for (int i = 0, n = roofCells.size(); i < n; i++) {
            long b = roofCells.getLong(i);
            int x = BlockPos.getX(b);
            int y = BlockPos.getY(b);
            int z = BlockPos.getZ(b);
            if (blockMap.isSolid(x, y + 1, z)) {
                ceilingCullPositions.add(BlockPos.asLong(x, y + 1, z));
            }
            if (blockMap.isSolid(x, y - 1, z)) {
                ceilingCullPositions.add(BlockPos.asLong(x, y - 1, z));
            }
        }
    }
}
