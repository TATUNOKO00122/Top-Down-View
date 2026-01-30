package com.topdownview.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 空間分類エンジン。
 *
 * <p>SpaceExplorer が探索した SpaceRegion（type=UNKNOWN）を受け取り、
 * 屋根・床の有無と空間形状から最終的な SpaceType を判定した
 * 新しい SpaceRegion を返す。
 *
 * <ul>
 *   <li>屋根なし → OUTDOOR</li>
 *   <li>屋根あり + 床なし → CAVE（浮遊空間）</li>
 *   <li>屋根あり + 床あり + 細長形状 → CORRIDOR</li>
 *   <li>屋根あり + 床あり + 部屋形状 → ROOM</li>
 *   <li>空間が小さすぎる（minRoomVolume 未満）→ UNKNOWN</li>
 * </ul>
 */
public final class SpaceAnalyzer {

    /** 屋根を探す上方スキャン距離 */
    private static final int ROOF_SCAN_HEIGHT = 4;
    /** 屋根/床判定のサンプリング上限 */
    private static final int SAMPLE_LIMIT = 30;

    private SpaceAnalyzer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * 空間を分類し、type を設定した新しい SpaceRegion を返す。
     *
     * @param level          ワールド（屋根/床判定に使用）
     * @param region         探索済み空間（type=UNKNOWN を想定）
     * @param minRoomVolume  部屋として認定する最小空気ブロック数
     */
    public static SpaceRegion classify(BlockGetter level, SpaceRegion region, int minRoomVolume) {
        if (!region.isValid()) {
            return region;
        }

        int airCount = region.getAirBlockCount();
        if (airCount < minRoomVolume) {
            return region.withType(SpaceType.UNKNOWN);
        }

        int dx = region.getMaxX() - region.getMinX() + 1;
        int dy = region.getMaxY() - region.getMinY() + 1;
        int dz = region.getMaxZ() - region.getMinZ() + 1;

        // 屋根・床のサンプリング判定
        int sampleStep = Math.max(1, airCount / SAMPLE_LIMIT);
        int sampled = 0, roofed = 0, floored = 0;
        int idx = 0;
        for (BlockPos airPos : region.getAirBlocks()) {
            if (idx++ % sampleStep != 0) continue;
            sampled++;
            if (hasRoofAbove(level, airPos)) roofed++;
            if (hasFloorBelow(level, airPos)) floored++;
        }

        boolean hasRoof = sampled > 0 && roofed * 2 >= sampled;
        boolean hasFloor = sampled > 0 && floored * 2 >= sampled;

        // 廊下判定：最大辺が最小辺の3倍以上かつ5ブロック以上
        int maxDim = Math.max(dx, Math.max(dy, dz));
        int minDim = Math.min(dx, Math.min(dy, dz));
        boolean isCorridor = maxDim >= minDim * 3 && maxDim >= 5;

        SpaceType type;
        if (!hasRoof) {
            type = SpaceType.OUTDOOR;
        } else if (!hasFloor) {
            type = SpaceType.CAVE;
        } else if (isCorridor) {
            type = SpaceType.CORRIDOR;
        } else {
            type = SpaceType.ROOM;
        }

        return region.withType(type);
    }

    /** 空気位置の上方 ROOF_SCAN_HEIGHT ブロック以内に固体があるか */
    private static boolean hasRoofAbove(BlockGetter level, BlockPos pos) {
        BlockPos cursor = pos.above();
        for (int i = 0; i < ROOF_SCAN_HEIGHT; i++) {
            if (WallAnalyzer.isSolid(level, cursor)) return true;
            cursor = cursor.above();
        }
        return false;
    }

    /** 空気位置の直下が固体か */
    private static boolean hasFloorBelow(BlockGetter level, BlockPos pos) {
        return WallAnalyzer.isSolid(level, pos.below());
    }
}
