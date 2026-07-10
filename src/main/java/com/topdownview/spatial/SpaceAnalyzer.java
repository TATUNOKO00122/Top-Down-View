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

        // 屋根のサンプリング判定
        int sampleStep = Math.max(1, airCount / SAMPLE_LIMIT);
        int sampled = 0, roofed = 0;
        int idx = 0;
        for (BlockPos airPos : region.getAirBlocks()) {
            if (idx++ % sampleStep != 0) continue;
            sampled++;
            if (hasRoofAbove(level, airPos)) roofed++;
        }

        boolean hasRoof = sampled > 0 && roofed * 2 >= sampled;

        SpaceType type = hasRoof ? SpaceType.ENCLOSED : SpaceType.OUTDOOR;

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
}
