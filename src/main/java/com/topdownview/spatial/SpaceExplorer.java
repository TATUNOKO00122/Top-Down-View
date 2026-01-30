package com.topdownview.spatial;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * 空間探索エンジン。
 *
 * <p>シード位置から BFS で空間を探索。境界判定は2段階：
 * <ul>
 *   <li>空気→空気遷移：遷移元の空気の平面（進行方向に垂直）で空気連結成分サイズ S を測定
 *     <ul>
 *       <li>S ≤ maxHoleSize → 壁の小穴。先は別空間、Opening 記録（BOUNDARY_HOLE）</li>
 *       <li>S > maxHoleSize → 開放。同一空間に追加</li>
 *     </ul>
 *   </li>
 *   <li>空気→固体遷移：壁解析。壁厚 ≤ maxWallThickness で向こう側が空気なら Opening 記録
 *     <ul>
 *       <li>向こう側の開口部サイズ ≤ maxHoleSize → BOUNDARY_HOLE（記録のみ）</li>
 *       <li>向こう側の開口部サイズ > maxHoleSize → PASSAGE（記録のみ）</li>
 *     </ul>
 *     壁の向こう側は常に別空間（同一空間には追加しない）</li>
 * </ul>
 *
 * <p>これにより「壁に1〜2ブロックの穴があっても部屋は部屋として認識」される。
 * 穴から外へ漏れるのを防ぐため、空気→空気の遷移でも平面連結成分で境界判定を行う。
 */
public final class SpaceExplorer {

    private SpaceExplorer() {
        throw new IllegalStateException("ユーティリティクラス");
    }

    /**
     * シード位置から空間を探索。
     *
     * @param level            ワールド
     * @param seed             探索開始位置（空気推奨）
     * @param maxBlocks        空気ブロック収集上限
     * @param maxWallThickness 壁厚上限。これ以下なら壁の向こう側を調査
     * @param maxHoleSize      壁の小穴として扱う最大サイズ
     * @return 探索された空間。type は UNKNOWN（分類は SpaceAnalyzer が行う）
     */
    public static SpaceRegion explore(BlockGetter level,
                                      BlockPos seed,
                                      int maxBlocks,
                                      int maxWallThickness,
                                      int maxHoleSize) {
        Set<BlockPos> airBlocks = new HashSet<>();
        Set<BlockPos> wallBlocks = new HashSet<>();
        Set<Opening> openings = new HashSet<>();

        // visited/recordedOpeningReps は long で管理し、BlockPos の equals/hashCode
        // オーバーヘッドを回避。airBlocks/wallBlocks は SpaceRegion 生成に使うため BlockPos のまま。
        Set<Long> visited = new HashSet<>();
        Set<Long> recordedOpeningReps = new HashSet<>();
        Set<Long> analyzedWallDirs = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(seed);
        visited.add(seed.asLong());

        // 平面 BFS のスキャン上限。maxHoleSize + 1 で十分
        // （maxHoleSize + 1 個目が見つかった瞬間に size > maxHoleSize が確定し打ち切れる）
        int maxHoleScan = maxHoleSize + 1;

        while (!queue.isEmpty() && airBlocks.size() < maxBlocks) {
            BlockPos pos = queue.poll();

            if (WallAnalyzer.isSolid(level, pos)) {
                wallBlocks.add(pos);
                analyzeWallForOpenings(level, pos, maxWallThickness, maxHoleSize, maxHoleScan,
                        openings, recordedOpeningReps, analyzedWallDirs);
            } else {
                airBlocks.add(pos);
                for (Direction dir : Direction.values()) {
                    BlockPos np = pos.relative(dir);
                    if (!visited.add(np.asLong())) continue;

                    if (WallAnalyzer.isSolid(level, np)) {
                        // np は固体 → 壁ブロック。壁解析で向こう側の Opening を記録
                        wallBlocks.add(np);
                        analyzeWallForOpenings(level, np, maxWallThickness, maxHoleSize, maxHoleScan,
                                openings, recordedOpeningReps, analyzedWallDirs);
                    } else {
                        // np は空気 → 壁の穴か開放か判定
                        // まず size だけ取得（Set 生成なし）。開放ケース（大半）で Set 生成を省略
                        int s = WallAnalyzer.countAirOnPlane(level, pos, dir, maxHoleScan);
                        if (s <= maxHoleSize) {
                            // 壁の小穴 → np は別空間、Opening 記録
                            // size が小さい（≤ maxHoleSize）ので collectAirOnPlane を呼び直しても軽い
                            Set<BlockPos> planeAir = WallAnalyzer.collectAirOnPlane(level, pos, dir, maxHoleScan);
                            recordOpening(planeAir, dir, OpeningType.BOUNDARY_HOLE,
                                    openings, recordedOpeningReps);
                        } else {
                            // 開放 → np を同一空間に追加
                            queue.add(np);
                        }
                    }
                }
            }
        }

        return new SpaceRegion(airBlocks, wallBlocks, openings, seed, SpaceType.UNKNOWN);
    }

    /**
     * 壁位置の6方向について壁解析を行い、壁の向こう側の開口部を Opening として記録する。
     * 壁の向こう側は常に別空間（同一空間には追加しない）。
     */
    private static void analyzeWallForOpenings(BlockGetter level,
                                               BlockPos wallPos,
                                               int maxWallThickness,
                                               int maxHoleSize,
                                               int maxHoleScan,
                                               Set<Opening> openings,
                                               Set<Long> recordedOpeningReps,
                                               Set<Long> analyzedWallDirs) {
        for (Direction dir : Direction.values()) {
            long key = makeWallDirKey(wallPos, dir);
            if (!analyzedWallDirs.add(key)) continue;

            int thickness = WallAnalyzer.measureWallThickness(level, wallPos, dir, maxWallThickness);
            if (thickness == 0) continue;
            if (thickness > maxWallThickness) continue;

            // 壁の向こう側の位置に進む
            BlockPos beyondPos = wallPos;
            for (int i = 0; i < thickness; i++) {
                beyondPos = beyondPos.relative(dir);
            }
            if (WallAnalyzer.isSolid(level, beyondPos)) continue;

            // 開口部ブロックを収集
            Set<BlockPos> openingBlocks = WallAnalyzer.collectAirOnPlane(level, beyondPos, dir, maxHoleScan);
            if (openingBlocks.isEmpty()) continue;

            int openingSize = openingBlocks.size();
            OpeningType type = WallAnalyzer.classifyOpening(openingSize, maxHoleSize);
            if (type == null) continue;

            recordOpening(openingBlocks, dir, type, openings, recordedOpeningReps);
        }
    }

    /**
     * Opening を記録。代表位置で重複排除する。
     */
    private static void recordOpening(Set<BlockPos> blocks,
                                      Direction dir,
                                      OpeningType type,
                                      Set<Opening> openings,
                                      Set<Long> recordedOpeningReps) {
        if (blocks.isEmpty()) return;
        // 代表位置（最小座標）で重複排除。long で格納し BlockPos 生成を回避
        BlockPos rep = findMinPos(blocks);
        if (!recordedOpeningReps.add(rep.asLong())) return;
        openings.add(new Opening(blocks, type, dir));
    }

    /** ブロック集合内の最小座標を返す */
    private static BlockPos findMinPos(Set<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
        }
        return new BlockPos(minX, minY, minZ);
    }

    /** (BlockPos, Direction) → long のハッシュキー生成 */
    private static long makeWallDirKey(BlockPos pos, Direction dir) {
        return (pos.asLong() << 3) | dir.get3DDataValue();
    }
}
